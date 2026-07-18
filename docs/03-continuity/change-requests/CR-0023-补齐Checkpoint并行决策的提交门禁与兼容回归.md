---
cr_id: CR-0023
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-governance-reviewer
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-17T22:36:43Z
updated_at: 2026-07-17T22:37:05Z
---
# CR-0023 — 补齐Checkpoint并行决策的提交门禁与兼容回归

## 用户需求摘要

项目所有者要求默认多代理规则成为硬性、可跨AI恢复的仓库规则。

## 原规则

Checkpoint和提交门禁只验证摘要、下一步、测试与指纹，不验证任务开始或范围变化时是否评估并行机会。

## 新规则

Checkpoint必须携带结构化parallel_execution；DELEGATED记录1至3个代理及互斥路径，未委托必须使用受控原因并填写说明；提交和严格门禁拒绝缺失、超过3个代理或伪造/空原因。

## 修改原因

CR-0021引入结构化并行决策后，提交门禁与现有bootstrap/cross-release回归必须同步，否则规则不能在每次提交强制且测试会漂移。

## 影响摘要

让长期自动委托授权进入每个Checkpoint和提交门禁，并同步现有生命周期回归。

## 影响文件

- `scripts/continuity_gate.py`
- `tests/test_continuity_bootstrap_recovery.py`
- `tests/test_continuity_cross_release_close.py`
- `tests/test_parallel_checkpoint_policy.py`

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

- `checkpoint-parallel-policy-strict-gate`
- `bootstrap-parallel-decision-compatibility`
- `cross-release-parallel-decision-compatibility`

## 版本

- `R02`
- `R03`
- `R04`

## 迁移与兼容策略

现有历史Checkpoint保持可读；新建Checkpoint必须提供结构化并行决策，测试夹具显式记录NO_SAFE_PARALLEL原因。

## 用户确认

项目所有者要求长期多代理默认规则硬性记录并供后续AI与电脑默认执行。

## 审批

- 审批人：`codex-governance-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-17T22:37:05Z`
- 说明：独立审查要求Checkpoint结构化并行决策必须由提交门禁验证，并同步既有生命周期回归；影响限于门禁和测试。

## 状态记录 · 2026-07-17T22:37:07Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：实现Checkpoint并行决策与提交门禁。

## 状态记录 · 2026-07-17T23:07:19Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：Checkpoint结构化并行决策、提交门禁与兼容回归已完成。
