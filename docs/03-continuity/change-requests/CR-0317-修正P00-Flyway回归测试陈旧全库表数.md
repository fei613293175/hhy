---
cr_id: CR-0317
status: APPROVED
requester_actor_id: codex-root-r12-data-20260725
approver_actor_id: codex-r12-test-reviewer
task_id: TASK-R12-002
session_id: SES-20260724T235749Z-6EC9DB09
created_at: 2026-07-25T02:20:54Z
updated_at: 2026-07-25T02:21:24Z
---
# CR-0317 — 修正P00 Flyway回归测试陈旧全库表数

## 用户需求摘要

按R01-R32仓库事实源持续开发并完成R12数据库门禁

## 原规则

P00FlywayMigrationTest把执行全部当前迁移后的hhy表数固定为198

## 新规则

执行全部当前迁移至V039后必须得到权威200表，同时P00三个关键保护触发器仍逐项存在

## 修改原因

真实PostgreSQL17后端MODULE显示P00FlywayMigrationTest仍断言198表，与当前200表权威Schema矛盾

## 影响摘要

只修正跨版本测试的陈旧全库计数；不修改P00、R12数据库结构、业务状态机或运行时代码

## 影响文件

- `services/backend/boot/src/test/java/cc/orbexa/hhy/P00FlywayMigrationTest.java`

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

- `P00FlywayMigrationTest; PostgreSQL17 backend boot MODULE 380 tests`

## 版本

- `R12`

## 迁移与兼容策略

V039不新增表；R03起目录、Schema静态门禁和迁移烟测均固定200表，历史198与199仅由verify_baseline保留升级兼容

## 用户确认

OWNER_ACTIVE_GOAL_CONTINUE_R01_R32_IN_REPOSITORY_ORDER

## 审批

- 审批人：`codex-r12-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T02:21:24Z`
- 说明：纠错范围仅一项测试断言，200表由目录、迁移烟测和Schema门禁三方证明，保留P00触发器断言

## 状态记录 · 2026-07-25T02:35:14Z

- Actor：`codex-root-r12-data-20260725`
- Status：`IMPLEMENTING`
- Session：`SES-20260724T235749Z-6EC9DB09`
- Note：已进入实现与验证

## 状态记录 · 2026-07-25T02:35:31Z

- Actor：`codex-root-r12-data-20260725`
- Status：`IMPLEMENTED`
- Session：`SES-20260724T235749Z-6EC9DB09`
- Note：P00 Flyway陈旧198表断言已纠正为当前200表，后端380项零失败零跳过
