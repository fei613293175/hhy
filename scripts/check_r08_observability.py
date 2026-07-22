#!/usr/bin/env python3
"""Static safety gate for R08 project observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r08-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r08-smoke/prometheus.yml")
alerts = text("infra/staging/r08-smoke/r08-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r08-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r08_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R08_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R08_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R08_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R08_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R08_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R08_SECRET_REQUIRED_{secret}")
require("${HHY_R08_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R08_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R08_SMOKE_SUBNET:-" in str(compose.get("networks")), "R08_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R08_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r08-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R08_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r08', 'targets: ["api:9091"]', 'release: R08'):
    require(token in prometheus, f"R08_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR08BackendDown", "HhyR08HighServerErrorRate", "HhyR08HighP95Latency",
    "HhyR08OutboxBacklog", "HhyR08ProjectContactRejectionBurst", "HhyR08ProjectReviewBacklog",
    "HhyR08BusinessMetricQueryFailure", "release: R08",
):
    require(token in alerts, f"R08_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.project.total.count", "hhy.project.online.count", "hhy.project.review.pending",
    "hhy.project.favorites.count", "hhy.project.contact.accesses.5m",
    "hhy.project.contact.rejections.5m", "hhy.r08.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R08_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R08_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_project_total_count", "hhy_project_online_count", "hhy_project_review_pending",
    "hhy_project_favorites_count", "hhy_project_contact_accesses_5m",
    "hhy_project_contact_rejections_5m", "hhy_r08_outbox_backlog",
):
    require(token in endpoint_test, f"R08_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "HhyR08BackendDown", "HhyR08OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "SHA256SUMS",
):
    require(token in runner, f"R08_RUNNER_CONTRACT_MISSING_{token}")
for token in ("R08 项目隔离预发布验收", "HhyR08OutboxBacklog", "V034", "artifacts/validation/r08-task006-staging/"):
    require(token in deployment, f"R08_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R08 项目隔离回滚演练", "V034", "禁止 U034"):
    require(token in rollback, f"R08_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R08_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R08_OBSERVABILITY_CONFIG_OK")
