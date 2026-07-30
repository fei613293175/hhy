#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"

if [[ "${HHY_DB_SMOKE_CONFIRM}" != "YES" ]]; then
  echo "Refusing to run destructive R01 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
fi
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r01_admin_security_invariants.sql"

suffix="$(date +%s)-${RANDOM}"
username="r01-concurrency-${suffix}"
code_hash="$(printf '%064d' "${RANDOM}")"
admin_id="$("${PSQL[@]}" -qAt -v username="${username}" -v code_hash="${code_hash}" <<'SQL'
SET search_path TO hhy, public;
WITH admin_row AS (
  INSERT INTO admin_users(username, password_hash, status)
  VALUES (:'username', repeat('c', 64), 'ACTIVE')
  RETURNING id
), recovery_row AS (
  INSERT INTO admin_recovery_codes(admin_user_id, code_hash, expires_at)
  SELECT id, :'code_hash', clock_timestamp() + interval '1 day' FROM admin_row
)
SELECT id FROM admin_row;
SQL
)"

concurrency_dir="$(mktemp -d)"
cleanup() { rm -rf "${concurrency_dir}"; }
trap cleanup EXIT

consume_code() {
  local writer="$1"
  "${PSQL[@]}" -qAt -v admin_id="${admin_id}" -v code_hash="${code_hash}" \
    >"${concurrency_dir}/${writer}.log" 2>&1 <<'SQL'
SET search_path TO hhy, public;
SELECT hhy.consume_admin_recovery_code(:'admin_id'::bigint, :'code_hash');
SQL
}

consume_code one & pid_one=$!
consume_code two & pid_two=$!
wait "${pid_one}"
wait "${pid_two}"

result_one="$(tr -d '[:space:]' <"${concurrency_dir}/one.log")"
result_two="$(tr -d '[:space:]' <"${concurrency_dir}/two.log")"
if [[ "${result_one}|${result_two}" != "t|f" && "${result_one}|${result_two}" != "f|t" ]]; then
  cat "${concurrency_dir}/one.log" >&2
  cat "${concurrency_dir}/two.log" >&2
  echo "Expected exactly one recovery-code consumer to succeed" >&2
  exit 1
fi

used_count="$("${PSQL[@]}" -qAt -v code_hash="${code_hash}" <<'SQL'
SELECT count(*) FROM hhy.admin_recovery_codes
WHERE code_hash=:'code_hash' AND used_at IS NOT NULL;
SQL
)"
[[ "${used_count}" == "1" ]] || { echo "Recovery-code used count mismatch: ${used_count}" >&2; exit 1; }
echo "R01_RECOVERY_CODE_CONCURRENCY_OK"

refresh_hash="r01-refresh-${suffix}"
insert_session() {
  local writer="$1"
  "${PSQL[@]}" -qAt -v admin_id="${admin_id}" -v refresh_hash="${refresh_hash}" \
    -v access_jti="r01-jti-${suffix}-${writer}" >"${concurrency_dir}/session-${writer}.log" 2>&1 <<'SQL'
SET search_path TO hhy, public;
INSERT INTO admin_sessions(admin_user_id, access_jti, refresh_hash, expires_at, last_active_at)
VALUES (:'admin_id'::bigint, :'access_jti', :'refresh_hash',
        clock_timestamp()+interval '1 hour', clock_timestamp());
SQL
}

set +e
insert_session one & session_pid_one=$!
insert_session two & session_pid_two=$!
wait "${session_pid_one}"; session_rc_one=$?
wait "${session_pid_two}"; session_rc_two=$?
set -e
if [[ $(( (session_rc_one == 0) + (session_rc_two == 0) )) -ne 1 ]]; then
  cat "${concurrency_dir}/session-one.log" "${concurrency_dir}/session-two.log" >&2
  echo "Expected exactly one duplicate refresh-hash session insert to succeed" >&2
  exit 1
fi

