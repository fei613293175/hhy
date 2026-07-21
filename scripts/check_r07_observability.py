#!/usr/bin/env python3
"""Static safety gate for R07 search/contact observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r07-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r07-smoke/prometheus.yml")
alerts = text("infra/staging/r07-smoke/r07-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r07-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r07_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R07_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R07_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R07_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R07_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R07_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R07_SECRET_REQUIRED_{secret}")
require("${HHY_R07_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R07_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R07_SMOKE_SUBNET:-" in str(compose.get("networks")), "R07_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R07_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r07-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R07_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r07', 'targets: ["api:9091"]', 'release: R07'):
    require(token in prometheus, f"R07_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR07BackendDown", "HhyR07HighServerErrorRate", "HhyR07HighP95Latency",
    "HhyR07OutboxBacklog", "HhyR07ContactRejectionBurst", "HhyR07SearchHistoryRowsHigh",
    "HhyR07BusinessMetricQueryFailure", "release: R07",
):
    require(token in alerts, f"R07_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.search.history.rows", "hhy.search.hot.terms.active", "hhy.publisher.active.count",
    "hhy.contact.accesses.5m", "hhy.contact.rejections.5m", "hhy.r07.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R07_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R07_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_search_history_rows", "hhy_search_hot_terms_active", "hhy_publisher_active_count",
    "hhy_contact_accesses_5m", "hhy_contact_rejections_5m", "hhy_r07_outbox_backlog",
):
    require(token in endpoint_test, f"R07_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "HhyR07BackendDown", "HhyR07OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "SHA256SUMS",
):
    require(token in runner, f"R07_RUNNER_CONTRACT_MISSING_{token}")
for token in ("R07 搜索与联系方式隔离预发布验收", "HhyR07OutboxBacklog", "V032", "artifacts/validation/r07-task006-staging/"):
    require(token in deployment, f"R07_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R07 搜索与联系方式隔离回滚演练", "V032", "禁止 U032"):
    require(token in rollback, f"R07_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R07_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R07_OBSERVABILITY_CONFIG_OK")
