---
cr_id: CR-0425
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-cr-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-27T23:48:01Z
updated_at: 2026-07-27T23:50:51Z
---
# CR-0425 — 修复R14聊天尺寸脱离Design Token生成事实

## 用户需求摘要

所有前端必须严格按效果图和硬性UI参数开发并保持换AI可复用

## 原规则

HhyTokens.kt应由design/tokens/hhy_design_tokens_v1.2.2.json确定性生成，但CR-0422手工追加ChatAvatar和ChatComposerHeight，R14会话页又出现裸dp，导致Token门禁失败

## 新规则

删除手工追加且未登记的ChatAvatar与ChatComposerHeight，使HhyTokens完全恢复生成器输出；私聊页与会话列表只用HhySize.MinimumTouchTarget、PrimaryButtonHeight、Hairline及HhySpacing组合表达40、48、分隔缩进、进度线和状态图标，不新增第二套尺寸Token，不改变权威JSON数值或其他平台CSS

## 修改原因

check_ui_tokens精确发现CR-0422手工加入ChatAvatar与ChatComposerHeight导致HhyTokens派生不再幂等，CR-0424新会话页也存在四处裸dp，必须恢复唯一Token事实源后才能继续验证

## 影响摘要

只修复R14聊天Android尺寸引用和生成派生漂移；不改变业务、布局层级、API、Token源JSON或其他平台

## 影响文件

- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ConversationListScreen.kt`

## 页面

- `SCR-CHAT-001`
- `SCR-CHAT-002`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `check_ui_tokens PASS；check_generated_assets PASS；Android UI基础门禁；feature-chat编译单测与Lint；像素数值等价断言由Token算式与编译证据覆盖`

## 版本

- `R14`

## 迁移与兼容策略

确定性源码纠偏，无运行时迁移；视觉数值保持原40/48及对应间距，HhyTokens重新与既有生成器完全一致

## 用户确认

项目所有者已明确授权持续推进开发且无需逐项批准

## 审批

- 审批人：`codex-r14-cr-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-27T23:50:51Z`
- 说明：独立计划复核通过：当前check_ui_tokens已精确证明HhyTokens.kt因手工追加ChatAvatar与ChatComposerHeight而不再与生成器幂等，R14ConversationListScreen.kt仍含72dp、两处2dp和两处40dp裸值。CR-0425仅删除未由生成器产出的手工字段，并在SCR-CHAT-001与SCR-CHAT-002中使用既有HhySize.MinimumTouchTarget、PrimaryButtonHeight、Hairline及HhySpacing等值表达40、48、72和2，不建立第二套Token，不修改design/tokens/hhy_design_tokens_v1.2.2.json、其他平台CSS、业务、API或布局层级。check_ui_tokens、check_generated_assets、Android UI基础门禁及feature-chat编译、单测、Lint能够同时证明派生幂等、无页面裸dp和视觉数值不变。批准按声明范围实施；必须清除全部5个会话列表裸dp出现点并保持HhyTokens与生成器输出完全一致。

## 状态记录 · 2026-07-27T23:51:03Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：独立复核通过，开始恢复Token派生幂等并清除R14聊天裸dp

## 状态记录 · 2026-07-28T00:11:55Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：HhyTokens已恢复生成幂等，R14聊天页面裸dp已全部替换为既有Token等值表达；Token、生成资产、UI基础、编译单测与chat Lint全部通过

## 状态记录 · 2026-07-28T00:49:23Z

- Actor：`codex-root-r14-client-20260728`
- Status：`CLOSED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：实现Commit已恢复Token生成幂等、清除R14聊天裸dp并通过Token、生成资产、Android UI基础、编译单测和Lint门禁
