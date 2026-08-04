#!/usr/bin/env python3
"""Static safety gate for R19 prop and exposure observability."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


alerts = yaml.safe_load(text("infra/staging/r19-alerts.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
request_filter = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/RequestIdFilter.java")

metric_names = (
    "hhy.prop.inventory.available",
    "hhy.prop.executions.failed.5m",
    "hhy.prop.executions.retrying",
    "hhy.exposure.expired.active",
    "hhy.headline.bookings.overdue",
    "hhy.r19.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R19_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R19_BUSINESS_METRIC_TEST_MISSING_{metric}")
    require(metric.replace(".", "_") in endpoint_test, f"R19_ENDPOINT_METRIC_MISSING_{metric}")

alert_names = {
    rule.get("alert")
    for group in alerts.get("groups", [])
    for rule in group.get("rules", [])
}
for alert in (
    "HhyR19BackendDown",
    "HhyR19HighServerErrorRate",
    "HhyR19HighP95Latency",
    "HhyR19PropExecutionFailureBurst",
    "HhyR19PropExecutionRetryBacklog",
    "HhyR19ExpiredExposureStillActive",
    "HhyR19HeadlineBookingOverdue",
    "HhyR19OutboxBacklog",
    "HhyR19BusinessMetricQueryFailure",
):
    require(alert in alert_names, f"R19_ALERT_RULE_MISSING_{alert}")

require('job="hhy-backend-r19"' in text("infra/staging/r19-alerts.yml"), "R19_ALERT_JOB_SCOPE_MISSING")
require("traceId" in request_filter and "request_completed" in request_filter, "R19_TRACE_LOG_CHAIN_MISSING")
require("http_server_requests_seconds_bucket" in endpoint_test, "R19_RED_METRIC_TEST_MISSING")

if ERRORS:
    print("\n".join(ERRORS))
    print("R19_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R19_OBSERVABILITY_CONFIG_OK")
