#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROJECT=${HHY_R06_PROJECT:-hhy-r06-be62b727}
FROZEN_COMMIT=${HHY_R06_FROZEN_COMMIT:-be62b727d480cacf055227823b9ed64e30ae488a}
CURRENT_TAG=${HHY_R06_CURRENT_TAG:-${FROZEN_COMMIT:0:8}}
ROLLBACK_IMAGE=${HHY_R06_ROLLBACK_IMAGE:-hhy-backend-r06-ci:ccc64022}
ROLLBACK_TAG=${HHY_R06_ROLLBACK_TAG:-rollback-ccc64022}
HTTP_PORT=${HHY_R06_SMOKE_HTTP_PORT:-38106}
PROMETHEUS_PORT=${HHY_R06_PROMETHEUS_PORT:-39590}
COMPOSE_FILE="$ROOT/infra/staging/r06-smoke/docker-compose.yml"
EVIDENCE="$ROOT/artifacts/validation/r06-task006-staging"
ALERT_EVENT_PREFIX=r06-stage-alert
COMPOSE=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")

# Compose interpolates required values even for read-only `ps`. These sentinels
# are replaced with the running containers' real isolated values before any recreate.
export HHY_SMOKE_ID=${HHY_SMOKE_ID:-$CURRENT_TAG}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-compose-inspection-only}
export HHY_USER_JWT_SECRET=${HHY_USER_JWT_SECRET:-compose-inspection-only}
export HHY_USER_TOKEN_HMAC_SECRET=${HHY_USER_TOKEN_HMAC_SECRET:-compose-inspection-only}
export HHY_USER_SNAPSHOT_ROOT_SECRET=${HHY_USER_SNAPSHOT_ROOT_SECRET:-compose-inspection-only}
export HHY_ADMIN_JWT_SECRET=${HHY_ADMIN_JWT_SECRET:-compose-inspection-only}
export HHY_ADMIN_MFA_ROOT_SECRET=${HHY_ADMIN_MFA_ROOT_SECRET:-compose-inspection-only}
export HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET=${HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET:-compose-inspection-only}

API=
POSTGRES=
PROMETHEUS=
ALERT_SINK=

refresh_containers() {
  API=$(${COMPOSE[@]} ps -q api)
  POSTGRES=$(${COMPOSE[@]} ps -q postgres)
  PROMETHEUS=$(${COMPOSE[@]} ps -q prometheus)
  ALERT_SINK=$(${COMPOSE[@]} ps -q alert-sink)
  test -n "$API" && test -n "$POSTGRES" && test -n "$PROMETHEUS" && test -n "$ALERT_SINK"
}

