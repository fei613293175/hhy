#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

CONTAINER="${HHY_R08_DB_CONTAINER:-hhy-r08-disposable-postgres}"
case "${CONTAINER}" in
  hhy-r08-*) ;;
  *) echo "R08 disposable container name must start with hhy-r08-" >&2; exit 2 ;;
esac

cleanup() { docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "${CONTAINER}" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hhy_r08 \
  -v "${ROOT}:${ROOT}:ro" postgres:17.10-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r08 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r08 >/dev/null

psql() { docker exec -i "${CONTAINER}" psql "$@"; }
export CONTAINER
export -f psql
DATABASE_URL=postgresql://postgres@localhost/hhy_r08
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  migration_name="$(basename "${migration}")"
  migration_version="${migration_name%%__*}"
  migration_number=$((10#${migration_version#V}))
  (( migration_number > 33 )) && break
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
table_count="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${table_count}" == "200" ]] || {
  echo "R08 empty-database migration expected 200 tables, got ${table_count}" >&2
  exit 1
}
objects="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r08_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r08%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r08_%' AND NOT tgisinternal);")"
[[ "${objects}" == "3|2|1" ]] || { echo "R08 empty database objects unexpected: ${objects}" >&2; exit 1; }
"${PSQL[@]}" -f "${ROOT}/database/tests/r08_project_invariants.sql" >/dev/null
echo "R08_EMPTY_DATABASE_TO_V033 PASS tables=${table_count} objects=${objects}"

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  [[ "$(basename "${migration}")" == V033__* ]] && break
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000803','ACTIVE','R08UPGRADE');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'PROJECT','R08 legacy upgrade project','DRAFT' FROM hhy.users WHERE invite_code='R08UPGRADE';
  INSERT INTO hhy.project_details(content_id,cooperation,region)
    SELECT id,'legacy upgrade','CN-44' FROM hhy.content_posts WHERE title='R08 legacy upgrade project';
  INSERT INTO hhy.content_stats(content_id,organic_views,favorites)
    SELECT id,'legacy-value','legacy-value' FROM hhy.content_posts WHERE title='R08 legacy upgrade project';
  INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
    SELECT id,'legacy-v1','{\"description\":\"legacy\"}'::jsonb,'r08-upgrade'
    FROM hhy.content_posts WHERE title='R08 legacy upgrade project';" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V033__r08_project_invariants.sql" >/dev/null
upgrade_state="$(${PSQL[@]} -qAt -c "
  SELECT content.status,detail.cooperation,stats.organic_views,version.version_no,
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r08_%')
  FROM hhy.content_posts content
  JOIN hhy.project_details detail ON detail.content_id=content.id
  JOIN hhy.content_stats stats ON stats.content_id=content.id
  JOIN hhy.content_versions version ON version.content_id=content.id
  WHERE content.title='R08 legacy upgrade project';")"
[[ "${upgrade_state}" == "DRAFT|legacy upgrade|legacy-value|legacy-v1|3" ]] || {
  echo "R08 upgrade database state was unexpected: ${upgrade_state}" >&2
  exit 1
}
echo "R08_UPGRADE_DATABASE_TO_V033 PASS state=${upgrade_state}"

DATABASE_URL="${DATABASE_URL}" HHY_DB_SMOKE_CONFIRM=YES bash "${ROOT}/scripts/run_r08_database_invariants.sh"
echo "R08_DISPOSABLE_POSTGRES_CONTAINER PASS"
