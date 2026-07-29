---
cr_id: CR-0486
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: project-owner-continuity-directive-20260730
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T19:33:08Z
updated_at: 2026-07-29T19:33:54Z
---
# CR-0486 — 更正R14拉黑禁发栏被输入法遮挡并登记Attempt9最终诊断轮

## 用户需求摘要

项目所有者已授权持续开发且不得为终端、候选或逐版本真机反馈暂停；相同错误最多三轮且必须产生新证据或代码变化。

## 原规则

PROB-0152把Attempt7失败归因于SharedPreferences.commit；拉黑成功后blocked分支只保留navigationBarsPadding，而可输入composer分支额外使用imePadding，成功回调也不释放输入焦点，候选发送消息后的IME可继续覆盖禁发栏。

## 新规则

更正PROB-0152而不新增并列问题：拉黑成功必须先清除输入焦点并请求关闭软键盘；禁发栏与输入composer必须使用一致的IME避让，r14.chat.blocked在IME仍处于过渡或关闭失败时也必须可见且composer不存在。Attempt8永久失败；同指纹第三且最终候选只能是绑定CR-0486和新修复Commit的Attempt9，失败则登记三轮上限并切换测试/布局方案，禁止Attempt10继续同路径。

## 修改原因

Attempt8再次证明后端200、user_blocks与幂等成功，但r14.chat.blocked在十秒内不可见；代码审计发现正常composer分支具备imePadding而blocked分支缺失，且拉黑成功未清除输入焦点或隐藏输入法。Attempt7的commit阻塞假设已被apply修复后的相同失败反证，必须更正既有PROB-0152并以输入法可见性修复作为同指纹第三且最终一轮。

## 影响摘要

修复R14聊天详情禁发栏的IME遮挡，补齐带焦点输入场景的Compose显示回归和候选失败诊断，写入Attempt8服务端/数据库证据并精确绑定最终Attempt9。

## 影响文件

- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt`
- `apps/android/feature/chat/src/androidTest/java/cc/orbexa/hhy/chat/R14ChatDetailScreenTest.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `artifacts/validation/r14-candidate-attempt8/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`

## 页面

- `R14聊天详情拉黑禁发底栏与输入法避让`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android候选Attempt9唯一授权、请求与同指纹三轮上限`

## 资金/账本与历史数据

- `Attempt8失败证据和PROB-0152更正`

## 测试

- `R14ChatDetailScreenTest：输入框获得焦点并输入后执行拉黑，禁发资源必须assertIsDisplayed且composer不存在`
- `ReleaseCandidateSmokeTest：保留By.res拉黑资源与composer消失，并在失败中记录Compose语义和IME诊断`
- `tests.test_android_ci_gate：强制blocked分支imePadding、成功清焦点/隐藏键盘和Attempt9精确例外`
- `tests.test_android_candidate_request：Attempt9请求精确绑定修复Commit与CR-0486`

## 版本

- `R14`

## 迁移与兼容策略

无数据库、API或持久化键迁移；保留apply修复、复合键、稳定资源、composer消失、重进、解除拉黑、长按删除和四张截图断言。仅调整焦点/软键盘生命周期与blocked分支布局内边距。

## 用户确认

用户已明确要求自行持续开发、无需再次批准，并要求相同错误三轮内产生真实修复或切换方案。

## 审批

- 审批人：`project-owner-continuity-directive-20260730`
- 决定：`APPROVED`
- 时间：`2026-07-29T19:33:54Z`
- 说明：项目所有者已明确授权终端、持续开发和无需逐次批准；本CR严格限定为R14同一失败的第三轮更正，不放宽验收。

## 状态记录 · 2026-07-29T19:34:00Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始实施IME遮挡修复、Attempt8证据和最终Attempt9门禁。
