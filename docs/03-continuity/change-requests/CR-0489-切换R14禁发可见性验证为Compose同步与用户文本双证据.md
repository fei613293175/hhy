---
cr_id: CR-0489
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: codex-independent-test-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T20:43:23Z
updated_at: 2026-07-29T20:44:03Z
---
# CR-0489 — 切换R14禁发可见性验证为Compose同步与用户文本双证据

## 用户需求摘要

持续完成R14至R32；GitHub候选由AI自行审核，同一失败不得反复浪费时间

## 原规则

R14候选首次拉黑与重进均只用UiAutomator By.res(r14.chat.blocked)判断禁发栏可见；失败诊断同时输出Compose节点数和composer resource-id。Attempt7、8、10三轮都沿用该跨框架resource-id可见性路径。

## 新规则

遵守三轮上限并停止原路径：R14候选必须先由Compose测试调度器等待精确testTag节点，再用assertIsDisplayed证明其在根布局可见；同时由UiAutomator等待用户真正看到的“当前无法发送消息”文本，并继续断言composer不存在。首次拉黑与返回重进复用同一严格帮助函数；不得只检查节点存在、延长超时或删除业务断言。现有PROB-0125“不假设testTag一定导出为UiAutomator资源”规则直接复用，不新增并列全局规则。

## 修改原因

Attempt10为PROB-0152第三个真实业务轮：POST拉黑200、Compose精确节点存在、composer资源消失，但UiAutomator无法把Compose testTag导出的resource-id判为可见。三轮上限要求停止原样重跑，改用Compose同步可见断言与UiAutomator用户文本双证据。

## 影响摘要

仅切换R14候选的跨框架可见性取证路径并归档Attempt10证据；不修改拉黑业务、页面布局、API、数据库、超时、截图数量、三轮上限或其他版本。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/validation/r14-candidate-attempt10/failure-evidence.json`

## 页面

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

- `固定Android工具链编译app AndroidTest Kotlin`
- `Compose禁发栏assertIsDisplayed与UiAutomator用户文本双证据`
- `android_ci_gate和candidate request治理回归`

## 版本

- `R14`

## 迁移与兼容策略

Attempt7、8、10保持不可变失败历史，Attempt10登记为旧路径第三且最终轮；后续候选必须绑定CR-0489的新验证路径与新Commit，不能把它称为原路径第四轮。

## 用户确认

项目所有者已明确授权AI持续推进、自动审核候选且同一GitHub问题不得反复浪费时间；相似规则必须更新既有事实源而非新增并列规则。

## 审批

- 审批人：`codex-independent-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T20:44:03Z`
- 说明：独立复审通过：Attempt10已达到旧resource-id路径三轮上限；新方案用Compose可见断言和UiAutomator用户文本交叉取证，仍保留composer消失、重进、解除拉黑、长按删除与四张截图，不构成弱化。

## 状态记录 · 2026-07-29T20:44:09Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt10不可变证据、原位更新PROB-0152，并把首次拉黑与重进切换为同一Compose可见和用户文本双证据帮助函数。
