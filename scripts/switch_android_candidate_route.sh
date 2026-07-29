#!/usr/bin/env bash
set -euo pipefail

: "${HHY_CANDIDATE_ROUTE_CONFIRM:?HHY_CANDIDATE_ROUTE_CONFIRM is required}"
: "${HHY_CANDIDATE_CONTAINER:?HHY_CANDIDATE_CONTAINER is required}"
: "${HHY_EXPECTED_OLD_UPSTREAM:?HHY_EXPECTED_OLD_UPSTREAM is required}"
: "${HHY_TARGET_UPSTREAM:?HHY_TARGET_UPSTREAM is required}"

probe_mode="${HHY_CANDIDATE_ROUTE_PROBE_MODE:-LEGACY_INVITE}"
case "$probe_mode" in
  LEGACY_INVITE)
    : "${HHY_CANDIDATE_REGISTRATION_INVITE_CODE:?HHY_CANDIDATE_REGISTRATION_INVITE_CODE is required}"
    ;;
  R14_CONVERSATIONS)
    : "${HHY_CANDIDATE_ACCESS_TOKEN:?HHY_CANDIDATE_ACCESS_TOKEN is required}"
    : "${HHY_EXPECTED_CANDIDATE_IMAGE_ID:?HHY_EXPECTED_CANDIDATE_IMAGE_ID is required}"
    : "${HHY_EXPECTED_SOURCE_COMMIT:?HHY_EXPECTED_SOURCE_COMMIT is required}"
    : "${HHY_EXPECTED_OLD_CONTAINER:?HHY_EXPECTED_OLD_CONTAINER is required}"
    : "${HHY_CANDIDATE_NETWORK:?HHY_CANDIDATE_NETWORK is required}"
    : "${HHY_CANDIDATE_DATABASE_CONTAINER:?HHY_CANDIDATE_DATABASE_CONTAINER is required}"
    : "${HHY_CANDIDATE_DATABASE_USER:?HHY_CANDIDATE_DATABASE_USER is required}"
    : "${HHY_CANDIDATE_DATABASE_NAME:?HHY_CANDIDATE_DATABASE_NAME is required}"
    : "${HHY_R14_CANDIDATE_FIXTURE_CONFIRM:?HHY_R14_CANDIDATE_FIXTURE_CONFIRM is required}"
    ;;
  *)
    echo "Unsupported candidate route probe mode" >&2
    exit 2
    ;;
esac

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
if [[ "$(docker exec "$HHY_CANDIDATE_CONTAINER" printenv SPRING_PROFILES_ACTIVE)" != "staging" ]]; then
  echo "Candidate readiness is restricted to the staging profile" >&2
  exit 2
fi
if [[ "$probe_mode" == "LEGACY_INVITE" ]] \
  && [[ ! "$HHY_CANDIDATE_REGISTRATION_INVITE_CODE" =~ ^[A-Za-z0-9_-]{6,64}$ ]]; then
  echo "Unsafe candidate registration invite code format" >&2
  exit 2
