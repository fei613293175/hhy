---
cr_id: CR-0397
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: project-owner-standing-authorization
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T09:15:04Z
updated_at: 2026-07-27T09:15:48Z
---
# CR-0397 — 同步CR-0396共享Android候选门禁断言

## 用户需求摘要

项目所有者要求终止原地打转并持续推进R13

## 原规则

共享Android候选回归直接断言ReleaseCandidateSmokeTest仍调用waitForScreen(hhy.screen.r06.home.loaded)，与已批准CR-0396冲突

## 新规则

共享Android候选回归只锁定CR-0396唯一入口helper、home.loaded与home.error两个可操作主框架状态，并明确拒绝旧home.loaded单态调用；不重复定义业务规则

## 修改原因

CR-0396实现后共享tests.test_android_ci_gate仍硬编码旧home.loaded单态字符串，导致受影响治理回归确定性失败；该CR只同步既有CR-0396，不建立第二套入口规则

## 影响摘要

仅同步一处共享Python静态回归到CR-0396；不改候选实现、生产代码、配置、接口、数据或视觉标准

## 影响文件

- `tests/test_android_ci_gate.py`

## 页面

- `无页面实现变化`

## API

- `无影响`

## 数据库与迁移

- `无变化`

## 配置

- `无变化`

## 资金/账本与历史数据

- `CR-0397仅作为CR-0396遗漏测试文件的补充审计`

## 测试

- `tests.test_android_ci_gate`

## 版本

- `R13`

## 迁移与兼容策略

纯测试合同同步；CR-0396继续是入口行为唯一事实源

## 用户确认

项目所有者已授权自主持续开发并要求避免重复规则与原地循环

## 审批

- 审批人：`project-owner-standing-authorization`
- 决定：`APPROVED`
- 时间：`2026-07-27T09:15:48Z`
- 说明：该CR不建立新行为规则，只把共享门禁同步到CR-0396唯一事实，防止旧断言继续制造假失败

## 状态记录 · 2026-07-27T09:23:39Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：同步共享Android候选门禁断言到CR-0396唯一规则

## 状态记录 · 2026-07-27T09:35:33Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：共享Android候选断言已同步CR-0396唯一入口规则并通过回归
