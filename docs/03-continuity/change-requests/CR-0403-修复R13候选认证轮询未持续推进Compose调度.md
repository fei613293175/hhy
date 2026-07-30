---
cr_id: CR-0403
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: project-owner-standing-authorization
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T11:53:16Z
updated_at: 2026-07-27T11:53:59Z
---
# CR-0403 — 修复R13候选认证轮询未持续推进Compose调度

## 用户需求摘要

持续推进R13且禁止原地循环；Attempt17失败后必须基于新指纹最小修复

## 原规则

CR-0401只在Activity首次可见后单次composeRule.waitForIdle，waitForAuthenticatedShell后续30秒仍仅用UiAutomator轮询

## 新规则

认证主框架轮询每轮必须先composeRule.waitForIdle再读取UiAutomator主框架与我的入口；网络完成后的Compose状态更新必须持续被测试调度器推进，原30秒总期限、可操作性和启动阻断诊断保持不变

## 修改原因

Attempt17 Run 30262304986编译、Lint、单测和打包PASS，模拟器精确失败为visibleBlockers=正在启动；一次性会话、GET /api/v1/me、平台状态和版本检查均200但无GET /api/v1/home。CR-0401仅在网络完成前单次waitForIdle，后续UiAutomator认证轮询未继续推进Compose测试调度

## 影响摘要

只修复R13 Android候选测试认证轮询调度，不修改生产UI、后端、登录、目标接口、媒体、四张截图或视觉标准，不授权下一候选轮次

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

AndroidTest专用同步修复；生产APK代码和服务端契约不变。Attempt17保持已消费，修复需通过候选回归、AndroidTest编译和后续独立候选验证

## 用户确认

项目所有者已要求持续开发、候选失败自行修复并禁止原地循环；当前修复产生新诊断与不同执行边界

## 审批

- 审批人：`project-owner-standing-authorization`
- 决定：`APPROVED`
- 时间：`2026-07-27T11:53:59Z`
- 说明：批准按Attempt17明确的正在启动指纹把Compose同步移入认证轮询；不得延长30秒、绕过启动登录、放宽页面媒体截图视觉门禁或预授权Attempt18

## 状态记录 · 2026-07-27T11:54:25Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始把Compose调度同步移入认证主框架轮询，并原位记录Attempt17正在启动指纹；候选轮次配置保持不变

## 状态记录 · 2026-07-27T12:01:36Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：认证主框架轮询每轮Compose同步及Attempt17正在启动取证已实现；R13/共享候选40项、治理知识17项、Android UI基础门禁及obx-test固定镜像AndroidTest编译212任务全部PASS；源SHA256 673872CD8B9BD2604F9FD3E927802666CFCA2915C48712D6689F2654A526F1C7，构建日志SHA256 483BFD6A267DD3F3842EFDE734AAAA272E9FC44FBA782226B3BFC8AF6F857668；Attempt18仍未授权
