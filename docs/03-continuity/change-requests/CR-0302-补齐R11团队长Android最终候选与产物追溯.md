---
cr_id: CR-0302
status: APPROVED
requester_actor_id: codex-root-r11-candidate
approver_actor_id: codex-reviewer-r11-candidate
task_id: TASK-R11-007
session_id: SES-20260724T032308Z-98D6D10A
created_at: 2026-07-24T03:29:21Z
updated_at: 2026-07-24T03:30:08Z
---
# CR-0302 — 补齐R11团队长Android最终候选与产物追溯

## 用户需求摘要

继续开发并静默执行全部终端命令，最终候选由AI自主审核后交付桌面

## 原规则

TASK-R11-007要求固定工具链构建签名、模拟器安装冒烟、R11页面截图、APK_MANIFEST和Commit/versionCode/SHA256追溯，但现有候选仍绑定R10群聊旅程与请求

## 新规则

将唯一Android候选入口切换为R11 attempt 1：隔离Staging准备V038完整实名团队长资料与真实Logo媒体、首页至团队长列表/详情/编辑四页旅程、精确视觉清单和静态回归；versionCode递增到10220，完整门禁只运行一次并由AI自主审核

## 修改原因

R11候选请求、隔离数据夹具、四页模拟器旅程、视觉清单、静态回归和单调版本号尚未从R10切换至R11

## 影响摘要

仅修改R11候选测试与身份文件，复用既有OIDC自动登录和唯一质量门禁，不改生产业务契约、数据库迁移或R01-R10视觉基线

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `apps/android/app/build.gradle.kts`
- `config/android-candidate-request.yaml`
- `scripts/prepare_r11_ci_fixture.sh`
- `tests/test_r11_candidate.py`
- `tests/test_android_ci_gate.py`
- `tests/android/visual-manifests/R11.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-HOME-001`
- `SCR-LIST-004`
- `SCR-DETAIL-004`
- `SCR-PUB-005`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `android-candidate-request:R11-attempt-1`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests.test_r11_candidate;tests.test_android_ci_gate;tests.test_android_candidate_request;Android app unit/instrumentation compile;UI foundation`

## 版本

- `R11`

## 迁移与兼容策略

测试夹具只允许专用R11 Staging候选容器并要求Flyway V038；既有R10候选证据保持只读，R11请求使用新的唯一request_id且不影响普通candidate=false提交

## 用户确认

项目所有者已明确要求继续开发、AI自主审核截图并静默执行终端命令

## 审批

- 审批人：`codex-reviewer-r11-candidate`
- 决定：`APPROVED`
- 时间：`2026-07-24T03:30:08Z`
- 说明：范围逐文件明确，R11夹具强制V038与专用容器，沿用唯一OIDC和候选工作流；四页旅程、视觉合同、静态回归及10220版本身份必须一起通过后才可推送候选

## 状态记录 · 2026-07-24T04:11:31Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTING`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：R11候选夹具、四页旅程、视觉清单、请求身份和静态回归已实施并通过本地及云端定向验证

## 状态记录 · 2026-07-24T04:12:05Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTED`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：R11候选实现完成；28项定向回归、UI基础与Token门禁、云端MODULE、夹具双次幂等及媒体200均通过
