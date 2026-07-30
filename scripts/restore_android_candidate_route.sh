#!/usr/bin/env bash
set -euo pipefail

: "${HHY_OWNER_TEST_ROUTE_CONFIRM:?HHY_OWNER_TEST_ROUTE_CONFIRM is required}"
: "${HHY_EXPECTED_CANDIDATE_UPSTREAM:?HHY_EXPECTED_CANDIDATE_UPSTREAM is required}"
: "${HHY_OWNER_TEST_UPSTREAM:?HHY_OWNER_TEST_UPSTREAM is required}"
: "${HHY_OWNER_TEST_CONTAINER:?HHY_OWNER_TEST_CONTAINER is required}"
: "${HHY_OWNER_TEST_DATABASE_CONTAINER:?HHY_OWNER_TEST_DATABASE_CONTAINER is required}"
: "${HHY_OWNER_TEST_DATABASE_NAME:?HHY_OWNER_TEST_DATABASE_NAME is required}"
: "${HHY_OWNER_TEST_DATABASE_USER:?HHY_OWNER_TEST_DATABASE_USER is required}"
: "${HHY_OWNER_TEST_DATABASE_VOLUME:?HHY_OWNER_TEST_DATABASE_VOLUME is required}"
: "${HHY_OWNER_TEST_NETWORK:?HHY_OWNER_TEST_NETWORK is required}"
: "${HHY_OWNER_TEST_REGISTRATION_INVITE_CODE:?HHY_OWNER_TEST_REGISTRATION_INVITE_CODE is required}"

if [[ "$HHY_OWNER_TEST_ROUTE_CONFIRM" != "YES" ]]; then
  echo "Refusing owner-test route restoration without explicit confirmation" >&2
  exit 2
fi
for upstream in "$HHY_EXPECTED_CANDIDATE_UPSTREAM" "$HHY_OWNER_TEST_UPSTREAM"; do
  if [[ ! "$upstream" =~ ^127\.0\.0\.1:[1-9][0-9]{3,4}$ ]]; then
    echo "Owner-test route restoration accepts loopback upstreams only" >&2
    exit 2
  fi
done
if [[ "$HHY_EXPECTED_CANDIDATE_UPSTREAM" == "$HHY_OWNER_TEST_UPSTREAM" ]]; then
  echo "Candidate and owner-test upstreams must be distinct" >&2
  exit 2
fi
if [[ ! "$HHY_OWNER_TEST_CONTAINER" =~ ^hhy-owner-test-api-[a-f0-9]{8,40}$ ]]; then
  echo "Unsafe owner-test backend container name" >&2
  exit 2
fi
if [[ "$HHY_OWNER_TEST_DATABASE_CONTAINER" != "hhy-owner-test-postgres" ]] \
  || [[ "$HHY_OWNER_TEST_DATABASE_VOLUME" != "hhy-owner-test-postgres-data" ]] \
  || [[ "$HHY_OWNER_TEST_NETWORK" != "hhy-owner-test" ]]; then
  echo "Owner-test persistent database identity drift" >&2
  exit 2
fi

expected_db_url="jdbc:postgresql://${HHY_OWNER_TEST_DATABASE_CONTAINER}:5432/${HHY_OWNER_TEST_DATABASE_NAME}"
if [[ "$(docker exec "$HHY_OWNER_TEST_CONTAINER" printenv HHY_DB_URL)" != "$expected_db_url" ]] \
  || [[ "$(docker exec "$HHY_OWNER_TEST_CONTAINER" printenv HHY_DB_USER)" != "$HHY_OWNER_TEST_DATABASE_USER" ]] \
  || [[ "$(docker exec "$HHY_OWNER_TEST_CONTAINER" printenv HHY_CI_AUTOMATION_ENABLED)" != "false" ]]; then
  echo "Owner-test backend is not bound to the persistent non-CI database" >&2
  exit 2
fi
if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$HHY_OWNER_TEST_CONTAINER")" != "healthy" ]]; then
  echo "Owner-test backend is not healthy" >&2
  exit 2
fi
if [[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' \
  "$HHY_OWNER_TEST_DATABASE_CONTAINER")" != "$HHY_OWNER_TEST_DATABASE_VOLUME" ]]; then
  echo "Owner-test PostgreSQL volume identity drift" >&2
  exit 2
fi
owner_networks="$(docker inspect --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' \
  "$HHY_OWNER_TEST_CONTAINER")"
if [[ " $owner_networks " != *" $HHY_OWNER_TEST_NETWORK "* ]]; then
  echo "Owner-test backend network identity drift" >&2
  exit 2
fi

nginx_config="/www/server/panel/vhost/nginx/api.orbexa.cc.conf"
candidate_pattern="^[[:space:]]*proxy_pass http://${HHY_EXPECTED_CANDIDATE_UPSTREAM//./\\.};[[:space:]]*$"
owner_pattern="^[[:space:]]*proxy_pass http://${HHY_OWNER_TEST_UPSTREAM//./\\.};[[:space:]]*$"
candidate_count="$(grep -Ec "$candidate_pattern" "$nginx_config" || true)"
owner_count="$(grep -Ec "$owner_pattern" "$nginx_config" || true)"
backup="${nginx_config}.pre-owner-test-restore-$(date -u +%Y%m%dT%H%M%SZ)"
activated="false"
rollback() {
  if [[ "$activated" == "true" ]]; then
    cp --preserve=mode,ownership,timestamps "$backup" "$nginx_config"
    nginx -t >/dev/null 2>&1
    systemctl reload nginx
  fi
}
trap rollback ERR

if [[ "$candidate_count" == "1" && "$owner_count" == "0" ]]; then
  cp --preserve=mode,ownership,timestamps "$nginx_config" "$backup"
  activated="true"
  sed -i "s#http://${HHY_EXPECTED_CANDIDATE_UPSTREAM}#http://${HHY_OWNER_TEST_UPSTREAM}#" "$nginx_config"
  nginx -t
  systemctl reload nginx
elif [[ "$candidate_count" == "0" && "$owner_count" == "1" ]]; then
  backup="ALREADY_ACTIVE"
else
  echo "Nginx route is ambiguous or not the expected candidate/owner-test pair" >&2
  exit 2
fi

public_status="https://api.orbexa.cc/public-api/v1/platform/status"
public_invite="https://api.orbexa.cc/api/v1/auth/invite-codes/validate"
curl --fail --silent --show-error --max-time 20 "$public_status" >/dev/null
invite_payload="{\"inviteCode\":\"${HHY_OWNER_TEST_REGISTRATION_INVITE_CODE}\"}"
invite_response="$(mktemp)"
trap 'rm -f "$invite_response"' EXIT
curl --fail --silent --show-error --max-time 20 \
  --header 'Content-Type: application/json' \
  --data "$invite_payload" "$public_invite" >"$invite_response"
grep -F '"status":"VALID"' "$invite_response" >/dev/null

lease="/root/hhy-owner-test/candidate-route-lease.env"
if [[ -f "$lease" ]]; then
  mv "$lease" "${lease}.restored-$(date -u +%Y%m%dT%H%M%SZ)"
fi
activated="false"
trap - ERR
echo "ANDROID_OWNER_TEST_ROUTE_RESTORED upstream=${HHY_OWNER_TEST_UPSTREAM} container=${HHY_OWNER_TEST_CONTAINER} backup=${backup}"
