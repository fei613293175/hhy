---
cr_id: CR-0373
status: APPROVED
requester_actor_id: codex-root-r13-staging-20260727
approver_actor_id: owner-standing-delegation-20260727
task_id: TASK-R13-006
session_id: SES-20260726T182231Z-EE79FA49
created_at: 2026-07-26T18:24:09Z
updated_at: 2026-07-26T18:24:52Z
---
# CR-0373 — 建立R13活动业务可观测性与隔离Staging回滚验收

## 用户需求摘要

项目所有者要求按仓库计划持续开发，普通版本不打断，模拟器与APK仅在最终候选执行。

## 原规则

R13复用通用HTTP RED、TraceId与R12发布指标，但没有R13活动域独立Gauge、告警、隔离Staging和V042回切证据。

## 新规则

R13增加收藏总量、历史行数、近5分钟分享、联系方式访问/拒绝、待处理失效反馈和活动Outbox积压七项低基数只读Gauge；建立七条告警、精确Commit隔离Staging、结构化日志脱敏、告警送达、Outbox合法终结与只回切应用镜像的V042演练。

## 修改原因

当前BusinessGaugeBinder、Prometheus、告警、Staging脚本和运行手册只覆盖到R12，R13收藏、历史、分享、联系方式行为与失效反馈缺少独立业务指标、告警、Trace/脱敏现场证据和V042应用回切验收。

## 影响摘要

新增R13观测指标、静态与模块测试、隔离Compose/Prometheus/Alertmanager、单一现场脚本、运行及回滚手册、证据报告和Release投影；不改变API、数据库Schema、页面、模拟器或APK。

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `infra/staging/r13-smoke/docker-compose.yml`
- `infra/staging/r13-smoke/prometheus.yml`
- `infra/staging/r13-smoke/alertmanager.yml`
- `infra/staging/r13-smoke/nginx.conf`
- `infra/staging/r13-smoke/r13-alerts.yml`
- `scripts/check_r13_observability.py`
- `scripts/run_r13_staging_acceptance.sh`
- `tests/test_r13_staging_acceptance.py`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R13/TASK-R13-006-staging.md`
- `artifacts/validation/r13-task006-staging/`
- `releases/R13/RELEASE_MANIFEST.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `BusinessGaugeBinderTest;ObservabilityEndpointsTest;check_r13_observability.py;test_r13_staging_acceptance.py;obx-test run_r13_staging_acceptance.sh`

## 版本

- `R13`

## 迁移与兼容策略

无新增数据库迁移；所有Gauge只读V042既有表。应用回切基线固定为包含V042的R13功能Commit c9741759，保持同一PostgreSQL容器和卷，禁止U042、降版本DDL或删除业务/审计/Outbox事实。

## 用户确认

项目所有者原文：批准，以后不要让我批准了 你自己持续开发就行了。

## 审批

- 审批人：`owner-standing-delegation-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-26T18:24:52Z`
- 说明：Owner持续开发授权覆盖TASK-R13-006既定观测与隔离Staging范围；不得扩大到生产切流、真实秘密、模拟器或APK。

## 状态记录 · 2026-07-26T18:26:06Z

- Actor：`codex-root-r13-staging-20260727`
- Status：`SUPERSEDED`
- Session：`SES-20260726T182231Z-EE79FA49`
- Note：范围应用前发现证据目录不是精确文件路径，CR未应用、未实施；由逐文件登记的后继CR替代。
