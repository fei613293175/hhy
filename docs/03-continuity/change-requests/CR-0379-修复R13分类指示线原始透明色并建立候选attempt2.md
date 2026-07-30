---
cr_id: CR-0379
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: owner-standing-delegation-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-26T20:26:50Z
updated_at: 2026-07-26T20:27:29Z
---
# CR-0379 — 修复R13分类指示线原始透明色并建立候选attempt2

## 用户需求摘要

项目所有者已批准持续开发、由AI自主处理候选失败并继续，不再逐轮索要批准。

## 原规则

R13分类页必须使用冻结Design Token，且大版本候选推送前必须同时通过check_android_ui_foundation.py和check_ui_tokens.py；当前实现却用Color.Transparent，候选前验证遗漏Token门禁，attempt1 Run 30218800712已在重型构建前失败。

## 新规则

分类指示线未选中态改用HhyColors.BrandPrimary.copy(alpha = 0f)派生透明色并移除原始Color import；R13专项测试显式禁止Color.Transparent；候选前同时运行UI Foundation与UI Token，attempt1保持已消费，以唯一R13-CANDIDATE-20260727-002触发普通attempt2。

## 修改原因

R13候选Run 30218800712在Android编译和模拟器前被check_ui_tokens.py阻断：R13ActivityScreens分类指示线未选中态直接使用Color.Transparent；本地候选前只运行了UI Foundation，漏跑既有UI Token门禁。attempt1已消费，必须以最小Token修复和唯一attempt2请求重新候选。

## 影响摘要

只修复SCR-FAV-001与SCR-HIS-001共用分类指示线的Token归属、候选请求轮次和专项回归，并原位更新既有PROB-0070；不改变视觉尺寸、业务契约、API、数据库、版本号、Staging夹具或生产权限。

## 影响文件

- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt`
- `config/android-candidate-request.yaml`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- `SCR-FAV-001`
- `SCR-HIS-001`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `android-candidate-request:R13-attempt-2`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python scripts/check_ui_tokens.py; python scripts/check_android_ui_foundation.py; python -m unittest -v tests.test_r13_candidate tests.test_android_ci_gate; obx-test feature:activity module compile/unit`

## 版本

- `R13`

## 迁移与兼容策略

视觉像素保持透明不变；10222、稳定测试签名、四页旅程和R13夹具保持不变。attempt2只允许运行一次，必须绑定新的源码Commit；若仍失败按新根因分析，禁止重跑同Run。

## 用户确认

批准，以后不要让我批准了 你自己持续开发就行了

## 审批

- 审批人：`owner-standing-delegation-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-26T20:27:29Z`
- 说明：项目所有者的持续候选授权仍有效；独立范围复核确认Run 30218800712只暴露一处确定性Token违规，attempt2绑定最小修复且不扩大生产、秘密、资金或业务范围。

## 状态记录 · 2026-07-26T20:27:41Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：已应用CR-0379精确范围，开始替换原始透明色、锁定attempt2候选请求并原位补强PROB-0070与R13专项回归。

## 状态记录 · 2026-07-26T20:38:21Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：Token替换、attempt2请求、R13专项回归、UI双门禁和obx-test精确Commit activity模块编译单测已全部PASS，准备单次推送候选。