revoke_session() {
  local writer="$1"
  "${PSQL[@]}" -qAt -v refresh_hash="${refresh_hash}" \
    >"${concurrency_dir}/revoke-${writer}.log" 2>&1 <<'SQL'
SET search_path TO hhy, public;
WITH revoked AS (
  UPDATE admin_sessions
  SET revoked_at=clock_timestamp(), version=version+1
  WHERE refresh_hash=:'refresh_hash' AND version=0 AND revoked_at IS NULL
  RETURNING id
)
SELECT count(*) FROM revoked;
SQL
}
revoke_session one & revoke_pid_one=$!
revoke_session two & revoke_pid_two=$!
wait "${revoke_pid_one}"
wait "${revoke_pid_two}"
revoke_one="$(tr -d '[:space:]' <"${concurrency_dir}/revoke-one.log")"
revoke_two="$(tr -d '[:space:]' <"${concurrency_dir}/revoke-two.log")"
if [[ "${revoke_one}|${revoke_two}" != "1|0" && "${revoke_one}|${revoke_two}" != "0|1" ]]; then
  echo "Expected one optimistic session revoke: ${revoke_one}|${revoke_two}" >&2
  exit 1
fi
echo "R01_SESSION_CONCURRENCY_OK"

insert_mfa() {
  local writer="$1"
  "${PSQL[@]}" -qAt -v admin_id="${admin_id}" -v secret_ref="vault://r01/${suffix}/${writer}" \
    >"${concurrency_dir}/mfa-${writer}.log" 2>&1 <<'SQL'
SET search_path TO hhy, public;
INSERT INTO admin_mfa_methods(admin_user_id, method, secret_ref)
VALUES (:'admin_id'::bigint, 'TOTP', :'secret_ref');
SQL
}
set +e
insert_mfa one & mfa_pid_one=$!
insert_mfa two & mfa_pid_two=$!
wait "${mfa_pid_one}"; mfa_rc_one=$?
wait "${mfa_pid_two}"; mfa_rc_two=$?
set -e
if [[ $(( (mfa_rc_one == 0) + (mfa_rc_two == 0) )) -ne 1 ]]; then
  cat "${concurrency_dir}/mfa-one.log" "${concurrency_dir}/mfa-two.log" >&2
  echo "Expected exactly one concurrent MFA enrollment to succeed" >&2
  exit 1
fi
echo "R01_MFA_ENROLLMENT_CONCURRENCY_OK"

idem_scope="adminSelfPostMfaEnroll:${admin_id}"
idem_key="r01-idem-${suffix}"
insert_idempotency() {
  local writer="$1"
  "${PSQL[@]}" -qAt -v scope="${idem_scope}" -v idem_key="${idem_key}" -v writer="${writer}" \
    >"${concurrency_dir}/idem-${writer}.log" 2>&1 <<'SQL'
SET search_path TO hhy, public;
INSERT INTO idempotency_records(scope, idem_key, request_hash, response_ref)
VALUES (:'scope', :'idem_key', md5(:'writer') || md5(:'writer'), 'r01-response-' || :'writer');
SQL
}
set +e
insert_idempotency one & idem_pid_one=$!
insert_idempotency two & idem_pid_two=$!
wait "${idem_pid_one}"; idem_rc_one=$?
wait "${idem_pid_two}"; idem_rc_two=$?
set -e
if [[ $(( (idem_rc_one == 0) + (idem_rc_two == 0) )) -ne 1 ]]; then
  cat "${concurrency_dir}/idem-one.log" "${concurrency_dir}/idem-two.log" >&2
  echo "Expected exactly one concurrent idempotency claim to succeed" >&2
  exit 1
fi
echo "R01_IDEMPOTENCY_CONCURRENCY_OK"

