---
cr_id: CR-0401
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: project-owner-standing-authorization
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T11:06:16Z
updated_at: 2026-07-27T11:06:57Z
---
# CR-0401 — 修复R13候选Compose测试规则阻塞主框架调度

## 用户需求摘要

持续推进R13且禁止原地循环；候选失败后必须产生新证据或实际修复

## 原规则

CR-0395引入createEmptyComposeRule以按标题执行performScrollTo，但启动后仍只用UiAutomator等待认证主框架，未同步Compose测试调度器；CR-0399随后把Attempt15错误归因为入口资源判断

## 新规则

保留三条真实媒体与按标题performScrollTo；测试启动目标Activity并确认窗口可见后必须先执行composeRule.waitForIdle，再进入UiAutomator认证主框架门禁。Attempt16及服务端时序作为新事实，Attempt15入口误判归因原位更正

## 修改原因

Attempt16 Run 30259083711服务端会话兑换、用户自检和启动门禁均200，但加入createEmptyComposeRule后的Attempt14至16均未触发首页请求；需同步Compose测试调度器并原位纠正Attempt15错误归因

## 影响摘要

只修复R13 Android候选测试调度与失败追溯，不放宽登录、启动门禁、接口、媒体、四张截图或视觉标准，不修改生产UI和后端

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `CHANGELOG.md`

## 页面

- `R13收藏/历史/分享/反馈候选旅程`

## API

- `登录与R13目标API保持真实验证`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `不新增候选轮次，不修改android-candidate-request`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests.test_r13_candidate;tests.test_android_ci_gate;obx-test app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

AndroidTest专用同步修复；生产APK代码和服务端契约不变。旧Attempt14至16保持已消费，修复需通过候选回归、AndroidTest编译和后续独立候选验证

## 用户确认

项目所有者已要求AI持续开发、候选失败自行修复并禁止原地循环；当前Goal要求每轮产生实际代码变化或新证据

## 审批

- 审批人：`project-owner-standing-authorization`
- 决定：`APPROVED`
- 时间：`2026-07-27T11:06:57Z`
- 说明：批准按Attempt13与Attempt14至16的服务端时序差异修复AndroidTest调度同步，并原位更正CR-0399错误归因；不得放宽认证、页面、媒体、截图或视觉门禁，不授权Attempt17

## 状态记录 · 2026-07-27T11:07:12Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始修复Activity启动后的Compose测试调度同步，并补Attempt16失败取证与归因回归；不修改候选轮次配置

## 状态记录 · 2026-07-27T11:18:59Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：Activity可见后显式composeRule.waitForIdle并扩展启动阻断诊断已实现；R13/共享候选40项、候选历史28项、治理知识17项、Android UI基础门禁及obx-test固定镜像AndroidTest编译212任务全部PASS；源SHA256 11F55B856A44E6A9231E53BC9C2A9EA3C6076A070865782C5FE881C53FB3B38E，构建日志SHA256 7CFCEAC07D7ED90A4E0C4E9757FDCD7998E32EC29743867BF8D74544F5C80F16；Attempt17仍未授权
