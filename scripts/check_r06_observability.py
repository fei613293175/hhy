#!/usr/bin/env python3
"""Static safety gate for R06 content observability and isolated staging wiring."""
from __future__ import annotations

from pathlib import Path
import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r06-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r06-smoke/prometheus.yml")
alerts = text("infra/staging/r06-smoke/r06-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r06-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R06_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R06_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R06_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R06_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R06_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET", "HHY_USER_SNAPSHOT_ROOT_SECRET",
    "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET", "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R06_SECRET_REQUIRED_{secret}")
require("${HHY_R06_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R06_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R06_SMOKE_SUBNET:-" in str(compose.get("networks")), "R06_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R06_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r06-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R06_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r06', 'targets: ["api:9091"]', 'release: R06'):
    require(token in prometheus, f"R06_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR06BackendDown", "HhyR06HighServerErrorRate", "HhyR06HighP95Latency",
    "HhyR06ContentOutboxBacklog", "HhyR06ContentReviewBacklog",
    "HhyR06BusinessMetricQueryFailure", "release: R06",
):
    require(token in alerts, f"R06_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.content.online.count", "hhy.content.review.pending",
    "hhy.content.outbox.backlog", "hhy.home.enabled.modules",
)
for metric in metric_names:
    require(metric in gauges, f"R06_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R06_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_content_online_count", "hhy_content_review_pending",
    "hhy_content_outbox_backlog", "hhy_home_enabled_modules",
):
    require(token in endpoint_test, f"R06_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in ("R06 内容与首页隔离预发布验收", "HhyR06ContentOutboxBacklog", "V030", "artifacts/validation/r06-task006-staging/"):
    require(token in deployment, f"R06_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R06 内容与首页隔离回滚演练", "V030", "禁止 U030"):
    require(token in rollback, f"R06_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R06_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R06_OBSERVABILITY_CONFIG_OK")
