---
cr_id: CR-0026
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-continuity-test-reviewer
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-17T23:46:06Z
updated_at: 2026-07-17T23:46:09Z
---
# CR-0026 — 让连续性生命周期演练使用真实本地transport

## 用户需求摘要

项目所有者要求跨电脑Git恢复和安全推送可由项目测试持续验证。

## 原规则

生命周期演练在无remote/upstream仓库中直接运行pre-push。

## 新规则

生命周期演练在baseline前生成本地bare remote descriptor，baseline后建立origin/upstream，再验证pre-push；不访问GitHub。

## 修改原因

新push门禁正确拒绝了没有remote/upstream的旧生命周期夹具；夹具必须建立本地bare remote并验证合法transport路径。

## 影响摘要

让真实Git生命周期演练覆盖新transport门禁的合法推送路径。

## 影响文件

- `scripts/test_continuity_protocol.py`

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

- `lifecycle local bare transport pre-push`

## 版本

- `R02`

## 迁移与兼容策略

仅影响隔离测试夹具和验证证据，不改变生产Git远程或业务实现。

## 用户确认

项目所有者要求跨电脑和跨AI的Git推送能力成为可持续验证的硬规则。

## 审批

- 审批人：`codex-continuity-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-17T23:46:09Z`
- 说明：独立复核通过：本地bare remote可验证transport且不会写GitHub。

## 状态记录 · 2026-07-17T23:46:11Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：开始适配生命周期本地transport夹具。

## 状态记录 · 2026-07-18T00:01:52Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：生命周期夹具已使用本地bare transport和真实upstream；14项演练与Handoff重建通过。
