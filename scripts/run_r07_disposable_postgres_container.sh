#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

CONTAINER="${HHY_R07_DB_CONTAINER:-hhy-r07-disposable-postgres}"
case "${CONTAINER}" in
  hhy-r07-*) ;;
  *) echo "R07 disposable container name must start with hhy-r07-" >&2; exit 2 ;;
esac

cleanup() { docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "${CONTAINER}" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hhy_r07 \
  -v "${ROOT}:${ROOT}:ro" postgres:17.10-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r07 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r07 >/dev/null

psql() { docker exec -i "${CONTAINER}" psql "$@"; }
export CONTAINER
export -f psql
DATABASE_URL=postgresql://postgres@localhost/hhy_r07
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
table_count="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${table_count}" == "200" ]] || {
  echo "R07 empty-database migration expected 200 tables, got ${table_count}" >&2
  exit 1
}
echo "R07_EMPTY_DATABASE_TO_V031 PASS tables=${table_count}"
"${PSQL[@]}" -f "${ROOT}/database/tests/r07_search_invariants.sql" >/dev/null

"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  [[ "$(basename "${migration}")" == V031__* ]] && break
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
"${PSQL[@]}" -c "
  INSERT INTO hhy.users(phone,status,invite_code) VALUES ('13900000702','ACTIVE','R07UPGRADE');
  INSERT INTO hhy.search_histories(user_id,keyword)
    SELECT id,'升级测试' FROM hhy.users WHERE invite_code='R07UPGRADE';
  INSERT INTO hhy.hot_search_terms(keyword,weight,enabled) VALUES ('升级热词',7,true);" >/dev/null
"${PSQL[@]}" --single-transaction -f "${ROOT}/database/migrations/V031__r07_search_invariants.sql" >/dev/null
upgrade_state="$(${PSQL[@]} -qAt -c "
  SELECT
    (SELECT count(*) FROM hhy.search_histories WHERE keyword='升级测试'),
    (SELECT count(*) FROM hhy.hot_search_terms WHERE keyword='升级热词'),
    (SELECT count(*) FROM pg_constraint WHERE connamespace='hhy'::regnamespace AND conname LIKE '%r07_%');")"
[[ "${upgrade_state}" == "1|1|4" ]] || {
  echo "R07 upgrade database state was unexpected: ${upgrade_state}" >&2
  exit 1
}
echo "R07_UPGRADE_DATABASE_TO_V031 PASS state=${upgrade_state}"

DATABASE_URL="${DATABASE_URL}" HHY_DB_SMOKE_CONFIRM=YES bash "${ROOT}/scripts/run_r07_database_invariants.sh"
echo "R07_DISPOSABLE_POSTGRES_CONTAINER PASS"
