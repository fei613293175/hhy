---
cr_id: CR-0063
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-18T22:26:31Z
updated_at: 2026-07-18T22:26:33Z
---
# CR-0063 — 固化安全验证设计Token并修复主文档门禁路径

## 用户需求摘要

后续UI必须严格按效果图字号圆角等硬性参数且不遗漏

## 原规则

页面代码禁止原始dp/sp/颜色但安全验证冻结值尚无专用Token，主文档检查读取已迁移的旧路径

## 新规则

把安全验证精确尺寸、字体、颜色引用纳入全局Token派生，H5与Android只引用Token；主文档检查读取docs/00-baseline权威文件

## 修改原因

冻结弹层精确值仍以内联dp/sp/颜色存在并触发Token门禁；主文档门禁仍指向旧根目录文件名

## 影响摘要

消除页面原始视觉值并恢复UI Token和主文档机器门禁

## 影响文件

- `design/tokens/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `packages/design-tokens/h5.css`
- `packages/design-tokens/admin.css`
- `scripts/check_ui_tokens.py`
- `scripts/check_main_doc.py`
- `apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt`
- `apps/h5/src/views/InviteRegistrationPage.vue`
- `apps/admin-web/src/components/ProviderCertificatePanel.vue`

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

- `scripts/check_ui_tokens.py、scripts/check_main_doc.py、tests/test_ui_tokens.py、Android/H5/admin编译测试`

## 版本

- `R02安全验证码交互热修复`

## 迁移与兼容策略

视觉数值保持冻结包一致，不改变业务接口

## 用户确认

2026-07-19严格按效果图字号布局圆角参数开发

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-18T22:26:33Z`
- 说明：落实用户新增的全局UI参数硬边界并恢复现有门禁

## 状态记录 · 2026-07-18T22:26:35Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：开始Token化冻结安全验证视觉参数并修复文档门禁

## 状态记录 · 2026-07-18T22:34:42Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
