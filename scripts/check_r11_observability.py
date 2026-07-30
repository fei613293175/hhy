#!/usr/bin/env python3
"""Static safety gate for R11 team-leader observability and isolated staging."""
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


compose = yaml.safe_load(text("infra/staging/r11-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r11-smoke/prometheus.yml")
alerts = text("infra/staging/r11-smoke/r11-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r11-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
runner = text("scripts/run_r11_staging_acceptance.sh")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R11_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
environment = api.get("environment") or {}
require(environment.get("HHY_MANAGEMENT_PORT") == 9091, "R11_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(item) for item in api.get("ports") or []), "R11_MANAGEMENT_PORT_PUBLISHED")
require(environment.get("HHY_IDENTITY_SANDBOX_ENABLED") is False, "R11_IDENTITY_SANDBOX_MUST_BE_DISABLED")
require(environment.get("HHY_CI_AUTOMATION_ENABLED") is False, "R11_CI_AUTOMATION_MUST_BE_DISABLED")
for secret in (
    "HHY_CONTENT_CONTACT_ROOT_SECRET", "HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET",
    "HHY_USER_SNAPSHOT_ROOT_SECRET", "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET",
    "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET",
):
    require(str(environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R11_SECRET_REQUIRED_{secret}")
require("${HHY_R11_TRUSTED_PROXY_CIDRS:-" in str(environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R11_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R11_SMOKE_SUBNET:-" in str(compose.get("networks")), "R11_SUBNET_NOT_CONFIGURABLE")
require("172.31.241.0/24" in str(compose.get("networks")), "R11_DEFAULT_SUBNET_NOT_ISOLATED")
require("${HHY_R11_PROMETHEUS_PORT:-39612}" in str((services.get("prometheus") or {}).get("ports")), "R11_PROMETHEUS_PORT_NOT_ISOLATED")
require("${HHY_R11_ALERTMANAGER_PORT:-39613}" in str((services.get("alertmanager") or {}).get("ports")), "R11_ALERTMANAGER_PORT_NOT_ISOLATED")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R11_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r11-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R11_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r11', 'targets: ["api:9091"]', 'release: R11'):
    require(token in prometheus, f"R11_PROMETHEUS_WIRING_MISSING_{token}")
for token in (
    "HhyR11BackendDown", "HhyR11HighServerErrorRate", "HhyR11HighP95Latency",
    "HhyR11OutboxBacklog", "HhyR11TeamLeaderContactRejectionBurst", "HhyR11TeamLeaderReviewBacklog",
    "HhyR11BusinessMetricQueryFailure", "release: R11",
):
    require(token in alerts, f"R11_ALERT_RULE_MISSING_{token}")

metric_names = (
    "hhy.team.leader.total.count", "hhy.team.leader.online.count", "hhy.team.leader.review.pending",
    "hhy.team.leader.favorites.count", "hhy.team.leader.contact.accesses.5m",
    "hhy.team.leader.contact.rejections.5m", "hhy.r11.outbox.backlog",
)
for metric in metric_names:
    require(metric in gauges, f"R11_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R11_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in (
    "hhy_team_leader_total_count", "hhy_team_leader_online_count", "hhy_team_leader_review_pending",
    "hhy_team_leader_favorites_count", "hhy_team_leader_contact_accesses_5m",
    "hhy_team_leader_contact_rejections_5m", "hhy_r11_outbox_backlog",
):
    require(token in endpoint_test, f"R11_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

for token in (
    "FROZEN_COMMIT", "test \"$(git rev-parse HEAD)\" = \"$FROZEN_COMMIT\"",
    "R11_STAGING_PROJECT_NOT_EMPTY", "R11_STAGING_BUILD_AND_START", "${COMPOSE[@]} up -d --build",
    "wait_prometheus_target", "R11_PROMETHEUS_TARGET_NOT_UP", "R11_STAGE_CAPTURE_BASELINE_OK",
    "HhyR11BackendDown", "HhyR11OutboxBacklog", "PUBLISHING", "PUBLISHED",
    "rollback_same_postgres_volume=PASS", "team_leader_facts_preserved=PASS", "SHA256SUMS",
):
    require(token in runner, f"R11_RUNNER_CONTRACT_MISSING_{token}")
require("hhy-backend-r11-baseline:5013a9a0" in runner, "R11_ROLLBACK_IMAGE_NOT_FROZEN_R11_BASELINE")
for token in ("R11 团队长隔离预发布验收", "HhyR11OutboxBacklog", "V038", "artifacts/validation/r11-task006-staging/"):
    require(token in deployment, f"R11_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R11 团队长隔离回滚演练", "V038", "禁止 U038", "禁止降版本 DDL"):
    require(token in rollback, f"R11_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R11_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R11_OBSERVABILITY_CONFIG_OK")
