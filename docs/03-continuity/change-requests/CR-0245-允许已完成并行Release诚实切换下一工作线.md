---
cr_id: CR-0245
status: APPROVED
requester_actor_id: codex-root-r08-008
approver_actor_id: codex-reviewer-continuity-r08-close
task_id: TASK-R08-008
session_id: SES-20260722T093645Z-C7DB8EF0
created_at: 2026-07-22T15:13:01Z
updated_at: 2026-07-22T15:13:25Z
---
# CR-0245 — 允许已完成并行Release诚实切换下一工作线

## 用户需求摘要

先全面收尾R08，再补历史UI并持续开发；不得因工具限制把已完成R08标记为受阻。

## 原规则

现有跨Release关闭：COMPLETED只能交接给声明依赖当前Release的首任务；--allow-independent-release及所有者确认仅允许BLOCKED外部门禁旁路。

## 新规则

更新同一迁移规则：COMPLETED终态也可在显式--allow-independent-release和项目所有者确认下交接给DAG中依赖已全部GREEN的独立Release首任务；当前任务仍必须真正DONE，目标首任务仍必须READY，不修改产品依赖。

## 修改原因

R08与R09在冻结DAG中是共享R05-R07依赖的并行兄弟Release，现有close只允许BLOCKED任务旁路到独立Release，导致COMPLETED终态无法合法交接。

## 影响摘要

扩展现有独立Release校验器区分BLOCKED外部门禁和COMPLETED兄弟Release；补直接校验回归并用R08到R09实际关闭证明。

## 影响文件

- `scripts/continuity.py`
- `tests/test_continuity_cross_release_close.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `continuity cross-release completion transition`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `completed sibling release validation; blocked external-gate regression; actual R08 completed close to R09 first task`

## 版本

- `R08`
- `R09`

## 迁移与兼容策略

既有同Release、直接依赖Release和BLOCKED外部门禁路径保持不变；只有同时提供显式旁路标志和所有者确认时启用COMPLETED独立交接。

## 用户确认

OWNER_EXPLICIT_CONTINUE_AFTER_R08_AND_DO_GLOBAL_UI_FIRST

## 审批

- 审批人：`codex-reviewer-continuity-r08-close`
- 决定：`APPROVED`
- 时间：`2026-07-22T15:13:25Z`
- 说明：批准修复终态迁移缺口；不得改变R08完成事实、R09依赖或降低目标依赖GREEN校验。

## 状态记录 · 2026-07-22T15:14:49Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：开始扩展现有独立Release迁移校验并补完成态兄弟Release回归。

## 状态记录 · 2026-07-22T15:20:05Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：完成态独立Release校验与旧BLOCKED外部门禁回归均通过；不修改产品依赖，目标依赖GREEN校验保留。