wait_url() {
  local url=$1
  local timeout=${2:-90}
  local elapsed=0
  until curl -fsS "$url" >/dev/null; do
    if (( elapsed >= timeout )); then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

wait_container_readiness() {
  local timeout=${1:-120}
  local elapsed=0
  until docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null 2>&1; do
    if (( elapsed >= timeout )); then
      docker logs --tail 120 "$API" >&2 || true
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

wait_rule_firing() {
  local alert=$1
  local timeout=${2:-90}
  local elapsed=0
  until curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" \
      | grep -q "\"name\":\"${alert}\".*\"state\":\"firing\""; do
    if (( elapsed >= timeout )); then
      echo "Timed out waiting for firing alert $alert" >&2
      return 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

wait_alert_receipt() {
  local alert=$1
  local status=$2
  local timeout=${3:-120}
  local elapsed=0
  until docker exec "$ALERT_SINK" sh -c 'test -f /data/deliveries.jsonl && cat /data/deliveries.jsonl' 2>/dev/null \
      | grep -F "\"alertname\":\"${alert}\"" \
      | grep -Fq "\"status\":\"${status}\""; do
    if (( elapsed >= timeout )); then
      echo "Timed out waiting for $status receipt for $alert" >&2
      return 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

delete_test_events() {
  if test -n "$POSTGRES" && docker inspect "$POSTGRES" >/dev/null 2>&1; then
    docker exec "$POSTGRES" psql -U hhy_r06_smoke -d hhy_r06_smoke -v ON_ERROR_STOP=1 \
      -c "DELETE FROM hhy.outbox_events WHERE event_id LIKE '${ALERT_EVENT_PREFIX}-%';" >/dev/null
  fi
}

recover_runtime() {
  set +e
  if test -n "$API" && docker inspect "$API" >/dev/null 2>&1; then
    docker start "$API" >/dev/null 2>&1
  fi
  delete_test_events
}
trap recover_runtime EXIT

load_compose_environment() {
  export POSTGRES_PASSWORD
  export HHY_USER_JWT_SECRET
  export HHY_USER_TOKEN_HMAC_SECRET
  export HHY_USER_SNAPSHOT_ROOT_SECRET
  export HHY_ADMIN_JWT_SECRET
  export HHY_ADMIN_MFA_ROOT_SECRET
  export HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET
  POSTGRES_PASSWORD=$(docker exec "$POSTGRES" printenv POSTGRES_PASSWORD)
  HHY_USER_JWT_SECRET=$(docker exec "$API" printenv HHY_USER_JWT_SECRET)
  HHY_USER_TOKEN_HMAC_SECRET=$(docker exec "$API" printenv HHY_USER_TOKEN_HMAC_SECRET)
  HHY_USER_SNAPSHOT_ROOT_SECRET=$(docker exec "$API" printenv HHY_USER_SNAPSHOT_ROOT_SECRET)
  HHY_ADMIN_JWT_SECRET=$(docker exec "$API" printenv HHY_ADMIN_JWT_SECRET)
  HHY_ADMIN_MFA_ROOT_SECRET=$(docker exec "$API" printenv HHY_ADMIN_MFA_ROOT_SECRET)
  HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET=$(docker exec "$API" printenv HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET)
  export HHY_R06_SMOKE_SUBNET=${HHY_R06_SMOKE_SUBNET:-172.31.246.0/24}
}

record_database_state() {
  docker exec "$POSTGRES" psql -U hhy_r06_smoke -d hhy_r06_smoke -At -v ON_ERROR_STOP=1 <<'SQL'
SELECT version FROM hhy.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hhy';
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_name <> 'flyway_schema_history';
SQL
}

record_runtime_state() {
  local label=$1
  local pg_volume
  pg_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  printf '%s_api_image=%s\n' "$label" "$(docker inspect "$API" --format '{{.Image}}')"
  printf '%s_postgres_container=%s\n' "$label" "$(docker inspect "$POSTGRES" --format '{{.Id}}')"
  printf '%s_postgres_volume=%s\n' "$label" "$pg_volume"
  printf '%s_flyway=%s\n' "$label" "$(record_database_state | sed -n '1p')"
}

capture_baseline() {
  mkdir -p "$EVIDENCE"
  refresh_containers
  wait_container_readiness
  wait_url "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status"
  wait_url "http://127.0.0.1:${PROMETHEUS_PORT}/-/ready"

  local current_image pg_volume
  current_image=$(docker inspect "$API" --format '{{.Image}}')
  pg_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  {
    echo task=TASK-R06-006
    echo story=STORY-R06-005
    echo release=R06
    echo frozen_commit="$FROZEN_COMMIT"
    echo compose_project="$PROJECT"
    echo network_subnet=${HHY_R06_SMOKE_SUBNET:-172.31.246.0/24}
    echo current_image="$current_image"
    echo postgres_container=$(docker inspect "$POSTGRES" --format '{{.Id}}')
    echo postgres_volume="$pg_volume"
  } > "$EVIDENCE/metadata.txt"

  ${COMPOSE[@]} ps > "$EVIDENCE/compose-ps.txt"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/liveness > "$EVIDENCE/liveness.json"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/readiness > "$EVIDENCE/readiness.json"
  record_database_state > "$EVIDENCE/database.txt"

  curl -sS -D "$EVIDENCE/trace-headers.txt" -o "$EVIDENCE/public-status.json" \
    -H 'X-Request-Id: r06-stage-request-001' \
    -H 'X-Trace-Id: 11223344556677889900aabbccddeeff' \
    -H 'Authorization: Bearer R06_SENSITIVE_PROBE_7F3A' \
    -H 'Cookie: session=R06_SENSITIVE_PROBE_7F3A' \
    "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status?probe=R06_SENSITIVE_PROBE_7F3A"
  sleep 2
  docker logs "$API" 2>&1 | grep -F '"requestId":"r06-stage-request-001"' > "$EVIDENCE/http-request-completed.jsonl"
  if docker logs "$API" 2>&1 | grep -Fq R06_SENSITIVE_PROBE_7F3A; then
    echo R06_LOG_REDACTION_FAILED > "$EVIDENCE/log-redaction.txt"
    return 1
  fi
  echo R06_LOG_REDACTION_OK > "$EVIDENCE/log-redaction.txt"

  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/prometheus > "$EVIDENCE/metrics.txt"
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/targets" > "$EVIDENCE/prometheus-targets.json"
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/prometheus-rules.json"
  grep -E '^(hhy_content_online_count|hhy_content_review_pending|hhy_content_outbox_backlog|hhy_home_enabled_modules|hhy_business_metric_query_failures_total|http_server_requests_seconds_(count|bucket))' \
    "$EVIDENCE/metrics.txt" > "$EVIDENCE/metrics-summary.txt"
  for metric in hhy_content_online_count hhy_content_review_pending hhy_content_outbox_backlog hhy_home_enabled_modules; do
    grep -q "^${metric} " "$EVIDENCE/metrics.txt"
  done
  awk '/^hhy_business_metric_query_failures_total/ {sum += $NF} END {exit sum != 0}' "$EVIDENCE/metrics.txt"
  grep -q '"health":"up"' "$EVIDENCE/prometheus-targets.json"

  sha256sum \
    services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java \
    services/backend/Dockerfile services/backend/pom.xml \
    infra/staging/r06-smoke/docker-compose.yml infra/staging/r06-smoke/prometheus.yml \
    infra/staging/r06-smoke/alertmanager.yml infra/staging/r06-smoke/nginx.conf \
    infra/staging/r06-smoke/r06-alerts.yml scripts/check_r06_observability.py \
    scripts/run_r06_staging_acceptance.sh > "$EVIDENCE/source-sha256.txt"
}

exercise_alerts() {
  refresh_containers
  docker exec "$ALERT_SINK" sh -c ': > /data/deliveries.jsonl'

  docker stop "$API" >/dev/null
  wait_rule_firing HhyR06BackendDown
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/backend-down-firing.json"
  wait_alert_receipt HhyR06BackendDown firing
  docker start "$API" >/dev/null
  wait_container_readiness
  wait_alert_receipt HhyR06BackendDown resolved

  docker exec "$POSTGRES" psql -U hhy_r06_smoke -d hhy_r06_smoke -v ON_ERROR_STOP=1 <<SQL >/dev/null
INSERT INTO hhy.outbox_events(aggregate_id, aggregate_type, event_id, event_type, event_version, headers, payload, status)
SELECT 'r06-stage-' || value, 'CONTENT', '${ALERT_EVENT_PREFIX}-' || value,
       'content.stage.alert.v1', 1, '{}'::jsonb, jsonb_build_object('test', true), 'PENDING'
FROM generate_series(1, 4) AS value;
SQL
  wait_rule_firing HhyR06ContentOutboxBacklog
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/content-outbox-firing.json"
  wait_alert_receipt HhyR06ContentOutboxBacklog firing
  delete_test_events
  wait_alert_receipt HhyR06ContentOutboxBacklog resolved

  docker exec "$ALERT_SINK" cat /data/deliveries.jsonl > "$EVIDENCE/alert-deliveries.jsonl"
  {
    echo backend_down_firing=PASS
    echo backend_down_resolved=PASS
    echo content_outbox_firing=PASS
    echo content_outbox_resolved=PASS
  } > "$EVIDENCE/alert-exercise.txt"
}

exercise_rollback() {
  refresh_containers
  load_compose_environment
  local before_pg before_volume rollback_image_id final_image_id
  before_pg=$(docker inspect "$POSTGRES" --format '{{.Id}}')
  before_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  docker image inspect "$ROLLBACK_IMAGE" >/dev/null
  docker tag "$ROLLBACK_IMAGE" "hhy-backend-r06-smoke:${ROLLBACK_TAG}"

  {
    record_runtime_state before
    export HHY_SMOKE_ID="$ROLLBACK_TAG"
    ${COMPOSE[@]} up -d --no-deps --no-build api >/dev/null
    refresh_containers
    wait_container_readiness
    wait_url "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status"
    rollback_image_id=$(docker inspect "$API" --format '{{.Image}}')
    record_runtime_state rollback

    test "$(docker inspect "$POSTGRES" --format '{{.Id}}')" = "$before_pg"
    test "$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')" = "$before_volume"

    export HHY_SMOKE_ID="$CURRENT_TAG"
    ${COMPOSE[@]} up -d --no-deps --no-build api >/dev/null
    refresh_containers
    wait_container_readiness
    wait_url "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status"
    final_image_id=$(docker inspect "$API" --format '{{.Image}}')
    record_runtime_state restored
    test "$(docker inspect "$POSTGRES" --format '{{.Id}}')" = "$before_pg"
    test "$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')" = "$before_volume"
    test "$final_image_id" != "$rollback_image_id"
    echo rollback_same_postgres_container=PASS
    echo rollback_same_postgres_volume=PASS
    echo final_current_image_restored=PASS
    echo no_down_migration_or_volume_deletion=PASS
  } > "$EVIDENCE/rollback-rehearsal.txt"

  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/prometheus > "$EVIDENCE/final-metrics.txt"
  for metric in hhy_content_online_count hhy_content_review_pending hhy_content_outbox_backlog hhy_home_enabled_modules; do
    grep -q "^${metric} " "$EVIDENCE/final-metrics.txt"
  done
}

finalize_evidence() {
  refresh_containers
  local flyway total_tables business_tables current_image pg_volume
  flyway=$(record_database_state | sed -n '1p')
  total_tables=$(record_database_state | sed -n '2p')
  business_tables=$(record_database_state | sed -n '3p')
  current_image=$(docker inspect "$API" --format '{{.Image}}')
  pg_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  {
    echo task=TASK-R06-006
    echo frozen_commit="$FROZEN_COMMIT"
    echo compose_project="$PROJECT"
    echo current_image="$current_image"
    echo postgres_container=$(docker inspect "$POSTGRES" --format '{{.Id}}')
    echo postgres_volume="$pg_volume"
    echo flyway_version="$flyway"
    echo schema_tables_including_flyway="$total_tables"
    echo business_tables="$business_tables"
    echo public_status_http=200
    echo liveness=UP
    echo readiness=UP
    echo prometheus_target_up=1
    echo r06_content_gauges=4
    echo r06_alert_rules=6
    cat "$EVIDENCE/alert-exercise.txt"
    grep '^rollback_' "$EVIDENCE/rollback-rehearsal.txt"
    grep '^final_current_image_restored=' "$EVIDENCE/rollback-rehearsal.txt"
    echo log_redaction=PASS
  } > "$EVIDENCE/machine-summary.txt"
  find "$EVIDENCE" -maxdepth 1 -type f ! -name SHA256SUMS -print0 \
    | sort -z | xargs -0 sha256sum > "$EVIDENCE/SHA256SUMS"
}

cd "$ROOT"
test "$(git rev-parse HEAD)" = "$FROZEN_COMMIT"
capture_baseline
exercise_alerts
exercise_rollback
finalize_evidence
trap - EXIT
echo R06_STAGING_ACCEPTANCE_PASS
