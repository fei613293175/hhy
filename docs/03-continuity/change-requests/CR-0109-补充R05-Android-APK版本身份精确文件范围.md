---
cr_id: CR-0109
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-19T23:49:13Z
updated_at: 2026-07-19T23:49:17Z
---
# CR-0109 — 补充R05 Android APK版本身份精确文件范围

## 用户需求摘要

用户要求持续推进R05正式开发并在桌面交付可测试APK，常规开发事项自行决定

## 原规则

R05-007会话范围未包含Android版本身份文件

## 新规则

仅增加Gradle、ReleasePolicy和VersionMetadataTest三个精确文件

## 修改原因

TASK-R05-007明确要求单调versionCode和APK追溯，但会话启动范围漏列三项Android版本身份文件

## 影响摘要

R05测试APK versionCode由10207单调递增至10208，versionName保持1.2.2-debug

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

- `R05 Android test APK version identity`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `VersionMetadataTest; test_android_apk_delivery; API base URL build gate`

## 版本

- `R05`

## 迁移与兼容策略

仅递增测试APK安装身份，支持覆盖R04测试包；不修改数据、API和用户功能

## 用户确认

用户已明确要求持续推进并将生成APK放在桌面，不再就常规开发事项反复确认

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T23:49:17Z`
- 说明：范围与APK任务直接对应，仅三个精确版本身份文件

## 状态记录 · 2026-07-19T23:49:19Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：应用R05 Android版本身份精确文件范围
