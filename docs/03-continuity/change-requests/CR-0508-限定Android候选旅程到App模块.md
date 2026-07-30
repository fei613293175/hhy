---
cr_id: CR-0508
status: APPROVED
requester_actor_id: codex-r14-attempt19-remediator
approver_actor_id: codex-r14-attempt19-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T02:51:03Z
updated_at: 2026-07-30T02:51:33Z
---
# CR-0508 — 限定Android候选旅程到App模块

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

候选脚本从Android根工程执行未限定模块的connectedDebugAndroidTest，同时传入只存在于app模块的ReleaseCandidateSmokeTest类过滤条件。

## 新规则

候选脚本必须只执行:app:connectedDebugAndroidTest；测试必须断言精确模块任务存在且禁止恢复未限定模块的connectedDebugAndroidTest，避免类过滤条件传播到Library模块。

## 修改原因

Attempt19的App级ReleaseCandidateSmokeTest完整通过并生成四图；根Gradle任务connectedDebugAndroidTest随后把App测试类过滤条件错误套到feature:chat，因测试类不在该模块而ClassNotFoundException。必须把候选任务限定为:app:connectedDebugAndroidTest并增加静态回归。

## 影响摘要

只收窄GitHub模拟器候选的Gradle任务范围到App模块；不修改App产品代码、功能验收、截图、API、数据库或其他模块自己的测试。

## 影响文件

- `scripts/run_android_emulator_gate.sh`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt19/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `GitHub Android候选模拟器执行器`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `App模块候选任务范围`

## 资金/账本与历史数据

- `PROB-0055`

## 测试

- `test_emulator_gate_script_is_single_process_and_preserves_evidence精确模块范围；35项治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；App候选旅程及JUnit根路径保持原样，feature模块测试仍由各自门禁运行，不再错误加载App候选类。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-attempt19-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T02:51:33Z`
- 说明：App级候选已功能通过；把类过滤条件限定到唯一拥有该测试类的App模块是最小修复，保留所有业务与视觉标准。

## 状态记录 · 2026-07-30T02:51:40Z

- Actor：`codex-r14-attempt19-remediator`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始把候选Gradle任务限定为:app:connectedDebugAndroidTest并锁定静态回归。
