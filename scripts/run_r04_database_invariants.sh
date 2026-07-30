#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R04 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r04_storage_media_invariants.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r04_media_upload_lifecycle_invariants.sql" >/dev/null
echo "R04_STORAGE_MEDIA_INVARIANTS PASS"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/rollback/U022__r04_media_upload_lifecycle.sql" \
  -f "${ROOT}/database/rollback/U021__r04_storage_media_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE 'ck_r04_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r04%');")"
[[ "${remaining}" == "0|0" ]] || {
  echo "R04 rollback left constraints or indexes: ${remaining}" >&2
  exit 1
}
echo "R04_U022_U021_ROLLBACK PASS"

"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/migrations/V021__r04_storage_media_invariants.sql" >/dev/null
"${PSQL[@]}" --single-transaction \
  -f "${ROOT}/database/migrations/V022__r04_media_upload_lifecycle.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r04_storage_media_invariants.sql" >/dev/null
"${PSQL[@]}" -f "${ROOT}/database/tests/r04_media_upload_lifecycle_invariants.sql" >/dev/null
echo "R04_V021_V022_REAPPLY PASS"
