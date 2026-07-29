---
cr_id: CR-0497
status: APPROVED
requester_actor_id: codex-r14-implementer
approver_actor_id: codex-r14-independent-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T23:34:36Z
updated_at: 2026-07-29T23:35:12Z
---
# CR-0497 — 修复R14拉黑成功回调跨线程触碰Android View

## 用户需求摘要

继续按既定规则自主完成R14候选测试和关闭，不需要用户逐项确认

## 原规则

executeAction默认假设suspend网络请求返回后仍在Compose主线程，直接更新Compose状态并调用focusManager和keyboardController；候选测试调度器可让续体恢复到DefaultDispatcher工作线程。

## 新规则

所有executeAction请求可以在接口内部使用IO调度器，但请求结果返回后，actionState、页面state、弹窗、提示、焦点、IME和持久化回调必须整体在Dispatchers.Main.immediate执行；禁止任何工作线程直接触碰Compose状态或Android View。

## 修改原因

Attempt14已越过发送后composer清空断言并产出02截图，随后POST拉黑成功回调在DefaultDispatcher-worker-1执行focusManager.clearFocus，触发CalledFromWrongThreadException；这是与composer旧指纹不同的新产品线程错误，必须在保持拉黑禁发与IME收口语义的前提下将结果驱动UI操作强制归位主线程。

## 影响摘要

保持拉黑接口、禁发语义、清焦点和隐藏键盘不变，仅建立明确UI线程边界，并加入从后台调度器返回成功的Compose回归；归档Attempt14新指纹及截图证据，原位补充PROB-0152。

## 影响文件

- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ChatDetailScreenTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt14/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `R14会话详情拉黑与全部命令结果收口`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- `PROB-0152原位补充Attempt14新线程错误`

## 测试

- `后台调度器返回拉黑成功后禁发栏可见、composer消失、持久化成功且无跨线程异常；静态锁；AndroidTest编译；MODULE门禁`

## 版本

- `R14`

## 迁移与兼容策略

无API、数据库和存储格式迁移；网络请求仍保持非主线程，成功/失败UI收口强制回到主线程，其他会话操作共享同一安全边界。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-independent-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T23:35:12Z`
- 说明：Attempt14堆栈精确指向R14ChatDetailScreen.kt:274与:298，且旧composer断言已通过；显式主线程收口既修复真实崩溃又不削弱拉黑禁发、IME和截图验收。

## 状态记录 · 2026-07-29T23:35:19Z

- Actor：`codex-r14-implementer`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档Attempt14并实现统一主线程UI收口及后台恢复回归。
