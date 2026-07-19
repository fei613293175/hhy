#!/usr/bin/env python3
"""Static safety gate for R05 identity observability and isolated staging wiring."""
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


compose = yaml.safe_load(text("infra/staging/r05-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r05-smoke/prometheus.yml")
alerts = text("infra/staging/r05-smoke/r05-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r05-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
provider_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/identity/AliyunMarketIdentityProviderClientTest.java")
callback_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/identity/IdentityCallbackServiceTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R05_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R05_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R05_MANAGEMENT_PORT_PUBLISHED")
for secret in (
    "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET", "HHY_USER_SNAPSHOT_ROOT_SECRET",
    "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET", "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R05_SECRET_REQUIRED_{secret}")
require("${HHY_R05_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R05_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R05_SMOKE_SUBNET:-" in str(compose.get("networks")), "R05_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R05_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r05-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R05_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r05', 'targets: ["api:9091"]', 'release: R05'):
    require(token in prometheus, f"R05_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR05BackendDown", "HhyR05HighServerErrorRate", "HhyR05HighP95Latency",
    "HhyR05IdentityProviderFailureBurst", "HhyR05IdentityManualReviewBacklog",
    "HhyR05IdentityPrivateMediaInvalid", "HhyR05BusinessMetricQueryFailure", "release: R05",
):
    require(token in alerts, f"R05_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.identity.active.sessions", "hhy.identity.provider.failures.5m",
    "hhy.identity.manual.review.pending", "hhy.identity.private.media.invalid",
)
for metric in metric_names:
    require(metric in gauges, f"R05_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R05_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_identity_active_sessions", "hhy_identity_provider_failures_5m",
    "hhy_identity_manual_review_pending", "hhy_identity_private_media_invalid",
):
    require(token in endpoint_test, f"R05_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

require("mapsTransportTimeoutToRetryableFailureAndZeroizesSecret" in provider_test, "R05_PROVIDER_TIMEOUT_TEST_MISSING")
require("concurrentDuplicateCallbacksHaveExactlyOneBusinessResult" in callback_test, "R05_CALLBACK_CONCURRENCY_TEST_MISSING")
for token in ("R05 实名认证隔离预发布验收", "HhyR05IdentityProviderFailureBurst", "V028", "artifacts/validation/r05-task006-staging/"):
    require(token in deployment, f"R05_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R05 实名认证隔离回滚演练", "V028", "禁止 U028"):
    require(token in rollback, f"R05_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R05_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R05_OBSERVABILITY_CONFIG_OK")
