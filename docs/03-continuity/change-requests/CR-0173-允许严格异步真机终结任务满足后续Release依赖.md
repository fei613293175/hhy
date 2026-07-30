---
cr_id: CR-0173
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T08:05:06Z
updated_at: 2026-07-21T08:05:44Z
---
# CR-0173 — 允许严格异步真机终结任务满足后续Release依赖

## 用户需求摘要

项目所有者要求真机反馈异步且不得阻断R07后续开发

## 原规则

目标Release的所有声明依赖只能在其全部任务DONE时视为GREEN，即使当前依赖Release唯一未完成项正是已通过严格Manifest验证的异步真机终结任务也拒绝。

## 新规则

仅当依赖Release等于当前Release、唯一被豁免的未完成任务等于当前关闭任务且该Release同时满足严格异步owner Manifest事实时，依赖校验可忽略该任务；其他Release、其他任务或任何Manifest缺项继续拒绝。

## 修改原因

独立Release校验已验证机器与owner门禁事实，但依赖GREEN循环仍无条件拒绝同一当前终结任务，导致合法转换无法落地

## 影响摘要

补齐CR-0172依赖侧状态判断，使R06-008可原子进入BLOCKED_EXTERNAL_GATE并切换R07，同时新增允许与拒绝回归。

## 影响文件

- `scripts/continuity.py`
- `tests/test_continuity_cross_release_close.py`
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

- `python -m unittest tests.test_continuity_cross_release_close`

## 版本

- `R06`
- `R07`

## 迁移与兼容策略

既有全部DONE依赖路径不变；豁免仅限当前Release当前终结任务且严格Manifest组合已PASS，不改变正式验收和生产激活阻断。

## 用户确认

项目所有者明确要求真机反馈异步输入并持续进入后续版本开发

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T08:05:44Z`
- 说明：豁免条件同时绑定当前依赖Release、当前终结任务及严格Manifest事实，其他未完成依赖继续拒绝，未削弱owner和生产门禁。

## 状态记录 · 2026-07-21T08:11:53Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：依赖侧修复已完成回归并进入远端提交绑定阶段

## 状态记录 · 2026-07-21T08:11:56Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：严格当前终结任务依赖豁免已由四项跨版本回归和远端提交证明

## 状态记录 · 2026-07-21T08:11:58Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：实现、正反向回归、提交及推送完成，可重新执行R06到R07原子转换
