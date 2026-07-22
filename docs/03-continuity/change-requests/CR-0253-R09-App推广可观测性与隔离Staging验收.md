---
cr_id: CR-0253
status: APPROVED
requester_actor_id: codex-root-r09-observability
approver_actor_id: codex-r09-observability-review
task_id: TASK-R09-006
session_id: SES-20260722T200509Z-8FC026EC
created_at: 2026-07-22T20:06:39Z
updated_at: 2026-07-22T20:07:11Z
---
# CR-0253 — R09 App推广可观测性与隔离Staging验收

## 用户需求摘要

持续完成R09；大版本最终候选前不运行GitHub模拟器，完成任务后继续后继任务。

## 原规则

R09沿用通用和R08项目指标，但没有APP专属指标、告警、隔离Staging环境或绑定V035的可复核演练证据，AC-R09-004仍为NOT_RUN。

## 新规则

在既有唯一观测与Staging框架内增加7项无敏感标签的APP业务Gauge、7条R09告警和隔离Compose；单一脚本必须绑定冻结Commit，验证RED、TraceId、日志脱敏、告警送达、Outbox合法终结和同库同卷镜像回切，并生成逐文件SHA256。

## 修改原因

R09尚无App专属业务指标、告警和可重复的隔离Staging运行手册证据。

## 影响摘要

增加R09 App观测SQL及测试，复用R08已验证配置形成R09隔离环境、静态门禁、现场演练、部署/回滚手册和AC-R09-004证据；不触发Android模拟器或候选APK。

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `infra/staging/r09-smoke/docker-compose.yml`
- `infra/staging/r09-smoke/prometheus.yml`
- `infra/staging/r09-smoke/r09-alerts.yml`
- `infra/staging/r09-smoke/alertmanager.yml`
- `infra/staging/r09-smoke/nginx.conf`
- `scripts/check_r09_observability.py`
- `scripts/run_r09_staging_acceptance.sh`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `releases/R09/ACCEPTANCE_MATRIX.csv`
- `artifacts/reports/R09/TASK-R09-006-staging.md`
- `artifacts/validation/r09-task006-staging/**`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `Flyway V035 forward-compatible rollback rehearsal`

## 配置

- `R09 isolated Compose, Prometheus and Alertmanager`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R09 observability static gate`
- `BusinessGaugeBinder and Prometheus endpoint tests`
- `R09 isolated staging acceptance and rollback rehearsal`

## 版本

- `R09`

## 迁移与兼容策略

数据库不新增迁移；R09环境完整迁移至V035，回切只允许V035兼容镜像并保持同一PostgreSQL容器和卷，禁止U035/U034、降版本DDL或删除业务、审计和Outbox事实。

## 用户确认

用户已授权持续推进并明确完整候选和模拟器只在大版本最终候选阶段执行。

## 审批

- 审批人：`codex-r09-observability-review`
- 决定：`APPROVED`
- 时间：`2026-07-22T20:07:11Z`
- 说明：复用唯一R08框架且精确迁移到APP、V035和真实content.app事件；秘密、端口、回切和不触发模拟器边界完整，允许实施。

## 状态记录 · 2026-07-22T20:07:16Z

- Actor：`codex-root-r09-observability`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：批准范围已应用，开始实现App指标、告警和隔离Staging演练。

## 状态记录 · 2026-07-22T20:08:15Z

- Actor：`codex-root-r09-observability`
- Status：`SUPERSEDED`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：精确范围应用前发现证据目录通配符不符合连续性协议；尚未修改产品文件，由逐文件范围CR取代。
