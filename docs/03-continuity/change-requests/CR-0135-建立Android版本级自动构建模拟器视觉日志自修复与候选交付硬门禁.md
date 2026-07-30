---
cr_id: CR-0135
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T10:00:54Z
updated_at: 2026-07-20T10:01:10Z
---
# CR-0135 — 建立Android版本级自动构建模拟器视觉日志自修复与候选交付硬门禁

## 用户需求摘要

项目所有者要求以后每个安卓版本由GitHub Actions自动编译打包、模拟器安装、功能测试、页面截图、日志和回归；失败由AI自行修复重跑，全部通过前不得标记完成或要求真机测试

## 原规则

Android CI仅执行lintDebug、testDebugUnitTest和assembleDebug；真机测试可能在完整自动化之前发生

## 新规则

R06起每个Android候选版本必须先通过GitHub Actions编译签名、模拟器安装、功能旅程、页面截图、日志与崩溃扫描、视觉回归、APK身份和交付证据；失败必须形成机器可读修复队列并由接续AI修复重跑，全部PASS前禁止完成和禁止邀请项目所有者真机测试

## 修改原因

现有CI只执行lint、单元测试和assembleDebug，不能证明安装、运行、UI、日志和回归通过

## 影响摘要

增加Android自动化策略、可复用Actions工作流、模拟器冒烟、CI证据分析、候选交付门禁和跨AI接续规则；R05以已验收10213作为迁移基线，R06开始硬阻断

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `.github/workflows/ci.yml`
- `config/android-automation.yaml`
- `scripts/android_ci_gate.py`
- `tests/test_android_ci_gate.py`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `apps/android/app/build.gradle.kts`
- `apps/android/gradle/libs.versions.toml`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `docs/08-testing/测试策略与质量门禁_V1.2.2.md`
- `docs/09-development/统一开发与交付效率规范.md`
- `docs/00-baseline/正式商业系统全局硬性开发边界.md`
- `AGENTS.md`
- `templates/AGENTS.md`
- `releases/R05/RELEASE_MANIFEST.yaml`
- `scripts/check_release_artifacts.py`

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

- `python -m unittest tests.test_android_ci_gate`
- `python scripts/android_ci_gate.py policy-check`
- `GitHub Actions android-quality-gate YAML schema and dry-run fixture`
- `python scripts/check_release_artifacts.py --release R05`

## 版本

- `R05`

## 迁移与兼容策略

R05已由项目所有者真机通过，不追溯阻断；R06-R32及所有涉及APK的Bug候选强制执行。第三方活体、短信与真实相机采用模拟供应商自动回归加沙箱合同测试，最终候选再由项目所有者单次真机体验验收

## 用户确认

2026-07-20 当前任务原文：以后每个安卓版本完成后由GitHub Actions自动完成编译、APK打包、模拟器安装、功能测试、页面截图、日志检查和回归测试；失败由AI自行修复重测；全部通过前不得标记完成或要求用户真机测试

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T10:01:10Z`
- 说明：项目所有者在当前任务中明确要求立即建立该长期体系，并规定自动测试全绿前不得完成或要求真机测试

## 状态记录 · 2026-07-20T10:15:43Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：长期Android自动化策略、Actions、模拟器测试和机器门禁正在实现与验证

## 状态记录 · 2026-07-20T13:19:50Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：GitHub CI #244完整通过，相关实现与回归证据已验证

## 状态记录 · 2026-07-20T13:20:07Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：CI #244通过编译、Lint、单测、APK、模拟器旅程、四张截图、日志与候选资格门禁
