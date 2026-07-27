---
cr_id: CR-0389
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-review-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T01:30:51Z
updated_at: 2026-07-27T01:33:08Z
---
# CR-0389 — 修复R13候选列表滚动容器未暴露UiAutomator资源并建立attempt9

## 用户需求摘要

用户已批准持续开发且不再逐项批准；R13最终候选失败后必须消费同一Run证据并自主最小修复

## 原规则

CR-0388候选使用By.scrollable(true)查找唯一Lazy列表再执行UiObject2.scroll；该规则假设Compose LazyColumn会向UiAutomator暴露scrollable属性。

## 新规则

R13活动LazyColumn必须按模式暴露稳定资源r13.<mode>.list；候选只允许用By.res定位收藏列表，并根据该节点visibleBounds在内容区执行受控UiDevice.swipe下滚激活和上滚回顶。禁止依赖scrollable属性、全屏固定坐标、延长30秒、降低三媒体数量或改变截图起点。

## 修改原因

Run 30229476784 attempt8已完成并精确失败于Expected exactly one R13 activity list count=0；Compose LazyColumn没有通过scrollable属性进入UiAutomator树，候选不能依赖By.scrollable(true)，必须暴露稳定列表testTag并在其可见边界内执行坐标滑动。runtime artifact SHA-256为cb6907f5b5903d650d74cc586ea22328a66769503e9f31d844e1dc42e233d897；attempt8已消费且不得重跑。

## 影响摘要

只修复R13列表自动化可定位性和候选滚动手势，不改变视觉像素、业务功能、API、数据库或真实媒体；attempt8已消费，首个修复Commit与模块证据完成后才登记精确attempt9/max1。

## 影响文件

- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_ci_gate.py`
- `tests/test_android_candidate_request.py`
- `CHANGELOG.md`

## 页面

- `R13收藏列表候选滚动容器语义`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R13 attempt9候选请求`

## 资金/账本与历史数据

- `TASK-R13-007 attempt8失败与attempt9单次授权`

## 测试

- `tests.test_r13_candidate;tests.test_android_ci_gate;tests.test_android_candidate_request;feature:activity:testDebugUnitTest;app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

生产页面仅新增不可见测试语义资源，现有导航、布局、媒体URL、确定尺寸ImageRequest和截图合同不变；旧Run 30229476784保持失败证据。

## 用户确认

项目所有者已明确批准AI持续开发、候选失败后自主修复并要求不再逐项批准；CR-0358站立授权继续适用，但attempt9只能在修复Commit和模块证据完成后精确登记一次。

## 审批

- 审批人：`codex-review-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T01:33:08Z`
- 说明：独立合同复核通过：Run 30229476784在业务断言开始前精确证明By.scrollable(true)为0；稳定testTag加节点边界内UiDevice.swipe是最小且可编译验证的修复，不改变视觉和业务，并保留原30秒、三媒体与旧Run禁重跑。

## 状态记录 · 2026-07-27T01:34:05Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始为R13 LazyColumn暴露稳定列表资源，并在候选中按资源节点visibleBounds执行受控坐标滑动；attempt9请求在首个修复Commit形成前不登记。

## 状态记录 · 2026-07-27T01:44:03Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：R13活动LazyColumn已暴露稳定r13.<mode>.list资源；候选按r13.favorites.list节点visibleBounds执行受控UiDevice.swipe激活和回顶，不再依赖scrollable属性。62项治理回归、Android UI基础门禁、git diff检查及obx-test固定镜像223任务全部PASS；attempt9尚未登记。
