---
cr_id: CR-0125
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T06:20:11Z
updated_at: 2026-07-20T06:20:45Z
---
# CR-0125 — 递增登录导航修复与实名沙箱闭环测试APK版本身份

## 用户需求摘要

项目所有者反馈登录方式切换、注册与忘记密码返回方向和来源错误，并要求持续推进开发；实名资料页通过但后续受服务阻断。

## 原规则

R05当前测试APK版本身份为versionName 1.2.2、versionCode 10210。

## 新规则

包含CR-0123登录真实返回栈修复并连接CR-0124已部署Staging实名沙箱的测试APK递增为versionName 1.2.2、versionCode 10211，同步发布策略、版本测试和测试说明。

## 修改原因

CR-0123客户端修复与CR-0124公网沙箱闭环均已完成，必须使用新的单调versionCode交付真机测试，禁止复用10210。

## 影响摘要

仅递增Android测试APK版本身份和交付说明，不改变API、数据库、生产版本号或沙箱隔离边界。

## 影响文件

- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`
- `CHANGELOG.md`
- `artifacts/reports/R05/R05-version-test-guide.md`

## 页面

- `SCR-AUTH-001`
- `SCR-AUTH-002`
- `SCR-AUTH-003`
- `SCR-AUTH-004`
- `SCR-ID-003`
- `SCR-ID-004`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `版本配置、ReleasePolicy与VersionMetadataTest统一为10211。`
- `verifyApiBaseUrl、testDebugUnitTest、lintDebug、assembleDebug、签名、badging、内嵌URL与桌面/仓库/服务器/公网哈希全部通过。`

## 版本

- `R05`

## 迁移与兼容策略

同applicationId与固定Staging签名覆盖安装；versionCode单调递增；既有账号、本地状态和服务端数据保持兼容。

## 用户确认

2026-07-20项目所有者提交登录注册动效与返回来源反馈，并要求持续推进开发和生成桌面测试APK。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T06:20:45Z`
- 说明：依据项目所有者明确反馈并要求不暂停持续推进，批准10211单调递增测试包交付。

## 状态记录 · 2026-07-20T06:20:48Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：开始同步10211版本身份并执行不可变构建交付。

## 状态记录 · 2026-07-20T06:50:13Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：10211不可变源码完成固定工具链构建、稳定签名、正式API扫描和桌面/仓库/服务器/HTTPS四方同哈希交付，真机验收保持PENDING。
