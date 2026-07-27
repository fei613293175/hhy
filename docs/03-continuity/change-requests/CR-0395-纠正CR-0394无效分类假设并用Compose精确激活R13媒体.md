---
cr_id: CR-0395
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-review-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T07:15:49Z
updated_at: 2026-07-27T07:16:02Z
---
# CR-0395 — 纠正CR-0394无效分类假设并用Compose精确激活R13媒体

## 用户需求摘要

项目所有者要求停止原地打转并持续推进开发

## 原规则

CR-0394拟通过项目、APP、群聊分类逐项激活媒体，但三个夹具条目实际全部为PROJECT

## 新规则

CR-0395替代且禁止实施CR-0394的分类假设；R13候选在原30秒期限内按三个唯一业务标题调用Compose LazyColumn原生performScrollTo，每项必须观察累计独立Success增加且零Error，最后回到首项并复验三条Success、零Loading、零Error后截图

## 修改原因

三个候选夹具条目经脚本核验均为PROJECT，CR-0394的分类逐项激活方案不可实施；Run 30244068773需要以LazyColumn原生语义滚动精确命中三个唯一标题

## 影响摘要

仅修复Android候选测试和既有问题事实源；不改生产页面、API、数据库、夹具、媒体URL、图片数量、超时或视觉门槛。CR-0394保留为已批准但因事实核验失败而未实施的审计历史，attempt13不得重跑，attempt14仍后置到首个修复Commit和模块证据

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `config/android-automation.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-FAV-001候选真实媒体激活`

## API

- `无影响`

## 数据库与迁移

- `无变化；三个候选内容继续保持PROJECT及READY媒体事实`

## 配置

- `attempt14只可在修复Commit和固定镜像证据后登记`

## 资金/账本与历史数据

- `CR-0394未实施纠正、attempt13失败和attempt14追溯`

## 测试

- `tests.test_r13_candidate、Android UI foundation、app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

生产APK和服务端不变；测试改用已有Compose UI测试依赖精确滚动，UiAutomator继续独立复验媒体三态

## 用户确认

项目所有者要求停止原地打转并持续自主推进

## 审批

- 审批人：`codex-review-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T07:16:02Z`
- 说明：夹具源代码证明三个条目均为PROJECT，因此CR-0394不得实施；Compose performScrollTo直接命中唯一业务标题并调用LazyColumn原生语义滚动，是当前证据支持的最小修复，保留全部原候选硬门槛

## 状态记录 · 2026-07-27T07:17:00Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始把候选媒体激活改为createEmptyComposeRule加三个唯一标题逐项performScrollTo，UiAutomator继续在原30秒内独立复验Success、Loading与Error；不登记attempt14

## 状态记录 · 2026-07-27T07:23:00Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：三个唯一标题逐项performScrollTo、回首项和UiAutomator三态复验已实现；72项R13候选治理回归、Android UI基础门禁与git diff检查通过，obx-test固定镜像app:compileDebugAndroidTestKotlin以212任务在1分26秒通过，本机与远端Kotlin SHA-256均为A8EDB9B969F19FD635CE8C228BC700810A601F4C62648F8DF42EE0BAC0731E20；一次性目录和容器已清理，attempt14尚未登记
