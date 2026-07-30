---
cr_id: CR-0499
status: APPROVED
requester_actor_id: codex-r14-observation-implementer
approver_actor_id: codex-r14-observation-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T00:12:43Z
updated_at: 2026-07-30T00:13:35Z
---
# CR-0499 — 切换R14解除拉黑恢复输入框验收为Compose可见语义

## 用户需求摘要

持续开发并由AI审核候选截图与功能，不因候选失败要求项目所有者核实

## 原规则

解除拉黑HTTP成功后，候选仅用UiAutomator By.res等待重新进入Compose分支的r14.chat.composer资源导出；分支重建后的resource-id可能不可见，即使Compose节点已恢复。

## 新规则

解除拉黑后必须在Compose未合并语义树的同一10秒期限内等待唯一r14.chat.composer节点并最终assertIsDisplayed；同时新增真实拉黑再解除Compose回归，后台调度器返回成功后必须证明composer恢复、禁发栏消失且持久状态解除。不得删除解除功能或只依赖HTTP 200。

## 修改原因

Attempt15已通过发送、拉黑禁发、返回重进禁发与解除菜单，生成前三张截图；DELETE /api/v1/users/{id}/block以61毫秒返回200且无崩溃，唯一失败为UiAutomator在分支重建后未观察到r14.chat.composer资源。应复用PROB-0125与PROB-0151，用Compose唯一可见节点验证实际恢复，同时增加后台返回成功的拉黑再解除页面回归。

## 影响摘要

只切换解除成功后的观察面并补全产品回归，不修改解除API、状态机、持久化或页面视觉；归档Attempt15三图、HTTP和JUnit证据并原位更新PROB-0151。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ChatDetailScreenTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt15/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `R14会话详情解除拉黑与候选恢复输入框验收`

## API

- `DELETE /api/v1/users/{id}/block合同保持不变`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- `PROB-0151复用与Attempt15不可变证据`

## 测试

- `Compose后台返回成功的拉黑再解除回归；候选解除后唯一composer节点assertIsDisplayed；禁止恢复By.res等待；AndroidTest编译；治理回归`

## 版本

- `R14`

## 迁移与兼容策略

正式App零运行时代码变化；候选使用与页面交互相同的Compose实时语义，原10秒期限及后续长按删除、第四图、JUnit和日志标准不变。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-observation-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T00:13:35Z`
- 说明：Attempt15已有三图、解除菜单、DELETE 200、无崩溃和既有状态机证据；Compose唯一可见断言比不稳定resource导出更直接且不削弱恢复输入框标准，必须同时补足真实页面解除回归。

## 状态记录 · 2026-07-30T00:13:42Z

- Actor：`codex-r14-observation-implementer`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档Attempt15并实现解除后Compose可见验收和拉黑再解除页面回归。

## 状态记录 · 2026-07-30T00:18:13Z

- Actor：`codex-r14-observation-implementer`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt15证据、PROB-0151原位更新、解除后Compose可见验收及后台返回拉黑再解除页面回归已完成；33项治理回归和obx-test固定工具链231任务通过。
