---
cr_id: CR-0471
status: APPROVED
requester_actor_id: codex-r14-apk-20260729
approver_actor_id: project-owner-continuous-development-20260729
task_id: TASK-R14-007
session_id: SES-20260728T205917Z-52E3B6B1
created_at: 2026-07-28T21:01:35Z
updated_at: 2026-07-28T21:02:17Z
---
# CR-0471 — 递增R14替换TEST_APK身份并交付举报目录最终包

## 用户需求摘要

持续开发且每个大版本交付最新APK和测试文档；真机反馈异步，不得因等待反馈停止

## 原规则

R14当前TEST_APK固定为ca57666/versionCode10223；举报目录补齐后必须重建，既有交付规则要求replace-existing的新versionCode严格递增

## 新规则

R14最终替换TEST_APK把Gradle、ReleasePolicy与VersionMetadataTest统一递增到10224；基于当前冻结源码在obx-test固定镜像构建，继续使用hhy-staging-test-v2、https://api.orbexa.cc与wss://ws.orbexa.cc，并通过replace-existing成对更新桌面APK和测试说明

## 修改原因

现有ca57666/10223包早于举报目录与R14消息修复；交付规则要求replace-existing的新versionCode严格递增

## 影响摘要

只更新R14测试包身份及其构建、签名、下载、桌面交付和追溯证据；举报目录与已完成聊天功能进入新包，不改versionName、包名、正式API、WSS、业务合同、数据库或生产签名

## 影响文件

- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`
- `artifacts/apk/R14/APK_MANIFEST.yaml`
- `artifacts/validation/r14-task007-android`
- `artifacts/validation/r14-apk-delivery/delivery-evidence.json`
- `artifacts/reports/R14/TASK-R14-007-android-apk.md`
- `artifacts/reports/R14/R14-version-test-guide.md`
- `releases/R14/RELEASE_MANIFEST.yaml`

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

- `VersionMetadataTest三方统一10224`
- `obx-test固定镜像verifyApiBaseUrl testDebugUnitTest lintDebug assembleDebug`
- `zipalign v2/v3稳定签名 包名 版本 内嵌API/WSS`
- `deliver_android_test_apk --replace-existing四方SHA与桌面APK/说明成对交付`

## 版本

- `R14`

## 迁移与兼容策略

10224沿用同一测试签名，可覆盖安装10223；旧ca57666文件和记录仅作历史证据，新下载路由精确到新文件名；项目所有者真机状态保持PENDING且无需同步反馈

## 用户确认

项目所有者已明确要求持续开发、每个大版本交付最新APK与测试文档、无需逐次批准且真机反馈异步

## 审批

- 审批人：`project-owner-continuous-development-20260729`
- 决定：`APPROVED`
- 时间：`2026-07-28T21:02:17Z`
- 说明：替换未真机PASS的R14测试包必须递增versionCode；范围只覆盖既有版本身份三投影和APK交付证据，不新增业务或规则事实源

## 状态记录 · 2026-07-28T21:02:48Z

- Actor：`codex-r14-apk-20260729`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T205917Z-52E3B6B1`
- Note：开始同步R14 versionCode 10224三处既有身份投影；随后在obx-test固定镜像进行唯一完整构建并使用replace-existing交付
