---
cr_id: CR-0148
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R06-004
session_id: SES-20260720T173038Z-35648A77
created_at: 2026-07-20T18:04:53Z
updated_at: 2026-07-20T18:05:40Z
---
# CR-0148 — 补齐TASK-R06-004后续Android Story精确会话范围

## 用户需求摘要

现在立即按你的想法开始开发R06~R08版本，R06完成后直接进入下一版本直到R08完成

## 原规则

当前Session仅允许apps/admin-web等STORY-R06-001管理端路径

## 新规则

仅为TASK-R06-004追加冻结STORY-R06-003和STORY-R06-004所需的四个Android精确实现文件

## 修改原因

当前Session由STORY-R06-001生成，仅含管理端路径，但同一TASK还冻结包含STORY-R06-003和004的Android关于页与首页

## 影响摘要

接入首页homeGetHome、关于页版本检查与协议读取、真实Navigation Compose返回栈；不改变冻结API、数据库、资金或发布规则

## 影响文件

- `apps/android/app/src/main/java/cc/orbexa/hhy/AboutScreen.kt`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ExperienceApi.kt`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`

## 页面

- `SCR-ABOUT-001;SCR-HOME-001`

## API

- `appReleasePostAppVersionCheck;publicGetAgreementsByCode;homeGetHome`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `admin typecheck;android UI foundation;remote compile;GitHub Android quality gate`

## 版本

- `R06`

## 迁移与兼容策略

无数据迁移；兼容既有认证会话、启动门禁和底部导航，仍执行R06 Android Actions候选门禁

## 用户确认

现在立即按你的想法开始开发R06~R08版本，也就是说R06开发完成后不用停止，直接进入下一个版本开发，直到R08开发完成

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T18:05:40Z`
- 说明：项目所有者已明确要求立即连续完成R06至R08；本CR仅补齐TASK-R06-004已冻结Android Story精确文件范围
