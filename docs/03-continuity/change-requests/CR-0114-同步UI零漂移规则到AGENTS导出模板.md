---
cr_id: CR-0114
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T00:52:09Z
updated_at: 2026-07-20T00:52:30Z
---
# CR-0114 — 同步UI零漂移规则到AGENTS导出模板

## 用户需求摘要

项目所有者要求跨电脑跨AI自动继承效果图与UI参数硬性规则。

## 原规则

templates/AGENTS.md仍为CR-0113实施前内容，与根AGENTS.md不一致。

## 新规则

templates/AGENTS.md必须与根AGENTS.md字节一致并包含相同UI视觉零漂移规则。

## 修改原因

项目Doctor要求根AGENTS.md与templates/AGENTS.md完全一致，CR-0113实施后模板必须同步，否则交接包会丢失新规则。

## 影响摘要

同步一份导出模板，保证新电脑、新AI和Handoff Bundle继承同一UI硬边界。

## 影响文件

- `templates/AGENTS.md`

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

- `scripts/project-doctor.py`

## 版本

- `R05`

## 迁移与兼容策略

纯治理模板同步，不改变运行时代码、API、数据库或用户功能。

## 用户确认

2026-07-20项目所有者明确要求即便换电脑换AI也必须自动遵守该硬规则。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T00:52:30Z`
- 说明：CR-0114仅同步CR-0113已批准规则到交接模板，是跨电脑跨AI生效的必要派生文件。

## 状态记录 · 2026-07-20T00:53:15Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：根AGENTS与导出模板已同步，SHA-256完全一致，待检查点刷新Context后运行Doctor。

## 状态记录 · 2026-07-20T00:57:52Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：根AGENTS与导出模板已同哈希提交，跨电脑跨AI恢复将读取相同UI硬边界。
