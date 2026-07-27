---
cr_id: CR-0418
status: APPROVED
requester_actor_id: codex-root-r14-20260728
approver_actor_id: codex-reviewer-r14-entry-20260728
task_id: TASK-R14-001
session_id: SES-20260727T181928Z-B51C7408
created_at: 2026-07-27T18:41:49Z
updated_at: 2026-07-27T18:42:33Z
---
# CR-0418 — 纠正视觉catalog-only误要求未实现代码

## 用户需求摘要

项目所有者要求门禁不能过度拖慢开发，同时UI必须在编码前具备精确视觉合同。

## 原规则

catalog-only声明仅验证视觉合同覆盖、精确来源和阻断原因，但实现路径存在性仍按关闭门禁执行。

## 新规则

catalog-only仍要求实现路径字段非空，但在页面尚未编码时不要求目标文件存在；完整release和historical-through继续硬要求实现文件、参考证据、截图、PASS和肉眼质量标记。

## 修改原因

check_ui_visual_acceptance.py的--catalog-only帮助语义是仅验证合同覆盖与来源，但实现仍无条件要求实现路径文件存在，导致编码前入口检查无法验证IN_REVIEW合同。

## 影响摘要

修复入口门禁阶段语义，不放宽页面实现或版本关闭视觉门禁。

## 影响文件

- `scripts/check_ui_visual_acceptance.py`
- `tests/test_ui_visual_acceptance.py`

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

- `test_ui_visual_acceptance catalog-only implementation deferral`

## 版本

- `R14`

## 迁移与兼容策略

命令行兼容；仅require_pass=false时跳过实现文件存在性，完整门禁行为不变。

## 用户确认

项目所有者已明确要求优化冗余门禁但不得影响最终开发效果，并长期授权持续推进。

## 审批

- 审批人：`codex-reviewer-r14-entry-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-27T18:42:33Z`
- 说明：独立复核确认catalog-only应在编码前验证合同而非实现存在性；完整release/historical门禁保持原严格行为，因此无验收弱化。

## 状态记录 · 2026-07-27T18:42:38Z

- Actor：`codex-root-r14-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T181928Z-B51C7408`
- Note：开始修正catalog-only实现路径阶段语义并补回归。

## 状态记录 · 2026-07-27T19:25:50Z

- Actor：`codex-root-r14-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T181928Z-B51C7408`
- Note：catalog-only编码前语义修正及完整门禁不变回归已在实现提交中完成
