---
cr_id: CR-0387
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-review-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T00:23:30Z
updated_at: 2026-07-27T00:24:19Z
---
# CR-0387 — 修复R13预组合媒体因未测量尺寸永久停留loading并建立attempt7

## 用户需求摘要

用户已批准持续开发且不再逐项批准；R13最终候选必须自主修复并完成真实媒体截图验收

## 原规则

R13 AsyncImage使用默认约束SizeResolver；候选一次性等待当前无障碍树内三条媒体loaded，未区分已组合未测量节点

## 新规则

固定尺寸Lazy列表媒体必须给ImageRequest提供与容器一致的确定像素尺寸，使预组合节点不依赖首次实际测量即可启动真实请求；继续要求三条success、零error、零loading后才截图，禁止延长超时、固定sleep或减少expectedCount

## 修改原因

Run 30226547910 attempt6精确报告expected=3 success=2 errors=0 loading=1，证明无障碍语义已生效，但第三个LazyColumn预组合AsyncImage尚未得到布局尺寸，默认约束SizeResolver没有启动请求；不得延长超时或放宽三媒体门禁

## 影响摘要

仅修复R13媒体加载确定性与候选治理：不改API、数据库、业务数据和页面模块；登记attempt6已消费，代码与固定镜像证据通过后才建立精确attempt7/max1

## 影响文件

- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`
- `tests/test_android_ci_gate.py`
- `tests/test_android_candidate_request.py`
- `CHANGELOG.md`

## 页面

- `R13收藏列表真实媒体`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R13 attempt7候选请求与版本元数据`

## 资金/账本与历史数据

- `TASK-R13-007候选失败与重试追溯`

## 测试

- `tests.test_r13_candidate;tests.test_android_ci_gate;tests.test_android_candidate_request;check_android_ui_foundation.py;feature:activity:testDebugUnitTest;app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

生产接口和存量数据完全兼容；图片仍优先thumbnailUrl再url、仍拒绝非HTTPS、仍使用Coil缓存和真实服务端媒体；仅显式给出既有固定容器尺寸

## 用户确认

用户2026-07-27明确批准持续开发并要求后续不再逐项批准，由开发代理自主完成门禁与候选修复

## 审批

- 审批人：`codex-review-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T00:24:19Z`
- 说明：独立合同复核通过：Run 30226547910的success=2/errors=0/loading=1与默认约束尺寸解析的预组合边界一致；显式固定尺寸不改变业务事实，且保留三媒体硬门禁、旧Run禁重跑和attempt7单次上限

## 状态记录 · 2026-07-27T00:24:40Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始实现确定尺寸ImageRequest、专项回归和既有事实源原位加固；attempt7请求在首个修复Commit形成前不登记

## 状态记录 · 2026-07-27T00:35:58Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：确定尺寸ImageRequest已实现；67项候选治理回归、Android UI基础门禁、git diff检查和obx-test固定镜像223任务全部PASS，尚未建立attempt7请求
