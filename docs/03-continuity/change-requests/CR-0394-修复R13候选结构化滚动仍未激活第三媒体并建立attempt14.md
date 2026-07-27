---
cr_id: CR-0394
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-review-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T07:11:26Z
updated_at: 2026-07-27T07:11:38Z
---
# CR-0394 — 修复R13候选结构化滚动仍未激活第三媒体并建立attempt14

## 用户需求摘要

项目所有者批准持续自主开发且候选失败后直接精确修复

## 原规则

R13候选在全部收藏列表内通过绑定r13.favorites.list的结构化DOWN/UP滚动激活三个Lazy媒体

## 新规则

R13候选在同一30秒期限内依次点击项目、APP、群聊三个真实分类资源，使每个候选媒体条目成为唯一可见项并等待其独立Success，随后点击全部并严格复验三条Success、零Loading、零Error后才截图

## 修改原因

Run 30244068773 attempt13确认19次结构化双向滚动后仍为2 Success、1 Loading、0 Error，需改用页面真实分类将三个媒体条目逐项置于唯一活动视口

## 影响摘要

仅修复R13候选真实媒体激活策略；不修改生产页面、接口、数据库、媒体URL、图片数量、等待期限或视觉门槛。attempt13保持已消费且不得重跑，attempt14只能在首个修复Commit和固定镜像证据完成后登记

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `tests/test_android_ci_gate.py`
- `CHANGELOG.md`

## 页面

- `SCR-FAV-001候选真实媒体激活`

## API

- `无直接影响；同一Run已通过自动登录和收藏数据加载`

## 数据库与迁移

- `无变化；沿用三条候选内容及其合法READY媒体`

## 配置

- `attempt14候选请求只可在修复Commit和模块证据完成后登记`

## 资金/账本与历史数据

- `TASK-R13-007 attempt13失败与attempt14单次授权追溯`

## 测试

- `tests.test_r13_candidate、tests.test_android_candidate_request、tests.test_android_ci_gate、app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

生产APK与服务端无变化；自动化使用页面已有分类交互替代无效滚动扫描，并保留同一期限及三态硬门禁

## 用户确认

项目所有者已明确批准持续自主开发，不再逐项批准

## 审批

- 审批人：`codex-review-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T07:11:38Z`
- 说明：Run 30244068773已证明结构化双向滚动执行19次仍无法激活第三媒体；依次使用已有分类使三个条目各自进入唯一活动视口是更小且可观测的用户级激活策略，保留三媒体、30秒、零错误与回到全部后的严格复验

## 状态记录 · 2026-07-27T07:16:02Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`SUPERSEDED_BEFORE_IMPLEMENTATION`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：夹具源代码确认三个候选条目全部为PROJECT，分类轮换无法逐项激活且未进入代码实现；由CR-0395纠正为按唯一业务标题调用Compose LazyColumn原生performScrollTo。CR-0394仅保留审计历史，不得用于attempt14授权