fi
if [[ "$probe_mode" == "R14_CONVERSATIONS" ]]; then
  if [[ ! "$HHY_EXPECTED_SOURCE_COMMIT" =~ ^[a-f0-9]{40}$ ]]; then
    echo "Expected candidate source commit must be a full lowercase Git commit" >&2
    exit 2
  fi
  repository_commit="$(git rev-parse --verify HEAD)"
  if [[ "$repository_commit" != "$HHY_EXPECTED_SOURCE_COMMIT" ]] \
    || [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
    echo "Candidate route repository is not the clean frozen source commit" >&2
    exit 2
  fi
  container_source_commit="$(docker inspect --format '{{index .Config.Labels "hhy.source_commit"}}' \
    "$HHY_CANDIDATE_CONTAINER")"
  if [[ "$container_source_commit" != "$HHY_EXPECTED_SOURCE_COMMIT" ]]; then
    echo "Candidate container source commit does not match the frozen repository commit" >&2
    exit 2
  fi
  if [[ ! "$HHY_EXPECTED_CANDIDATE_IMAGE_ID" =~ ^sha256:[a-f0-9]{64}$ ]] \
    || [[ "$(docker inspect --format '{{.Image}}' "$HHY_CANDIDATE_CONTAINER")" \
      != "$HHY_EXPECTED_CANDIDATE_IMAGE_ID" ]]; then
    echo "Candidate image does not match the frozen R14 image ID" >&2
    exit 2
  fi
  if [[ "$(docker inspect --format '{{index .Config.Labels "hhy.release"}}' \
      "$HHY_CANDIDATE_CONTAINER")" != "R14" ]]; then
    echo "Candidate release label is not R14" >&2
    exit 2
  fi
  candidate_networks="$(docker inspect --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' \
    "$HHY_CANDIDATE_CONTAINER")"
  if [[ " $candidate_networks " != *" $HHY_CANDIDATE_NETWORK "* ]]; then
    echo "Candidate container is not attached to the exact R14 staging network" >&2
    exit 2
  fi
  old_networks="$(docker inspect --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' \
    "$HHY_EXPECTED_OLD_CONTAINER")"
  if [[ " $old_networks " != *" $HHY_CANDIDATE_NETWORK "* ]]; then
    echo "Expected old candidate is not attached to the exact R14 staging network" >&2
    exit 2
  fi
  candidate_db_url="$(docker exec "$HHY_CANDIDATE_CONTAINER" printenv HHY_DB_URL)"
  expected_db_url="jdbc:postgresql://${HHY_CANDIDATE_DATABASE_CONTAINER}:5432/${HHY_CANDIDATE_DATABASE_NAME}"
  if [[ "$candidate_db_url" != "$expected_db_url" ]] \
    || [[ "$(docker exec "$HHY_CANDIDATE_CONTAINER" printenv HHY_DB_USER)" \
      != "$HHY_CANDIDATE_DATABASE_USER" ]]; then
    echo "Candidate database binding does not match the exact R14 smoke database" >&2
    exit 2
  fi
  mfa_mount_rw="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/hhy/secrets"}}{{.RW}}{{end}}{{end}}' \
    "$HHY_CANDIDATE_CONTAINER")"
  if [[ "$mfa_mount_rw" != "true" ]]; then
    echo "Candidate MFA secret volume must be writable" >&2
    exit 2
  fi
  if [[ "$(docker inspect --format '{{.State.Health.Status}}' \
      "$HHY_EXPECTED_OLD_CONTAINER")" != "healthy" ]]; then
    echo "Expected old candidate is not healthy" >&2
    exit 2
  fi
  flyway_version="$(docker exec "$HHY_CANDIDATE_DATABASE_CONTAINER" psql \
    -U "$HHY_CANDIDATE_DATABASE_USER" -d "$HHY_CANDIDATE_DATABASE_NAME" \
    -Atc "SELECT max(version) FROM hhy.flyway_schema_history WHERE success")"
  if [[ "$flyway_version" != "044" ]]; then
    echo "Candidate database has not reached Flyway V044" >&2
    exit 2
  fi
  bash scripts/prepare_r14_candidate_fixture.sh
fi

nginx_config="/www/server/panel/vhost/nginx/api.orbexa.cc.conf"
public_probe_url="https://api.orbexa.cc/public-api/v1/platform/status"
local_probe_url="http://${HHY_TARGET_UPSTREAM}/public-api/v1/platform/status"
if [[ "$probe_mode" == "LEGACY_INVITE" ]]; then
  public_readiness_url="https://api.orbexa.cc/api/v1/auth/invite-codes/validate"
  local_readiness_url="http://${HHY_TARGET_UPSTREAM}/api/v1/auth/invite-codes/validate"
  readiness_payload="{\"inviteCode\":\"${HHY_CANDIDATE_REGISTRATION_INVITE_CODE}\"}"
else
  public_readiness_url="https://api.orbexa.cc/api/v1/conversations?page=1&pageSize=20&sort=updatedAt%3Adesc"
  local_readiness_url="http://${HHY_TARGET_UPSTREAM}/api/v1/conversations?page=1&pageSize=20&sort=updatedAt%3Adesc"
  public_startup_version_url="https://api.orbexa.cc/public-api/v1/app/version-check"
  local_startup_version_url="http://${HHY_TARGET_UPSTREAM}/public-api/v1/app/version-check"
  startup_version_payload='{"platform":"ANDROID","versionCode":2147483647,"versionName":"candidate-probe","channel":"official","environment":"STAGING"}'
