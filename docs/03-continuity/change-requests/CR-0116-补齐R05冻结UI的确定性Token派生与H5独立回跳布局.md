---
cr_id: CR-0116
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T02:41:04Z
updated_at: 2026-07-20T02:41:25Z
---
# CR-0116 — 补齐R05冻结UI的确定性Token派生与H5独立回跳布局

## 用户需求摘要

项目所有者已提供R05冻结视觉补充包并要求立即落地开发。

## 原规则

冻结UI仅登记页面实现文件，Token新增值、派生资产、生成器、H5全局壳层例外、截图和接收报告未纳入精确影响范围。

## 新规则

R05冻结UI新增值必须进入V1.2.2权威Token并由check_ui_tokens.py确定性派生到Android与H5/Admin CSS；H5-012回跳页不显示通用公开站头尾；包接收哈希、浏览器截图与验证报告进入R05证据目录。

## 修改原因

CR-0115已批准业务与视觉范围，但实际落地还需同步生成Token资产、Token生成器、H5全局壳层例外、测试与截图报告文件。

## 影响摘要

补齐冻结UI实现所需全部派生文件和证据，避免手工Token漂移并确保H5回跳页面结构与冻结稿一致。

## 影响文件

- `design/tokens/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `packages/design-tokens/h5.css`
- `packages/design-tokens/admin.css`
- `scripts/check_ui_tokens.py`
- `apps/h5/src/App.vue`
- `apps/h5/src/styles.css`
- `apps/h5/src/views/IdentityCallbackPage.test.ts`
- `artifacts/validation/r05-ui/H5-012-browser-360x800.png`
- `artifacts/reports/R05/R05-UI-FROZEN-PACKAGE-INTAKE.md`

## 页面

- `SCR-ID-001`
- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`
- `H5-012`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `check_ui_tokens.py；check_generated_assets.py；H5 22 tests/typecheck/360x800 screenshot；Android identity compile/test`

## 版本

- `R05`

## 迁移与兼容策略

纯UI与确定性生成资产同步；不修改API、数据库、认证状态机、权限或生产配置。通用H5头尾仅在H5-012隐藏，其余页面保持原行为。

## 用户确认

2026-07-20项目所有者提供已完善冻结版补充包并明确要求立即推进开发。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T02:41:25Z`
- 说明：这是已批准R05冻结UI补充包落地所必需的确定性派生、H5壳层和证据范围，不扩展业务功能。
