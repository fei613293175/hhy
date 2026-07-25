---
cr_id: CR-0335
status: APPROVED
requester_actor_id: codex-root-r12-observability-20260726
approver_actor_id: codex-reviewer-r12-network
task_id: TASK-R12-006
session_id: SES-20260725T180922Z-D7231210
created_at: 2026-07-25T18:37:59Z
updated_at: 2026-07-25T18:38:36Z
---
# CR-0335 — 修正R12隔离Staging子网与R09冲突

## 用户需求摘要

按既定计划连续开发且不浪费时间反复重跑

## 原规则

CR-0334初始登记R12隔离网络为172.31.243.0/24

## 新规则

R12隔离网络改为服务器审计确认未占用的172.31.240.0/24，端口38112/39614/39615保持不变

## 修改原因

obx-test只读网络审计确认172.31.243.0/24仍由hhy-r09-staging_smoke占用，R12必须使用独立未占用子网

## 影响摘要

仅修正R12隔离Compose、运行脚本、静态门禁、回归测试和运行手册中的默认子网，不触碰R09网络或任何业务事实

## 影响文件

- `infra/staging/r12-smoke/docker-compose.yml`
- `scripts/check_r12_observability.py`
- `scripts/run_r12_staging_acceptance.sh`
- `tests/test_r12_staging_acceptance.py`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `artifacts/reports/R12/TASK-R12-006-staging.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R12隔离子网172.31.240.0/24`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `check_r12_observability.py;test_r12_staging_acceptance.py;obx-test docker network audit`

## 版本

- `R12`

## 迁移与兼容策略

纯Staging隔离网络修正，无数据库、API、业务兼容性变化；旧R09网络保持原样

## 用户确认

项目所有者要求连续推进并避免无效重跑，已长期授权终端静默执行

## 审批

- 审批人：`codex-reviewer-r12-network`
- 决定：`APPROVED`
- 时间：`2026-07-25T18:38:36Z`
- 说明：服务器证据明确证明243网段被R09占用；240网段须在执行前再次审计未占用，修改仅限R12隔离资源
