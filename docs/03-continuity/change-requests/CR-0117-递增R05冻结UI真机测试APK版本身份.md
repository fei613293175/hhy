---
cr_id: CR-0117
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T02:47:21Z
updated_at: 2026-07-20T02:48:00Z
---
# CR-0117 — 递增R05冻结UI真机测试APK版本身份

## 用户需求摘要

项目所有者要求立即落地R05冻结视觉补充包并推进开发。

## 原规则

R05现有真机测试APK使用versionCode 10208，对应冻结UI落地前的旧页面。

## 新规则

R05冻结UI真机回归APK使用versionCode 10209，versionName保持1.2.2-debug、固定Staging测试签名和HHY_API_BASE_URL=https://api.orbexa.cc；测试包不得冒充正式发布产物。

## 修改原因

冻结UI实现必须生成可覆盖安装且不与旧R05测试包混淆的新APK，versionCode需从10208单调递增。

## 影响摘要

仅递增Android测试包版本身份并重新生成既有R05-007交付证据与桌面副本。

## 影响文件

- `apps/android/app/build.gradle.kts`

## 页面

- `SCR-ID-001`
- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android versionCode=10209`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `verifyApiBaseUrl；testDebugUnitTest；lintDebug；assembleDebug；签名与四方SHA；真机覆盖安装`

## 版本

- `R05`

## 迁移与兼容策略

支持在10208测试包上覆盖安装；不改变applicationId、API、数据库、签名策略或正式发布状态。

## 用户确认

项目所有者明确提供冻结视觉补充包并要求立即推进开发，且既有长期规则要求测试APK放桌面。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T02:48:00Z`
- 说明：冻结UI需要新的单调版本测试包供项目所有者覆盖安装验收。
