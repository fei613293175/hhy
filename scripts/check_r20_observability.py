#!/usr/bin/env python3
"""Static safety gate for R20 red-packet observability."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


alerts_text = text("infra/staging/r20-alerts.yml")
alerts = yaml.safe_load(alerts_text) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
request_filter = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/RequestIdFilter.java")

metric_names = (
    "hhy.redpacket.campaigns.pending.review",
    "hhy.redpacket.campaigns.active.expired",
    "hhy.redpacket.stock.available",
    "hhy.redpacket.stock.invariant.violations",
    "hhy.redpacket.quotes.initial.expired",
)
for metric in metric_names:
    require(metric in gauges, f"R20_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R20_BUSINESS_METRIC_TEST_MISSING_{metric}")
    require(metric.replace(".", "_") in endpoint_test, f"R20_ENDPOINT_METRIC_MISSING_{metric}")

alert_names = {
    rule.get("alert")
    for group in alerts.get("groups", [])
    for rule in group.get("rules", [])
}
for alert in (
    "HhyR20BackendDown",
    "HhyR20HighServerErrorRate",
    "HhyR20HighP95Latency",
    "HhyR20CampaignReviewBacklog",
    "HhyR20ActiveCampaignExpired",
    "HhyR20StockInvariantViolation",
    "HhyR20InitialQuoteExpired",
    "HhyR20BusinessMetricQueryFailure",
):
    require(alert in alert_names, f"R20_ALERT_RULE_MISSING_{alert}")

require('job="hhy-backend-r20"' in alerts_text, "R20_ALERT_JOB_SCOPE_MISSING")
require("http_server_requests_seconds_bucket" in alerts_text, "R20_P95_ALERT_WITHOUT_HISTOGRAM_BUCKET")
require("traceId" in request_filter and "request_completed" in request_filter, "R20_TRACE_LOG_CHAIN_MISSING")
require("http_server_requests_seconds_bucket" in endpoint_test, "R20_RED_METRIC_TEST_MISSING")

if ERRORS:
    print("\n".join(ERRORS))
    print("R20_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R20_OBSERVABILITY_CONFIG_OK")
