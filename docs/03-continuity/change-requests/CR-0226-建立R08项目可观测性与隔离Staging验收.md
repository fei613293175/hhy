---
cr_id: CR-0226
status: APPROVED
requester_actor_id: codex-root-r08-006
approver_actor_id: codex-reviewer-r08-observability
task_id: TASK-R08-006
session_id: SES-20260722T060947Z-B45C6BC0
created_at: 2026-07-22T06:12:05Z
updated_at: 2026-07-22T06:12:53Z
---
# CR-0226 — 建立R08项目可观测性与隔离Staging验收

## 用户需求摘要

项目所有者要求持续推进并由AI自行完成测试判定；完整模拟器与候选APK仅在R08最终候选阶段执行。

## 原规则

R08继承通用RED、TraceId、脱敏日志和R07以前业务Gauge，但没有项目域专属指标、告警或可重复隔离Staging验收。

## 新规则

R08增加项目总量、在线量、待审核量、收藏量、近5分钟联系方式访问/拒绝量和R08 Outbox积压七项无敏感字段只读Gauge；精确冻结Commit必须在隔离Staging验证TraceId与脱敏、RED、七项Gauge、后端与R08 Outbox告警firing/resolved及同一PostgreSQL容器和卷的应用镜像回切。完整模拟器和候选APK继续仅由TASK-R08-007触发。

## 修改原因

R08项目业务已实现并通过专项测试，但现有指标与Staging现场验收只覆盖到R07，缺少项目专属Gauge、告警、Trace脱敏、告警恢复和同库同卷回切证据。

## 影响摘要

复用R07已验证骨架，以逐文件范围新增R08项目只读Gauge、单测、静态门禁、独立监控栈、自动现场验收、运行手册、机器证据和AC状态；不改业务API或数据库Schema。

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `scripts/check_r08_observability.py`
- `scripts/run_r08_staging_acceptance.sh`
- `infra/staging/r08-smoke/docker-compose.yml`
- `infra/staging/r08-smoke/prometheus.yml`
- `infra/staging/r08-smoke/alertmanager.yml`
- `infra/staging/r08-smoke/nginx.conf`
- `infra/staging/r08-smoke/r08-alerts.yml`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R08/TASK-R08-006-staging.md`
- `releases/R08/ACCEPTANCE_MATRIX.csv`
- `CHANGELOG.md`
- `artifacts/validation/r08-task006-staging/metadata.txt`
- `artifacts/validation/r08-task006-staging/compose-ps.txt`
- `artifacts/validation/r08-task006-staging/liveness.json`
- `artifacts/validation/r08-task006-staging/readiness.json`
- `artifacts/validation/r08-task006-staging/database.txt`
- `artifacts/validation/r08-task006-staging/trace-headers.txt`
- `artifacts/validation/r08-task006-staging/public-status.json`
- `artifacts/validation/r08-task006-staging/http-request-completed.jsonl`
- `artifacts/validation/r08-task006-staging/log-redaction.txt`
- `artifacts/validation/r08-task006-staging/metrics.txt`
- `artifacts/validation/r08-task006-staging/metrics-summary.txt`
- `artifacts/validation/r08-task006-staging/prometheus-targets.json`
- `artifacts/validation/r08-task006-staging/prometheus-rules.json`
- `artifacts/validation/r08-task006-staging/source-sha256.txt`
- `artifacts/validation/r08-task006-staging/backend-down-firing.json`
- `artifacts/validation/r08-task006-staging/r08-outbox-firing.json`
- `artifacts/validation/r08-task006-staging/outbox-test-events.txt`
- `artifacts/validation/r08-task006-staging/alert-deliveries.jsonl`
- `artifacts/validation/r08-task006-staging/alert-exercise.txt`
- `artifacts/validation/r08-task006-staging/rollback-rehearsal.txt`
- `artifacts/validation/r08-task006-staging/final-metrics.txt`
- `artifacts/validation/r08-task006-staging/machine-summary.txt`
- `artifacts/validation/r08-task006-staging/SHA256SUMS`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `只读content_posts、content_favorites、content_contact_access_logs和outbox_events；隔离验收仅插入并合法终结带冻结Commit唯一前缀的R08 Outbox测试事件`

## 配置

- `R08隔离Staging、Prometheus采集、Alertmanager审计回执、项目域业务告警`

## 资金/账本与历史数据

- `不读写账本或资金数据`

## 测试

- `R08静态可观测性门禁、BusinessGauge单元测试与Prometheus端点测试`
- `Compose/promtool配置验证、隔离Staging TraceId与日志脱敏、告警firing/resolved、同库同卷回滚演练`

## 版本

- `R08`

## 迁移与兼容策略

纯增量可观测性与隔离Staging配置；Gauge只读现有表，关闭R08 Compose即可回退；数据库保持前向V034，禁止降级DDL或删除卷。测试Outbox事件使用r08-api来源事件类型并按PENDING到PUBLISHING到PUBLISHED合法终结。

## 用户确认

项目所有者已长期授权持续推进、由AI判断测试结果，并要求大版本最终候选才执行模拟器与APK。

## 审批

- 审批人：`codex-reviewer-r08-observability`
- 决定：`APPROVED`
- 时间：`2026-07-22T06:12:53Z`
- 说明：范围逐文件明确；七项Gauge只读非敏感列，Staging与生产隔离，回滚保持同库同卷且禁止降级DDL，候选APK仍由TASK-R08-007独立触发。

## 状态记录 · 2026-07-22T06:12:58Z

- Actor：`codex-root-r08-006`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T060947Z-B45C6BC0`
- Note：批准范围已应用，开始实现R08七项项目业务Gauge与隔离Staging验收。

## 状态记录 · 2026-07-22T06:37:05Z

- Actor：`codex-root-r08-006`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T060947Z-B45C6BC0`
- Note：七项项目Gauge、独立监控栈及精确Commit隔离Staging全部通过；Trace脱敏、RED、两项告警firing/resolved、Outbox合法终结和同库同卷回切证据已归档。

## 状态记录 · 2026-07-22T06:38:35Z

- Actor：`codex-root-r08-006`
- Status：`CLOSED`
- Session：`SES-20260722T060947Z-B45C6BC0`
- Note：实现提交与现场证据提交均已推送；AC-R08-004 PASS，CR范围内无遗留实现或验收项。
