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
  echo "$(basename "$migration") APPLY"
  "${PSQL[@]}" -f "$migration" >/dev/null
  echo "$(basename "$migration") PASS"
done

TABLE_COUNT="$("${PSQL[@]}" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';")"
[[ "$TABLE_COUNT" == "188" ]] || { echo "Expected 188 hhy tables, got $TABLE_COUNT" >&2; exit 1; }
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
echo "POSTGRESQL_MIGRATION_SMOKE PASS"
