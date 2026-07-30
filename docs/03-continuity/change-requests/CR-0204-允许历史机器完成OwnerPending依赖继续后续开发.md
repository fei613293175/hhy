---
cr_id: CR-0204
status: APPROVED
requester_actor_id: codex-root-r07-008
approver_actor_id: codex-reviewer-continuity-async-dag
task_id: TASK-R07-008
session_id: SES-20260721T235847Z-F9109B61
created_at: 2026-07-22T00:05:07Z
updated_at: 2026-07-22T00:05:13Z
---
# CR-0204 — 允许历史机器完成OwnerPending依赖继续后续开发

## 用户需求摘要

项目所有者明确要求真机反馈异步且不得因未反馈停止后续版本开发。

## 原规则

跨Release校验只允许当前Release的异步Owner门禁未DONE，所有更早依赖Release即使机器完成Manifest明确允许后续开发也必须TASKS全DONE。

## 新规则

目标Release的每个历史依赖若Manifest完整满足机器交付PASS、Owner真机PENDING、生产阻断且next_release_development=ALLOWED，则仅允许其最后一个BLOCKED外部门禁任务作为开发依赖GREEN；任何更早未完成任务仍阻断。

## 修改原因

R07切换R08时校验器只豁免当前Release的异步关闭任务，未识别更早R06已登记MACHINE_COMPLETE_OWNER_PENDING且next_release_development=ALLOWED，产生确定性假阻断。

## 影响摘要

修复多代异步真机积压时的跨Release开发假阻断，新增正反向回归、Problem登记和踩坑规则；不放宽正式验收或生产激活。

## 影响文件

- `scripts/continuity.py`
- `tests/test_continuity_cross_release_close.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `跨Release异步Owner门禁依赖判定`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_continuity_cross_release_close及真实R07到R08关闭转换`

## 版本

- `R07`

## 迁移与兼容策略

纯连续性校验兼容修复；既有全DONE依赖行为不变，只有完整机器完成Manifest且仅最后外部门禁BLOCKED的历史Release可继续。

## 用户确认

项目所有者已明确真机反馈不强制逐版本提供且开发不得停下。

## 审批

- 审批人：`codex-reviewer-continuity-async-dag`
- 决定：`APPROVED`
- 时间：`2026-07-22T00:05:13Z`
- 说明：仅历史Release最后一个BLOCKED外部门禁可由完整机器完成Manifest豁免，其他未完成任务仍硬阻断。

## 状态记录 · 2026-07-22T00:05:16Z

- Actor：`codex-root-r07-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T235847Z-F9109B61`
- Note：开始修复历史OwnerPending依赖判定并增加回归与经验记录。

## 状态记录 · 2026-07-22T00:21:08Z

- Actor：`codex-root-r07-008`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T235847Z-F9109B61`
- Note：历史Owner Pending依赖判定修复、正反向回归与踩坑记录已完成并推送

## 状态记录 · 2026-07-22T00:21:11Z

- Actor：`codex-root-r07-008`
- Status：`CLOSED`
- Session：`SES-20260721T235847Z-F9109B61`
- Note：实现提交已推送，5项跨Release单测、11项重建及14项生命周期检查全部PASS
