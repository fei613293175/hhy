---
cr_id: CR-0241
status: APPROVED
requester_actor_id: codex-root-r08-008
approver_actor_id: codex-reviewer-user-self-r08
task_id: TASK-R08-008
session_id: SES-20260722T093645Z-C7DB8EF0
created_at: 2026-07-22T13:24:45Z
updated_at: 2026-07-22T13:25:00Z
---
# CR-0241 — 隔离GitHub模拟器Pixel Launcher系统ANR并补齐R02认证视觉证据

## 用户需求摘要

项目所有者要求GitHub不得反复浪费时间，自动截图由AI独立判断，发现确定性根因后修复并持续推进。

## 原规则

沿用既有候选最多3次有界修复、系统弹层不得进入产品视觉证据、不得将环境噪声误判为业务失败的规则。

## 新规则

不新增平行规则；候选测试在每次截图前检测并点击系统ANR的Wait/等待动作，等待弹层消失后才采图；认证入口使用冻结完整文案，媒体底部面板使用系统返回关闭；只在本地androidTest编译通过后将同一修复候选推进到attempt 2。

## 修改原因

Run 29922189010证明产品构建与OIDC通过，但Pixel Launcher系统ANR对话框覆盖认证截图并阻断Compose点击；另有媒体面板取消按钮因长屏滚动不可见。必须在截图与操作前显式关闭系统ANR噪声，并用系统返回关闭底部面板。

## 影响摘要

仅修正Android候选测试对GitHub模拟器系统ANR与长屏底部操作的稳定性；不修改生产UI、接口、数据库或业务状态。

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/HistoricalVisualAuditTest.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `config/android-candidate-request.yaml`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-AUTH-001;SCR-AUTH-002;SCR-AUTH-003;SCR-AUTH-004;SCR-AUTH-007;SCR-AUTH-008;SHEET-MEDIA-001`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R08 candidate remediation attempt 2`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Android androidTest compile; static ANR guard regression; GitHub candidate attempt 2; AI screenshot review`

## 版本

- `R08`

## 迁移与兼容策略

纯androidTest和候选请求变更；产品Release代码与业务数据零变化，Run 29922189010失败证据保留并登记。

## 用户确认

CONFIRMED_BY_OWNER_NO_REPEATED_GITHUB_WASTE_AND_AI_VISUAL_REVIEW_RULE

## 审批

- 审批人：`codex-reviewer-user-self-r08`
- 决定：`APPROVED`
- 时间：`2026-07-22T13:25:00Z`
- 说明：批准只修复Run 29922189010已证明的系统ANR覆盖与不可见取消按钮，不扩展产品功能；本地门禁通过后允许一次attempt 2。

## 状态记录 · 2026-07-22T13:25:15Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：开始增加系统ANR排除断言、认证完整文案点击和媒体面板稳定关闭。

## 状态记录 · 2026-07-22T14:07:22Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：本地候选门禁21项通过；obx-test固定API36工具链在单工作线程、1.5GiB堆和进程内Kotlin编译策略下完成compileDebugAndroidTestKotlin，147项0失败；允许推送attempt 2。
