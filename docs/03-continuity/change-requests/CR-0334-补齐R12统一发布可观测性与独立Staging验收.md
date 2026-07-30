---
cr_id: CR-0334
status: APPROVED
requester_actor_id: codex-root-r12-observability-20260726
approver_actor_id: codex-reviewer-r12-observability
task_id: TASK-R12-006
session_id: SES-20260725T180922Z-D7231210
created_at: 2026-07-25T18:11:02Z
updated_at: 2026-07-25T18:11:51Z
---
# CR-0334 — 补齐R12统一发布可观测性与独立Staging验收

## 用户需求摘要

按R01-R32既定计划连续开发，终端静默执行，Android构建固定使用obx-test

## 原规则

TASK-R12-006要求部署Staging并验证结构化日志、TraceId、RED指标、业务指标、告警和回滚

## 新规则

新增R12统一发布和审核七项低基数业务Gauge、七条专属告警、隔离Compose Staging、精确提交现场验收、同库同卷应用回切与发布审核事实快照

## 修改原因

R12尚缺统一发布和审核业务Gauge、专属告警、隔离Staging演练、回滚业务事实证据及验收矩阵签字

## 影响摘要

仅增量扩展R12可观测性和验收资源，复用既有成熟Staging框架，不修改R01-R11隔离资源、公开API或数据库结构

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `infra/staging/r12-smoke/docker-compose.yml`
- `infra/staging/r12-smoke/prometheus.yml`
- `infra/staging/r12-smoke/alertmanager.yml`
- `infra/staging/r12-smoke/nginx.conf`
- `infra/staging/r12-smoke/r12-alerts.yml`
- `scripts/check_r12_observability.py`
- `scripts/run_r12_staging_acceptance.sh`
- `tests/test_r12_staging_acceptance.py`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R12/TASK-R12-006-staging.md`
- `artifacts/validation/r12-task006-staging`
- `releases/R12/ACCEPTANCE_MATRIX.csv`
- `releases/R12/RELEASE_MANIFEST.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R12隔离端口38112/39614/39615与172.31.243.0/24网络`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `BusinessGaugeBinderTest;ObservabilityEndpointsTest;check_r12_observability.py;test_r12_staging_acceptance.py;run_r12_staging_acceptance.sh`

## 版本

- `R12`

## 迁移与兼容策略

数据库沿用V041；回切只切换应用镜像并保持PostgreSQL容器、卷和V041不变，禁止U041/U040/U039、降版本DDL和业务事实删除

## 用户确认

项目所有者已明确要求按既定计划连续开发并授权静默执行终端命令

## 审批

- 审批人：`codex-reviewer-r12-observability`
- 决定：`APPROVED`
- 时间：`2026-07-25T18:11:51Z`
- 说明：范围为纯增量R12低基数指标、隔离Staging和回滚证据；不得修改公开契约或数据库结构，现场演练必须绑定精确Commit并保持V041及业务事实

## 状态记录 · 2026-07-25T18:27:06Z

- Actor：`codex-root-r12-observability-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T180922Z-D7231210`
- Note：R12七项业务Gauge、告警、隔离Staging、回滚事实快照和静态回归已实现，等待精确Commit远端测试与现场演练

## 状态记录 · 2026-07-25T19:08:02Z

- Actor：`codex-root-r12-observability-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T180922Z-D7231210`
- Note：七项Gauge、七条告警、隔离Staging、精确Commit现场验收、告警和回滚证据全部实现

## 状态记录 · 2026-07-25T19:14:59Z

- Actor：`codex-root-r12-observability-20260726`
- Status：`CLOSED`
- Session：`SES-20260725T180922Z-D7231210`
- Note：R12现场验收、证据签字和Commit对象哈希均已推送并通过，变更请求关闭
