---
cr_id: CR-0503
status: APPROVED
requester_actor_id: codex-r14-list-text-requester
approver_actor_id: codex-r14-list-text-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T01:41:28Z
updated_at: 2026-07-30T01:42:00Z
---
# CR-0503 — 将R14列表业务文字验收切换为Compose精确语义

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

R14候选首次进入与搜索后按UiAutomator resource-id定位r14.conversation.peer-name和r14.conversation.preview，再读取一次性text快照核对业务文字。

## 新规则

保留联系人和预览的唯一testTag、20秒首次期限与10秒搜索后期限；候选必须在Compose未合并语义树等待对应唯一节点并严格assertTextEquals。禁止再用UiAutomator resource-id或一次性跨框架文字快照验收这两个Compose业务值。

## 修改原因

Attempt17请求、构建、OIDC、会话兑换、用户信息、首页和会话接口全部成功，GET /api/v1/conversations两次HTTP 200；唯一首发失败是UiAutomator无法找到r14.conversation.peer-name资源，而同一页面已有Compose唯一testTag和精确文字回归。复用PROB-0151，不能把跨框架导出波动误判为业务缺失。

## 影响摘要

只修正R14列表联系人与预览的候选观察框架并归档Attempt17证据；不修改页面产品代码、接口、数据库、文字内容、搜索行为或后续完整旅程。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt17/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `R14消息列表联系人和预览候选验收`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R14候选Compose语义验收锁`

## 资金/账本与历史数据

- `PROB-0151 Attempt17证据更新`

## 测试

- `Attempt17证据、治理回归、Compose候选回归编译与固定Android工具链验证`

## 版本

- `R14`

## 迁移与兼容策略

无数据、API或运行时迁移；testTag和业务文字保持不变，已有Compose页面回归继续兼容。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-list-text-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T01:42:00Z`
- 说明：候选后端会话兑换、用户信息、首页和两次会话GET均为HTTP 200，页面也已进入r14 conversations；同一业务文字已有稳定Compose testTag。改用同框架唯一节点精确文字断言增强证据且不放宽结果。

## 状态记录 · 2026-07-30T01:42:06Z

- Actor：`codex-r14-list-text-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档Attempt17证据并将联系人和预览的候选断言切换为Compose精确语义。

## 状态记录 · 2026-07-30T01:47:12Z

- Actor：`codex-r14-list-text-requester`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt17证据、PROB-0151原位更新和Compose列表业务文字验收已提交；35项治理回归及obx-test精确提交231任务编译通过。
