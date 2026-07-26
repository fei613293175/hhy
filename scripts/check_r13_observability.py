#!/usr/bin/env python3
"""Static safety gate for R13 activity observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r13-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r13-smoke/prometheus.yml")
alerts = text("infra/staging/r13-smoke/r13-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r13-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r13_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R13_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R13_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R13_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R13_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R13_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R13_SECRET_REQUIRED_{secret}")
require("${HHY_R13_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R13_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R13_SMOKE_SUBNET:-" in str(compose.get("networks")), "R13_SUBNET_NOT_CONFIGURABLE")
require("172.31.239.0/24" in str(compose.get("networks")), "R13_DEFAULT_SUBNET_NOT_ISOLATED")
require("${HHY_R13_PROMETHEUS_PORT:-39616}" in str((services.get("prometheus") or {}).get("ports")), "R13_PROMETHEUS_PORT_NOT_ISOLATED")
require("${HHY_R13_ALERTMANAGER_PORT:-39617}" in str((services.get("alertmanager") or {}).get("ports")), "R13_ALERTMANAGER_PORT_NOT_ISOLATED")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R13_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r13-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R13_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r13', 'targets: ["api:9091"]', 'release: R13'):
    require(token in prometheus, f"R13_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR13BackendDown", "HhyR13HighServerErrorRate", "HhyR13HighP95Latency",
    "HhyR13OutboxBacklog", "HhyR13InvalidFeedbackBacklog", "HhyR13ContactRejectionBurst",
    "HhyR13BusinessMetricQueryFailure", "release: R13",
):
    require(token in alerts, f"R13_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.activity.favorites.count", "hhy.activity.history.rows", "hhy.activity.shares.5m",
    "hhy.activity.contact.accesses.5m", "hhy.activity.contact.rejections.5m",
    "hhy.activity.invalid.feedback.pending", "hhy.r13.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R13_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R13_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_activity_favorites_count", "hhy_activity_history_rows", "hhy_activity_shares_5m",
    "hhy_activity_contact_accesses_5m", "hhy_activity_contact_rejections_5m",
    "hhy_activity_invalid_feedback_pending", "hhy_r13_outbox_backlog",
):
    require(token in endpoint_test, f"R13_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "R13_STAGING_PROJECT_NOT_EMPTY", "R13_STAGING_BUILD_AND_START", "${COMPOSE[@]} up -d --build",
    "wait_prometheus_target", "R13_PROMETHEUS_TARGET_NOT_UP", "R13_STAGE_CAPTURE_BASELINE_OK",
    "HhyR13BackendDown", "HhyR13OutboxBacklog", "R13_ACTIVITY", "PUBLISHING", "PUBLISHED",
    "STORY-R13-003", "record_activity_facts", "activity-facts-before.txt", "activity-facts-after.txt",
    "rollback_same_postgres_volume=PASS", "activity_facts_preserved=PASS", "SHA256SUMS",
):
    require(token in runner, f"R13_RUNNER_CONTRACT_MISSING_{token}")
require("hhy-backend-r13-baseline:c9741759" in runner, "R13_ROLLBACK_IMAGE_NOT_FROZEN_R13_BASELINE")
for token in ("R13 活动域隔离预发布验收", "HhyR13OutboxBacklog", "V042", "artifacts/validation/r13-task006-staging/"):
    require(token in deployment, f"R13_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R13 活动域隔离回滚演练", "V042", "禁止 U042", "禁止降版本 DDL"):
    require(token in rollback, f"R13_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R13_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R13_OBSERVABILITY_CONFIG_OK")
