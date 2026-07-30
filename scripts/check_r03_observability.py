#!/usr/bin/env python3
"""Static safety gate for R03 configuration observability and isolated staging wiring."""
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


compose = yaml.safe_load(text("infra/staging/r03-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r03-smoke/prometheus.yml")
alerts = text("infra/staging/r03-smoke/r03-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r03-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
coordinator_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/admin/ProviderConnectionTestCoordinatorTest.java")
lifecycle_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/admin/ProviderConfigVersionServiceTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R03_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
api_environment = api.get("environment") or {}
require(api_environment.get("HHY_MANAGEMENT_PORT") == 9091, "R03_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(value) for value in api.get("ports") or []), "R03_MANAGEMENT_PORT_PUBLISHED")
for secret in (
    "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET", "HHY_USER_SNAPSHOT_ROOT_SECRET",
    "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET", "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(api_environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R03_SECRET_REQUIRED_{secret}")
require("${HHY_R03_TRUSTED_PROXY_CIDRS:-" in str(api_environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R03_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R03_SMOKE_SUBNET:-" in str(compose.get("networks")), "R03_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R03_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r03-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R03_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r03', 'targets: ["api:9091"]', 'release: R03'):
    require(token in prometheus, f"R03_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR03BackendDown", "HhyR03HighServerErrorRate", "HhyR03HighP95Latency",
    "HhyR03ProviderConnectionFailureBurst", "HhyR03UntestedActiveProviderConfig",
    "HhyR03CertificateExpiring30d", "HhyR03DomainVerificationFailure",
    "HhyR03BusinessMetricQueryFailure", "release: R03",
):
    require(token in alerts, f"R03_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.provider.connection.test.failures.5m",
    "hhy.provider.config.untested.active",
    "hhy.provider.certificates.expiring.30d",
    "hhy.domain.verification.failures",
)
for metric in metric_names:
    require(metric in gauges, f"R03_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R03_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_provider_connection_test_failures_5m", "hhy_provider_config_untested_active",
    "hhy_provider_certificates_expiring_30d", "hhy_domain_verification_failures",
):
    require(token in endpoint_test, f"R03_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "providerExceptionIsReducedToSafeFailureCodeAndCannotAdvance",
    "unavailableSecretIsRecordedWithoutCallingConnector",
    "missingRequiredSecretRefIsRejectedBeforeProbe",
):
    require(token in coordinator_test, f"R03_FAILURE_INJECTION_MISSING_{token}")
require("fullFlowCreatesTestsActivatesSupersedesAndRollsBackAtomically" in lifecycle_test, "R03_ACTIVATION_ROLLBACK_TEST_MISSING")

for token in (
    "R03 隔离预发布验收", "HhyR03ProviderConnectionFailureBurst", "200 张表",
    "连接测试失败不得激活", "artifacts/validation/r03-task006-staging/",
):
    require(token in deployment, f"R03_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R03 配置中心隔离回滚演练", "ROLLED_BACK", "V019", "禁止 U019"):
    require(token in rollback, f"R03_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R03_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R03_OBSERVABILITY_CONFIG_OK")
