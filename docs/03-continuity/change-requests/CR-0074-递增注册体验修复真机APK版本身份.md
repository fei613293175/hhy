---
cr_id: CR-0074
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-19T04:19:42Z
updated_at: 2026-07-19T04:20:01Z
---
# CR-0074 — 递增注册体验修复真机APK版本身份

## 用户需求摘要

用户完成注册页面真机测试并反馈四项问题，要求持续推进开发

## 原规则

当前R03测试APK版本身份固定为10204

## 新规则

注册体验与错误细化回归APK统一使用10205版本身份

## 修改原因

修复包必须高于当前10204以支持同应用覆盖安装

## 影响摘要

仅同步Gradle、运行时发布策略和版本单元测试，应用ID与固定测试签名不变

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

- `VersionMetadataTest、testDebugUnitTest、lintDebug、assembleDebug、APK签名/API/SHA门禁`

## 版本

- `R03`

## 迁移与兼容策略

versionCode单调递增至10205，可覆盖安装10204；后端和用户数据不受影响

## 用户确认

用户2026-07-19注册页面真机反馈及持续开发授权

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T04:20:01Z`
- 说明：用户已明确反馈并要求持续开发，新测试APK必须可安全覆盖安装

## 状态记录 · 2026-07-19T04:20:02Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：开始同步10205版本身份并准备云端构建

## 状态记录 · 2026-07-19T04:54:01Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：Android Gradle、ReleasePolicy和版本测试已统一为10205

## 状态记录 · 2026-07-19T04:54:03Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：10205固定签名真机APK构建与覆盖安装身份门禁通过
