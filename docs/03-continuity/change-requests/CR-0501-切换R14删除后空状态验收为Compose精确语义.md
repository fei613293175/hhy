---
cr_id: CR-0501
status: APPROVED
requester_actor_id: codex-r14-empty-state-requester
approver_actor_id: codex-r14-empty-state-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T00:58:37Z
updated_at: 2026-07-30T00:58:50Z
---
# CR-0501 — 切换R14删除后空状态验收为Compose精确语义

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

删除确认后等待r14.conversation.row从Compose树消失，再用UiAutomator By.text一次性判断没有找到相关会话。

## 新规则

保留删除HTTP 200、行消失和十秒期限；空状态文字暴露r14.conversations.empty-message稳定testTag，候选在Compose未合并语义树等待唯一节点、assertIsDisplayed并严格assertTextEquals没有找到相关会话。页面回归必须先搜索真实联系人再长按删除并证明同一空状态。

## 修改原因

Attempt16已通过全部前置业务、四图、解除恢复、长按和删除HTTP 200，Compose会话行也已消失；唯一失败是最终仍用UiAutomator By.text观察Compose空状态，复用PROB-0151切换到稳定Compose语义。

## 影响摘要

只修正R14删除后空状态的观察面并补充回归；不修改删除API、会话数据、搜索逻辑、四图、JUnit或日志门禁。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ConversationListScreen.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ConversationListScreenTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt16/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `R14消息列表删除后筛选空状态`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R14候选Compose语义验收锁`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Attempt16证据、治理回归、Compose页面回归编译与固定Android工具链模块验证`

## 版本

- `R14`

## 迁移与兼容策略

无数据和API迁移；已有页面文字和删除行为不变，仅增加稳定语义身份并切换测试观察框架。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-empty-state-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T00:58:50Z`
- 说明：删除接口200、四图和Compose行消失已证明业务成功；改用同框架唯一节点和精确文字断言提高证据强度且不放宽结果。

## 状态记录 · 2026-07-30T00:58:56Z

- Actor：`codex-r14-empty-state-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档Attempt16证据并实现空状态稳定Compose语义与回归锁。

## 状态记录 · 2026-07-30T01:06:01Z

- Actor：`codex-r14-empty-state-requester`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt16 filtered-empty failure replaced with exact Compose semantics; local governance PASS and exact commit Android test compilation PASS on obx-test (231 tasks).
