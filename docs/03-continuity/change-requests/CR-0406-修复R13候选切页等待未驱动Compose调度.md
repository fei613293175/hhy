---
cr_id: CR-0406
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-reviewer-r13-candidate-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T12:46:12Z
updated_at: 2026-07-27T12:46:38Z
---
# CR-0406 — 修复R13候选切页等待未驱动Compose调度

## 用户需求摘要

R13版本必须完成桌面交付并持续推进，不得原地循环

## 原规则

R13候选waitForScreen只调用UiDevice.wait，Compose同步仅在waitForAuthenticatedShell执行；Attempt18点击shell.navigation.me后hhy.screen.r12.me未出现

## 新规则

候选所有页面转换等待必须在限定时窗内持续调用Compose测试规则同步，并同时等待目标marker出现及来源marker消失；失败必须输出required/gone可见性诊断，页面、登录、接口、媒体、四张截图和视觉门禁保持不变

## 修改原因

Attempt18已经进入认证主框架，但点击我的后waitForScreen仅轮询UiAutomator，未像认证入口一样驱动Compose调度，导致hhy.screen.r12.me未出现且四张截图未生成

## 影响摘要

仅修复ReleaseCandidateSmokeTest页面转换同步与诊断，并记录Attempt18运行事实；不修改业务功能、正式UI、API、数据库或候选门槛

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
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

- `R13 waitForScreen Compose同步源码回归`
- `Android UI基础门禁`
- `R13候选治理回归`
- `obx-test AndroidTest编译`

## 版本

- `R13`

## 迁移与兼容策略

纯androidTest兼容修复；正式APK运行时代码和数据契约不变，旧候选记录保持不可变，Attempt18不得重跑

## 用户确认

项目所有者已长期授权持续开发并要求R13完成桌面交付，不逐次审批修复

## 审批

- 审批人：`codex-reviewer-r13-candidate-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T12:46:38Z`
- 说明：Attempt18日志明确为R13 me home did not become visible，修复边界仅为候选测试同步与诊断，未降低任何验收门槛

## 状态记录 · 2026-07-27T12:58:43Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：开始实施已批准的R13候选页面转换Compose同步修复

## 状态记录 · 2026-07-27T12:58:47Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：首个修复Commit 11b601b2已通过84项候选与共享治理回归、Android UI基础门禁、git diff检查及obx-test固定镜像app:compileDebugAndroidTestKotlin；BUILD SUCCESSFUL in 1m36s，212 tasks，本机/远端源码SHA-256均为A2E0D4C8E0F9C10B2BCF69583615BA1929B02562FA37BF292D28AC1302273EA1；Attempt18不重跑，Attempt19尚未授权
