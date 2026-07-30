#!/usr/bin/env bash
set -euo pipefail

: "${HHY_OWNER_TEST_PROMOTION_CONFIRM:?HHY_OWNER_TEST_PROMOTION_CONFIRM is required}"
: "${HHY_OWNER_TEST_RELEASE:?HHY_OWNER_TEST_RELEASE is required}"
: "${HHY_OWNER_TEST_SOURCE_COMMIT:?HHY_OWNER_TEST_SOURCE_COMMIT is required}"
: "${HHY_OWNER_TEST_IMAGE:?HHY_OWNER_TEST_IMAGE is required}"
: "${HHY_OWNER_TEST_CURRENT_CONTAINER:?HHY_OWNER_TEST_CURRENT_CONTAINER is required}"
: "${HHY_OWNER_TEST_TARGET_CONTAINER:?HHY_OWNER_TEST_TARGET_CONTAINER is required}"
: "${HHY_OWNER_TEST_CURRENT_UPSTREAM:?HHY_OWNER_TEST_CURRENT_UPSTREAM is required}"
: "${HHY_OWNER_TEST_TARGET_UPSTREAM:?HHY_OWNER_TEST_TARGET_UPSTREAM is required}"

if [[ "$HHY_OWNER_TEST_PROMOTION_CONFIRM" != "YES" ]]; then
  echo "Refusing owner-test promotion without explicit confirmation" >&2
  exit 2
fi
if [[ ! "$HHY_OWNER_TEST_RELEASE" =~ ^R(0[1-9]|[12][0-9]|3[0-2])$ ]] \
  || [[ ! "$HHY_OWNER_TEST_SOURCE_COMMIT" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Owner-test promotion release or source commit is invalid" >&2
  exit 2
fi
for upstream in "$HHY_OWNER_TEST_CURRENT_UPSTREAM" "$HHY_OWNER_TEST_TARGET_UPSTREAM"; do
  [[ "$upstream" =~ ^127\.0\.0\.1:[1-9][0-9]{3,4}$ ]] \
    || { echo "Owner-test promotion accepts loopback upstreams only" >&2; exit 2; }
done
[[ "$HHY_OWNER_TEST_CURRENT_UPSTREAM" != "$HHY_OWNER_TEST_TARGET_UPSTREAM" ]] \
  || { echo "Owner-test promotion requires distinct blue/green upstreams" >&2; exit 2; }
[[ "$HHY_OWNER_TEST_CURRENT_CONTAINER" =~ ^hhy-owner-test-api-[a-f0-9]{8,40}$ ]] \
  || { echo "Unsafe current owner-test container name" >&2; exit 2; }
[[ "$HHY_OWNER_TEST_TARGET_CONTAINER" =~ ^hhy-owner-test-api-[a-f0-9]{8,40}$ ]] \
  || { echo "Unsafe target owner-test container name" >&2; exit 2; }
[[ "$(docker inspect --format '{{index .Config.Labels "hhy.source_commit"}}' "$HHY_OWNER_TEST_IMAGE")" \
  == "$HHY_OWNER_TEST_SOURCE_COMMIT" ]] \
  || { echo "Owner-test image source commit mismatch" >&2; exit 2; }

base=/root/hhy-owner-test
api_env="$base/secrets/api.env"
database_container=hhy-owner-test-postgres
database_name=hhy_owner_test
database_user=hhy_owner_test
database_volume=hhy-owner-test-postgres-data
network=hhy-owner-test
[[ -f "$api_env" ]] || { echo "Owner-test API secret environment is missing" >&2; exit 2; }
[[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' \
  "$database_container")" == "$database_volume" ]] \
  || { echo "Owner-test PostgreSQL volume identity drift" >&2; exit 2; }

install -d -m 700 "$base/backups"
snapshot="$base/backups/pre-${HHY_OWNER_TEST_RELEASE}-${HHY_OWNER_TEST_SOURCE_COMMIT:0:8}-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec "$database_container" pg_dump -U "$database_user" -d "$database_name" -Fc >"$snapshot"
chmod 600 "$snapshot"
before_counts="$(docker exec "$database_container" psql -U "$database_user" -d "$database_name" -Atqc \
  "SELECT count(*) FROM hhy.users; SELECT count(*) FROM hhy.user_credentials; SELECT count(*) FROM hhy.invite_codes;")"

target_port="${HHY_OWNER_TEST_TARGET_UPSTREAM##*:}"
if docker ps -a --format '{{.Names}}' | grep -qx "$HHY_OWNER_TEST_TARGET_CONTAINER"; then
  echo "Owner-test target container already exists" >&2
  exit 2
fi
cleanup_target() {
  docker stop "$HHY_OWNER_TEST_TARGET_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup_target ERR
docker run -d --name "$HHY_OWNER_TEST_TARGET_CONTAINER" --restart unless-stopped \
  --network "$network" -p "127.0.0.1:${target_port}:8080" \
  --env-file "$api_env" -v "$base/mfa-secrets:/var/lib/hhy/secrets" \
  "$HHY_OWNER_TEST_IMAGE" >/dev/null
for attempt in {1..90}; do
  if curl --fail --silent --show-error --max-time 5 \
    "http://${HHY_OWNER_TEST_TARGET_UPSTREAM}/public-api/v1/platform/status" >/dev/null 2>&1; then
    break
  fi
  sleep 1
  [[ "$attempt" != "90" ]] || { echo "Owner-test target backend did not become ready" >&2; false; }
done
[[ "$(docker exec "$HHY_OWNER_TEST_TARGET_CONTAINER" printenv HHY_CI_AUTOMATION_ENABLED)" == "false" ]] \
  || { echo "Owner-test target must keep CI automation disabled" >&2; false; }
after_counts="$(docker exec "$database_container" psql -U "$database_user" -d "$database_name" -Atqc \
  "SELECT count(*) FROM hhy.users; SELECT count(*) FROM hhy.user_credentials; SELECT count(*) FROM hhy.invite_codes;")"
[[ "$after_counts" == "$before_counts" ]] \
  || { echo "Owner-test user or invite counts changed during application-only promotion" >&2; false; }

nginx_config=/www/server/panel/vhost/nginx/api.orbexa.cc.conf
current_pattern="^[[:space:]]*proxy_pass http://${HHY_OWNER_TEST_CURRENT_UPSTREAM//./\\.};[[:space:]]*$"
[[ "$(grep -Ec "$current_pattern" "$nginx_config" || true)" == "1" ]] \
  || { echo "Public API is not on the expected current owner-test upstream" >&2; false; }
backup="${nginx_config}.pre-owner-test-${HHY_OWNER_TEST_RELEASE}-$(date -u +%Y%m%dT%H%M%SZ)"
cp --preserve=mode,ownership,timestamps "$nginx_config" "$backup"
sed -i "s#http://${HHY_OWNER_TEST_CURRENT_UPSTREAM}#http://${HHY_OWNER_TEST_TARGET_UPSTREAM}#" "$nginx_config"
nginx -t
systemctl reload nginx
curl --fail --silent --show-error --max-time 20 \
  https://api.orbexa.cc/public-api/v1/platform/status >/dev/null

trap - ERR
echo "OWNER_TEST_PROMOTION_OK release=${HHY_OWNER_TEST_RELEASE} commit=${HHY_OWNER_TEST_SOURCE_COMMIT} upstream=${HHY_OWNER_TEST_TARGET_UPSTREAM} snapshot=${snapshot} backup=${backup}"
