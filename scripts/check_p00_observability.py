#!/usr/bin/env python3
"""Static safety gate for P00 application observability and staging wiring."""
from __future__ import annotations

from pathlib import Path
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


pom = text("services/backend/boot/pom.xml")
application = yaml.safe_load(text("services/backend/boot/src/main/resources/application.yml")) or {}
security = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityConfiguration.java")
request_filter = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/RequestIdFilter.java")
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
dockerfile = text("services/backend/Dockerfile")
compose = yaml.safe_load(text("infra/staging/docker-compose.p00.yml")) or {}
prometheus = text("infra/staging/prometheus.yml")
alerts = text("infra/staging/p00-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/alertmanager.yml")) or {}
alert_sink = text("infra/staging/alert-sink/alert_sink.py")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

require("micrometer-registry-prometheus" in pom, "PROMETHEUS_REGISTRY_DEPENDENCY_MISSING")
structured_format = (((application.get("logging") or {}).get("structured") or {}).get("format") or {}).get("console")
require(structured_format == "logstash", "STRUCTURED_JSON_LOGGING_NOT_ENABLED")
management = application.get("management") or {}
require((management.get("server") or {}).get("port") == "${HHY_MANAGEMENT_PORT:9091}", "MANAGEMENT_PORT_NOT_ISOLATED")
histograms = (((management.get("metrics") or {}).get("distribution") or {}).get("percentiles-histogram") or {})
require(histograms.get("http.server.requests") is True, "HTTP_REQUEST_HISTOGRAM_NOT_ENABLED")
web_exposure = (((management.get("endpoints") or {}).get("web") or {}).get("exposure") or {}).get("include")
if isinstance(web_exposure, str):
    exposed = {value.strip() for value in web_exposure.split(",") if value.strip()}
else:
    exposed = set(web_exposure or [])
require(exposed == {"health", "info", "prometheus"}, "ACTUATOR_EXPOSURE_NOT_MINIMAL")
require((((management.get("endpoint") or {}).get("health") or {}).get("show-details")) == "never", "HEALTH_DETAILS_NOT_HIDDEN")
require("/actuator/prometheus" in security and ".permitAll()" in security, "PROMETHEUS_SECURITY_RULE_MISSING")

for field in ["requestId", "traceId", "operation", "status", "latency"]:
    require(f'"{field}"' in request_filter, f"REQUEST_LOG_FIELD_MISSING_{field}")
for forbidden in ["getParameter(", "getParameterMap(", "getQueryString(", "getCookies(", '"Authorization"', '"Cookie"', '"password"']:
    require(forbidden not in request_filter, f"SENSITIVE_REQUEST_LOGGING_{forbidden}")

metric_names = {
    "hhy.outbox.backlog": "hhy.outbox_events",
    "hhy.outbox.dead.letter": "DEAD_LETTER",
    "hhy.ledger.unbalanced.transactions": "hhy.accounting_entries",
    "hhy.reconciliation.open.differences": "hhy.reconciliation_differences",
}
for metric, source in metric_names.items():
    require(metric in gauges, f"BUSINESS_GAUGE_MISSING_{metric}")
    require(source in gauges, f"BUSINESS_GAUGE_SOURCE_MISSING_{metric}")

backend = ((compose.get("services") or {}).get("backend") or {})
environment = backend.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "STAGING_MANAGEMENT_PORT_MISSING")
published_ports = "\n".join(str(value) for value in backend.get("ports") or [])
require("9091" not in published_ports, "MANAGEMENT_PORT_PUBLISHED_TO_HOST")
healthcheck = " ".join(str(value) for value in (backend.get("healthcheck") or {}).get("test") or [])
require("127.0.0.1:9091/actuator/health/readiness" in healthcheck, "STAGING_READINESS_NOT_ON_MANAGEMENT_PORT")
require('targets: ["backend:9091"]' in prometheus, "PROMETHEUS_TARGET_PORT_DRIFT")
require("127.0.0.1:9091/actuator/health/readiness" in dockerfile, "IMAGE_HEALTHCHECK_PORT_DRIFT")
require("http_server_requests_seconds_bucket" in alerts, "P95_ALERT_WITHOUT_HISTOGRAM_BUCKET")

services = compose.get("services") or {}
sink_service = services.get("alert-sink") or {}
require(bool(sink_service), "ALERT_SINK_SERVICE_MISSING")
require(not (sink_service.get("ports") or []), "ALERT_SINK_MUST_NOT_PUBLISH_PORT")
require("/data" in "\n".join(str(value) for value in sink_service.get("volumes") or []), "ALERT_SINK_RECEIPT_VOLUME_MISSING")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
audit_receiver = receivers.get("p00-audit-log") or {}
webhooks = audit_receiver.get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "ALERT_DELIVERY_RECEIVER_MISSING")
for token in ["body_sha256", "group_status", "alertname", "severity", "status"]:
    require(token in alert_sink, f"ALERT_RECEIPT_FIELD_MISSING_{token}")
for forbidden in ["Authorization", "Cookie", "password", "self.headers.items"]:
    require(forbidden not in alert_sink, f"ALERT_SINK_SENSITIVE_CAPTURE_{forbidden}")

for token in ["docker compose", "actuator/health/readiness", "hhy_outbox_backlog", "http_server_requests_seconds_bucket", "deliveries.jsonl", "http_request_completed", "jq"]:
    require(token in deployment, f"DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ["--no-deps backend", "前向修复", "docker compose down -v", "hhy_ledger_unbalanced_transactions"]:
    require(token in rollback, f"ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    for error in ERRORS:
        print(error)
    print("P00_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("P00_OBSERVABILITY_CONFIG_OK")
