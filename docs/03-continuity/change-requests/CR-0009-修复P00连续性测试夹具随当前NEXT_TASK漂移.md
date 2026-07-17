---
cr_id: CR-0009
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-P00-005
session_id: SES-20260717T024407Z-B03C9375
created_at: 2026-07-17T04:58:46Z
updated_at: 2026-07-17T05:00:33Z
---
# CR-0009 — 修复P00连续性测试夹具随当前NEXT_TASK漂移

## 用户需求摘要

完成P00版本后暂停推进

## 原规则

连续性隔离测试复制当前仓库状态后只清空Session和CURRENT_STATUS活动字段，隐含假设NEXT_TASK仍为TASK-P00-001

## 新规则

隔离夹具必须显式恢复P00初始场景：NEXT_TASK指向TASK-P00-001、TASK-P00-001为READY、后续任务为BLOCKED，并清除当前P00完成记录；测试不受真实仓库推进阶段影响

## 修改原因

59项矩阵在TASK-P00-005阶段复验时，两个隔离连续性测试仍假定NEXT_TASK为TASK-P00-001，导致54/59而非预期56/59

## 影响摘要

仅修复连续性回归测试夹具的确定性；不改变生产连续性协议、业务代码、API、数据库、Android产物或版本范围

## 影响文件

- `tests/test_continuity_bootstrap_recovery.py`
- `scripts/test_continuity_protocol.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

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

- `python tests/test_continuity_bootstrap_recovery.py`
- `python scripts/test_continuity_protocol.py`
- `python scripts/run_p00_test_matrix.py --check`

## 版本

- `P00`

## 迁移与兼容策略

测试夹具在临时复制仓库内重置状态；真实仓库机器状态不迁移也不修改，兼容任意当前NEXT_TASK阶段

## 用户确认

用户已要求完成P00并在完成后暂停；修复阻断P00 59项矩阵的测试夹具漂移属于必要收口

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T05:00:33Z`
- 说明：复现证据确认两项连续性失败均由隔离副本继承真实NEXT_TASK=TASK-P00-005所致；批准仅在临时fixture恢复P00初始任务状态并登记问题，禁止修改真实仓库状态或生产连续性协议

## 状态记录 · 2026-07-17T05:02:36Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：按批准边界仅修复两个隔离测试夹具并登记PROB-0006；真实仓库状态不变

## 状态记录 · 2026-07-17T05:08:51Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：两个隔离夹具已恢复P00初始任务状态，bootstrap 2项和生命周期14检查实测通过

## 状态记录 · 2026-07-17T05:08:52Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：问题PROB-0006与回归测试均归档，真实仓库状态和生产协议未改
