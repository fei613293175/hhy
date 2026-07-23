---
cr_id: CR-0266
status: APPROVED
requester_actor_id: codex-root-r10-entry
approver_actor_id: codex-r10-test-review
task_id: TASK-R10-001
session_id: SES-20260723T025104Z-E29F6208
created_at: 2026-07-23T03:53:47Z
updated_at: 2026-07-23T03:54:41Z
---
# CR-0266 — 同步首页模块重构后的Android工具链回归断言

## 用户需求摘要

项目所有者要求修正首页并确保功能一一对应开发文档。

## 原规则

Android CI工具链回归通过字符串断言旧首页空模块分支home != null && home!!.modules.isNotEmpty()，该实现已被CR-0265的按模块类型渲染替代。

## 新规则

原测试改为断言首页明确过滤NOTICE/BANNER、存在HomeBannerModule、HomeCategory四项和HomeEmptyState；继续证明API成功空模块有明确降级，同时冻结开发文档要求的新信息架构。

## 修改原因

CR-0265删除旧首页空模块条件表达式后，tests/test_android_ci_gate.py仍按字符串断言旧实现，导致工具链回归假失败；必须改为验证新首页Banner、四分类和分型内容模块。

## 影响摘要

仅同步既有工具链回归断言，不修改产品代码、合同、数据、配置或工作流。

## 影响文件

- `tests/test_android_ci_gate.py`

## 页面

- `SCR-HOME-001`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_android_ci_gate tests.test_home_contract_alignment`

## 版本

- `R10`

## 迁移与兼容策略

无迁移；测试从旧实现细节更新为新行为语义。

## 用户确认

项目所有者明确要求修正首页并确保功能与开发文档一一对应。

## 审批

- 审批人：`codex-r10-test-review`
- 决定：`APPROVED`
- 时间：`2026-07-23T03:54:41Z`
- 说明：复核确认仅替换失效的实现字符串断言，新的语义断言覆盖Banner、四分类、模块分型和空态；不扩大产品范围。

## 状态记录 · 2026-07-23T03:54:47Z

- Actor：`codex-root-r10-entry`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T025104Z-E29F6208`
- Note：同步Android工具链首页语义断言。

## 状态记录 · 2026-07-23T04:35:59Z

- Actor：`codex-root-r10-entry`
- Status：`IMPLEMENTED`
- Session：`SES-20260723T025104Z-E29F6208`
- Note：旧首页字符串断言已同步至完整首页模型，android_ci_gate与home_contract_alignment共26项通过。
