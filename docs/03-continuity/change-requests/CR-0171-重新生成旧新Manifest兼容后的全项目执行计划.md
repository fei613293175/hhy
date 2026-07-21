---
cr_id: CR-0171
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T06:58:34Z
updated_at: 2026-07-21T06:58:39Z
---
# CR-0171 — 重新生成旧新Manifest兼容后的全项目执行计划

## 用户需求摘要

项目所有者要求解决测试阻断并持续开发。

## 原规则

PROGRAM_EXECUTION_PLAN仍保存旧生成器把分层合同字典键数当接口数的派生统计。

## 新规则

使用修正后的生成器重新生成全项目计划，R03-R06按paths记录真实端点数量，其余事实保持确定性。

## 修改原因

CR-0170修正端点统计语义后，PROGRAM_EXECUTION_PLAN必须由权威生成器确定性刷新。

## 影响摘要

仅更新生成派生计划的接口统计和组合总数，不改Release范围、依赖、风险、任务或业务合同。

## 影响文件

- `releases/PROGRAM_EXECUTION_PLAN.yaml`

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

- `python scripts/generate_program_execution_plan.py --check`
- `python -m unittest tests.test_program_execution_plan`

## 版本

- `R06`

## 迁移与兼容策略

确定性派生刷新；可用generate_program_execution_plan.py --check复现。

## 用户确认

项目所有者要求解决当前测试阻断并持续开发。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T06:58:39Z`
- 说明：输出由受测生成器机械产生，仅校正统计语义，不改变计划治理。
