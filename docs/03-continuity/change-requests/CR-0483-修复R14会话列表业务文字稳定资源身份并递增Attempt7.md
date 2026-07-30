---
cr_id: CR-0483
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: project-owner-continuity-directive-20260730
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T17:30:06Z
updated_at: 2026-07-29T17:30:49Z
---
# CR-0483 — 修复R14会话列表业务文字稳定资源身份并递增Attempt7

## 用户需求摘要

持续完成R14至R32；候选失败由AI读取证据、最小修复并自行重测，不得无变化重跑或跳版

## 原规则

R14候选会话列表以UiAutomator精确文本查找联系人和预览文字，ConversationRow只有整行r14.conversation.row资源，无法区分数据缺失与Compose文字未导出为稳定跨层资源。

## 新规则

R14会话列表的联系人名称和最后消息预览必须分别暴露唯一资源标识r14.conversation.peer-name与r14.conversation.preview；候选先按资源定位并严格核对节点文本业务值，搜索后继续复核联系人值，保留整行点击、错误文案、后续发送/拉黑/重进/解除拉黑/长按删除及截图断言。Attempt6证据入库后，只有绑定新修复Commit、CR-0483与唯一request007的Attempt7可运行一次。

## 修改原因

Attempt6后端夹具、数据库与真实HTTP响应均正常，UiAutomator按精确文字无法稳定发现Compose会话行中的联系人和预览文字，需要建立稳定资源身份并保留业务值断言

## 影响摘要

消除Compose与UiAutomator之间会话列表文字身份漂移，仍严格验证候选联系人及最后消息业务值；不改变页面布局、公开API、数据库、生产配置或业务行为。

## 影响文件

- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ConversationListScreen.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ConversationListScreenTest.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt6/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `CHANGELOG.md`

## 页面

- `UI-PAGE-104`

## API

- `GET /api/v1/conversations合同不变；真实候选响应已验证`

## 数据库与迁移

- `无迁移；R14 V044候选库夹具与当前会话唯一可见性已验证`

## 配置

- `登记CR-0483精确Attempt7单次例外与request007，全局max_ai_attempts=3保持不变`

## 资金/账本与历史数据

- `无资金、账本或结算影响`

## 测试

- `R14ConversationListScreenTest稳定peer-name/preview资源和值`
- `ReleaseCandidateSmokeTest按稳定资源核对精确业务值`
- `test_android_ci_gate禁止R14列表按By.text夹具等待`
- `test_android_candidate_request精确Attempt7绑定与漂移拒绝`
- `Attempt7完整R14模拟器旅程`

## 版本

- `R14`

## 迁移与兼容策略

仅增加Android测试语义资源与自动化值核对助手；现有视觉、点击语义、接口字段、持久化和服务端兼容。Attempt7继续执行R14完整真实交互旅程。

## 用户确认

当前持续开发Goal及既有明确指令：候选失败由AI判断、最小修复并继续，不能无变化重跑、不能跳过R14。

## 审批

- 审批人：`project-owner-continuity-directive-20260730`
- 决定：`APPROVED`
- 时间：`2026-07-29T17:30:49Z`
- 说明：项目所有者已明确要求持续推进R14至R32、候选失败由AI自行读取证据并修复，不等待逐次批准。本CR只增加稳定资源身份与严格业务值核对，保留全部真实交互断言，精确授权唯一Attempt7。

## 状态记录 · 2026-07-29T17:34:02Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt6 GitHub、候选数据库、真实HTTP和三项Artifact证据已入库；会话联系人与预览稳定资源、UiAutomator精确值核对、Compose与治理回归已实现，89项Android治理测试PASS，准备执行obx-test受影响模块门禁。

## 状态记录 · 2026-07-29T17:40:26Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：修复Commit 64e089d0已形成并通过提交门禁；联系人与预览稳定资源、精确业务值核对、Attempt6证据、Problem Registry和回归测试均已入库，准备绑定唯一Attempt7请求。
