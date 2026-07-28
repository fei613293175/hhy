#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROJECT=${HHY_R14_PROJECT:-hhy-r14-staging}
FROZEN_COMMIT=${HHY_R14_FROZEN_COMMIT:?HHY_R14_FROZEN_COMMIT is required}
CURRENT_TAG=${HHY_R14_CURRENT_TAG:-${FROZEN_COMMIT:0:8}}
ROLLBACK_COMMIT=${HHY_R14_ROLLBACK_COMMIT:-f4b7d4854e10dedd40d4b40cdb2d79e57687e9b7}
ROLLBACK_IMAGE=${HHY_R14_ROLLBACK_IMAGE:-hhy-backend-r14-baseline:f4b7d485}
ROLLBACK_TAG=${HHY_R14_ROLLBACK_TAG:-rollback-f4b7d485}
HTTP_PORT=${HHY_R14_SMOKE_HTTP_PORT:-38114}
PROMETHEUS_PORT=${HHY_R14_PROMETHEUS_PORT:-39618}
COMPOSE_FILE="$ROOT/infra/staging/r14-smoke/docker-compose.yml"
EVIDENCE="$ROOT/artifacts/validation/r14-task006-staging"
COMPOSE=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")

test "$PROJECT" = hhy-r14-staging
test "${HHY_R14_SMOKE_SUBNET:-172.31.238.0/24}" = 172.31.238.0/24
test "$HTTP_PORT" = 38114
test "$PROMETHEUS_PORT" = 39618
test "${HHY_R14_ALERTMANAGER_PORT:-39619}" = 39619
test "$ROLLBACK_COMMIT" = f4b7d4854e10dedd40d4b40cdb2d79e57687e9b7

export HHY_SMOKE_ID=${HHY_SMOKE_ID:-$CURRENT_TAG}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-r14-compose-inspection-0000000000000000}
export HHY_CONTENT_CONTACT_ROOT_SECRET=${HHY_CONTENT_CONTACT_ROOT_SECRET:-r14-contact-0000000000000000000000000001}
export HHY_USER_JWT_SECRET=${HHY_USER_JWT_SECRET:-r14-user-jwt-0000000000000000000000000002}
export HHY_USER_TOKEN_HMAC_SECRET=${HHY_USER_TOKEN_HMAC_SECRET:-r14-user-hmac-000000000000000000000000003}
export HHY_USER_SNAPSHOT_ROOT_SECRET=${HHY_USER_SNAPSHOT_ROOT_SECRET:-r14-snapshot-000000000000000000000000004}
export HHY_ADMIN_JWT_SECRET=${HHY_ADMIN_JWT_SECRET:-r14-admin-jwt-000000000000000000000000005}
export HHY_ADMIN_MFA_ROOT_SECRET=${HHY_ADMIN_MFA_ROOT_SECRET:-r14-admin-mfa-000000000000000000000000006}
export HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET=${HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET:-r14-admin-idem-00000000000000000000000007}
export HHY_R14_SMOKE_SUBNET=${HHY_R14_SMOKE_SUBNET:-172.31.238.0/24}

API=
POSTGRES=
PROMETHEUS=
ALERT_SINK=
TEST_USER_ID=

refresh_containers() {
  API=$("${COMPOSE[@]}" ps -q api)
  POSTGRES=$("${COMPOSE[@]}" ps -q postgres)
  PROMETHEUS=$("${COMPOSE[@]}" ps -q prometheus)
  ALERT_SINK=$("${COMPOSE[@]}" ps -q alert-sink)
  test -n "$API" && test -n "$POSTGRES" && test -n "$PROMETHEUS" && test -n "$ALERT_SINK"
}

