#!/usr/bin/env python3
"""Static safety gate for R10 群聊 observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r10-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r10-smoke/prometheus.yml")
alerts = text("infra/staging/r10-smoke/r10-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r10-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r10_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R10_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R10_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R10_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R10_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R10_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R10_SECRET_REQUIRED_{secret}")
require("${HHY_R10_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R10_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R10_SMOKE_SUBNET:-" in str(compose.get("networks")), "R10_SUBNET_NOT_CONFIGURABLE")
require("172.31.242.0/24" in str(compose.get("networks")), "R10_DEFAULT_SUBNET_NOT_ISOLATED")
require("${HHY_R10_PROMETHEUS_PORT:-39610}" in str((services.get("prometheus") or {}).get("ports")), "R10_PROMETHEUS_PORT_NOT_ISOLATED")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R10_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r10-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R10_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r10', 'targets: ["api:9091"]', 'release: R10'):
    require(token in prometheus, f"R10_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR10BackendDown", "HhyR10HighServerErrorRate", "HhyR10HighP95Latency",
    "HhyR10OutboxBacklog", "HhyR10GroupContactRejectionBurst", "HhyR10GroupReviewBacklog",
    "HhyR10BusinessMetricQueryFailure", "release: R10",
):
    require(token in alerts, f"R10_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.group.total.count", "hhy.group.online.count", "hhy.group.review.pending",
    "hhy.group.favorites.count", "hhy.group.contact.accesses.5m",
    "hhy.group.contact.rejections.5m", "hhy.r10.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R10_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R10_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_group_total_count", "hhy_group_online_count", "hhy_group_review_pending",
    "hhy_group_favorites_count", "hhy_group_contact_accesses_5m",
    "hhy_group_contact_rejections_5m", "hhy_r10_outbox_backlog",
):
    require(token in endpoint_test, f"R10_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "R10_STAGING_PROJECT_NOT_EMPTY", "R10_STAGING_BUILD_AND_START", "${COMPOSE[@]} up -d --build",
    "wait_prometheus_target", "R10_PROMETHEUS_TARGET_NOT_UP", "R10_STAGE_CAPTURE_BASELINE_OK",
    "HhyR10BackendDown", "HhyR10OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "SHA256SUMS",
):
    require(token in runner, f"R10_RUNNER_CONTRACT_MISSING_{token}")
require("hhy-backend-r09-smoke:d056c54f" in runner, "R10_ROLLBACK_IMAGE_NOT_FROZEN_R09")
for token in ("R10 群聊隔离预发布验收", "HhyR10OutboxBacklog", "V036", "artifacts/validation/r10-task006-staging/"):
    require(token in deployment, f"R10_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R10 群聊隔离回滚演练", "V036", "禁止 U036", "禁止 U035"):
    require(token in rollback, f"R10_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R10_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R10_OBSERVABILITY_CONFIG_OK")
