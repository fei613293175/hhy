---
cr_id: CR-0138
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T11:27:34Z
updated_at: 2026-07-20T11:27:58Z
---
# CR-0138 — 精确修复Android自动门禁Linux与稳定SDK缺陷

## 用户需求摘要

发现自动测试失败后必须自行分析修改重打包重测，全部通过前不得完成或要求真机测试

## 原规则

GitHub工作流未显式配置中文路径和Linux Hook模式，Android compileSdk为API 37，原始供应商证据被文本换行过滤

## 新规则

Linux干净克隆必须可通过；Git core.quotepath=false；四个Hook保留100755；供应商原始证据按二进制；Android compileSdk统一为稳定API 36

## 修改原因

CR-0137通配符范围无法应用，需以精确文件清单修复首轮CI失败

## 影响摘要

修复连续性、全量测试和Android构建三类首轮CI失败

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `.github/workflows/ci.yml`
- `.github/workflows/continuity-gate.yml`
- `.gitattributes`
- `.githooks/commit-msg`
- `.githooks/pre-commit`
- `.githooks/pre-push`
- `.githooks/prepare-commit-msg`
- `apps/android/build.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/core/designsystem/build.gradle.kts`
- `apps/android/core/network/build.gradle.kts`
- `apps/android/feature/auth/build.gradle.kts`
- `apps/android/feature/identity/build.gradle.kts`
- `apps/android/feature/media/build.gradle.kts`
- `apps/android/feature/shell/build.gradle.kts`
- `apps/android/feature/startup/build.gradle.kts`
- `docs/04-vendors/identity/source/输出内容(1).txt`
- `tests/test_android_ci_gate.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android compileSdk=36; Git core.quotepath=false; Hook executable mode; frozen evidence binary`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Linux clean-clone continuity/project-doctor/full unittest; Android Gradle compile/lint/test/package; GitHub Actions rerun`

## 版本

- `R05`

## 迁移与兼容策略

不改业务功能；R05真机结果继续有效；R06起使用稳定API 36自动门禁

## 用户确认

本任务原文要求发现编译错误、崩溃、测试失败或明显UI偏差后自行分析修改重新打包重新测试

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T11:27:58Z`
- 说明：用户明确要求自动测试失败必须自行修复重测

## 状态记录 · 2026-07-20T11:28:01Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：按精确文件范围实施Linux与稳定SDK修复

## 状态记录 · 2026-07-20T13:19:54Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：GitHub CI #244完整通过，相关实现与回归证据已验证

## 状态记录 · 2026-07-20T13:20:11Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：CI #244通过编译、Lint、单测、APK、模拟器旅程、四张截图、日志与候选资格门禁
