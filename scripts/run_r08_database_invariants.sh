#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_DB_SMOKE_CONFIRM:?Set HHY_DB_SMOKE_CONFIRM=YES for a disposable database}"
[[ "${HHY_DB_SMOKE_CONFIRM}" == "YES" ]] || {
  echo "Refusing destructive R08 database tests without HHY_DB_SMOKE_CONFIRM=YES" >&2
  exit 2
}
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -f "${ROOT}/database/tests/r08_project_invariants.sql" >/dev/null
echo "R08_PROJECT_INVARIANTS PASS"

"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000802','ACTIVE','R08ROLLBACK');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'PROJECT','R08 rollback project','DRAFT' FROM hhy.users WHERE invite_code='R08ROLLBACK';
  INSERT INTO hhy.project_details(content_id,cooperation,region)
    SELECT id,'rollback verification','CN-31' FROM hhy.content_posts WHERE title='R08 rollback project';
  INSERT INTO hhy.content_stats(content_id,organic_views,favorites)
    SELECT id,'12','3' FROM hhy.content_posts WHERE title='R08 rollback project';
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
    SELECT id,'0','{\"description\":\"rollback\",\"categoryCode\":\"test\"}'::jsonb,'r08-runner'
    FROM hhy.content_posts WHERE title='R08 rollback project';" >/dev/null

before="$(${PSQL[@]} -qAt -c "
  SELECT content.id,detail.cooperation,stats.organic_views,version.version_no
  FROM hhy.content_posts content
  JOIN hhy.project_details detail ON detail.content_id=content.id
  JOIN hhy.content_stats stats ON stats.content_id=content.id
  JOIN hhy.content_versions version ON version.content_id=content.id
  WHERE content.title='R08 rollback project';")"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/rollback/U033__r08_project_invariants.sql" >/dev/null
remaining="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r08_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r08%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r08_%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_proc function JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
       WHERE namespace.nspname='hhy' AND function.proname LIKE '%r08%');")"
after="$(${PSQL[@]} -qAt -c "
  SELECT content.id,detail.cooperation,stats.organic_views,version.version_no
  FROM hhy.content_posts content
  JOIN hhy.project_details detail ON detail.content_id=content.id
  JOIN hhy.content_stats stats ON stats.content_id=content.id
  JOIN hhy.content_versions version ON version.content_id=content.id
  WHERE content.title='R08 rollback project';")"
[[ "${remaining}" == "0|0|0|0" && "${after}" == "${before}" ]] || {
  echo "R08 U033 rollback changed data or left objects: objects=${remaining} before=${before} after=${after}" >&2
  exit 1
}
echo "R08_U033_ROLLBACK PASS objects=${remaining} data=${after}"

"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V033__r08_project_invariants.sql" >/dev/null
reapplied="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r08_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r08%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r08_%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_proc function JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
       WHERE namespace.nspname='hhy' AND function.proname LIKE '%r08%');")"
[[ "${reapplied}" == "3|2|1|1" ]] || {
  echo "R08 V033 replay counts were unexpected: ${reapplied}" >&2
  exit 1
}
"${PSQL[@]}" -f "${ROOT}/database/tests/r08_project_invariants.sql" >/dev/null
echo "R08_V033_REPLAY PASS constraints=3 indexes=2 triggers=1 functions=1"