fi
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
if [[ "$probe_mode" == "LEGACY_INVITE" ]]; then
  curl --fail --silent --show-error --max-time 20 \
    --header 'Content-Type: application/json' \
    --data "$readiness_payload" \
    "$local_readiness_url" >/dev/null
else
  curl --fail --silent --show-error --max-time 20 \
    --header 'Content-Type: application/json' \
    --data "$startup_version_payload" \
    "$local_startup_version_url" >/dev/null
  curl --fail --silent --show-error --max-time 20 \
    --header "Authorization: Bearer ${HHY_CANDIDATE_ACCESS_TOKEN}" \
    "$local_readiness_url" >/dev/null
fi

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
      nginx -t >/dev/null 2>&1
      systemctl reload nginx
      [[ "$(grep -Ec "$old_pattern" "$nginx_config" || true)" == "1" ]]
      if [[ "$probe_mode" == "R14_CONVERSATIONS" ]]; then
        [[ "$(docker inspect --format '{{.State.Health.Status}}' \
          "$HHY_EXPECTED_OLD_CONTAINER")" == "healthy" ]]
      fi
      curl --fail --silent --show-error --max-time 20 "$public_probe_url" >/dev/null
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

response_headers="$(mktemp)"
cleanup_headers() {
  rm -f "$response_headers"
}
trap cleanup_headers EXIT
if [[ "$probe_mode" == "LEGACY_INVITE" ]]; then
  curl --fail --silent --show-error --max-time 20 \
    --header 'Content-Type: application/json' \
    --data "$readiness_payload" \
    "$public_readiness_url" >/dev/null
else
  curl --fail --silent --show-error --max-time 20 \
    --header 'Content-Type: application/json' \
    --data "$startup_version_payload" \
    "$public_startup_version_url" >/dev/null
  curl --fail --silent --show-error --max-time 20 \
    --header "Authorization: Bearer ${HHY_CANDIDATE_ACCESS_TOKEN}" \
    "$public_readiness_url" >/dev/null
fi
matched="false"
for probe_attempt in {1..10}; do
  sent_request_id="hhy-route-$(date -u +%Y%m%dT%H%M%SZ)-${probe_attempt}-${RANDOM}${RANDOM}"
  probe_since="$(date -u -d '-2 seconds' +%Y-%m-%dT%H:%M:%SZ)"
  : >"$response_headers"
  curl --fail --silent --show-error --max-time 20 \
    --dump-header "$response_headers" \
    --header "X-Request-ID: ${sent_request_id}" \
    "$public_probe_url" >/dev/null
  response_request_id="$(awk '
    tolower($0) ~ /^x-request-id:[[:space:]]*/ {
      sub(/\r$/, "")
      sub(/^[^:]*:[[:space:]]*/, "")
      value = $0
    }
    END { print value }
  ' "$response_headers")"
  if [[ ! "$response_request_id" =~ ^[A-Za-z0-9_-]{8,64}$ ]]; then
    echo "Public response did not provide a valid backend X-Request-Id" >&2
    false
  fi

  for _ in 1 2; do
    if docker logs --since "$probe_since" "$HHY_CANDIDATE_CONTAINER" 2>&1 \
      | grep -F "\"requestId\":\"${response_request_id}\"" >/dev/null; then
      matched="true"
      break 2
    fi
    sleep 1
  done
  sleep 1
done
if [[ "$matched" != "true" ]]; then
  echo "Public request did not reach the intended candidate container" >&2
  false
fi

activated="false"
trap - ERR
echo "ANDROID_CANDIDATE_ROUTE_OK mode=${probe_mode} container=${HHY_CANDIDATE_CONTAINER} upstream=${HHY_TARGET_UPSTREAM} backup=${backup} sent_request_id=${sent_request_id} response_request_id=${response_request_id}"
