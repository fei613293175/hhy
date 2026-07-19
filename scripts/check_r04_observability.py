#!/usr/bin/env python3
"""Static safety gate for R04 media/storage observability and isolated staging wiring."""
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

compose = yaml.safe_load(text("infra/staging/r04-smoke/docker-compose.yml")) or {}
prometheus = text("infra/staging/r04-smoke/prometheus.yml")
alerts = text("infra/staging/r04-smoke/r04-alerts.yml")
alertmanager = yaml.safe_load(text("infra/staging/r04-smoke/alertmanager.yml")) or {}
gauges = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java")
gauge_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java")
endpoint_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java")
exception_handler = text("services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandler.java")
exception_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandlerTest.java")
migration_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/StorageMigrationServiceTest.java")
provider_test = text("services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/StorageProviderAdapterTest.java")
deployment = text("docs/07-operations/DEPLOYMENT_RUNBOOK.md")
rollback = text("docs/07-operations/ROLLBACK_RUNBOOK.md")

services = compose.get("services") or {}
for service in ("postgres", "api", "nginx", "prometheus", "alertmanager", "alert-sink"):
    require(service in services, f"R04_STAGING_SERVICE_MISSING_{service}")
api = services.get("api") or {}
api_environment = api.get("environment") or {}
require(api_environment.get("HHY_MANAGEMENT_PORT") == 9091, "R04_MANAGEMENT_PORT_MISSING")
require("9091" not in "\n".join(str(value) for value in api.get("ports") or []), "R04_MANAGEMENT_PORT_PUBLISHED")
for secret in ("HHY_USER_JWT_SECRET", "HHY_USER_TOKEN_HMAC_SECRET", "HHY_USER_SNAPSHOT_ROOT_SECRET",
               "HHY_ADMIN_JWT_SECRET", "HHY_ADMIN_MFA_ROOT_SECRET", "HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET"):
    require(str(api_environment.get(secret, "")) == f"${{{secret}:?{secret} is required}}", f"R04_SECRET_REQUIRED_{secret}")
require("${HHY_R04_TRUSTED_PROXY_CIDRS:-" in str(api_environment.get("HHY_ADMIN_TRUSTED_PROXY_CIDRS")), "R04_TRUSTED_PROXY_NOT_CONFIGURABLE")
require("${HHY_R04_SMOKE_SUBNET:-" in str(compose.get("networks")), "R04_SUBNET_NOT_CONFIGURABLE")

sink = services.get("alert-sink") or {}
require(not (sink.get("ports") or []), "R04_ALERT_SINK_MUST_NOT_PUBLISH_PORT")
receivers = {item.get("name"): item for item in alertmanager.get("receivers") or [] if isinstance(item, dict)}
webhooks = (receivers.get("r04-audit-log") or {}).get("webhook_configs") or []
require(any(item.get("url") == "http://alert-sink:8080/alerts" and item.get("send_resolved") is True for item in webhooks), "R04_ALERT_DELIVERY_RECEIVER_MISSING")

for token in ('job_name: hhy-backend-r04', 'targets: ["api:9091"]', 'release: R04'):
    require(token in prometheus, f"R04_PROMETHEUS_WIRING_MISSING_{token}")
for token in ("HhyR04BackendDown", "HhyR04HighServerErrorRate", "HhyR04HighP95Latency",
              "HhyR04MediaUploadFailureBurst", "HhyR04ExpiredUploadBacklog", "HhyR04MediaDeleteBacklog",
              "HhyR04StorageMigrationBlocked", "HhyR04BusinessMetricQueryFailure", "release: R04"):
    require(token in alerts, f"R04_ALERT_RULE_MISSING_{token}")

metric_names = ("hhy.media.upload.failures.5m", "hhy.media.upload.expired.open",
                "hhy.media.delete.pending", "hhy.storage.migration.blocked")
for metric in metric_names:
    require(metric in gauges, f"R04_BUSINESS_METRIC_MISSING_{metric}")
    require(metric in gauge_test, f"R04_BUSINESS_METRIC_TEST_MISSING_{metric}")
for token in ("hhy_media_upload_failures_5m", "hhy_media_upload_expired_open",
              "hhy_media_delete_pending", "hhy_storage_migration_blocked"):
    require(token in endpoint_test, f"R04_PROMETHEUS_ENDPOINT_TEST_MISSING_{token}")

require('.log("unhandled_api_exception")' in exception_handler, "R04_SAFE_EXCEPTION_EVENT_MISSING")
require("LOG.error(" not in exception_handler, "R04_EXCEPTION_THROWABLE_MUST_NOT_BE_LOGGED")
for token in ("must-not-appear", "getThrowableProxy()).isNull()"):
    require(token in exception_test, f"R04_EXCEPTION_REDACTION_TEST_MISSING_{token}")
require("providerFailurePausesWithoutAdvancingCursorAndCanResume" in migration_test, "R04_PROVIDER_FAILURE_RECOVERY_TEST_MISSING")
require("privateReadUrlIsShortLivedAndNeverUsesPublicBaseUrl" in provider_test, "R04_PRIVATE_URL_BOUNDARY_TEST_MISSING")

for token in ("R04 隔离预发布验收", "HhyR04MediaUploadFailureBurst", "200 张表", "签名 URL",
              "artifacts/validation/r04-task006-staging/"):
    require(token in deployment, f"R04_DEPLOYMENT_RUNBOOK_MISSING_{token}")
for token in ("R04 媒体与存储隔离回滚演练", "V022", "禁止 U022"):
    require(token in rollback, f"R04_ROLLBACK_RUNBOOK_MISSING_{token}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R04_OBSERVABILITY_CONFIG_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R04_OBSERVABILITY_CONFIG_OK")
