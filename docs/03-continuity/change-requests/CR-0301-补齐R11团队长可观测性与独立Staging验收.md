---
cr_id: CR-0301
status: APPROVED
requester_actor_id: codex-root-r11-observability
approver_actor_id: codex-reviewer-r11-observability
task_id: TASK-R11-006
session_id: SES-20260724T021901Z-603D54F5
created_at: 2026-07-24T02:28:21Z
updated_at: 2026-07-24T02:29:19Z
---
# CR-0301 — 补齐R11团队长可观测性与独立Staging验收

## 用户需求摘要

继续开发并静默执行全部终端命令

## 原规则

R11-006要求部署Staging并验证日志、TraceId、RED指标、业务指标、告警和回滚

## 新规则

新增R11团队长七项业务Gauge、七条专属告警、隔离Compose Staging、静态检查、一次性现场验收脚本、现场报告与部署回滚手册

## 修改原因

R11尚缺团队长业务Gauge、专属告警、独立Staging演练和回滚证据

## 影响摘要

仅扩展R11可观测性和验收资源，复用R10成熟框架，不修改既有R01-R10 Staging资源

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `infra/staging/r11-smoke/docker-compose.yml`
- `infra/staging/r11-smoke/prometheus.yml`
- `infra/staging/r11-smoke/alertmanager.yml`
- `infra/staging/r11-smoke/nginx.conf`
- `infra/staging/r11-smoke/r11-alerts.yml`
- `scripts/check_r11_observability.py`
- `scripts/run_r11_staging_acceptance.sh`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R11/TASK-R11-006-staging.md`
- `releases/R11/ACCEPTANCE_MATRIX.csv`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R11隔离端口38111/39612/39613与172.31.241.0/24网络`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `BusinessGaugeBinderTest;ObservabilityEndpointsTest;check_r11_observability.py;run_r11_staging_acceptance.sh`

## 版本

- `R11`

## 迁移与兼容策略

纯增量指标与R11隔离基础设施；数据库沿用V038，回滚不执行降级迁移或删除卷，并比较团队长业务事实快照

## 用户确认

项目所有者已明确要求继续开发并授权自动执行

## 审批

- 审批人：`codex-reviewer-r11-observability`
- 决定：`APPROVED`
- 时间：`2026-07-24T02:29:19Z`
- 说明：逐文件范围明确，隔离资源不触碰R01-R10，现场演练须绑定精确提交并保留V038业务事实

## 状态记录 · 2026-07-24T03:19:01Z

- Actor：`codex-root-r11-observability`
- Status：`IMPLEMENTING`
- Session：`SES-20260724T021901Z-603D54F5`
- Note：按批准范围完成R11可观测性实现与现场证据归档

## 状态记录 · 2026-07-24T03:19:05Z

- Actor：`codex-root-r11-observability`
- Status：`IMPLEMENTED`
- Session：`SES-20260724T021901Z-603D54F5`
- Note：团队长七项Gauge、专属告警、隔离Staging与回滚业务快照已实现，精确Commit现场演练PASS

## 状态记录 · 2026-07-24T03:19:09Z

- Actor：`codex-reviewer-r11-observability`
- Status：`CLOSED`
- Session：`SES-20260724T021901Z-603D54F5`
- Note：25项现场证据、AC-R11-004精确报告、SHA256与PROB-0104回归均已复核
