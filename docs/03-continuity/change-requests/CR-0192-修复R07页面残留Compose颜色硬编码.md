---
cr_id: CR-0192
status: APPROVED
requester_actor_id: codex-root-r07-007
approver_actor_id: codex-reviewer-ui-token
task_id: TASK-R07-007
session_id: SES-20260721T210252Z-D631F6E4
created_at: 2026-07-21T21:15:09Z
updated_at: 2026-07-21T21:15:29Z
---
# CR-0192 — 修复R07页面残留Compose颜色硬编码

## 用户需求摘要

项目所有者要求UI字号边距颜色等硬参数遵守开发文档，并在大版本候选由AI自行完成门禁。

## 原规则

R07发布者卡片和空状态直接使用Color.White，违反Compose页面必须通过HhyColors冻结令牌取色的硬规则。

## 新规则

R07全部页面表面色必须使用HhyColors.Surface等冻结设计令牌，禁止直接Color.White或其他原始Compose颜色常量。

## 修改原因

候选前置check_ui_tokens确定性发现R07DiscoveryScreens两处Color.White绕过冻结HhyColors令牌，会阻断完整候选门禁。

## 影响摘要

仅替换两处等值颜色引用并移除无用import，视觉值保持不变；登记问题与回归证据。

## 影响文件

- `apps/android/feature/discovery/src/main/java/cc/orbexa/hhy/discovery/R07DiscoveryScreens.kt`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- `SCR-PUBLISHER-001`
- `SCR-SEARCH-001`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android HhyColors冻结设计令牌`

## 资金/账本与历史数据

- `PROB-0070 R07页面残留Color.White硬编码`

## 测试

- `scripts/check_ui_tokens.py与Android受影响MODULE`

## 版本

- `R07`

## 迁移与兼容策略

纯Android源码等值替换，不涉及API、数据库、本地数据或运行时迁移。

## 用户确认

项目所有者已明确要求UI硬性参数严格按开发文档执行。

## 审批

- 审批人：`codex-reviewer-ui-token`
- 决定：`APPROVED`
- 时间：`2026-07-21T21:15:29Z`
- 说明：两处HhyColors.Surface为等值令牌替换，不改变冻结视觉，且恢复候选硬门禁合规。

## 状态记录 · 2026-07-21T21:15:34Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：开始替换两处原始Compose颜色并补Problem Registry。
