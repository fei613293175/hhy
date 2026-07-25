#!/usr/bin/env python3
"""Static safety gate for R12 publishing observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r12-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r12-smoke/prometheus.yml")
alerts = text("infra/staging/r12-smoke/r12-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r12-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r12_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R12_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R12_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R12_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R12_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R12_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R12_SECRET_REQUIRED_{secret}")
require("${HHY_R12_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R12_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R12_SMOKE_SUBNET:-" in str(compose.get("networks")), "R12_SUBNET_NOT_CONFIGURABLE")
require("172.31.243.0/24" in str(compose.get("networks")), "R12_DEFAULT_SUBNET_NOT_ISOLATED")
require("${HHY_R12_PROMETHEUS_PORT:-39614}" in str((services.get("prometheus") or {}).get("ports")), "R12_PROMETHEUS_PORT_NOT_ISOLATED")
require("${HHY_R12_ALERTMANAGER_PORT:-39615}" in str((services.get("alertmanager") or {}).get("ports")), "R12_ALERTMANAGER_PORT_NOT_ISOLATED")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R12_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r12-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R12_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r12', 'targets: ["api:9091"]', 'release: R12'):
    require(token in prometheus, f"R12_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR12BackendDown", "HhyR12HighServerErrorRate", "HhyR12HighP95Latency",
    "HhyR12OutboxBacklog", "HhyR12ReviewBacklog", "HhyR12RejectedBacklog",
    "HhyR12BusinessMetricQueryFailure", "release: R12",
):
    require(token in alerts, f"R12_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.publish.total.count", "hhy.publish.draft.count", "hhy.publish.review.pending",
    "hhy.publish.reviewing.count", "hhy.publish.rejected.count",
    "hhy.publish.online.count", "hhy.r12.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R12_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R12_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_publish_total_count", "hhy_publish_draft_count", "hhy_publish_review_pending",
    "hhy_publish_reviewing_count", "hhy_publish_rejected_count",
    "hhy_publish_online_count", "hhy_r12_outbox_backlog",
):
    require(token in endpoint_test, f"R12_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "R12_STAGING_PROJECT_NOT_EMPTY", "R12_STAGING_BUILD_AND_START", "${COMPOSE[@]} up -d --build",
    "wait_prometheus_target", "R12_PROMETHEUS_TARGET_NOT_UP", "R12_STAGE_CAPTURE_BASELINE_OK",
    "HhyR12BackendDown", "HhyR12OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "publishing_facts_preserved=PASS", "SHA256SUMS",
):
    require(token in runner, f"R12_RUNNER_CONTRACT_MISSING_{token}")
require("hhy-backend-r12-baseline:5419d682" in runner, "R12_ROLLBACK_IMAGE_NOT_FROZEN_R12_BASELINE")
for token in ("R12 统一发布隔离预发布验收", "HhyR12OutboxBacklog", "V041", "artifacts/validation/r12-task006-staging/"):
    require(token in deployment, f"R12_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R12 统一发布隔离回滚演练", "V041", "禁止 U041", "禁止降版本 DDL"):
    require(token in rollback, f"R12_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R12_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R12_OBSERVABILITY_CONFIG_OK")
