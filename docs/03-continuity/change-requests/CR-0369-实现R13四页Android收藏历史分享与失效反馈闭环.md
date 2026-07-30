---
cr_id: CR-0369
status: APPROVED
requester_actor_id: codex-root-r13-client-20260726
approver_actor_id: codex-android-contract-review-r13-20260726
task_id: TASK-R13-004
session_id: SES-20260726T134047Z-F9A80405
created_at: 2026-07-26T13:54:50Z
updated_at: 2026-07-26T13:55:52Z
---
# CR-0369 — 实现R13四页Android收藏历史分享与失效反馈闭环

## 用户需求摘要

项目所有者批准持续开发且日常开发无需再次批准；按冻结开发文档和精确视觉规格实现R13。

## 原规则

R13六个冻结operationId服务端已完成，但Android仍复用旧版本直接分享动作且缺少取消收藏、收藏列表、浏览历史和失效反馈四页闭环。

## 新规则

新增独立ContractR13Api与activity功能模块，四页严格绑定冻结operationId和精确视觉规格；我的首页进入收藏与历史，内容详情统一打开分享和失效反馈底部面板，所有写操作使用稳定幂等键并覆盖完整错误恢复。

## 修改原因

R13-003已完成六个operationId服务端合同，R13-004需要新增ContractR13Api、四页状态与Compose UI、我的入口及内容详情动作回接，并形成MODULE证据。

## 影响摘要

实现R13四页Android真实客户端闭环、四类内容详情动作回接、我的入口、类型化传输和MODULE测试；H5与后台页面按Release Manifest显式不适用，不修改OpenAPI、数据库、资金或生产配置。

## 影响文件

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR13Api.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ContractR13ApiTest.kt`
- `apps/android/feature/activity/build.gradle.kts`
- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityState.kt`
- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt`
- `apps/android/feature/activity/src/test/java/cc/orbexa/hhy/activity/R13ActivityStateTest.kt`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/R12MeHomeScreen.kt`
- `apps/android/feature/project/src/main/java/cc/orbexa/hhy/project/R08ProjectScreens.kt`
- `apps/android/feature/app-promotion/src/main/java/cc/orbexa/hhy/apppromotion/R09AppScreens.kt`
- `apps/android/feature/group-promotion/src/main/java/cc/orbexa/hhy/grouppromotion/R10GroupScreens.kt`
- `apps/android/feature/team-leader/src/main/java/cc/orbexa/hhy/teamleader/R11TeamLeaderDetailScreen.kt`
- `catalogs/ui_visual_acceptance.csv`
- `releases/R13/RELEASE_MANIFEST.yaml`
- `docs/03-continuity/R13_TASK-004_CLIENT_GATE.md`
- `CHANGELOG.md`

## 页面

- `SHEET-SHARE-001`
- `SCR-FAV-001`
- `SCR-HIS-001`
- `SHEET-CONTENT-INVALID-001`

## API

- `contentDeleteContentsByIdFavorite`
- `contentGetMeFavorites`
- `contentGetMeHistory`
- `contentPostContentsByIdShare`
- `contentPostContentsByIdInvalidFeedback`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `ContractR13Api serialization validation and error mapping tests`
- `R13 favorites history pagination retention grouping and stable idempotency state tests`
- `R13 four-page Compose navigation and visual contract tests`

## 版本

- `R13`

## 迁移与兼容策略

无数据库、OpenAPI或配置迁移；新增Android模块与可选回调保持既有详情导航兼容，既有R08至R12接口调用不变。

## 用户确认

项目所有者已批准持续开发并明确日常开发无需再次批准。

## 审批

- 审批人：`codex-android-contract-review-r13-20260726`
- 决定：`APPROVED`
- 时间：`2026-07-26T13:55:52Z`
- 说明：独立复核确认完整影响文件、四页、五个客户端operationId、B08精确面板与两份补充规格边界一致；H5和后台页面显式不适用，客户端新增模块不修改冻结合同。

## 状态记录 · 2026-07-26T13:55:59Z

- Actor：`codex-root-r13-client-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T134047Z-F9A80405`
- Note：开始实施R13类型化Android传输、四页状态与Compose UI、我的入口及四类内容详情动作回接。

## 状态记录 · 2026-07-26T15:20:17Z

- Actor：`codex-root-r13-client-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T134047Z-F9A80405`
- Note：R13客户端精确提交已通过obx-test固定镜像MODULE单测与Lint，视觉目录保持IN_REVIEW等待最终候选。

## 状态记录 · 2026-07-26T15:24:08Z

- Actor：`codex-root-r13-client-20260726`
- Status：`CLOSED`
- Session：`SES-20260726T134047Z-F9A80405`
- Note：R13四页客户端闭环、MODULE证据、视觉IN_REVIEW目录和TASK-R13-004门禁报告已提交。
