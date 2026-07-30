#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

CONTAINER="${HHY_R06_DB_CONTAINER:-hhy-r06-disposable-postgres}"
case "${CONTAINER}" in
  hhy-r06-*) ;;
  *) echo "R06 disposable container name must start with hhy-r06-" >&2; exit 2 ;;
esac

cleanup() { docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "${CONTAINER}" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hhy_r06 \
  -v "${ROOT}:${ROOT}:ro" postgres:17.10-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r06 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${CONTAINER}" pg_isready -h 127.0.0.1 -U postgres -d hhy_r06 >/dev/null

psql() { docker exec -i "${CONTAINER}" psql "$@"; }
export CONTAINER
export -f psql
DATABASE_URL=postgresql://postgres@localhost/hhy_r06
PSQL=(psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1)
"${PSQL[@]}" -c "DROP SCHEMA IF EXISTS hhy CASCADE" >/dev/null
for migration in "${ROOT}"/database/migrations/V*.sql; do
  "${PSQL[@]}" --single-transaction -f "${migration}" >/dev/null
done
table_count="$(${PSQL[@]} -qAt -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE'")"
[[ "${table_count}" == "200" ]] || {
  echo "R06 empty-database migration expected 200 tables, got ${table_count}" >&2
  exit 1
}
echo "R06_EMPTY_DATABASE_TO_V030 PASS tables=${table_count}"
DATABASE_URL="${DATABASE_URL}" HHY_DB_SMOKE_CONFIRM=YES bash "${ROOT}/scripts/run_r06_database_invariants.sh"
echo "R06_DISPOSABLE_POSTGRES_CONTAINER PASS"
