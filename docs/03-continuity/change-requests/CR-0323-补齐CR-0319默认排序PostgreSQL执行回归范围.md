---
cr_id: CR-0323
status: APPROVED
requester_actor_id: codex-root-r12-client-20260725
approver_actor_id: codex-r12-postgres-test-scope-reviewer
task_id: TASK-R12-004
session_id: SES-20260725T053515Z-11D4084D
created_at: 2026-07-25T07:16:29Z
updated_at: 2026-07-25T07:16:58Z
---
# CR-0323 — 补齐CR-0319默认排序PostgreSQL执行回归范围

## 用户需求摘要

OWNER_ACTIVE_GOAL_CONTINUE_R01_R32_IN_REPOSITORY_ORDER

## 原规则

CR-0319明确要求R12ReviewService/Postgres排序测试，但真实PostgreSQL Store测试文件未列入影响范围。

## 新规则

仅在现有R12ReviewPostgresStoreTest.java增加对priority:desc,createdAt:asc真实查询的执行断言，确保PostgreSQL 17接受冻结默认排序且返回无游标的页码结果；运行时规则仍来自CR-0319。

## 修改原因

CR-0319明确要求R12ReviewService/Postgres排序测试；CR-0322已补服务层行为，但真实PostgreSQL执行文件仍未纳入当前批准范围。本CR只让现有R12ReviewPostgresStoreTest实际执行默认复合排序查询。

## 影响摘要

补齐CR-0319默认复合排序的真实PostgreSQL执行证据，不增加业务、权限、API或迁移。

## 影响文件

- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R12ReviewPostgresStoreTest.java`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `GET /admin-api/v1/reviews/queue`

## 数据库与迁移

- `PostgreSQL17 review default ordering execution`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `obx-test Java21 R12ReviewPostgresStoreTest with PostgreSQL17`

## 版本

- `R12`

## 迁移与兼容策略

纯测试范围扩展；无运行时或数据迁移。

## 用户确认

OWNER_ACTIVE_GOAL_CONTINUE_R01_R32_IN_REPOSITORY_ORDER

## 审批

- 审批人：`codex-r12-postgres-test-scope-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T07:16:58Z`
- 说明：仅增加CR-0319已要求的默认复合排序真实PostgreSQL17执行断言；不改变Store实现、合同、权限或迁移。

## 状态记录 · 2026-07-25T07:18:02Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：真实PostgreSQL Store默认复合排序执行断言已落盘，准备在obx-test PostgreSQL17验证。

## 状态记录 · 2026-07-25T07:41:45Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：冻结实现已提交并推送；obx-test Java21、PostgreSQL17、V041/U041与仓库SQL合同全部PASS，日志/tmp/hhy-r12-task004-13d85385-v3.log sha256=2efd38006ae7e87b0827417d43e5f845921bbd4b02131d2877484c78ab747864

## 状态记录 · 2026-07-25T17:06:59Z

- Actor：`codex-root-r12-client-20260725`
- Status：`CLOSED`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：TASK-R12-004八个Story、模块证据与Release门禁报告已完成并推送，冻结范围无遗留实现项；最终截图与APK按既定策略留到TASK-R12-007。
