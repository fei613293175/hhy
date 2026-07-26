#!/usr/bin/env bash
set -euo pipefail

: "${HHY_CANDIDATE_ROUTE_CONFIRM:?HHY_CANDIDATE_ROUTE_CONFIRM is required}"
: "${HHY_CANDIDATE_CONTAINER:?HHY_CANDIDATE_CONTAINER is required}"
: "${HHY_EXPECTED_OLD_UPSTREAM:?HHY_EXPECTED_OLD_UPSTREAM is required}"
: "${HHY_TARGET_UPSTREAM:?HHY_TARGET_UPSTREAM is required}"

if [[ "$HHY_CANDIDATE_ROUTE_CONFIRM" != "YES" ]]; then
  echo "Refusing candidate route activation without HHY_CANDIDATE_ROUTE_CONFIRM=YES" >&2
  exit 2
fi
if [[ ! "$HHY_CANDIDATE_CONTAINER" =~ ^hhy-r(0[6-9]|[12][0-9]|3[0-2])-ci-candidate-[a-z0-9]{8,40}$ ]]; then
  echo "Unsafe Android candidate container name" >&2
  exit 2
fi
if [[ ! "$HHY_EXPECTED_OLD_UPSTREAM" =~ ^127\.0\.0\.1:[1-9][0-9]{3,4}$ ]] \
  || [[ ! "$HHY_TARGET_UPSTREAM" =~ ^127\.0\.0\.1:[1-9][0-9]{3,4}$ ]] \
  || [[ "$HHY_EXPECTED_OLD_UPSTREAM" == "$HHY_TARGET_UPSTREAM" ]]; then
  echo "Candidate upstreams must be distinct explicit loopback ports" >&2
  exit 2
fi

nginx_config="/www/server/panel/vhost/nginx/api.orbexa.cc.conf"
public_probe_url="https://api.orbexa.cc/public-api/v1/platform/status"
local_probe_url="http://${HHY_TARGET_UPSTREAM}/public-api/v1/platform/status"
expected_port="${HHY_TARGET_UPSTREAM##*:}"
published_port="$(docker port "$HHY_CANDIDATE_CONTAINER" 8080/tcp 2>/dev/null || true)"
if [[ "$published_port" != "127.0.0.1:${expected_port}" ]]; then
  echo "Candidate container does not publish the exact target upstream" >&2
  exit 2
fi
if [[ "$(docker inspect --format '{{.State.Running}}' "$HHY_CANDIDATE_CONTAINER")" != "true" ]]; then
  echo "Candidate container is not running" >&2
  exit 2
fi
curl --fail --silent --show-error --max-time 20 "$local_probe_url" >/dev/null

old_pattern="^[[:space:]]*proxy_pass http://${HHY_EXPECTED_OLD_UPSTREAM//./\\.};[[:space:]]*$"
target_pattern="^[[:space:]]*proxy_pass http://${HHY_TARGET_UPSTREAM//./\\.};[[:space:]]*$"
old_count="$(grep -Ec "$old_pattern" "$nginx_config" || true)"
target_count="$(grep -Ec "$target_pattern" "$nginx_config" || true)"
if [[ "$old_count" == "1" && "$target_count" == "0" ]]; then
  backup="${nginx_config}.pre-candidate-$(date -u +%Y%m%dT%H%M%SZ)"
  cp --preserve=mode,ownership,timestamps "$nginx_config" "$backup"
  activated="true"
  rollback() {
    if [[ "${activated:-false}" == "true" ]]; then
      cp --preserve=mode,ownership,timestamps "$backup" "$nginx_config"
      nginx -t >/dev/null 2>&1 && systemctl reload nginx
    fi
  }
  trap rollback ERR
  sed -i "s#http://${HHY_EXPECTED_OLD_UPSTREAM}#http://${HHY_TARGET_UPSTREAM}#" "$nginx_config"
  [[ "$(grep -Ec "$target_pattern" "$nginx_config" || true)" == "1" ]]
  [[ "$(grep -Ec "$old_pattern" "$nginx_config" || true)" == "0" ]]
  nginx -t
  systemctl reload nginx
elif [[ "$old_count" == "0" && "$target_count" == "1" ]]; then
  backup="ALREADY_ACTIVE"
  activated="false"
else
  echo "Nginx upstream is ambiguous or differs from the exact expected route" >&2
  exit 2
fi

request_id="hhy-route-$(date -u +%Y%m%dT%H%M%SZ)-${RANDOM}${RANDOM}"
probe_since="$(date -u -d '-2 seconds' +%Y-%m-%dT%H:%M:%SZ)"
curl --fail --silent --show-error --max-time 20 \
  --header "X-Request-ID: ${request_id}" \
  "$public_probe_url" >/dev/null

matched="false"
for _ in 1 2 3 4 5; do
  if docker logs --since "$probe_since" "$HHY_CANDIDATE_CONTAINER" 2>&1 \
    | grep -Fq "\"requestId\":\"${request_id}\""; then
    matched="true"
    break
  fi
  sleep 1
done
if [[ "$matched" != "true" ]]; then
  echo "Public request did not reach the intended candidate container" >&2
  false
fi

activated="false"
trap - ERR
echo "ANDROID_CANDIDATE_ROUTE_OK container=${HHY_CANDIDATE_CONTAINER} upstream=${HHY_TARGET_UPSTREAM} backup=${backup} request_id=${request_id}"
