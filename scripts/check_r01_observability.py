#!/usr/bin/env python3
"""Static safety gate for R01 observability and isolated staging wiring."""
from __future__ import annotations

from decimal import Decimal
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def config_default(key: str) -> str:
    registry = yaml.safe_load(text("config/CONFIG_REGISTRY.yaml")) or []
    items = (registry.get("items") or []) if isinstance(registry, dict) else registry
    for item in items:
        if isinstance(item, dict) and item.get("key") == key:
            return str(item.get("default"))
    return ""


compose = yaml.safe_load(text("infra/staging/r01-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r01-smoke/prometheus.yml")
alerts = text("infra/staging/r01-smoke/r01-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r01-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
api_error = text("services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/api/ApiErrorResponse.java")
security_error = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityErrorResponseWriter.java")
global_errors = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandler.java")
observability_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ["postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"]:
    require(service in services, f"R01_STAGING_SERVICE_MISSING_{service}")

api = services.get("api") or {}
api_environment = api.get("environment") or {}
require(api_environment.get("HHY_MANAGEMENT_PORT") == 9091, "R01_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(value) for value in api.get("ports") or []), "R01_MANAGEMENT_PORT_PUBLISHED")
require("${HHY_R01_TRUSTED_PROXY_CIDRS:-" in str(api_environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R01_TRUSTED_PROXY_NOT_CONFIGURABLE")
network_config = ((((compose.get("networks") or {}).get("smoke") or {}).get("ipam") or {}).get("config") or [{}])[0]
require("${HHY_R01_SMOKE_SUBNET:-" in str(network_config.get("subnet")), "R01_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R01_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
require("/data" in "\n".join(str(value) for value in sink.get("volumes") or []), "R01_ALERT_RECEIPT_VOLUME_MISSING")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r01-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R01_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ['job_name: hhy-backend-r01', 'targets: ["api:9091"]', 'release: R01']:
    require(token in prometheus, f"R01_PROMETHEUS_WIRING_MISSING_{token}")
for token in [
        "HhyR01BackendDown", "HhyR01HighServerErrorRate", "HhyR01HighP95Latency",
        "HhyR01AdminAuthFailureBurst", "HhyR01IncompleteIdempotencySnapshot",
        "HhyR01BusinessMetricQueryFailure", "release: R01"]:
    require(token in alerts, f"R01_ALERT_RULE_MISSING_{token}")

error_threshold = Decimal(config_default("ops.alert.error_rate_percent")) / Decimal("100")
latency_threshold = Decimal(config_default("ops.alert.api_p95_ms")) / Decimal("1000")
require(f"> {error_threshold}" in alerts, "R01_ERROR_RATE_THRESHOLD_DRIFT")
require(f"> {latency_threshold}" in alerts, "R01_P95_THRESHOLD_DRIFT")

metric_names = [
    "hhy.admin.active.sessions",
    "hhy.admin.auth.failures.5m",
    "hhy.admin.mfa.active.methods",
    "hhy.admin.idempotency.incomplete.snapshots",
    "hhy.business.metric.query.failures",
]
for metric in metric_names:
    require(metric in gauges, f"R01_BUSINESS_METRIC_MISSING_{metric}")
require("queryFailures.increment()" in gauges, "R01_GAUGE_FAILURE_COUNTER_NOT_INCREMENTED")

require("String traceId" in api_error and "retryable, traceId" in api_error, "API_ERROR_TRACE_ID_NOT_EXPLICIT")
require('request.getAttribute("traceId")' in security_error, "SECURITY_ERROR_TRACE_ID_NOT_PROPAGATED")
require("NoResourceFoundException" in global_errors and "COMMON-404-NOT_FOUND" in global_errors, "UNKNOWN_RESOURCE_NOT_MAPPED_TO_404")
for token in ["securityRejectionKeepsRequestIdAndTraceIdDistinctAndCorrelated", "$.error.traceId", "X-Trace-Id"]:
    require(token in observability_test, f"R01_TRACE_REJECTION_TEST_MISSING_{token}")

for token in ["R01 隔离预发布验收", "promtool check rules", "hhy_business_metric_query_failures_total", "AC-R01-004"]:
    require(token in deployment, f"R01_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ["R01 隔离预发布回滚演练", "--no-deps api", "前向修复", "down -v"]:
    require(token in rollback, f"R01_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    for error in ERRORS:
        print(error)
    print("R01_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R01_OBSERVABILITY_CONFIG_OK")
