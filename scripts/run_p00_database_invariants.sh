#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"

if [[ "${HHY_DB_SMOKE_CONFIRM}" != "YES" ]]; then
  echo "Refusing to run destructive P00 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
fi
command -v psql >/dev/null 2>&1 || {
  echo "psql is required" >&2
  exit 2
}

psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 \
  -f "${ROOT}/database/tests/p00_event_ledger_invariants.sql"

suffix="$(date +%s)-${RANDOM}"
cash_account="P00-CONC-CASH-${suffix}"
user_account="P00-CONC-USER-${suffix}"
transaction_no="P00-CONC-TX-${suffix}"
idempotency_key="P00-CONC-IDEM-${suffix}"

psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 \
  -v cash_account="${cash_account}" -v user_account="${user_account}" <<'SQL'
SET search_path TO hhy, public;
INSERT INTO ledger_accounts(account_no, owner_type, owner_id, account_type, currency, status)
VALUES
  (:'cash_account', 'PLATFORM', :'cash_account', 'CASH', 'CNY', 'ACTIVE'),
  (:'user_account', 'USER', :'user_account', 'BALANCE', 'CNY', 'ACTIVE');
SQL

concurrency_dir="$(mktemp -d)"
cleanup() {
  rm -rf "${concurrency_dir}"
}
trap cleanup EXIT

run_accounting_writer() {
  local writer="$1"
  psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 \
    -v writer="${writer}" \
    -v cash_account="${cash_account}" \
    -v user_account="${user_account}" \
    -v transaction_no="${transaction_no}-${writer}" \
    -v idempotency_key="${idempotency_key}" <<'SQL'
SET search_path TO hhy, public;
BEGIN;
SELECT pg_advisory_xact_lock(97001002);
WITH inserted_transaction AS (
  INSERT INTO accounting_transactions(
    transaction_no, biz_type, biz_id, idempotency_key, status
  ) VALUES (
    :'transaction_no', 'P00_CONCURRENCY', :'writer', :'idempotency_key', 'POSTED'
  ) RETURNING id
)
INSERT INTO accounting_entries(
  transaction_id, account_id, direction, amount_cent, currency, sequence
)
SELECT t.id, a.id, v.direction, 100, 'CNY', v.sequence
FROM inserted_transaction t
CROSS JOIN (VALUES (:'cash_account', 'DEBIT', 1), (:'user_account', 'CREDIT', 2))
  AS v(account_no, direction, sequence)
JOIN ledger_accounts a ON a.account_no = v.account_no;
COMMIT;
SQL
}

set +e
run_accounting_writer one >"${concurrency_dir}/accounting-one.log" 2>&1 &
pid_one=$!
run_accounting_writer two >"${concurrency_dir}/accounting-two.log" 2>&1 &
pid_two=$!
wait "${pid_one}"; rc_one=$?
wait "${pid_two}"; rc_two=$?
set -e

if [[ $(( (rc_one == 0) + (rc_two == 0) )) -ne 1 ]]; then
  cat "${concurrency_dir}/accounting-one.log" >&2
  cat "${concurrency_dir}/accounting-two.log" >&2
  echo "Expected exactly one concurrent accounting writer to succeed" >&2
  exit 1
fi

accounting_counts="$(psql "${DATABASE_URL}" -qAt -v ON_ERROR_STOP=1 \
  -v idempotency_key="${idempotency_key}" <<'SQL'
SET search_path TO hhy, public;
SELECT count(*)::text || '|' || (
  SELECT count(*) FROM accounting_entries e
  JOIN accounting_transactions t ON t.id = e.transaction_id
  WHERE t.biz_type = 'P00_CONCURRENCY' AND t.idempotency_key = :'idempotency_key'
)::text
FROM accounting_transactions
WHERE biz_type = 'P00_CONCURRENCY' AND idempotency_key = :'idempotency_key';
SQL
)"
if [[ "${accounting_counts}" != "1|2" ]]; then
  echo "Concurrent accounting result mismatch: ${accounting_counts}" >&2
  exit 1
fi

recon_scene="P00-CONC-RECON-${suffix}"
run_recon_writer() {
  local writer="$1"
  psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 \
    -v writer="${writer}" -v recon_scene="${recon_scene}" <<'SQL'
SET search_path TO hhy, public;
INSERT INTO reconciliation_runs(
  run_no, scene, period_start, period_end, status
) VALUES (
  'P00-CONC-RECON-' || :'writer' || '-' || txid_current()::text,
  :'recon_scene', '2026-02-01T00:00:00Z', '2026-02-02T00:00:00Z', 'CREATED'
);
SQL
}

set +e
run_recon_writer one >"${concurrency_dir}/recon-one.log" 2>&1 &
recon_pid_one=$!
run_recon_writer two >"${concurrency_dir}/recon-two.log" 2>&1 &
recon_pid_two=$!
wait "${recon_pid_one}"; recon_rc_one=$?
wait "${recon_pid_two}"; recon_rc_two=$?
set -e

if [[ $(( (recon_rc_one == 0) + (recon_rc_two == 0) )) -ne 1 ]]; then
  cat "${concurrency_dir}/recon-one.log" >&2
  cat "${concurrency_dir}/recon-two.log" >&2
  echo "Expected exactly one overlapping reconciliation writer to succeed" >&2
  exit 1
fi

recon_count="$(psql "${DATABASE_URL}" -qAt -v ON_ERROR_STOP=1 \
  -v recon_scene="${recon_scene}" <<'SQL'
SET search_path TO hhy, public;
SELECT count(*) FROM reconciliation_runs WHERE scene = :'recon_scene';
SQL
)"
if [[ "${recon_count}" != "1" ]]; then
  echo "Concurrent reconciliation result mismatch: ${recon_count}" >&2
  exit 1
fi

echo "P00_DATABASE_CONCURRENCY_OK"
