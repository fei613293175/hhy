#!/usr/bin/env python3
"""Static safety gate for R02 auth observability and isolated staging wiring."""
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


compose = yaml.safe_load(text("infra/staging/r02-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r02-smoke/prometheus.yml")
alerts = text("infra/staging/r02-smoke/r02-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r02-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
provider_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthVerificationServiceTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R02_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
api_environment = api.get("environment") or {}
require(api_environment.get("HHY_MANAGEMENT_PORT") == 9091, "R02_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(value) for value in api.get("ports") or []), "R02_MANAGEMENT_PORT_PUBLISHED")
require("${HHY_R02_TRUSTED_PROXY_CIDRS:-" in str(api_environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R02_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R02_SMOKE_SUBNET:-" in str(compose.get("networks")), "R02_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R02_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r02-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R02_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r02', 'targets: ["api:9091"]', 'release: R02'):
    require(token in prometheus, f"R02_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR02BackendDown", "HhyR02HighServerErrorRate", "HhyR02HighP95Latency",
    "HhyR02UserChallengeFailureBurst", "HhyR02ExpiredUnusedSmsCodes",
    "HhyR02BusinessMetricQueryFailure", "release: R02",
):
    require(token in alerts, f"R02_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.user.active.sessions",
    "hhy.user.security.challenge.failures.5m",
    "hhy.user.sms.expired.unused",
)
for metric in metric_names:
    require(metric in gauges, f"R02_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R02_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in ("hhy_user_active_sessions", "hhy_user_security_challenge_failures_5m", "hhy_user_sms_expired_unused"):
    require(token in endpoint_test, f"R02_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")
require("missingSmsProviderFailsClosedBeforeChallengeOrCodePersistence" in provider_test, "R02_SMS_PROVIDER_FAILURE_INJECTION_MISSING")

for token in (
    "R02 隔离预发布验收",
    "HhyR02UserChallengeFailureBurst",
    "POSTGRESQL_MIGRATION_SMOKE PASS",
    "AC-R02-004",
):
    require(token in deployment, f"R02_DEPLOYMENT_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R02_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R02_OBSERVABILITY_CONFIG_OK")
