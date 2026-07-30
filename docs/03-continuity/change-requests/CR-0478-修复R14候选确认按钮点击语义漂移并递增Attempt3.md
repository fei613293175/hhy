---
cr_id: CR-0478
status: APPROVED
requester_actor_id: codex-r14-close-20260729
approver_actor_id: project-owner-continuity-directive-20260729
task_id: TASK-R14-008
session_id: SES-20260728T220632Z-FE7D82FD
created_at: 2026-07-28T23:32:35Z
updated_at: 2026-07-28T23:33:13Z
---
# CR-0478 — 修复R14候选确认按钮点击语义漂移并递增Attempt3

## 用户需求摘要

持续完成R14并确保R14至R32开发不漂移；候选失败由AI读取真实日志后修复并继续，不得盲目重跑或停下等待确认

## 原规则

PITFALLS第32条与PATTERN-ANDROID-REMOTE-001已要求文字节点向上选择首个enabled且clickable祖先，但ReleaseCandidateSmokeTest.clickLastExactText仍直接点击最后一个enabled文字子节点

## 新规则

不新增平行规则；原位让clickLastExactText与clickExactText复用同一可点击祖先解析语义，并以源码治理回归阻断任何直接node.click旁路；Attempt2失败证据入库后只允许绑定修复Commit的Attempt3

## 修改原因

Run 30407390156日志证明clickLastExactText直接点击不可点击Text语义子节点，违反既有PITFALLS第32条和PATTERN-ANDROID-REMOTE-001，导致确认拉黑请求未触发

## 影响摘要

修复R14候选确认拉黑、解除拉黑和删除会话的重复文字确认按钮点击；登记Attempt2真实失败和PROB-0147；递增唯一候选请求到Attempt3，不修改产品UI、API、数据库或正式业务逻辑

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `config/android-candidate-request.yaml`
- `artifacts/validation/r14-candidate-attempt2/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_android_ci_gate tests.test_continuity_version_sequence`

## 版本

- `R14`

## 迁移与兼容策略

纯Android候选测试与治理证据变更；正式App行为、公共OpenAPI、数据库和线上数据不变。Attempt3仍需从新冻结Commit重建同Commit候选后端并通过完整旅程，禁止复用Attempt2结果或无修复重跑

## 用户确认

项目所有者已要求持续完成R14至R32且候选失败由AI自行分析修复，后续不得发生开发漂移或因逐次确认停止

## 审批

- 审批人：`project-owner-continuity-directive-20260729`
- 决定：`APPROVED`
- 时间：`2026-07-28T23:33:13Z`
- 说明：沿用既有点击祖先硬规则进行窄范围修复，证据与回归范围完整，不扩展产品行为；Attempt2已消费，批准绑定修复Commit后触发唯一Attempt3

## 状态记录 · 2026-07-28T23:36:19Z

- Actor：`codex-r14-close-20260729`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T220632Z-FE7D82FD`
- Note：Attempt2证据、PROB-0147、点击祖先修复、治理回归与Attempt3请求已实现，进入冻结Commit及obx-test模块验证
