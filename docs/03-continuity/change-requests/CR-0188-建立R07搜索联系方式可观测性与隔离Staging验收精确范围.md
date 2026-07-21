---
cr_id: CR-0188
status: APPROVED
requester_actor_id: codex-root-r07-006
approver_actor_id: codex-reviewer-r07-observability
task_id: TASK-R07-006
session_id: SES-20260721T201747Z-CD80A1EE
created_at: 2026-07-21T20:20:52Z
updated_at: 2026-07-21T20:21:29Z
---
# CR-0188 — 建立R07搜索联系方式可观测性与隔离Staging验收精确范围

## 用户需求摘要

项目所有者要求持续推进开发，由AI判断自动测试结果；完整模拟器与候选APK仅在大版本最终候选阶段运行。

## 原规则

R07仅继承通用RED与R06以前业务Gauge，且CR-0187的证据通配符无法应用；尚无可执行的R07精确范围。

## 新规则

R07必须提供搜索历史量、有效热词量、活跃发布者量、近5分钟联系方式访问/拒绝量和R07 Outbox积压六项无敏感字段业务Gauge；隔离Staging必须在精确冻结Commit上验证TraceId与脱敏、RED、六项Gauge、后端与R07 Outbox告警firing/resolved及同一PostgreSQL容器和卷的应用镜像回切。完整模拟器和候选APK仍只在TASK-R07-007最终候选阶段执行。

## 修改原因

CR-0187的证据目录通配符不符合连续性工具精确路径约束且未应用任何文件范围；必须以逐文件证据清单重建等价批准范围后实施TASK-R07-006。

## 影响摘要

以逐文件清单新增R07只读业务Gauge和测试、静态门禁、独立监控栈、自动现场验收、运行手册、机器证据与AC-R07-004状态；等价替代未应用范围的CR-0187。

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
- `releases/R07/ACCEPTANCE_MATRIX.csv`
- `CHANGELOG.md`
- `artifacts/validation/r07-task006-staging/metadata.txt`
- `artifacts/validation/r07-task006-staging/compose-ps.txt`
- `artifacts/validation/r07-task006-staging/liveness.json`
- `artifacts/validation/r07-task006-staging/readiness.json`
- `artifacts/validation/r07-task006-staging/database.txt`
- `artifacts/validation/r07-task006-staging/trace-headers.txt`
- `artifacts/validation/r07-task006-staging/public-status.json`
- `artifacts/validation/r07-task006-staging/http-request-completed.jsonl`
- `artifacts/validation/r07-task006-staging/log-redaction.txt`
- `artifacts/validation/r07-task006-staging/metrics.txt`
- `artifacts/validation/r07-task006-staging/metrics-summary.txt`
- `artifacts/validation/r07-task006-staging/prometheus-targets.json`
- `artifacts/validation/r07-task006-staging/prometheus-rules.json`
- `artifacts/validation/r07-task006-staging/source-sha256.txt`
- `artifacts/validation/r07-task006-staging/backend-down-firing.json`
- `artifacts/validation/r07-task006-staging/r07-outbox-firing.json`
- `artifacts/validation/r07-task006-staging/outbox-test-events.txt`
- `artifacts/validation/r07-task006-staging/alert-deliveries.jsonl`
- `artifacts/validation/r07-task006-staging/alert-exercise.txt`
- `artifacts/validation/r07-task006-staging/rollback-rehearsal.txt`
- `artifacts/validation/r07-task006-staging/final-metrics.txt`
- `artifacts/validation/r07-task006-staging/machine-summary.txt`
- `artifacts/validation/r07-task006-staging/SHA256SUMS`

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
- 时间：`2026-07-21T20:21:29Z`
- 说明：逐文件范围消除通配符歧义；能力、数据与安全边界与已批准CR-0187完全一致。

## 状态记录 · 2026-07-21T20:21:33Z

- Actor：`codex-root-r07-006`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T201747Z-CD80A1EE`
- Note：精确文件范围已应用，开始实现R07六项业务Gauge与隔离Staging验收。
