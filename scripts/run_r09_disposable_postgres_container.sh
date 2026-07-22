#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

CONTAINER="${HHY_R09_DB_CONTAINER:-hhy-r09-disposable-postgres}"
case "${CONTAINER}" in
  hhy-r09-*) ;;
  *) echo "R09 disposable container name must start with hhy-r09-" >&2; exit 2 ;;
esac

cleanup() { docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "${CONTAINER}" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hhy_r09 \
  -v "${ROOT}:${ROOT}:ro" postgres:17.10-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r09 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r09 >/dev/null

psql() { docker exec -i "${CONTAINER}" psql "$@"; }
export CONTAINER
export -f psql
DATABASE_URL=postgresql://postgres@localhost/hhy_r09
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
table_count="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${table_count}" == "200" ]] || {
  echo "R09 empty-database migration expected 200 tables, got ${table_count}" >&2
  exit 1
}
objects="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r09_%'),
    (SELECT count(*) FROM pg_indexes WHERE schemaname='hhy' AND indexname LIKE '%r09%'),
    (SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'trg_r09_%' AND NOT tgisinternal),
    (SELECT count(*) FROM pg_proc function JOIN pg_namespace namespace ON namespace.oid=function.pronamespace
       WHERE namespace.nspname='hhy' AND function.proname LIKE '%r09%');")"
[[ "${objects}" == "2|2|4|4" ]] || { echo "R09 empty database objects unexpected: ${objects}" >&2; exit 1; }
"${PSQL[@]}" -f "${ROOT}/database/tests/r09_app_promotion_invariants.sql" >/dev/null
echo "R09_EMPTY_DATABASE_TO_V035 PASS tables=${table_count} objects=${objects}"

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  [[ "$(basename "${migration}")" == V035__* ]] && break
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000903','ACTIVE','R09UPGRADE');
  INSERT INTO hhy.content_posts(owner_id,type,title,status)
    SELECT id,'APP','R09 legacy upgrade App','DRAFT' FROM hhy.users WHERE invite_code='R09UPGRADE';
  INSERT INTO hhy.app_details(content_id,app_name,platform,version_text,download_url,website)
    SELECT id,'Legacy App','ANDROID','0.9','https://download.example.invalid/legacy','https://legacy.example.invalid'
    FROM hhy.content_posts WHERE title='R09 legacy upgrade App';" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V035__r09_app_promotion_invariants.sql" >/dev/null
upgrade_state="$(${PSQL[@]} -qAt -c "
  SELECT content.status,detail.app_name,detail.platform,detail.version_text,
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r09_%')
  FROM hhy.content_posts content
  JOIN hhy.app_details detail ON detail.content_id=content.id
  WHERE content.title='R09 legacy upgrade App';")"
[[ "${upgrade_state}" == "DRAFT|Legacy App|ANDROID|0.9|2" ]] || {
  echo "R09 upgrade database state was unexpected: ${upgrade_state}" >&2
  exit 1
}
echo "R09_UPGRADE_DATABASE_TO_V035 PASS state=${upgrade_state}"

DATABASE_URL="${DATABASE_URL}" HHY_DB_SMOKE_CONFIRM=YES bash "${ROOT}/scripts/run_r09_database_invariants.sh"
echo "R09_DISPOSABLE_POSTGRES_CONTAINER PASS"
