---
cr_id: CR-0120
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T04:27:30Z
updated_at: 2026-07-20T04:27:52Z
---
# CR-0120 — 递增统一导航图标动效基础设施测试APK版本身份

## 用户需求摘要

用户要求立即全量优化既有页面并强制记录大型App成熟方案，完成后继续交付可真机验证APK。

## 原规则

R05当前测试APK版本身份为versionName 1.2.2、versionCode 10209。

## 新规则

全局统一导航、图标和动效基础设施真机测试APK递增为versionName 1.2.2、versionCode 10210，并同步ReleasePolicy与版本测试。

## 修改原因

本次全局导航返回栈、矢量图标和页面过渡变化必须使用新的单调versionCode，禁止复用10209。

## 影响摘要

仅递增Android测试APK版本身份并更新版本一致性断言，不改变API、数据库和正式版本号。

## 影响文件

- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`

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

- `云端testDebugUnitTest、lintDebug、assembleDebug、aapt badging与ReleasePolicy一致性全部通过。`

## 版本

- `R05`

## 迁移与兼容策略

同applicationId覆盖安装；versionCode单调递增；既有账号和本地状态兼容。

## 用户确认

2026-07-20用户明确要求立即优化全部既有页面、加入硬性规则并继续开发。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T04:27:52Z`
- 说明：依据项目所有者本轮明确要求立即全量优化并持续生成桌面测试APK，批准单调递增测试包版本身份。

## 状态记录 · 2026-07-20T04:27:54Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：开始同步10210版本身份并执行不可变提交构建。

## 状态记录 · 2026-07-20T05:01:07Z

- Actor：`codex`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：Android测试版本身份已提升至versionCode 10210并通过不可变构建、签名和版本门禁。
