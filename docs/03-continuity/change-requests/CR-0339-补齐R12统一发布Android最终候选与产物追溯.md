---
cr_id: CR-0339
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: codex-independent-r12-candidate-reviewer
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-25T19:27:17Z
updated_at: 2026-07-25T19:35:04Z
---
# CR-0339 — 补齐R12统一发布Android最终候选与产物追溯

## 用户需求摘要

持续开发，终端静默执行，最终候选截图由AI自主判断并将APK与文档交付桌面

## 原规则

TASK-R12-007要求固定工具链构建签名、模拟器安装冒烟、R12全部Android页面截图、APK_MANIFEST和Commit/versionCode/SHA256追溯，但当前候选仍绑定R11团队长夹具、四页旅程、R11视觉清单和10220身份

## 新规则

将唯一Android候选入口切换为R12 attempt 1：在专用hhy-r12-ci-candidate环境以Flyway V041构造真实CI用户、实名认证、会员/奖励摘要及DRAFT/REVIEWING/REJECTED/ONLINE发布事实；通过OIDC自动登录依次采集SCR-PUB-001、SCR-PUB-006、SCR-PUB-007、SCR-MYC-001至005、SCR-ME-001、SCR-ME-002十页真实截图；versionCode单调递增为10221；完整门禁只在本次最终候选运行并由AI逐图审核

## 修改原因

当前唯一Android候选仍绑定R11团队长夹具、四页旅程和10220版本身份，R12十页候选合同尚未建立

## 影响摘要

仅修改R12候选测试、夹具、视觉清单、版本身份和候选请求，复用既有OIDC自动登录及唯一Android质量门禁；不改生产业务契约、V041迁移、R01-R11视觉基线或生产环境

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`
- `config/android-candidate-request.yaml`
- `scripts/prepare_r12_ci_fixture.sh`
- `tests/test_r12_candidate.py`
- `tests/test_android_ci_gate.py`
- `tests/android/visual-manifests/R12.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-PUB-001`
- `SCR-PUB-006`
- `SCR-PUB-007`
- `SCR-MYC-001`
- `SCR-MYC-002`
- `SCR-MYC-003`
- `SCR-MYC-004`
- `SCR-MYC-005`
- `SCR-ME-001`
- `SCR-ME-002`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `android-candidate-request:R12-attempt-1`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests.test_r12_candidate;tests.test_android_ci_gate;tests.test_android_candidate_request`
- `Android app unit/instrumentation compile;UI foundation;UI tokens`
- `R12 fixture idempotence;Flyway V041;ten-page visual manifest`

## 版本

- `R12`

## 迁移与兼容策略

R12夹具只允许专用hhy-r12-ci-candidate-*容器并强制Flyway V041；R11候选和基线保持归档只读；10221沿用稳定测试签名并支持覆盖安装；首次无R12基线按BASELINE_REVIEW_REQUIRED处理，AI审核合格后仅做轻量基线晋升，不重复模拟器

## 用户确认

项目所有者已明确授权持续开发、AI自主逐图判断截图质量并静默执行全部终端命令。

## 审批

- 审批人：`codex-independent-r12-candidate-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T19:35:04Z`
- 说明：独立复核确认TASK-R12-007范围完整：十个R12 Android页面、专用hhy-r12-ci-candidate容器与Flyway V041夹具、既有OIDC自动登录、10221单调版本身份、首次基线单次采集后轻量晋升均已覆盖；仅调整候选测试、夹具、视觉清单和版本元数据，不修改生产业务/API/数据库契约。
