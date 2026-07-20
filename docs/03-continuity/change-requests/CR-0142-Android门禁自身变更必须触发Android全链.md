---
cr_id: CR-0142
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T12:23:49Z
updated_at: 2026-07-20T12:23:53Z
---
# CR-0142 — Android门禁自身变更必须触发Android全链

## 用户需求摘要

自动测试失败必须自行分析、修改、重新打包和重新测试

## 原规则

android-module仅匹配apps/android、contracts和domain-types

## 新规则

Android自动化策略、工作流、门禁脚本及其测试发生变化时必须选择android任务

## 修改原因

第四轮影响分析仅选择tooling，跳过了刚修改的Android模拟器门禁，存在测试系统未自测缺口

## 影响摘要

保证每次修改Android测试系统都会实际运行编译与模拟器全链

## 影响文件

- `config/test-impact-map.yaml`
- `tests/test_run_affected_tests.py`

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

- `python -m unittest tests.test_run_affected_tests`

## 版本

- `R05`

## 迁移与兼容策略

仅扩展CI影响映射，普通非Android改动仍按受影响范围执行

## 用户确认

用户要求自动测试全部通过前不得完成版本

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T12:23:53Z`
- 说明：依据自动测试体系必须自验证的长期门禁要求

## 状态记录 · 2026-07-20T12:23:56Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：扩展Android影响映射并补回归
