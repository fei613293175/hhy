---
cr_id: CR-0091
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T18:55:11Z
updated_at: 2026-07-19T18:55:35Z
---
# CR-0091 — 精确补齐TASK-R05-004 Android实施文件范围

## 用户需求摘要

用户要求持续推进R05及后续版本开发，不遗漏开发文档中的任何页面和功能

## 原规则

TASK-R05-004精确允许路径遗漏Android四页面所需文件

## 新规则

仅将列出的12个Android实名实现与集成文件加入当前会话允许范围

## 修改原因

CR-0090已批准但使用目录通配符，范围门禁要求逐文件精确登记，现以相同边界更正表达

## 影响摘要

对CR-0090的通配符表达进行精确化，不新增页面、接口或数据库变更

## 影响文件

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractIdentityApi.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`
- `apps/android/feature/identity/build.gradle.kts`
- `apps/android/feature/identity/src/main/AndroidManifest.xml`
- `apps/android/feature/identity/src/main/java/cc/orbexa/hhy/identity/IdentityFlowScreen.kt`
- `apps/android/feature/identity/src/main/java/cc/orbexa/hhy/identity/IdentityPresentation.kt`
- `apps/android/feature/identity/src/test/java/cc/orbexa/hhy/identity/IdentityPresentationTest.kt`

## 页面

- `SCR-ID-001`
- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`

## API

- `identityPostIdentitySessions`
- `identityPostIdentitySessionsByIdLivenessToken`
- `identityGetIdentitySessionsById`
- `identityPostIdentitySessionsByIdRetry`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `HHY_IDENTITY_CONSENT_VERSION`
- `HHY_H5_BASE_URL`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `:feature:identity:testDebugUnitTest,:feature:identity:lintDebug,:app:compileDebugKotlin`

## 版本

- `R05`

## 迁移与兼容策略

新增identity模块，App显式依赖；现有登录与安全页面保持兼容

## 用户确认

用户已明确要求立即持续推进整个项目开发，严格完成开发文档每个版本的全部功能，不允许遗漏

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T18:55:35Z`
- 说明：精确文件清单与已批准CR-0090和TASK-R05-004原目标一致
