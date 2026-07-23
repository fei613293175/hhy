---
cr_id: CR-0271
status: APPROVED
requester_actor_id: codex-root-r10-observability
approver_actor_id: codex-r10-observability-review
task_id: TASK-R10-006
session_id: SES-20260723T115555Z-5C81458B
created_at: 2026-07-23T12:03:09Z
updated_at: 2026-07-23T12:03:53Z
---
# CR-0271 — R10群聊推广可观测性与隔离Staging验收精确范围

## 用户需求摘要

持续完成R10；大版本最终候选前不运行GitHub模拟器，完成后继续后继任务

## 原规则

R10沿用通用和R09指标，但没有群聊专属指标、告警、隔离Staging环境或绑定V036的可复核演练证据，AC-R10-004仍为NOT_RUN

## 新规则

在既有唯一观测与Staging框架内增加7项无敏感标签的群聊业务Gauge、7条R10告警和隔离Compose；单一脚本绑定冻结Commit，验证RED、TraceId、日志脱敏、告警送达、Outbox合法终结和同库同卷镜像回切，并生成逐文件SHA256

## 修改原因

R10缺少群聊专属指标、告警、隔离Staging和绑定V036的可复核演练证据

## 影响摘要

增加R10群聊观测SQL及测试，复用R09已验证框架形成R10隔离环境、静态门禁、现场演练、部署回滚手册和AC-R10-004证据；不触发Android模拟器或候选APK

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `infra/staging/r10-smoke/docker-compose.yml`
- `infra/staging/r10-smoke/prometheus.yml`
- `infra/staging/r10-smoke/r10-alerts.yml`
- `infra/staging/r10-smoke/alertmanager.yml`
- `infra/staging/r10-smoke/nginx.conf`
- `scripts/check_r10_observability.py`
- `scripts/run_r10_staging_acceptance.sh`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `releases/R10/ACCEPTANCE_MATRIX.csv`
- `artifacts/reports/R10/TASK-R10-006-staging.md`
- `CHANGELOG.md`
- `artifacts/validation/r10-task006-staging/alert-deliveries.jsonl`
- `artifacts/validation/r10-task006-staging/alert-exercise.txt`
- `artifacts/validation/r10-task006-staging/backend-down-firing.json`
- `artifacts/validation/r10-task006-staging/compose-ps.txt`
- `artifacts/validation/r10-task006-staging/database.txt`
- `artifacts/validation/r10-task006-staging/final-metrics.txt`
- `artifacts/validation/r10-task006-staging/http-request-completed.jsonl`
- `artifacts/validation/r10-task006-staging/liveness.json`
- `artifacts/validation/r10-task006-staging/log-redaction.txt`
- `artifacts/validation/r10-task006-staging/machine-summary.txt`
- `artifacts/validation/r10-task006-staging/metadata.txt`
- `artifacts/validation/r10-task006-staging/metrics-summary.txt`
- `artifacts/validation/r10-task006-staging/metrics.txt`
- `artifacts/validation/r10-task006-staging/outbox-test-events.txt`
- `artifacts/validation/r10-task006-staging/prometheus-rules.json`
- `artifacts/validation/r10-task006-staging/prometheus-targets.json`
- `artifacts/validation/r10-task006-staging/public-status.json`
- `artifacts/validation/r10-task006-staging/r10-outbox-firing.json`
- `artifacts/validation/r10-task006-staging/readiness.json`
- `artifacts/validation/r10-task006-staging/rollback-rehearsal.txt`
- `artifacts/validation/r10-task006-staging/SHA256SUMS`
- `artifacts/validation/r10-task006-staging/source-sha256.txt`
- `artifacts/validation/r10-task006-staging/trace-headers.txt`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `Flyway V036 forward-compatible rollback rehearsal`

## 配置

- `R10 isolated Compose, Prometheus and Alertmanager`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R10 observability static gate`
- `BusinessGaugeBinder and Prometheus endpoint tests`
- `R10 isolated staging acceptance and rollback rehearsal`

## 版本

- `R10`

## 迁移与兼容策略

数据库不新增迁移；R10环境完整迁移至V036，回切只允许已验证R09不可变镜像并保持同一PostgreSQL容器和卷，禁止U036/U035、降版本DDL或删除业务、审计和Outbox事实

## 用户确认

项目所有者已授权持续推进R06至R32，且明确Android模拟器只在大版本最终候选阶段执行

## 审批

- 审批人：`codex-r10-observability-review`
- 决定：`APPROVED`
- 时间：`2026-07-23T12:03:53Z`
- 说明：逐文件精确范围完整；群聊指标无敏感标签，V036同库同卷回切、隔离端口子网、秘密注入和非候选边界符合既有R06至R09模式

## 状态记录 · 2026-07-23T12:27:35Z

- Actor：`codex-root-r10-observability`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T115555Z-5C81458B`
- Note：批准范围已应用并形成冻结实现提交

## 状态记录 · 2026-07-23T12:27:38Z

- Actor：`codex-root-r10-observability`
- Status：`IMPLEMENTED`
- Session：`SES-20260723T115555Z-5C81458B`
- Note：R10群聊指标、告警、隔离Staging、V036同库同卷回切和23文件证据全部实现并通过

## 状态记录 · 2026-07-23T12:27:42Z

- Actor：`codex-root-r10-observability`
- Status：`CLOSED`
- Session：`SES-20260723T115555Z-5C81458B`
- Note：AC-R10-004已PASS，报告、哈希复核与敏感扫描完整，关闭实现CR
