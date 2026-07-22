---
cr_id: CR-0255
status: APPROVED
requester_actor_id: codex-root-r09-observability
approver_actor_id: codex-r09-observability-review
task_id: TASK-R09-006
session_id: SES-20260722T200509Z-8FC026EC
created_at: 2026-07-22T20:22:57Z
updated_at: 2026-07-22T20:23:27Z
---
# CR-0255 — R09隔离Staging单一入口自启动修复

## 用户需求摘要

持续推进R09，版本关闭应高效可复用，发现问题后直接解决且不反复浪费时间。

## 原规则

R09运行手册要求run_r09_staging_acceptance.sh是单一入口，但脚本只查询既有容器；全新隔离项目无容器时refresh_containers无诊断退出，实际依赖未记录的人工Compose启动步骤。

## 新规则

沿用既有单一入口，不新增平行流程；入口先拒绝复用同名非空Compose项目，再自行构建并启动隔离栈、刷新容器标识并进入既有验收。静态门禁冻结自启动与空项目保护令牌，失败必须明确输出阶段。

## 修改原因

首次在全新obx-test工作区执行冻结Commit时，单一入口未构建或启动Compose便直接查询容器并静默退出；与运行手册声明的单一入口不一致。

## 影响摘要

修复R09脚本自包含性并登记PROB-0095；不改变API、页面、数据库迁移、候选APK或GitHub模拟器策略。

## 影响文件

- `scripts/run_r09_staging_acceptance.sh`
- `scripts/check_r09_observability.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R09 isolated Compose bootstrap`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R09 observability static gate verifies bootstrap contract`
- `obx-test clean project end-to-end staging acceptance`

## 版本

- `R09`

## 迁移与兼容策略

无数据库迁移；只在全新hhy-r09-staging项目启动，已有同名容器时拒绝执行以避免污染，回滚仍保持V035同库同卷且禁止U035/U034。

## 用户确认

用户已授权持续推进、发现问题直接修复，并要求优化版本关闭效率与经验复用。

## 审批

- 审批人：`codex-r09-observability-review`
- 决定：`APPROVED`
- 时间：`2026-07-22T20:23:27Z`
- 说明：范围只修复既有单一入口并增加确定性防回归，明确拒绝非空同名项目且不触及公网、数据库迁移或候选流程。

## 状态记录 · 2026-07-22T20:24:47Z

- Actor：`codex-root-r09-observability`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：开始修复单一入口的空项目保护、自构建自启动和确定性诊断，并登记PROB-0095。
