---
cr_id: CR-0303
status: APPROVED
requester_actor_id: codex-root-r11-candidate
approver_actor_id: codex-reviewer-r11-version
task_id: TASK-R11-007
session_id: SES-20260724T032308Z-98D6D10A
created_at: 2026-07-24T03:49:24Z
updated_at: 2026-07-24T03:49:58Z
---
# CR-0303 — 同步R11候选ReleasePolicy与版本回归身份

## 用户需求摘要

继续开发并静默执行全部终端命令

## 原规则

Android候选版本号必须在Gradle BuildConfig、ReleasePolicy和版本回归中保持单调且完全一致

## 新规则

R11最终候选的Gradle versionCode、ReleasePolicy.VERSION_CODE和VersionMetadataTest统一为10220，测试提示语同步R11

## 修改原因

云端MODULE验证发现BuildConfig已递增10220但ReleasePolicy和VersionMetadataTest仍固定10219，必须原子同步

## 影响摘要

只修复R11版本身份一致性，不改变versionName、合同版本、渠道、环境或任何业务行为

## 影响文件

- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `android-version-code:10220`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `VersionMetadataTest;app testDebugUnitTest;compileDebugAndroidTestKotlin`

## 版本

- `R11`

## 迁移与兼容策略

从R10测试包10219单调递增到R11测试包10220，保持同一应用标识与固定测试签名升级兼容

## 用户确认

项目所有者已明确要求持续开发并由AI自行处理终端和测试问题

## 审批

- 审批人：`codex-reviewer-r11-version`
- 决定：`APPROVED`
- 时间：`2026-07-24T03:49:58Z`
- 说明：失败证据精确指向ReleasePolicy与版本测试仍为10219；同步为10220是最小修复，保持版本单调且不扩展业务范围

## 状态记录 · 2026-07-24T04:11:34Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTING`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：versionCode、ReleasePolicy与版本回归已统一为10220，云端Android MODULE已通过

## 状态记录 · 2026-07-24T04:12:09Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTED`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：R11版本身份三方统一10220并通过云端testDebugUnitTest与compileDebugAndroidTestKotlin