reset_and_apply_until() {
  local max_version="$1"
  "${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
  for migration in "${ROOT}"/database/migrations/V*.sql; do
    local migration_name migration_version migration_number
    migration_name="$(basename "${migration}")"
    migration_version="${migration_name%%__*}"
    migration_number=$((10#${migration_version#V}))
    (( migration_number > max_version )) && continue
    "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
  done
}

reset_and_apply_until 11
"${PSQL[@]}" <<'SQL' >/dev/null
SET search_path TO hhy, public;
INSERT INTO admin_users(username,password_hash,status,mfa_secret_ref)
VALUES ('r01-legacy-upgrade',repeat('e',64),'ACTIVE','vault://legacy/same');
SQL
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V012__r01_admin_security_invariants.sql" >/dev/null
legacy_upgrade="$(${PSQL[@]} -qAt -c "SELECT u.mfa_secret_ref IS NULL, m.status, m.secret_ref FROM hhy.admin_users u JOIN hhy.admin_mfa_methods m ON m.admin_user_id=u.id AND m.method='TOTP' WHERE u.username='r01-legacy-upgrade';")"
[[ "${legacy_upgrade}" == "t|ACTIVE|vault://legacy/same" ]] || {
  echo "Legacy MFA upgrade mismatch: ${legacy_upgrade}" >&2; exit 1;
}
"${PSQL[@]}" <<'SQL' >/dev/null
SET search_path TO hhy, public;
WITH admin_row AS (
  INSERT INTO admin_users(username,password_hash,status)
  VALUES ('r01-long-secret-ref',repeat('a',64),'ACTIVE') RETURNING id
)
INSERT INTO admin_mfa_methods(admin_user_id,method,secret_ref)
SELECT id,'TOTP',repeat('z',300) FROM admin_row;
UPDATE admin_mfa_methods
SET status='ACTIVE',confirmed_at=clock_timestamp(),version=version+1
WHERE admin_user_id=(SELECT id FROM admin_users WHERE username='r01-long-secret-ref');
SQL
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U012__r01_admin_security_invariants.sql" >/dev/null
legacy_rollback="$(${PSQL[@]} -qAt -c "SELECT mfa_secret_ref FROM hhy.admin_users WHERE username='r01-legacy-upgrade';")"
[[ "${legacy_rollback}" == "vault://legacy/same" ]] || {
  echo "U012 did not preserve the legacy MFA reference" >&2; exit 1;
}
long_rollback="$(${PSQL[@]} -qAt -c "SELECT u.mfa_secret_ref IS NULL,char_length(m.secret_ref) FROM hhy.admin_users u JOIN hhy.admin_mfa_methods m ON m.admin_user_id=u.id WHERE u.username='r01-long-secret-ref';")"
[[ "${long_rollback}" == "t|300" ]] || {
  echo "U012 truncated or lost a long normalized MFA reference: ${long_rollback}" >&2; exit 1;
}
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V012__r01_admin_security_invariants.sql" >/dev/null
echo "R01_V011_UPGRADE_U012_REAPPLY_OK"

reset_and_apply_until 11
"${PSQL[@]}" <<'SQL' >/dev/null
SET search_path TO hhy, public;
WITH admin_row AS (
  INSERT INTO admin_users(username,password_hash,status,mfa_secret_ref)
  VALUES ('r01-legacy-conflict',repeat('f',64),'ACTIVE','vault://legacy/a')
  RETURNING id
)
INSERT INTO admin_mfa_methods(admin_user_id,method,secret_ref,status)
SELECT id,'TOTP','vault://method/b','PENDING' FROM admin_row;
SQL
set +e
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V012__r01_admin_security_invariants.sql" \
  >"${concurrency_dir}/legacy-conflict.log" 2>&1
conflict_rc=$?
set -e
if [[ "${conflict_rc}" -eq 0 ]] || ! grep -q "ADMIN_MFA_LEGACY_CONFLICT" "${concurrency_dir}/legacy-conflict.log"; then
  cat "${concurrency_dir}/legacy-conflict.log" >&2
  echo "Conflicting legacy MFA facts must fail the V012 upgrade explicitly" >&2
  exit 1
fi
conflict_state="$(${PSQL[@]} -qAt -c "SELECT u.mfa_secret_ref,m.secret_ref,m.status FROM hhy.admin_users u JOIN hhy.admin_mfa_methods m ON m.admin_user_id=u.id WHERE u.username='r01-legacy-conflict';")"
[[ "${conflict_state}" == "vault://legacy/a|vault://method/b|PENDING" ]] || {
  echo "Conflicting MFA facts changed after rejected upgrade: ${conflict_state}" >&2; exit 1;
}
echo "R01_LEGACY_MFA_CONFLICT_ROLLBACK_OK"

reset_and_apply_until 11
"${PSQL[@]}" <<'SQL' >/dev/null
SET search_path TO hhy, public;
WITH admin_row AS (
  INSERT INTO admin_users(username,password_hash,status,mfa_secret_ref)
  VALUES ('r01-refresh-conflict',repeat('b',64),'ACTIVE','vault://legacy/atomic')
  RETURNING id
)
INSERT INTO admin_sessions(admin_user_id,access_jti,refresh_hash,expires_at)
SELECT id,'r01-duplicate-jti-1','r01-duplicate-refresh',clock_timestamp()+interval '1 hour' FROM admin_row
UNION ALL
SELECT id,'r01-duplicate-jti-2','r01-duplicate-refresh',clock_timestamp()+interval '1 hour' FROM admin_row;
SQL
set +e
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V012__r01_admin_security_invariants.sql" \
  >"${concurrency_dir}/refresh-conflict.log" 2>&1
refresh_conflict_rc=$?
set -e
if [[ "${refresh_conflict_rc}" -eq 0 ]] || ! grep -q "ADMIN_SESSION_DUPLICATE_REFRESH_HASH" "${concurrency_dir}/refresh-conflict.log"; then
  cat "${concurrency_dir}/refresh-conflict.log" >&2
  echo "Duplicate refresh hashes must reject V012 before migration" >&2
  exit 1
fi
atomic_state="$(${PSQL[@]} -qAt -c "SELECT mfa_secret_ref,(SELECT count(*) FROM pg_constraint WHERE conname='ck_admin_users_legacy_mfa_secret_empty') FROM hhy.admin_users WHERE username='r01-refresh-conflict';")"
[[ "${atomic_state}" == "vault://legacy/atomic|0" ]] || {
  echo "Rejected V012 left a partially migrated schema or MFA value: ${atomic_state}" >&2; exit 1;
}
echo "R01_V012_ATOMIC_CONFLICT_ROLLBACK_OK"

reset_and_apply_until 12
"${PSQL[@]}" -f "${ROOT}/database/verification/verify_baseline.sql" >/dev/null
echo "R01_FINAL_EMPTY_DATABASE_OK"

reset_and_apply_until 16
"${PSQL[@]}" -f "${ROOT}/database/tests/r01_idempotency_snapshot_invariants.sql" >/dev/null
echo "R01_V016_SNAPSHOT_PAIR_OK"

"${PSQL[@]}" <<'SQL' >/dev/null
SET search_path TO hhy, public;
INSERT INTO idempotency_records(
  scope, idem_key, request_hash, response_ref,
  response_type, response_payload_ciphertext, expires_at)
VALUES (
  'r01.rollback.block', 'snapshot-present', repeat('b', 64), 'session:23',
  'r01.command-result.v1', 'hhy-idem-v1.A256GCM.v1.test-nonce.test-ciphertext',
  clock_timestamp() + interval '1 day');
SQL
set +e
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U016__r01_idempotency_response_snapshots.sql" \
  >"${concurrency_dir}/u016-blocked.log" 2>&1
u016_blocked_rc=$?
set -e
if [[ "${u016_blocked_rc}" -eq 0 ]] \
    || ! grep -q "R01_IDEMPOTENCY_SNAPSHOT_ROLLBACK_BLOCKED_SNAPSHOTS_EXIST" \
      "${concurrency_dir}/u016-blocked.log"; then
  cat "${concurrency_dir}/u016-blocked.log" >&2
  echo "U016 must reject rollback while any snapshot column contains data" >&2
  exit 1
fi
snapshot_columns_after_block="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND table_name='idempotency_records' AND column_name IN ('response_type','response_payload_ciphertext');")"
[[ "${snapshot_columns_after_block}" == "2" ]] || {
  echo "Rejected U016 changed snapshot columns" >&2; exit 1;
}
echo "R01_U016_BLOCKED_WITH_SNAPSHOT_OK"

reset_and_apply_until 16
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U016__r01_idempotency_response_snapshots.sql" >/dev/null
snapshot_columns_after_safe="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='hhy' AND table_name='idempotency_records' AND column_name IN ('response_type','response_payload_ciphertext');")"
[[ "${snapshot_columns_after_safe}" == "0" ]] || {
  echo "Safe U016 did not remove both snapshot columns" >&2; exit 1;
}
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V016__r01_idempotency_response_snapshots.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/verification/verify_baseline.sql" >/dev/null
echo "R01_U016_SAFE_ROLLBACK_REAPPLY_OK"
