#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R07 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_search_invariants.sql" >/dev/null
echo "R07_SEARCH_INVARIANTS PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U031__r07_search_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r07_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r07%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r07_%' AND NOT tgisinternal);")"
weight_default="$(${PSQL[@]} -qAt -c "
  SELECT COALESCE(column_default, '') FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='hot_search_terms' AND column_name='weight';")"
[[ "${remaining}|${weight_default}" == "0|0|0|" ]] || {
  echo "R07 rollback left database objects or weight default: ${remaining}|${weight_default}" >&2
  exit 1
}
echo "R07_U031_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V031__r07_search_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r07_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r07%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r07_%' AND NOT tgisinternal);")"
weight_default="$(${PSQL[@]} -qAt -c "
  SELECT COALESCE(column_default, '') FROM information_schema.columns
  WHERE table_schema='hhy' AND table_name='hot_search_terms' AND column_name='weight';")"
[[ "${reapplied}|${weight_default}" == "4|4|1|0" ]] || {
  echo "R07 reapply counts were unexpected: ${reapplied}|${weight_default}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_search_invariants.sql" >/dev/null
echo "R07_V031_REAPPLY PASS constraints=4 indexes=4 triggers=1"
