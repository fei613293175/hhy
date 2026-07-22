#!/usr/bin/env python3
"""Static safety gate for R09 App observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r09-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r09-smoke/prometheus.yml")
alerts = text("infra/staging/r09-smoke/r09-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r09-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r09_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R09_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R09_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R09_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R09_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R09_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R09_SECRET_REQUIRED_{secret}")
require("${HHY_R09_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R09_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R09_SMOKE_SUBNET:-" in str(compose.get("networks")), "R09_SUBNET_NOT_CONFIGURABLE")
require("172.31.243.0/24" in str(compose.get("networks")), "R09_DEFAULT_SUBNET_NOT_ISOLATED")
require("${HHY_R09_PROMETHEUS_PORT:-39597}" in str((services.get("prometheus") or {}).get("ports")), "R09_PROMETHEUS_PORT_NOT_ISOLATED")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R09_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r09-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R09_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r09', 'targets: ["api:9091"]', 'release: R09'):
    require(token in prometheus, f"R09_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR09BackendDown", "HhyR09HighServerErrorRate", "HhyR09HighP95Latency",
    "HhyR09OutboxBacklog", "HhyR09AppContactRejectionBurst", "HhyR09AppReviewBacklog",
    "HhyR09BusinessMetricQueryFailure", "release: R09",
):
    require(token in alerts, f"R09_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.app.total.count", "hhy.app.online.count", "hhy.app.review.pending",
    "hhy.app.favorites.count", "hhy.app.contact.accesses.5m",
    "hhy.app.contact.rejections.5m", "hhy.r09.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R09_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R09_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_app_total_count", "hhy_app_online_count", "hhy_app_review_pending",
    "hhy_app_favorites_count", "hhy_app_contact_accesses_5m",
    "hhy_app_contact_rejections_5m", "hhy_r09_outbox_backlog",
):
    require(token in endpoint_test, f"R09_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "R09_STAGING_PROJECT_NOT_EMPTY", "R09_STAGING_BUILD_AND_START", "${COMPOSE[@]} up -d --build",
    "HhyR09BackendDown", "HhyR09OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "SHA256SUMS",
):
    require(token in runner, f"R09_RUNNER_CONTRACT_MISSING_{token}")
for token in ("R09 App隔离预发布验收", "HhyR09OutboxBacklog", "V035", "artifacts/validation/r09-task006-staging/"):
    require(token in deployment, f"R09_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R09 App隔离回滚演练", "V035", "禁止 U035", "禁止 U034"):
    require(token in rollback, f"R09_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R09_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R09_OBSERVABILITY_CONFIG_OK")
