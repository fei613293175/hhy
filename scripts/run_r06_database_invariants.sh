#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R06 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r06_content_home_invariants.sql" >/dev/null
echo "R06_CONTENT_HOME_INVARIANTS PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U029__r06_content_home_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r06_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r06%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r06_%' AND NOT tgisinternal);")"
[[ "${remaining}" == "0|0|0" ]] || {
  echo "R06 rollback left constraints, indexes, or triggers: ${remaining}" >&2
  exit 1
}
echo "R06_U029_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V029__r06_content_home_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r06_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r06%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r06_%' AND NOT tgisinternal);")"
[[ "${reapplied}" == "12|7|8" ]] || {
  echo "R06 reapply counts were unexpected: ${reapplied}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r06_content_home_invariants.sql" >/dev/null
echo "R06_V029_REAPPLY PASS constraints=12 indexes=7 triggers=8"
