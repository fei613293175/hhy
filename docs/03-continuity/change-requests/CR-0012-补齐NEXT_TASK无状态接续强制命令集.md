---
cr_id: CR-0012
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-R01-001
session_id: SES-20260717T074317Z-C575A687
created_at: 2026-07-17T07:47:24Z
updated_at: 2026-07-17T07:49:22Z
---
# CR-0012 — 补齐NEXT_TASK无状态接续强制命令集

## 用户需求摘要

开始R01版本开发并保证项目可持续无状态接续

## 原规则

resolve_next_task只生成start_command和next_after，NEXT_TASK不包含其余统一CLI恢复命令

## 新规则

resolve_next_task必须生成commands映射，包含resume、start、checkpoint、handoff、export_clean和cr_amend；当前NEXT_TASK同步回填，所有后续同版本和跨版本关闭自动继承

## 修改原因

P00跨版本关闭生成的NEXT_TASK仅含start_command，缺少strict gate要求的resume/checkpoint/handoff/export-clean/cr-amend命令，导致新AI无法从单文件获得完整操作入口

## 影响摘要

仅增强持续接续元数据生成和隔离回归断言；不改变业务代码、页面、API、数据库、配置或Android运行时

## 影响文件

- `scripts/continuity.py`
- `NEXT_TASK.yaml`
- `tests/test_continuity_cross_release_close.py`

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

- `python tests/test_continuity_cross_release_close.py`
- `python scripts/check_v123_continuity.py --strict`

## 版本

- `R01`

## 迁移与兼容策略

无需运行时迁移；旧NEXT_TASK同步回填，字段为向后兼容新增，现有start_command保留

## 用户确认

开始R01版本开发并保证项目可持续无状态接续

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T07:49:22Z`
- 说明：确认NEXT_TASK作为无状态接续入口必须暴露resume、start、checkpoint、handoff、export-clean与cr-amend统一CLI；批准仅增强resolve_next_task生成、回填当前NEXT_TASK并补充跨Release隔离回归断言，保留start_command且禁止业务运行时变更

## 状态记录 · 2026-07-17T07:52:35Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T074317Z-C575A687`
- Note：按独立批准合同边界实施

## 状态记录 · 2026-07-17T07:52:36Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T074317Z-C575A687`
- Note：合同范围内代码、元数据与回归测试均已完成
