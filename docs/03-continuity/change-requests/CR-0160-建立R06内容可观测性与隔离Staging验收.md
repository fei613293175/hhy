---
cr_id: CR-0160
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R06-006
session_id: SES-20260721T020225Z-397AF410
created_at: 2026-07-21T02:12:12Z
updated_at: 2026-07-21T02:13:05Z
---
# CR-0160 — 建立R06内容可观测性与隔离Staging验收

## 用户需求摘要

项目所有者要求立即按既定方案持续推进，真机反馈异步且不得成为版本开发等待点

## 原规则

R06仅继承通用RED与R01至R05业务Gauge，尚无内容域专属指标、告警、隔离Staging编排和回滚验收规范

## 新规则

R06必须提供内容在线量、待审核量、内容Outbox积压和已启用首页模块四项无敏感字段业务Gauge；独立Staging必须验证TraceId、RED、业务Gauge、后端与内容Outbox告警firing/resolved及同一PostgreSQL容器和卷的应用镜像回切

## 修改原因

TASK-R06-006要求验证结构化日志、TraceId、RED、R06业务指标、告警和同库卷回滚，现有仓库仅有R05以前的专属监控配置

## 影响摘要

新增R06业务Gauge及测试、静态门禁、独立Compose/Prometheus/Alertmanager/Nginx配置、部署回滚手册和机器验收证据；不修改公开API、数据库结构、生产路由、真实供应商或现有R01至R05环境

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `scripts/check_r06_observability.py`
- `infra/staging/r06-smoke/docker-compose.yml`
- `infra/staging/r06-smoke/prometheus.yml`
- `infra/staging/r06-smoke/alertmanager.yml`
- `infra/staging/r06-smoke/nginx.conf`
- `infra/staging/r06-smoke/r06-alerts.yml`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R06/TASK-R06-006-staging.md`
- `artifacts/validation/r06-task006-staging/**`
- `releases/R06/ACCEPTANCE_MATRIX.csv`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `只读content_posts、home_modules和outbox_events；无迁移或数据模型变化`

## 配置

- `R06隔离Staging、Prometheus采集、Alertmanager回执与内容业务告警`

## 资金/账本与历史数据

- `不读写账本或资金数据`

## 测试

- `R06静态可观测性门禁、BusinessGauge单元测试、Prometheus端点测试、Compose/promtool/amtool校验、隔离Staging告警与同库卷回滚演练`

## 版本

- `R06`

## 迁移与兼容策略

纯增量可观测性与隔离Staging配置；Gauge仅只读现有表，关闭R06 Compose即可回退；数据库保持前向迁移，禁止降级DDL或删除卷

## 用户确认

2026-07-21项目所有者明确要求立即按既定计划持续推进，真机反馈异步且不得因未反馈停止版本开发

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-21T02:13:05Z`
- 说明：TASK-R06-006冻结验收要求与影响范围一致；仅新增隔离Staging和只读观测能力，不扩大生产、供应商或数据库变更权限

## 状态记录 · 2026-07-21T03:26:03Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T020225Z-397AF410`
- Note：应用已批准的R06内容可观测性与隔离Staging范围；本地和Java21定向门禁已通过，准备形成冻结Commit

## 状态记录 · 2026-07-21T04:18:55Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T020225Z-397AF410`
- Note：R06四项业务Gauge、隔离监控链、静态门禁、运行手册和精确Commit机器证据已实现，AC-R06-004为PASS

## 状态记录 · 2026-07-21T04:19:17Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T020225Z-397AF410`
- Note：实现、Staging验收、AC-R06-004、报告与机器证据全部完成并推送
