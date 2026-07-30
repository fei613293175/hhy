---
cr_id: CR-0025
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-git-safety-reviewer
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-17T23:31:46Z
updated_at: 2026-07-17T23:32:08Z
---
# CR-0025 — 绑定Git pre-push实际目标以消除跨remote绕过

## 用户需求摘要

项目所有者要求跨电脑和跨AI均可安全恢复并正常推送，且推送必须不可绕过transport规则。

## 原规则

pre-push Hook仅调用通用门禁，不传递Git提供的实际remote名称和URL，门禁只能读取tracked origin。

## 新规则

pre-push Hook必须把实际remote名称和URL传入continuity_gate；门禁将其与tracked descriptor精确比对，任何非origin或URL漂移均拒绝。

## 修改原因

CR-0024集成复核发现现有pre-push Hook未把Git传入的remote名称和URL交给门禁，直接push到非origin目标可能绕过受控descriptor校验。

## 影响摘要

消除直接git push到错误remote时绕过transport descriptor的路径。

## 影响文件

- `.githooks/pre-push`
- `scripts/continuity_gate.py`
- `tests/test_git_transport_recovery.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `git_transport.actual_push_target`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `pre-push hook actual remote and URL rejection`

## 版本

- `R02`

## 迁移与兼容策略

仅扩展Hook与门禁参数；正常origin推送保持兼容，错误remote/URL将被明确拒绝。

## 用户确认

项目所有者已要求跨电脑/跨AI能够正常且安全推送；该修复是CR-0024推送安全规则的必要实现。

## 审批

- 审批人：`codex-git-safety-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-17T23:32:08Z`
- 说明：独立安全复核通过：透传Git实际目标是实现不可绕过推送门禁所必需的最小变更。

## 状态记录 · 2026-07-17T23:32:10Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：开始实现pre-push实际remote/URL绑定。

## 状态记录 · 2026-07-18T00:01:49Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：pre-push Hook已透传Git实际remote/URL，错误目标和force路径不可绕过；本地bare回归通过。