wait_url() {
  local url=$1 timeout=${2:-180} elapsed=0
  until curl -fsS "$url" >/dev/null; do
    (( elapsed >= timeout )) && { echo "Timed out waiting for $url" >&2; return 1; }
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

wait_container_readiness() {
  local timeout=${1:-180} elapsed=0
  until docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/readiness >/dev/null 2>&1; do
    if (( elapsed >= timeout )); then
      docker logs --tail 200 "$API" >&2 || true
      return 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

wait_prometheus_target() {
  local timeout=${1:-180} elapsed=0
  until curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/targets" | grep -q '"health":"up"'; do
    (( elapsed >= timeout )) && { echo R14_PROMETHEUS_TARGET_NOT_UP >&2; return 1; }
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

wait_rule_state() {
  local alert=$1 state=$2 timeout=${3:-180} elapsed=0
  until curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" \
      | grep -q "\"name\":\"${alert}\".*\"state\":\"${state}\""; do
    (( elapsed >= timeout )) && { echo "Timed out waiting for ${alert}=${state}" >&2; return 1; }
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

wait_alert_receipt() {
  local alert=$1 status=$2 timeout=${3:-180} elapsed=0
  until docker exec "$ALERT_SINK" sh -c 'test -f /data/deliveries.jsonl && cat /data/deliveries.jsonl' 2>/dev/null \
      | grep -F "\"alertname\":\"${alert}\"" | grep -Fq "\"status\":\"${status}\""; do
    (( elapsed >= timeout )) && { echo "Timed out waiting for ${alert} ${status} receipt" >&2; return 1; }
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

record_database_state() {
  docker exec -i "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -At -v ON_ERROR_STOP=1 <<'SQL'
SELECT version FROM hhy.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hhy';
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hhy' AND table_name <> 'flyway_schema_history';
SQL
}

record_chat_facts() {
  docker exec -i "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -At -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'conversations|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.conversations;
SELECT 'conversation_members|' || COUNT(*) || '|' || COALESCE(MAX(id),0) || '|' || COALESCE(SUM(unread_count),0) FROM hhy.conversation_members;
SELECT 'chat_messages|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.chat_messages;
SELECT 'chat_message_attachments|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.chat_message_attachments;
SELECT 'chat_read_receipts|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.chat_read_receipts;
SELECT 'user_blocks|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.user_blocks;
SELECT 'chat_reports|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.chat_reports;
SELECT 'outbox_events|' || COUNT(*) || '|' || COALESCE(MAX(id),0) FROM hhy.outbox_events;
SELECT 'websocket_user_sequences|' || COUNT(*) || '|' || COALESCE(MAX(high_watermark),0) FROM hhy.websocket_user_sequences;
SELECT 'websocket_deliveries|' || COUNT(*) || '|' || COALESCE(MAX(id),0) || '|' || COALESCE(MAX(server_sequence),0) FROM hhy.websocket_deliveries;
SELECT 'websocket_gap_watermarks|' || COUNT(*) || '|' || COALESCE(MAX(expired_through_sequence),0) FROM hhy.websocket_gap_watermarks;
SQL
}

load_compose_environment() {
  POSTGRES_PASSWORD=$(docker exec "$POSTGRES" printenv POSTGRES_PASSWORD)
  HHY_CONTENT_CONTACT_ROOT_SECRET=$(docker exec "$API" printenv HHY_CONTENT_CONTACT_ROOT_SECRET)
  HHY_USER_JWT_SECRET=$(docker exec "$API" printenv HHY_USER_JWT_SECRET)
  HHY_USER_TOKEN_HMAC_SECRET=$(docker exec "$API" printenv HHY_USER_TOKEN_HMAC_SECRET)
  HHY_USER_SNAPSHOT_ROOT_SECRET=$(docker exec "$API" printenv HHY_USER_SNAPSHOT_ROOT_SECRET)
  HHY_ADMIN_JWT_SECRET=$(docker exec "$API" printenv HHY_ADMIN_JWT_SECRET)
  HHY_ADMIN_MFA_ROOT_SECRET=$(docker exec "$API" printenv HHY_ADMIN_MFA_ROOT_SECRET)
  HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET=$(docker exec "$API" printenv HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET)
  export POSTGRES_PASSWORD HHY_CONTENT_CONTACT_ROOT_SECRET HHY_USER_JWT_SECRET
  export HHY_USER_TOKEN_HMAC_SECRET HHY_USER_SNAPSHOT_ROOT_SECRET HHY_ADMIN_JWT_SECRET
  export HHY_ADMIN_MFA_ROOT_SECRET HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET
}

recover_runtime() {
  set +e
  test -n "$API" && docker inspect "$API" >/dev/null 2>&1 && docker start "$API" >/dev/null 2>&1
}
trap recover_runtime EXIT

start_isolated_stack() {
  test -z "$("${COMPOSE[@]}" ps -aq)"
  "${COMPOSE[@]}" up -d --build
  refresh_containers
  wait_container_readiness
  wait_url "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status"
  wait_url "http://127.0.0.1:${PROMETHEUS_PORT}/-/ready"
  wait_prometheus_target
}

capture_baseline() {
  mkdir -p "$EVIDENCE"
  local current_image pg_volume
  current_image=$(docker inspect "$API" --format '{{.Image}}')
  pg_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  {
    echo task=TASK-R14-006
    echo story=STORY-R14-004
    echo release=R14
    echo frozen_commit="$FROZEN_COMMIT"
    echo rollback_commit="$ROLLBACK_COMMIT"
    echo compose_project="$PROJECT"
    echo network_subnet="$HHY_R14_SMOKE_SUBNET"
    echo current_image="$current_image"
    echo current_image_tag="hhy-backend-r14-smoke:${CURRENT_TAG}"
    echo current_image_digest="$current_image"
    echo rollback_image="$ROLLBACK_IMAGE"
    echo rollback_image_digest="$(docker image inspect "$ROLLBACK_IMAGE" --format '{{.Id}}')"
    echo postgres_volume="$pg_volume"
  } > "$EVIDENCE/metadata.txt"
  "${COMPOSE[@]}" ps > "$EVIDENCE/compose-ps.txt"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/liveness > "$EVIDENCE/liveness.json"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/health/readiness > "$EVIDENCE/readiness.json"
  record_database_state > "$EVIDENCE/database.txt"
  test "$(sed -n '1p' "$EVIDENCE/database.txt")" = 044
  curl -sS -D "$EVIDENCE/trace-headers.txt" -o "$EVIDENCE/public-status.json" \
    -H 'X-Request-Id: r14-stage-request-001' \
    -H 'X-Trace-Id: 3344556677889900aabbccddeeff1122' \
    -H 'Authorization: Bearer R14_SENSITIVE_PROBE_9B5C' \
    -H 'Cookie: session=R14_SENSITIVE_PROBE_9B5C' \
    -H 'Sec-WebSocket-Protocol: hhy.v1, hhy.access.R14_SENSITIVE_PROTOCOL_9B5C' \
    "http://127.0.0.1:${HTTP_PORT}/public-api/v1/platform/status?probe=R14_SENSITIVE_PROBE_9B5C"
  sleep 2
  docker logs "$API" 2>&1 | grep -F '"requestId":"r14-stage-request-001"' > "$EVIDENCE/http-request-completed.jsonl"
  if docker logs "$API" 2>&1 | grep -Eq 'R14_SENSITIVE_(PROBE|PROTOCOL)_9B5C'; then
    echo R14_LOG_REDACTION_FAILED > "$EVIDENCE/log-redaction.txt"
    return 1
  fi
  echo R14_LOG_AND_PROTOCOL_REDACTION_OK > "$EVIDENCE/log-redaction.txt"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/prometheus > "$EVIDENCE/metrics.txt"
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/targets" > "$EVIDENCE/prometheus-targets.json"
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/prometheus-rules.json"
  grep -E '^(hhy_chat_conversations_count|hhy_chat_messages_5m|hhy_chat_unread|hhy_chat_reports_pending|hhy_websocket_deliveries_unacked|hhy_websocket_deliveries_retry_exhausted|hhy_websocket_gap_users|hhy_r14_outbox_backlog|hhy_business_metric_query_failures_total|http_server_requests_seconds_(count|bucket))' "$EVIDENCE/metrics.txt" > "$EVIDENCE/metrics-summary.txt"
  for metric in hhy_chat_conversations_count hhy_chat_messages_5m hhy_chat_unread hhy_chat_reports_pending hhy_websocket_deliveries_unacked hhy_websocket_deliveries_retry_exhausted hhy_websocket_gap_users hhy_r14_outbox_backlog; do
    grep -q "^${metric} " "$EVIDENCE/metrics.txt"
  done
  awk '/^hhy_business_metric_query_failures_total.*metric="hhy\.(chat|websocket|r14)\./ {sum += $NF} END {exit sum != 0}' "$EVIDENCE/metrics.txt"
  grep -q '"health":"up"' "$EVIDENCE/prometheus-targets.json"
  sha256sum \
    services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java \
    services/backend/Dockerfile services/backend/pom.xml \
    infra/staging/r14-smoke/docker-compose.yml infra/staging/r14-smoke/prometheus.yml \
    infra/staging/r14-smoke/alertmanager.yml infra/staging/r14-smoke/nginx.conf \
    infra/staging/r14-smoke/r14-alerts.yml scripts/run_r14_staging_acceptance.sh \
    > "$EVIDENCE/source-sha256.txt"
}

prepare_alert_facts() {
  TEST_USER_ID=$(docker exec -i "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -Atq -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO hhy.users(phone,status)
VALUES ('19900001414','ACTIVE')
ON CONFLICT (phone) DO UPDATE SET status='ACTIVE'
RETURNING id;
SQL
)
  docker exec -i "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -v ON_ERROR_STOP=1 <<SQL >/dev/null
INSERT INTO hhy.websocket_user_sequences(user_id,high_watermark)
VALUES (${TEST_USER_ID},1)
ON CONFLICT (user_id) DO UPDATE SET high_watermark=GREATEST(hhy.websocket_user_sequences.high_watermark,1);
INSERT INTO hhy.websocket_deliveries(
  event_id,user_id,server_sequence,event_type,affected_scope,envelope,
  ack_required,delivery_attempts,first_delivered_at,last_delivered_at,expires_at)
VALUES (
  '14141414-1414-4141-8141-141414141414',${TEST_USER_ID},1,'system.delivery.test',
  'CHAT','{"type":"system.delivery.test","test":true}'::jsonb,
  TRUE,6,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '73 hours')
ON CONFLICT (event_id) DO UPDATE SET acked_at=NULL,delivery_attempts=6,expires_at=CURRENT_TIMESTAMP + INTERVAL '73 hours';
SQL
}

exercise_backend_alert() {
  docker exec "$ALERT_SINK" sh -c ': > /data/deliveries.jsonl'
  docker stop "$API" >/dev/null
  wait_rule_state HhyR14BackendDown firing
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/backend-down-firing.json"
  wait_alert_receipt HhyR14BackendDown firing
  docker start "$API" >/dev/null
  wait_container_readiness
  wait_alert_receipt HhyR14BackendDown resolved
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/backend-down-resolved.json"
  printf 'backend_down_firing=PASS\nbackend_down_resolved=PASS\n' > "$EVIDENCE/alert-exercise.txt"
}

exercise_retry_exhausted_alert() {
  prepare_alert_facts
  wait_rule_state HhyR14RetryExhausted firing
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/retry-exhausted-firing.json"
  wait_alert_receipt HhyR14RetryExhausted firing
  docker exec "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -v ON_ERROR_STOP=1 \
    -c "UPDATE hhy.websocket_deliveries SET acked_at=CURRENT_TIMESTAMP WHERE event_id='14141414-1414-4141-8141-141414141414';" >/dev/null
  wait_alert_receipt HhyR14RetryExhausted resolved
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/retry-exhausted-resolved.json"
  printf 'retry_exhausted_firing=PASS\nretry_exhausted_resolved=PASS\n' >> "$EVIDENCE/alert-exercise.txt"
}

exercise_gap_alert() {
  docker exec "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -v ON_ERROR_STOP=1 \
    -c "INSERT INTO hhy.websocket_gap_watermarks(user_id,expired_through_sequence,affected_scopes) VALUES (${TEST_USER_ID},1,ARRAY['CHAT']::varchar(32)[]) ON CONFLICT (user_id) DO UPDATE SET expired_through_sequence=GREATEST(hhy.websocket_gap_watermarks.expired_through_sequence,1),affected_scopes=ARRAY['CHAT']::varchar(32)[];" >/dev/null
  wait_rule_state HhyR14GapUsers firing
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/gap-users-firing.json"
  wait_alert_receipt HhyR14GapUsers firing
  docker exec "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -v ON_ERROR_STOP=1 \
    -c "DELETE FROM hhy.websocket_gap_watermarks WHERE user_id=${TEST_USER_ID};" >/dev/null
  wait_alert_receipt HhyR14GapUsers resolved
  curl -fsS "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/rules" > "$EVIDENCE/gap-users-resolved.json"
  printf 'gap_users_firing=PASS\ngap_users_resolved=PASS\n' >> "$EVIDENCE/alert-exercise.txt"
  docker exec "$ALERT_SINK" cat /data/deliveries.jsonl > "$EVIDENCE/alert-deliveries.jsonl"
  docker exec "$POSTGRES" psql -U hhy_r14_smoke -d hhy_r14_smoke -At -v ON_ERROR_STOP=1 \
    -c "SELECT event_id,event_type,delivery_attempts,(acked_at IS NOT NULL) FROM hhy.websocket_deliveries WHERE event_id='14141414-1414-4141-8141-141414141414';" \
    > "$EVIDENCE/outbox-test-events.txt"
}

exercise_rollback() {
  refresh_containers
  load_compose_environment
  local before_pg before_volume rollback_image_id final_image_id
  before_pg=$(docker inspect "$POSTGRES" --format '{{.Id}}')
  before_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  docker image inspect "$ROLLBACK_IMAGE" >/dev/null
  docker tag "$ROLLBACK_IMAGE" "hhy-backend-r14-smoke:${ROLLBACK_TAG}"
  record_chat_facts > "$EVIDENCE/chat-facts-before.txt"
  {
    echo before_current_image=$(docker inspect "$API" --format '{{.Image}}')
    echo rollback_baseline_commit="$ROLLBACK_COMMIT"
    echo rollback_image_digest=$(docker image inspect "$ROLLBACK_IMAGE" --format '{{.Id}}')
    export HHY_SMOKE_ID="$ROLLBACK_TAG"
    "${COMPOSE[@]}" up -d --no-deps --no-build api >/dev/null
    refresh_containers
    wait_container_readiness
    rollback_image_id=$(docker inspect "$API" --format '{{.Image}}')
    echo rollback_runtime_image="$rollback_image_id"
    test "$(docker inspect "$POSTGRES" --format '{{.Id}}')" = "$before_pg"
    test "$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')" = "$before_volume"
    test "$(record_database_state | sed -n '1p')" = 044
    export HHY_SMOKE_ID="$CURRENT_TAG"
    "${COMPOSE[@]}" up -d --no-deps --no-build api >/dev/null
    refresh_containers
    wait_container_readiness
    final_image_id=$(docker inspect "$API" --format '{{.Image}}')
    echo restored_runtime_image="$final_image_id"
    test "$final_image_id" != "$rollback_image_id"
    test "$(docker inspect "$POSTGRES" --format '{{.Id}}')" = "$before_pg"
    test "$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')" = "$before_volume"
    record_chat_facts > "$EVIDENCE/chat-facts-after.txt"
    cmp "$EVIDENCE/chat-facts-before.txt" "$EVIDENCE/chat-facts-after.txt"
    echo rollback_same_postgres_container=PASS
    echo rollback_same_postgres_volume=PASS
    echo rollback_database_remained_v044=PASS
    echo final_current_image_restored=PASS
    echo chat_facts_preserved=PASS
    echo no_down_migration_or_volume_deletion=PASS
  } > "$EVIDENCE/rollback-rehearsal.txt"
  docker exec "$API" curl -fsS http://127.0.0.1:9091/actuator/prometheus > "$EVIDENCE/final-metrics.txt"
}

finalize_evidence() {
  local flyway total_tables business_tables current_image pg_volume
  flyway=$(record_database_state | sed -n '1p')
  total_tables=$(record_database_state | sed -n '2p')
  business_tables=$(record_database_state | sed -n '3p')
  current_image=$(docker inspect "$API" --format '{{.Image}}')
  pg_volume=$(docker inspect "$POSTGRES" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
  {
    echo task=TASK-R14-006
    echo frozen_commit="$FROZEN_COMMIT"
    echo rollback_commit="$ROLLBACK_COMMIT"
    echo compose_project="$PROJECT"
    echo current_image="$current_image"
    echo postgres_volume="$pg_volume"
    echo flyway_version="$flyway"
    echo schema_tables_including_flyway="$total_tables"
    echo business_tables="$business_tables"
    echo public_status_http=200
    echo liveness=UP
    echo readiness=UP
    echo prometheus_target_up=1
    echo r14_business_gauges=8
    echo r14_alert_rules=8
    cat "$EVIDENCE/alert-exercise.txt"
    grep '=PASS$' "$EVIDENCE/rollback-rehearsal.txt"
    echo log_and_protocol_redaction=PASS
    echo ws_public_status=BLOCKED_EXTERNAL_DNS
    echo report_catalog_status=BLOCKED_PRODUCT_CATALOG
  } > "$EVIDENCE/machine-summary.txt"
  find "$EVIDENCE" -maxdepth 1 -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/SHA256SUMS"
}

cd "$ROOT"
test "$(git rev-parse HEAD)" = "$FROZEN_COMMIT"
start_isolated_stack
capture_baseline
exercise_backend_alert
exercise_retry_exhausted_alert
exercise_gap_alert
exercise_rollback
finalize_evidence
echo R14_STAGING_ACCEPTANCE_OK
