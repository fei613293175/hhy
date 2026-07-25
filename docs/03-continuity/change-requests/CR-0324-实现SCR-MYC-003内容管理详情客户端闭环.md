---
cr_id: CR-0324
status: APPROVED
requester_actor_id: codex-root-r12-client-20260725
approver_actor_id: codex-r12-myc003-independent-reviewer
task_id: TASK-R12-004
session_id: SES-20260725T053515Z-11D4084D
created_at: 2026-07-25T07:56:50Z
updated_at: 2026-07-25T07:57:27Z
---
# CR-0324 — 实现SCR-MYC-003内容管理详情客户端闭环

## 用户需求摘要

OWNER_ACTIVE_GOAL_CONTINUE_R01_R32_WITH_EXACT_UI_AND_CLOUD_ANDROID

## 原规则

SCR-MYC-003已有冻结43字段、7状态、3动作、B08/P03精确视觉合同和三项OpenAPI操作，但Android没有运行时模块；旧ERROR投影要求展示技术错误字段，与正式商业系统全局边界冲突。

## 新规则

仅实现contentGetContentsById、contentPostContentsByIdCopy、contentGetContentsByIdAnalytics三项冻结能力和B08/P03真实视觉结构；复制使用expectedVersion、稳定幂等键、同资源单写锁和二次确认；UI覆盖LOADING、CONTENT、STALE_CACHE、NOT_FOUND、FORBIDDEN、ERROR、OFFLINE且隐藏技术字段；编辑、下架、置顶、道具、红包等未登记动作不得实现。

## 修改原因

冻结页面、API与B08/P03视觉合同已齐备，但R12 Android运行时模块、生成合同适配、真实Navigation路由和商业错误投影尚未实现；本CR仅授权施工，不新增产品能力。

## 影响摘要

新增R12内容管理Android模块、合同网络适配、官方语义图标与Jetpack Navigation Compose详情路由；修正本页ERROR商业文案投影并将真实实现登记IN_REVIEW，不改变OpenAPI、数据库或服务端业务。

## 影响文件

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR12Api.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/R12ApiModelsSerializationTest.kt`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyIcons.kt`
- `apps/android/feature/content-management/build.gradle.kts`
- `apps/android/feature/content-management/src/main/AndroidManifest.xml`
- `apps/android/feature/content-management/src/main/java/cc/orbexa/hhy/feature/contentmanagement/R12ContentManagementState.kt`
- `apps/android/feature/content-management/src/main/java/cc/orbexa/hhy/feature/contentmanagement/R12ContentManagementDetailScreen.kt`
- `apps/android/feature/content-management/src/test/java/cc/orbexa/hhy/feature/contentmanagement/R12ContentManagementStateTest.kt`
- `catalogs/ui_visual_acceptance.csv`
- `docs/02-ui/page-specs/android/SCR-MYC-003_内容管理详情.md`
- `CHANGELOG.md`

## 页面

- `SCR-MYC-003`

## API

- `contentGetContentsById`
- `contentPostContentsByIdCopy`
- `contentGetContentsByIdAnalytics`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- `CR-0314 B08/P03 visual mapping; CR-0321 acceptance projection; CR-0119 navigation/icon rules`

## 测试

- `scripts/check_android_ui_foundation.py; :core:network:testDebugUnitTest; :feature:content-management:testDebugUnitTest; :app:compileDebugKotlin`

## 版本

- `R12`

## 迁移与兼容策略

纯Android客户端增量和文档冲突修正；复用既有ContentResource、ContentStatisticsResource、CommandResultResource；无数据库迁移、无服务端兼容性变化，旧页面入口由后续真实列表故事接入。

## 用户确认

OWNER_ACTIVE_GOAL_CONTINUE_R01_R32_WITH_EXACT_UI_AND_CLOUD_ANDROID

## 审批

- 审批人：`codex-r12-myc003-independent-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T07:57:27Z`
- 说明：独立复核通过：仅施工既有三项冻结API、B08/P03和商业UI边界；禁止未登记动作，复制并发与幂等条件完整，技术错误仅内部诊断。

## 状态记录 · 2026-07-25T07:57:42Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：已完成独立审批和精确范围应用，开始实现R12 Android内容管理详情合同、状态、Compose UI及真实导航。

## 状态记录 · 2026-07-25T08:34:57Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：SCR-MYC-003网络合同、状态、B08/P03 Compose UI与typed Navigation已提交推送；obx-test模块单测、Lint和app编译PASS，完整候选留待TASK-R12-007。

## 状态记录 · 2026-07-25T17:07:03Z

- Actor：`codex-root-r12-client-20260725`
- Status：`CLOSED`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：TASK-R12-004八个Story、模块证据与Release门禁报告已完成并推送，冻结范围无遗留实现项；最终截图与APK按既定策略留到TASK-R12-007。
