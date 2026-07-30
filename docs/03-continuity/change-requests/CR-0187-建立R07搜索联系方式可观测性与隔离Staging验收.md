---
cr_id: CR-0187
status: APPROVED
requester_actor_id: codex-root-r07-006
approver_actor_id: codex-reviewer-r07-observability
task_id: TASK-R07-006
session_id: SES-20260721T201747Z-CD80A1EE
created_at: 2026-07-21T20:19:52Z
updated_at: 2026-07-21T20:20:22Z
---
# CR-0187 — 建立R07搜索联系方式可观测性与隔离Staging验收

## 用户需求摘要

项目所有者要求持续推进开发，由AI判断自动测试结果；完整模拟器与候选APK仅在大版本最终候选阶段运行。

## 原规则

R07仅继承通用RED与R06以前业务Gauge，尚无搜索、发布者、联系方式专属指标、告警、隔离Staging编排和可重复回滚验收。

## 新规则

R07必须提供搜索历史量、有效热词量、活跃发布者量、近5分钟联系方式访问/拒绝量和R07 Outbox积压六项无敏感字段业务Gauge；隔离Staging必须在精确冻结Commit上验证TraceId与脱敏、RED、六项Gauge、后端与R07 Outbox告警firing/resolved及同一PostgreSQL容器和卷的应用镜像回切。完整模拟器和候选APK仍只在TASK-R07-007最终候选阶段执行。

## 修改原因

TASK-R07-006要求验证结构化日志、TraceId、RED、R07业务指标、告警和同库卷回滚；当前仅有R06以前的专属监控栈，R07搜索、发布者和联系方式能力尚无业务Gauge、告警和隔离Staging自动验收入口。

## 影响摘要

新增R07只读业务Gauge和测试、静态门禁、独立Compose/Prometheus/Alertmanager/Nginx配置、自动现场验收脚本、部署回滚手册、机器证据与AC-R07-004状态；不修改公开API、数据库结构、生产路由、真实供应商或现有R01至R06环境。

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinder.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/BusinessGaugeBinderTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/observability/ObservabilityEndpointsTest.java`
- `scripts/check_r07_observability.py`
- `scripts/run_r07_staging_acceptance.sh`
- `infra/staging/r07-smoke/docker-compose.yml`
- `infra/staging/r07-smoke/prometheus.yml`
- `infra/staging/r07-smoke/alertmanager.yml`
- `infra/staging/r07-smoke/nginx.conf`
- `infra/staging/r07-smoke/r07-alerts.yml`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `docs/07-operations/ROLLBACK_RUNBOOK.md`
- `artifacts/reports/R07/TASK-R07-006-staging.md`
- `artifacts/validation/r07-task006-staging/**`
- `releases/R07/ACCEPTANCE_MATRIX.csv`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `GET /api/v1/search`
- `GET /api/v1/search/hot`
- `GET /api/v1/search/history`
- `DELETE /api/v1/search/history`
- `GET /api/v1/publishers/{id}`
- `POST /api/v1/contents/{id}/contacts/{channel}/access`

## 数据库与迁移

- `只读search_histories、hot_search_terms、users、content_posts、content_contact_access_logs和outbox_events；隔离验收仅插入并合法终结带冻结Commit唯一前缀的Outbox测试事件`

## 配置

- `R07隔离Staging、Prometheus采集、Alertmanager审计回执、搜索与联系方式业务告警`

## 资金/账本与历史数据

- `不读写账本或资金数据`

## 测试

- `R07静态可观测性门禁、BusinessGauge单元测试与Prometheus端点测试`
- `Compose/promtool/amtool配置验证、隔离Staging TraceId与日志脱敏、告警firing/resolved、同库卷回滚演练`

## 版本

- `R07`

## 迁移与兼容策略

纯增量可观测性与隔离Staging配置；Gauge只读现有表，关闭R07 Compose即可回退；数据库保持前向迁移，禁止降级DDL或删除卷。测试Outbox事件按PENDING到PUBLISHING到PUBLISHED合法状态终结并保留审计。

## 用户确认

项目所有者明确要求持续开发、AI自行判断测试，并把高效可复用方案固化到仓库。

## 审批

- 审批人：`codex-reviewer-r07-observability`
- 决定：`APPROVED`
- 时间：`2026-07-21T20:20:22Z`
- 说明：冻结验收与影响范围一致；仅增加无敏感字段只读指标、隔离Staging和可审计测试事件，不触发模拟器、候选APK或生产变更。

## 状态记录 · 2026-07-21T20:20:25Z

- Actor：`codex-root-r07-006`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T201747Z-CD80A1EE`
- Note：开始实现R07六项业务Gauge、独立监控栈和精确Commit自动验收入口。

## 状态记录 · 2026-07-21T20:20:50Z

- Actor：`codex-root-r07-006`
- Status：`SUPERSEDED`
- Session：`SES-20260721T201747Z-CD80A1EE`
- Note：范围工具只接受精确文件路径，原CR包含证据目录通配符，尚未应用任何文件范围；由CR-0188以逐文件证据清单等价替代。
