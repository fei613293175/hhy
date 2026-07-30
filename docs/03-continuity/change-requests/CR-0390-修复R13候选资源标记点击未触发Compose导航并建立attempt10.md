---
cr_id: CR-0390
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-review-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T03:17:56Z
updated_at: 2026-07-27T03:18:40Z
---
# CR-0390 — 修复R13候选资源标记点击未触发Compose导航并建立attempt10

## 用户需求摘要

项目所有者已批准持续开发、不再逐项批准；候选失败必须依据同一Run证据自主最小修复

## 原规则

候选clickResource找到UiAutomator资源节点后直接调用node.click，并假设testTag资源节点自身就是Compose clickable节点。

## 新规则

候选关键资源点击必须从唯一UiAutomator资源节点向上选择首个enabled且clickable祖先并点击；找不到可点击祖先立即失败。Run 30231157257/attempt9保持已消费且禁止重跑；修复Commit与专项证据完成后才允许唯一R13 attempt10/request010/CR-0390/max1。不得改为文字点击、坐标点击、固定sleep、延长页面等待或放宽目标marker。

## 修改原因

Run 30231157257 attempt9后端结构化日志证明自动会话、首页和我的页请求均成功，但没有任何GET /api/v1/me/favorites，请求；失败发生于clickResource(mine.favorites)后33秒。资源testTag可被UiAutomator找到但直接点击未触发其Compose clickable祖先，必须按既有文字点击模式向上选择首个enabled且clickable节点。runtime artifact SHA-256为1e16b7319ffe94e0970f4e0393295c44224d27622e985b81f8b54f993deb6f6c，attempt9已消费且不得重跑。

## 影响摘要

只修复R13候选跨UiAutomator与Compose的资源点击稳定性并扩展既有问题、踩坑和复用事实，不改变生产页面视觉、导航业务、API、数据库或真实媒体；attempt9已消费，attempt10仅绑定首个修复Commit。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `tests/test_android_ci_gate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `CHANGELOG.md`

## 页面

- `R13收藏入口候选自动化导航`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R13 attempt10候选请求`

## 资金/账本与历史数据

- `TASK-R13-007 attempt9失败与attempt10单次授权`

## 测试

- `tests.test_r13_candidate;tests.test_android_ci_gate;tests.test_android_candidate_request;app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

生产APK源码与用户行为不变；候选仍使用既有mine.favorites等稳定资源和原页面marker，只把点击动作绑定到实际可点击祖先，旧Run 30231157257保持失败证据。

## 用户确认

项目所有者已明确批准AI持续开发、终端与门禁自主执行、不再逐项批准；CR-0358站立授权继续适用，attempt10仍只能在修复Commit和模块证据完成后精确登记一次。

## 审批

- 审批人：`codex-review-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T03:18:40Z`
- 说明：独立合同复核通过：目标容器在测试时段无favorites请求，精确证明失败发生在导航前；复用已经验证的enabled+clickable祖先选择是最小修复，不改变生产业务、视觉或等待门槛，并保留旧Run禁重跑和attempt10后置绑定。

## 状态记录 · 2026-07-27T03:19:24Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始实现资源标记到enabled且clickable祖先的稳定点击，并原位更新既有问题、踩坑、复用与专项回归；attempt10在首个修复Commit前不登记。

## 状态记录 · 2026-07-27T03:33:19Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：候选clickResource已统一从唯一资源节点向上选择首个enabled且clickable祖先；既有PROB-0126、踩坑32、PATTERN-ANDROID-REMOTE-001和自动化规范原位加固。39项专项治理回归、UI基础门禁、git diff检查与obx-test固定镜像app:compileDebugAndroidTestKotlin 212任务全部PASS；本地/远端源SHA一致。attempt10尚未登记。
