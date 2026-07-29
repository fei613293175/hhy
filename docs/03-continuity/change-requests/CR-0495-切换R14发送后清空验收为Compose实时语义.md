---
cr_id: CR-0495
status: APPROVED
requester_actor_id: codex-implementation
approver_actor_id: codex-independent-test-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T23:05:53Z
updated_at: 2026-07-29T23:06:22Z
---
# CR-0495 — 切换R14发送后清空验收为Compose实时语义

## 用户需求摘要

持续开发并由AI审核GitHub候选，不因候选失败停下或要求项目所有者核实

## 原规则

R14发送后使用UiAutomator按r14.chat.composer资源取一次文本快照并立即与空串比较；跨框架resource文本可能滞后于Compose状态。

## 新规则

R14发送后必须在Compose未合并语义树等待唯一r14.chat.composer节点的可编辑文本严格变为空串，再执行最终assertTextEquals；超时仍失败，空值期望和截图前置条件不得弱化。UiAutomator资源只保留外部存在性诊断，不再作为Compose可变文本的事实源。

## 修改原因

Attempt13在CR-0493产品修复后仍由UiAutomator resource文本快照报告旧composer值；服务端消息POST 128毫秒返回200，现有候选同一Compose旅程仅此断言仍使用跨框架resource文本，应复用PROB-0125以Compose实时语义等待并严格核对空值。

## 影响摘要

只切换候选验收观察面，不改变产品代码、消息接口或空值标准；归档Attempt13原始工件、HTTP 200与同指纹第二轮证据。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt13/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `R14候选会话详情发送验收`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- `PROB-0151与PROB-0125复用`

## 测试

- `Compose实时等待composer严格为空；禁止恢复assertResourceText；Attempt13证据解析；AndroidTest编译`

## 版本

- `R14`

## 迁移与兼容策略

正式App和服务端零变更；候选从跨框架快照改为与输入动作相同的Compose实时语义，其他R14旅程、四图、JUnit和日志审核保持不变。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-independent-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T23:06:22Z`
- 说明：Attempt13与服务端128毫秒HTTP 200证明旧resource文本观察面仍不可靠；改用Compose实时语义不弱化空值断言，属于既有PROB-0125的直接复用。

## 状态记录 · 2026-07-29T23:06:28Z

- Actor：`codex-implementation`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档Attempt13并实现Compose实时空值帮助函数和静态锁。
