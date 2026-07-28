#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL must point to a disposable PostgreSQL database}"
if [[ "${HHY_DB_SMOKE_CONFIRM:-}" != "YES" ]]; then
  echo "Refusing to reset schema hhy. Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database." >&2
  exit 2
fi
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE;" >/dev/null
for migration in "$ROOT"/database/migrations/V*.sql; do
  migration_name="$(basename "$migration")"
  migration_version="${migration_name%%__*}"
  migration_number=$((10#${migration_version#V}))
  (( migration_number >= 10 )) && continue
  echo "$(basename "$migration") APPLY"
  "${PSQL[@]}" --single-transaction -f "$migration" >/dev/null
  echo "$(basename "$migration") PASS"
done

echo "V009_TO_V010 UPGRADE"
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V010__p00_event_ledger_invariants.sql" >/dev/null
echo "V010__p00_event_ledger_invariants.sql PASS"

for migration in "$ROOT"/database/migrations/V*.sql; do
  migration_name="$(basename "$migration")"
  migration_version="${migration_name%%__*}"
  migration_number=$((10#${migration_version#V}))
  (( migration_number <= 10 )) && continue
  echo "$(basename "$migration") APPLY"
  "${PSQL[@]}" --single-transaction -f "$migration" >/dev/null
  echo "$(basename "$migration") PASS"
done

IDEMPOTENCY_SCOPE_WIDTH="$("${PSQL[@]}" -Atc "
  SELECT character_maximum_length
  FROM information_schema.columns
  WHERE table_schema='hhy'
    AND table_name='idempotency_records'
    AND column_name='scope';")"
[[ "$IDEMPOTENCY_SCOPE_WIDTH" == "128" ]] || {
  echo "Expected idempotency scope width 128, got $IDEMPOTENCY_SCOPE_WIDTH" >&2
  exit 1
}
echo "R01_IDEMPOTENCY_SCOPE_WIDTH $IDEMPOTENCY_SCOPE_WIDTH"
TOTP_REPLAY_COLUMN_COUNT="$("${PSQL[@]}" -Atc "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy'
    AND table_name='admin_mfa_methods'
    AND column_name='last_accepted_step';")"
[[ "$TOTP_REPLAY_COLUMN_COUNT" == "1" ]] || {
  echo "Expected the R01 TOTP replay column" >&2
  exit 1
}
echo "R01_TOTP_REPLAY_COLUMN PASS"

TABLE_COUNT="$("${PSQL[@]}" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';")"
[[ "$TABLE_COUNT" == "203" ]] || { echo "Expected 203 hhy tables, got $TABLE_COUNT" >&2; exit 1; }
echo "TABLE_COUNT $TABLE_COUNT"

"${PSQL[@]}" -f "$ROOT/database/tests/postgres_smoke_success.sql" >/dev/null
echo "BALANCED_TRANSACTION PASS"

expect_failure() {
  local name="$1"
  local sql_file
  sql_file="$(mktemp)"
  cat > "$sql_file"
  if "${PSQL[@]}" -f "$sql_file" >/dev/null 2>&1; then
    rm -f "$sql_file"
    echo "$name was incorrectly accepted" >&2
    exit 1
  fi
  rm -f "$sql_file"
  echo "$name BLOCKED"
}

expect_failure "UNBALANCED_TRANSACTION" <<'SQL'
SET search_path TO hhy, public;
DO $$
DECLARE
  tx_id bigint;
  debit_account bigint;
  credit_account bigint;
BEGIN
  SELECT id INTO debit_account FROM ledger_accounts WHERE account_no = 'SMOKE-CASH';
  SELECT id INTO credit_account FROM ledger_accounts WHERE account_no = 'SMOKE-REWARD';
  INSERT INTO accounting_transactions(transaction_no, biz_type, biz_id, idempotency_key, status)
  VALUES ('SMOKE-TX-UNBALANCED', 'SMOKE_TEST', 'SMOKE-BIZ-2', 'SMOKE-IDEM-2', 'POSTED')
  RETURNING id INTO tx_id;
  INSERT INTO accounting_entries(transaction_id, account_id, direction, amount_cent, currency, sequence)
  VALUES
    (tx_id, debit_account, 'DEBIT', 100, 'CNY', 1),
    (tx_id, credit_account, 'CREDIT', 99, 'CNY', 2);
  SET CONSTRAINTS ALL IMMEDIATE;
END $$;
SQL

expect_failure "POSTED_ENTRY_MUTATION" <<'SQL'
SET search_path TO hhy, public;
UPDATE accounting_entries
SET amount_cent = amount_cent + 1
WHERE transaction_id = (SELECT id FROM accounting_transactions WHERE transaction_no='SMOKE-TX-BALANCED')
  AND sequence = 1;
SQL

ROLE_CODES="$("${PSQL[@]}" -Atc "SELECT string_agg(code, ',' ORDER BY code) FROM hhy.admin_roles;")"
ADMIN_COUNT="$("${PSQL[@]}" -Atc "SELECT count(*) FROM hhy.admin_users;")"
[[ "$ROLE_CODES" == "AUDITOR,OPERATOR,SUPER_ADMIN" ]] || { echo "Unexpected roles: $ROLE_CODES" >&2; exit 1; }
[[ "$ADMIN_COUNT" == "0" ]] || { echo "A default administrator must not be seeded" >&2; exit 1; }
echo "SEEDED_ROLES $ROLE_CODES"
echo "DEFAULT_ADMIN_COUNT $ADMIN_COUNT"

"${PSQL[@]}" -f "$ROOT/database/verification/verify_baseline.sql" >/dev/null
echo "BASELINE_VERIFICATION PASS"

bash "$ROOT/scripts/run_p00_database_invariants.sh"
bash "$ROOT/scripts/run_r01_database_invariants.sh"
# The R01 invariant suite leaves a verified V016 schema. Continue its rollback
# checks from that exact historical point, then restore R02 below.

"${PSQL[@]}" -q <<'SQL' >/dev/null
INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,response_ref)
VALUES ('r01-short-scope','r01-u014-short',repeat('a',64),'r01-short-response');
SQL
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/rollback/U014__r01_idempotency_scope_capacity.sql" >/dev/null
U014_SHORT_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT
    (SELECT character_maximum_length FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='idempotency_records' AND column_name='scope'),
    (SELECT count(*) FROM hhy.idempotency_records
      WHERE scope='r01-short-scope' AND idem_key='r01-u014-short');")"
[[ "$U014_SHORT_STATE" == "64|1" ]] || {
  echo "U014 safe rollback did not preserve compatible data: $U014_SHORT_STATE" >&2
  exit 1
}
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/migrations/V014__r01_idempotency_scope_capacity.sql" >/dev/null
echo "U014_SAFE_ROLLBACK_REAPPLY_V014 PASS"

"${PSQL[@]}" -q <<'SQL' >/dev/null
INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,response_ref)
VALUES (repeat('l',76),'r01-u014-long',repeat('b',64),'r01-long-response');
SQL
U014_BLOCK_LOG="$(mktemp)"
set +e
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/rollback/U014__r01_idempotency_scope_capacity.sql" \
  >"$U014_BLOCK_LOG" 2>&1
U014_BLOCK_RC=$?
set -e
if [[ "$U014_BLOCK_RC" -eq 0 ]] \
  || ! grep -q "R01_IDEMPOTENCY_SCOPE_ROLLBACK_BLOCKED_LONG_VALUES_EXIST" "$U014_BLOCK_LOG"; then
  cat "$U014_BLOCK_LOG" >&2
  rm -f "$U014_BLOCK_LOG"
  echo "U014 must refuse to truncate persisted long scopes" >&2
  exit 1
fi
rm -f "$U014_BLOCK_LOG"
U014_BLOCK_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT
    (SELECT character_maximum_length FROM information_schema.columns
      WHERE table_schema='hhy' AND table_name='idempotency_records' AND column_name='scope'),
    (SELECT count(*) FROM hhy.idempotency_records
      WHERE char_length(scope)=76 AND idem_key='r01-u014-long');")"
[[ "$U014_BLOCK_STATE" == "128|1" ]] || {
  echo "Rejected U014 changed width or long-scope data: $U014_BLOCK_STATE" >&2
  exit 1
}
echo "U014_LONG_SCOPE_ROLLBACK_BLOCKED PASS"

"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/rollback/U015__r01_totp_replay_guard.sql" >/dev/null
U015_SAFE_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='admin_mfa_methods'
    AND column_name='last_accepted_step';")"
[[ "$U015_SAFE_STATE" == "0" ]] || {
  echo "U015 safe rollback did not remove the unused replay column" >&2
  exit 1
}
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/migrations/V015__r01_totp_replay_guard.sql" >/dev/null
echo "U015_SAFE_ROLLBACK_REAPPLY_V015 PASS"

"${PSQL[@]}" -q <<'SQL' >/dev/null
WITH admin_row AS (
  INSERT INTO hhy.admin_users(username,password_hash,status)
  VALUES ('r01-u015-replay',repeat('c',64),'ACTIVE')
  RETURNING id
)
INSERT INTO hhy.admin_mfa_methods(
  admin_user_id,method,secret_ref,status,last_accepted_step
)
SELECT id,'TOTP','secret-file:v1:r01-u015-replay','PENDING',123456
FROM admin_row;
SQL
U015_BLOCK_LOG="$(mktemp)"
set +e
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/rollback/U015__r01_totp_replay_guard.sql" \
  >"$U015_BLOCK_LOG" 2>&1
U015_BLOCK_RC=$?
set -e
if [[ "$U015_BLOCK_RC" -eq 0 ]] \
  || ! grep -q "R01_TOTP_REPLAY_GUARD_ROLLBACK_BLOCKED_CONSUMED_STEPS_EXIST" "$U015_BLOCK_LOG"; then
  cat "$U015_BLOCK_LOG" >&2
  rm -f "$U015_BLOCK_LOG"
  echo "U015 must refuse to erase consumed TOTP steps" >&2
  exit 1
fi
rm -f "$U015_BLOCK_LOG"
U015_BLOCK_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT count(*),min(last_accepted_step)
  FROM hhy.admin_mfa_methods
  WHERE admin_user_id=(SELECT id FROM hhy.admin_users WHERE username='r01-u015-replay');")"
[[ "$U015_BLOCK_STATE" == "1|123456" ]] || {
  echo "Rejected U015 changed consumed replay state: $U015_BLOCK_STATE" >&2
  exit 1
}
"${PSQL[@]}" -q -c "TRUNCATE hhy.admin_mfa_methods, hhy.admin_users RESTART IDENTITY CASCADE" >/dev/null
echo "U015_CONSUMED_STEP_ROLLBACK_BLOCKED PASS"

"${PSQL[@]}" -f "$ROOT/database/tests/r01_admin_self_rbac.sql" >/dev/null
echo "R01_ADMIN_SELF_RBAC PASS"

BOOTSTRAP_ADMIN_HASH='$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
DATABASE_URL="$DATABASE_URL" \
HHY_BOOTSTRAP_ADMIN_CONFIRM=YES \
HHY_BOOTSTRAP_ADMIN_USERNAME=r01-bootstrap-admin \
HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH="$BOOTSTRAP_ADMIN_HASH" \
  bash "$ROOT/scripts/bootstrap-admin.sh" >/dev/null
DATABASE_URL="$DATABASE_URL" \
HHY_BOOTSTRAP_ADMIN_CONFIRM=YES \
HHY_BOOTSTRAP_ADMIN_USERNAME=r01-bootstrap-admin \
HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH="$BOOTSTRAP_ADMIN_HASH" \
  bash "$ROOT/scripts/bootstrap-admin.sh" >/dev/null
BOOTSTRAP_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT count(*), min(admin.version), count(user_role.id)
  FROM hhy.admin_users AS admin
  JOIN hhy.admin_user_roles AS user_role ON user_role.admin_id=admin.id
  JOIN hhy.admin_roles AS role ON role.id=user_role.role_id AND role.code='SUPER_ADMIN'
  WHERE admin.username='r01-bootstrap-admin' AND admin.status='ACTIVE';")"
[[ "$BOOTSTRAP_STATE" == "1|0|1" ]] || {
  echo "Administrator bootstrap is not idempotent: $BOOTSTRAP_STATE" >&2
  exit 1
}
BOOTSTRAP_CONFLICT_HASH="${BOOTSTRAP_ADMIN_HASH%?}x"
BOOTSTRAP_CONFLICT_LOG="$(mktemp)"
set +e
DATABASE_URL="$DATABASE_URL" \
HHY_BOOTSTRAP_ADMIN_CONFIRM=YES \
HHY_BOOTSTRAP_ADMIN_USERNAME=r01-bootstrap-admin \
HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH="$BOOTSTRAP_CONFLICT_HASH" \
  bash "$ROOT/scripts/bootstrap-admin.sh" >"$BOOTSTRAP_CONFLICT_LOG" 2>&1
BOOTSTRAP_CONFLICT_RC=$?
set -e
if [[ "$BOOTSTRAP_CONFLICT_RC" -eq 0 ]] \
  || ! grep -q "ADMIN_BOOTSTRAP_CREDENTIAL_CONFLICT" "$BOOTSTRAP_CONFLICT_LOG"; then
  cat "$BOOTSTRAP_CONFLICT_LOG" >&2
  rm -f "$BOOTSTRAP_CONFLICT_LOG"
  echo "Administrator bootstrap must reject credential replacement" >&2
  exit 1
fi
rm -f "$BOOTSTRAP_CONFLICT_LOG"
echo "R01_BOOTSTRAP_ADMIN PASS"

"${PSQL[@]}" --single-transaction -f "$ROOT/database/rollback/U013__r01_admin_self_rbac.sql" >/dev/null
RBAC_ROLLBACK_STATE="$("${PSQL[@]}" -qAt -c "
  SELECT
    (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('admin.self.read','admin.self.security')),
    (SELECT count(*) FROM hhy.admin_role_permissions AS role_permission
      JOIN hhy.admin_roles AS role ON role.id=role_permission.role_id
      JOIN hhy.admin_permissions AS permission ON permission.id=role_permission.permission_id
      WHERE role.code='SUPER_ADMIN'
        AND permission.code IN ('admin.self.read','admin.self.security'));")"
[[ "$RBAC_ROLLBACK_STATE" == "0|0" ]] || {
  echo "U013 did not cleanly roll back the baseline RBAC seed: $RBAC_ROLLBACK_STATE" >&2
  exit 1
}
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V013__r01_admin_self_rbac.sql" >/dev/null
"${PSQL[@]}" -f "$ROOT/database/tests/r01_admin_self_rbac.sql" >/dev/null
echo "U013_REAPPLY_V013 PASS"

"${PSQL[@]}" --single-transaction -f "$ROOT/database/rollback/U012__r01_admin_security_invariants.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V012__r01_admin_security_invariants.sql" >/dev/null
"${PSQL[@]}" -f "$ROOT/database/tests/r01_admin_security_invariants.sql" >/dev/null
echo "U012_REAPPLY_V012 PASS"

"${PSQL[@]}" --single-transaction -f "$ROOT/database/rollback/U010__p00_event_ledger_invariants.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V010__p00_event_ledger_invariants.sql" >/dev/null
"${PSQL[@]}" -f "$ROOT/database/tests/p00_event_ledger_invariants.sql" >/dev/null
echo "U010_REAPPLY_V010 PASS"

"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V017__r02_user_auth_invariants.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V018__r02_admin_user_controls.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V019__r03_provider_configuration_center.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V020__r03_auth_registration_policy_alignment.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V021__r04_storage_media_invariants.sql" >/dev/null
"${PSQL[@]}" --single-transaction -f "$ROOT/database/migrations/V022__r04_media_upload_lifecycle.sql" >/dev/null
"${PSQL[@]}" -f "$ROOT/database/tests/r02_admin_user_controls.sql" >/dev/null
"${PSQL[@]}" -f "$ROOT/database/tests/r03_provider_configuration_invariants.sql" >/dev/null
DATABASE_URL="$DATABASE_URL" HHY_DB_SMOKE_CONFIRM=YES \
  bash "$ROOT/scripts/run_r04_database_invariants.sh"
for migration in "$ROOT"/database/migrations/V*.sql; do
  migration_name="$(basename "$migration")"
  migration_version="${migration_name%%__*}"
  migration_number=$((10#${migration_version#V}))
  (( migration_number <= 22 )) && continue
  (( migration_number >= 39 )) && break
  "${PSQL[@]}" --single-transaction -f "$migration" >/dev/null
done
echo "MIGRATIONS_TO_V038_AFTER_R04 PASS"
DATABASE_URL="$DATABASE_URL" HHY_DB_SMOKE_CONFIRM=YES \
  bash "$ROOT/scripts/run_r11_database_invariants.sh"
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/migrations/V039__r12_publish_management_invariants.sql" >/dev/null
# The R12 invariant runner includes the granular permission projection test.
# Its object-count assertions intentionally describe V039, so apply the
# data-only V041 alignment here while V040 remains covered by the R14 chain.
"${PSQL[@]}" --single-transaction \
  -f "$ROOT/database/migrations/V041__r12_review_permission_alignment.sql" >/dev/null
DATABASE_URL="$DATABASE_URL" HHY_DB_SMOKE_CONFIRM=YES \
  bash "$ROOT/scripts/run_r12_database_invariants.sh"
DATABASE_URL="$DATABASE_URL" HHY_DB_SMOKE_CONFIRM=YES \
  bash "$ROOT/scripts/run_r14_database_invariants.sh"
FINAL_TABLE_COUNT="$("${PSQL[@]}" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';")"
[[ "$FINAL_TABLE_COUNT" == "203" ]] || {
  echo "Final schema must contain 203 tables, got $FINAL_TABLE_COUNT" >&2
  exit 1
}
echo "R02_ADMIN_USER_CONTROLS PASS"
echo "R03_PROVIDER_CONFIGURATION_INVARIANTS PASS"
echo "R04_STORAGE_MEDIA_INVARIANTS PASS"
echo "FINAL_TABLE_COUNT $FINAL_TABLE_COUNT"

"${PSQL[@]}" -f "$ROOT/database/verification/verify_baseline.sql" >/dev/null
FINAL_IDEMPOTENCY_SCOPE_WIDTH="$("${PSQL[@]}" -Atc "
  SELECT character_maximum_length
  FROM information_schema.columns
  WHERE table_schema='hhy'
    AND table_name='idempotency_records'
    AND column_name='scope';")"
[[ "$FINAL_IDEMPOTENCY_SCOPE_WIDTH" == "128" ]] || {
  echo "Final idempotency scope width must be 128, got $FINAL_IDEMPOTENCY_SCOPE_WIDTH" >&2
  exit 1
}
echo "FINAL_R01_IDEMPOTENCY_SCOPE_WIDTH $FINAL_IDEMPOTENCY_SCOPE_WIDTH"
FINAL_TOTP_REPLAY_COLUMN_COUNT="$("${PSQL[@]}" -Atc "
  SELECT count(*) FROM information_schema.columns
  WHERE table_schema='hhy'
    AND table_name='admin_mfa_methods'
    AND column_name='last_accepted_step';")"
[[ "$FINAL_TOTP_REPLAY_COLUMN_COUNT" == "1" ]] || {
  echo "Final R01 TOTP replay column is missing" >&2
  exit 1
}
echo "FINAL_R01_TOTP_REPLAY_COLUMN PASS"
echo "FINAL_BASELINE_VERIFICATION PASS"
echo "POSTGRESQL_MIGRATION_SMOKE PASS"
