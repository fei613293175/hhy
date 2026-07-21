---
cr_id: CR-0170
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T06:55:18Z
updated_at: 2026-07-21T06:55:23Z
---
# CR-0170 — 修复商业UI生产源筛选与Manifest分层统计兼容

## 用户需求摘要

项目所有者要求解决测试阻断并持续开发。

## 原规则

商业UI扫描仅排除名为test的目录而未排除Android标准androidTest；计划统计只支持contracts端点列表。

## 新规则

商业UI扫描明确排除androidTest测试源但继续扫描main生产源；计划统计同时支持旧列表和新operation_ids+paths结构，并只按paths计算端点引用。

## 修改原因

完整Python回归发现商业UI门禁误扫描androidTest断言，执行计划生成器把operation_ids+paths字典长度误作接口数量。

## 影响摘要

修复两个测试/派生工具的误报，不改生产UI、业务契约、API、数据库或APK。

## 影响文件

- `scripts/check_commercial_ui_boundaries.py`
- `tests/test_commercial_ui_boundaries.py`
- `scripts/generate_program_execution_plan.py`
- `tests/test_program_execution_plan.py`

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

- `python -m unittest tests.test_commercial_ui_boundaries tests.test_program_execution_plan`

## 版本

- `R06`

## 迁移与兼容策略

旧Release列表结构保持相同统计；R06及后续分层合同结构按paths统计；androidTest仍由自身编译和仪器测试覆盖。

## 用户确认

项目所有者要求解决当前阻断后持续开发。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T06:55:23Z`
- 说明：修复将测试源与生产源边界明确化并保持旧新Manifest统计兼容，不削弱生产扫描。

## 状态记录 · 2026-07-21T07:14:01Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：已按批准范围实施并完成本地回归。

## 状态记录 · 2026-07-21T07:14:04Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：实现与证据已进入提交609c2ae6。

## 状态记录 · 2026-07-21T07:14:06Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：提交609c2ae6已推送，pre-push严格门禁通过。
