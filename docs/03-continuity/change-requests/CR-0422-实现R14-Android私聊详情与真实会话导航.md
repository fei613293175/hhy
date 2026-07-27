---
cr_id: CR-0422
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-cr-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-27T22:23:17Z
updated_at: 2026-07-27T22:30:37Z
---
# CR-0422 — 实现R14 Android私聊详情与真实会话导航

## 用户需求摘要

持续推进R14并确保前后端功能与开发文档和冻结效果图一一对应

## 原规则

SCR-CHAT-002与R14三项冻结API已定义，但Android尚无私聊模块、生成合同形状传输、真实会话返回栈和R08至R11创建会话后的详情导航

## 新规则

design/R14-UI-FROZEN/specs/SCR-CHAT-002.md是私聊页唯一视觉权威；B07/P02仅继承会话骨架，P04仅继承内容卡视觉语言。新增独立feature-chat并仅消费core-network冻结DTO，完整实现四类消息和CONNECTING、CONTENT、SENDING、OFFLINE、BLOCKED、ERROR及首屏、空、刷新、翻页局部失败。图片严格走PRIVATE_CHAT的create-upload-complete后发送IMAGE，失败可预览、重试或清理。发送与已读对同一业务意图保持clientMessageId和幂等键稳定。新增类型安全ChatDetail路由，内部列表、通知、分享目标与R08至R11仅在conversationId合法时进入；登录前目标由认证壳保留，403或404返回安全上级，来源失效回消息模块，未登记或缺参深链拒绝。UI禁止技术字段、虚构内容和冻结范围外能力

## 修改原因

STORY-R14-001要求SCR-CHAT-002完整私聊流程；当前Android缺少feature-chat、R14网络合同和创建会话后的真实导航入口

## 影响摘要

新增R14 Android网络合同、私聊状态机与详情页、页面级Compose证据、模块接线和导航测试；扩展HhyIcons与冻结尺寸Token；回接R08至R11直接会话；复用现有PRIVATE_CHAT媒体上传器；不修改OpenAPI、后端、数据库或配置，不提前启用会话列表底栏

## 影响文件

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/NavigationPolicyTest.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/AuthenticatedNavigationTest.kt`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyIcons.kt`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR14Api.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ContractR14ApiTest.kt`
- `apps/android/feature/chat/build.gradle.kts`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatState.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt`
- `apps/android/feature/chat/src/test/java/cc/orbexa/hhy/chat/R14ChatStateTest.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ChatDetailScreenTest.kt`
- `apps/android/feature/project/src/main/java/cc/orbexa/hhy/project/R08ProjectScreens.kt`
- `apps/android/feature/app-promotion/src/main/java/cc/orbexa/hhy/apppromotion/R09AppScreens.kt`
- `apps/android/feature/group-promotion/src/main/java/cc/orbexa/hhy/grouppromotion/R10GroupScreens.kt`
- `apps/android/feature/team-leader/src/main/java/cc/orbexa/hhy/teamleader/R11TeamLeaderDetailScreen.kt`
- `CHANGELOG.md`

## 页面

- `SCR-CHAT-002`

## API

- `chatGetConversationsByIdMessages`
- `chatPostConversationsByIdMessages`
- `chatPostConversationsByIdRead`
- `chatPostConversationsDirect`
- `mediaPostMediaUploadSessions`
- `mediaPostMediaUploadSessionsByIdComplete`
- `mediaDeleteMediaById`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `四类消息请求编码与响应判别解码；GET分页参数和错误信封；PRIVATE_CHAT create-upload-complete-IMAGE发送及失败清理；稳定clientMessageId与幂等键重试；首屏、空、刷新、翻页失败保留；离线、401、403、404、409、422、429、500、BLOCKED与限频；图片加载失败、预览和附件无障碍；技术字段不可见；类型安全路由、缺参拒绝、登录目标恢复、安全上级与真实popBackStack；R08至R11创建会话成功导航；check_android_ui_foundation；R14 UI catalog；core-network、feature-chat、app单测与Compose测试；obx-test受影响Android编译和Lint`

## 版本

- `R14`

## 迁移与兼容策略

纯客户端增量；保留既有API路径和内部路由，回调只增加可兼容默认实现；非法或未登记深链安全拒绝，旧客户端与后端无需迁移；通知及分享消费者后续只复用同一ChatDetail路由策略，不另建导航事实源

## 用户确认

项目所有者已明确授权持续推进开发且无需逐项批准

## 审批

- 审批人：`codex-r14-cr-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-27T22:30:37Z`
- 说明：独立计划复核通过：CR-0422已将design/R14-UI-FROZEN/specs/SCR-CHAT-002.md确立为唯一视觉权威，并限定B07/P02仅继承会话骨架、P04仅继承内容卡语言；四类封闭消息、六个主状态、首屏/空/刷新/翻页局部失败及技术字段过滤与STORY-R14-001一致。chatGetConversationsByIdMessages、chatPostConversationsByIdMessages、chatPostConversationsByIdRead、chatPostConversationsDirect及媒体create/complete/delete均已登记，PRIVATE_CHAT与既有私有存储PurposePolicy一致。发送仅以clientMessageId和幂等键保持同一业务意图稳定，已读以lastReadMessageId和幂等键保持同一业务意图稳定。类型安全ChatDetail覆盖合法参数、登录目标恢复、403/404安全返回、来源失效、缺参/未登记深链拒绝及R08至R11成功导航；单测、页面级Compose测试、导航测试、完整错误分支、图片闭环、无障碍、Android基础门禁及obx-test编译Lint范围完整。批准按声明范围进入实现，最终截图和AI视觉PASS仍由后续R14候选门禁完成。

## 状态记录 · 2026-07-27T23:17:50Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：已按批准范围完成Android私聊详情、网络合同、媒体闭环和真实会话导航实现，进入验证收口

## 状态记录 · 2026-07-27T23:17:57Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：obx-test固定镜像编译、三组单测及聊天模块和App Lint通过；R14入口、API、生成资产、Android UI与视觉目录静态门禁通过，最终截图留在大版本候选阶段
