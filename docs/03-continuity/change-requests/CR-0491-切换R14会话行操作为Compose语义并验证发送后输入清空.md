---
cr_id: CR-0491
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: codex-independent-test-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T22:01:06Z
updated_at: 2026-07-29T22:01:43Z
---
# CR-0491 — 切换R14会话行操作为Compose语义并验证发送后输入清空

## 用户需求摘要

持续完成R14至R32；GitHub候选由AI读取真实交互证据并修复，同一失败不得原样反复浪费时间

## 原规则

Attempt11候选在会话列表首次进入、拉黑后复进、长按和删除消失均使用UiAutomator按Compose testTag导出的resource-id定位，composer也用UiAutomator文本输入；该跨框架路径在真实界面元素可见时仍可能失败，且没有严格证明发送后输入清空。

## 新规则

R14候选的会话行首次进入、拉黑后复进、长按和删除消失必须使用Compose唯一testTag、assertIsDisplayed与原生语义动作；composer输入必须使用Compose文本替换，发送成功后必须严格断言composer为空。发送未误标已读、拉黑禁发、返回复进、解除拉黑、长按删除、四张截图、JUnit和目标进程日志审核全部保留。

## 修改原因

Attempt11重跑已越过首次拉黑三重断言，唯一业务失败转移为返回会话列表后UiAutomator无法定位肉眼可见的Compose会话行，截图同时暴露发送后输入未清空；需要切换为Compose语义操作并增加输入清空断言。

## 影响摘要

只修正R14真实交互候选的自动化框架边界并补充发送后输入清空验收，不修改产品业务、后端、数据库或既有验收范围；Attempt11证据归档到既有Problem Registry条目，禁止无变化重跑。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `artifacts/validation/r14-candidate-attempt11/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `tests/test_android_ci_gate.py`
- `CHANGELOG.md`

## 页面

- `R14会话列表与聊天详情仅测试路径，无产品UI改动`

## API

- `无API合同变更`

## 数据库与迁移

- `无数据库变更`

## 配置

- `无运行时配置变更`

## 资金/账本与历史数据

- `无资金或账本影响`

## 测试

- `R14候选会话行Compose语义操作与发送后composer为空回归`
- `python -m unittest tests.test_android_ci_gate`

## 版本

- `R14`

## 迁移与兼容策略

Attempt11首次Run基础设施失败及同Commit唯一失败Job重跑保持不可变历史；旧UiAutomator会话行路径停止使用，Compose语义路径由新修复Commit和独立候选请求验证，其他版本与产品运行时不受影响。

## 用户确认

项目所有者明确要求由AI审核GitHub候选、持续推进开发，并禁止在相同失败上反复浪费时间。

## 审批

- 审批人：`codex-independent-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T22:01:43Z`
- 说明：独立复审确认新路径保持全部R14业务断言，只替换跨框架不稳定的会话行操作并新增发送后输入清空证明；Attempt11不得无变化重跑。

## 状态记录 · 2026-07-29T22:01:59Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：登记已完成的CR-0491修复实现与固定工具链验证，修复提交保持c1b637e2。

## 状态记录 · 2026-07-29T22:02:05Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Commit c1b637e299288279d96e0ef85fde21b03e889251已完成会话行Compose点击、复进、长按、删除消失与composer语义输入和清空断言；117项治理回归及固定Android工具链225任务PASS。
