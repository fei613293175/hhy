#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R09 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r09_app_promotion_invariants.sql" >/dev/null
echo "R09_APP_PROMOTION_INVARIANTS PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000902','ACTIVE','R09ROLLBACK');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'APP','R09 rollback App','DRAFT' FROM hhy.users WHERE invite_code='R09ROLLBACK';
  INSERT INTO hhy.app_details(content_id,app_name,platform,download_url,website)
    SELECT id,'Rollback App','ANDROID','https://download.example.invalid/rollback','https://app.example.invalid'
    FROM hhy.content_posts WHERE title='R09 rollback App';" >/dev/null

before="$(${PSQL[@]} -qAt -c "
  SELECT content.id,detail.app_name,detail.platform,detail.download_url
  FROM hhy.content_posts content
  JOIN hhy.app_details detail ON detail.content_id=content.id
  WHERE content.title='R09 rollback App';")"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U035__r09_app_promotion_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r09_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r09%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r09_%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_proc function JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
       WHERE namespace.nspname='hhy' AND function.proname LIKE '%r09%');")"
after="$(${PSQL[@]} -qAt -c "
  SELECT content.id,detail.app_name,detail.platform,detail.download_url
  FROM hhy.content_posts content
  JOIN hhy.app_details detail ON detail.content_id=content.id
  WHERE content.title='R09 rollback App';")"
[[ "${remaining}" == "0|0|0|0" && "${after}" == "${before}" ]] || {
  echo "R09 U035 rollback changed data or left objects: objects=${remaining} before=${before} after=${after}" >&2
  exit 1
}
echo "R09_U035_ROLLBACK PASS objects=${remaining} data=${after}"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V035__r09_app_promotion_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r09_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r09%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r09_%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_proc function JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
       WHERE namespace.nspname='hhy' AND function.proname LIKE '%r09%');")"
[[ "${reapplied}" == "2|2|4|4" ]] || {
  echo "R09 V035 replay counts were unexpected: ${reapplied}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r09_app_promotion_invariants.sql" >/dev/null
echo "R09_V035_REPLAY PASS constraints=2 indexes=2 triggers=4 functions=4"
