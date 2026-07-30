---
cr_id: CR-0069
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-19T02:32:20Z
updated_at: 2026-07-19T02:32:40Z
---
# CR-0069 — 允许同一版本热修复APK使用更高单调versionCode

## 用户需求摘要

验证码修复后生成可覆盖安装的桌面APK

## 原规则

交付versionCode必须精确等于10200加Release序号

## 新规则

交付versionCode不得低于Release基线，允许同一Release热修复使用更高单调值

## 修改原因

交付工具精确等于版本基线会拒绝同一大版本的覆盖安装热修复包

## 影响摘要

交付门禁继续拒绝低版本，同时允许R03的10204覆盖安装包

## 影响文件

- `scripts/deliver_android_test_apk.py`
- `tests/test_android_apk_delivery.py`

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

- `tests.test_android_apk_delivery versionCode minimum and hotfix cases`

## 版本

- `R03`

## 迁移与兼容策略

R02基线10202、R03基线10203保持；仅放宽高于基线，不接受布尔或低值

## 用户确认

用户要求持续修复并交付可覆盖安装APK

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T02:32:40Z`
- 说明：单调覆盖安装必须允许热修复高版本码，低于版本基线仍严格拒绝

## 状态记录 · 2026-07-19T02:32:42Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：更新交付版本码门禁并补回归测试

## 状态记录 · 2026-07-19T02:41:38Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：交付工具允许高于Release基线的单调热修复版本码，14项回归及10204交付通过

## 状态记录 · 2026-07-19T02:41:43Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：热修复版本码下限门禁与14项交付工具回归通过
