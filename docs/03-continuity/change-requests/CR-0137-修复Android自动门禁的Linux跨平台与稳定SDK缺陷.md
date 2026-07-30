---
cr_id: CR-0137
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T11:25:47Z
updated_at: 2026-07-20T11:26:40Z
---
# CR-0137 — 修复Android自动门禁的Linux跨平台与稳定SDK缺陷

## 用户需求摘要

发现编译错误、崩溃、测试失败或明显UI偏差后必须自行分析、修改、重新打包和重新测试，自动测试全部通过前不得标记完成或要求真机测试

## 原规则

自动门禁默认沿用Windows工作区换行和文件模式，Android编译SDK为API 37，GitHub未显式关闭中文路径转义

## 新规则

所有自动门禁在Linux干净克隆中可复现；Git路径不转义；Hook保留可执行位；冻结原始证据按二进制保存；Context来源换行稳定；Android统一稳定API 36

## 修改原因

GitHub首轮证据确认中文路径转义、Hook执行位、CRLF冻结证据、Context Pack换行及Android API 37预览依赖导致自动门禁失败

## 影响摘要

修复GitHub首轮工具、连续性、Android构建失败并增加跨平台回归

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `.github/workflows/ci.yml`
- `.github/workflows/continuity-gate.yml`
- `.gitattributes`
- `.githooks/*`
- `apps/android/**/build.gradle.kts`
- `docs/04-vendors/identity/source/输出内容(1).txt`
- `scripts/continuity_lib.py`
- `scripts/check_v123_continuity.py`
- `tests/test_continuity_bootstrap_recovery.py`
- `tests/test_continuity_cross_release_close.py`
- `tests/test_r05_identity_vendor_contract.py`
- `tests/test_android_ci_gate.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android compileSdk=36; Git core.quotepath=false; Hook executable mode`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Linux clean-clone continuity/project-doctor/full unittest; Android Gradle compile/lint/test/package; GitHub Actions rerun`

## 版本

- `R05`

## 迁移与兼容策略

不改变业务功能和R05已通过真机体验；仅迁移构建SDK、仓库元数据和测试夹具的跨平台表示

## 用户确认

用户要求发现任何自动测试失败后必须自行分析、修改、重新打包和重新测试

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T11:26:40Z`
- 说明：项目所有者当前任务已明确授权自动分析修复重打包重测，当前变更为首轮CI失败的必要自修复

## 状态记录 · 2026-07-20T11:26:43Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：开始修复Linux跨平台和Android稳定SDK失败

## 状态记录 · 2026-07-20T11:27:32Z

- Actor：`codex-root`
- Status：`SUPERSEDED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：影响文件含通配符不满足精确路径门禁，改由精确范围CR替代
