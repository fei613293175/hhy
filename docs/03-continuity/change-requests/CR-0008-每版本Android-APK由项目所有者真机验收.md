---
cr_id: CR-0008
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-P00-005
session_id: SES-20260717T024407Z-B03C9375
created_at: 2026-07-17T04:09:43Z
updated_at: 2026-07-17T04:11:58Z
---
# CR-0008 — 每版本Android APK由项目所有者真机验收

## 用户需求摘要

如果打包了apk安装包，可以保留到电脑桌面，我真机测试，后续每个版本的apk均由我真机测试

## 原规则

Android版本要求可下载、可安装并执行安装冒烟，但未固定必须由谁在真机上验收，也未要求保留桌面副本

## 新规则

每个标记Android测试APK=YES的版本由Codex完成构建、签名、哈希、下载与桌面副本交付；项目所有者在真机执行安装和启动验收，只有收到其明确通过结果后APK安装门禁才可标记PASS并关闭版本

## 修改原因

把项目所有者真机验收设为每个Android版本关闭前的正式门禁，避免模拟器或构建成功被误报为真机通过

## 影响摘要

仅强化所有Android版本的验收责任和证据边界；P00桌面APK已交付且真机结果待项目所有者回传，不改变产品功能、API、数据库、版本号或签名策略

## 影响文件

- `docs/05-app-build/APK持续交付强制规则_V1.2.2.md`
- `PENDING_USER_ACTIONS.md`
- `releases/P00/TASKS.yaml`

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

- `python scripts/check_v123_documentation.py --strict --release P00`

## 版本

- `P00`

## 迁移与兼容策略

从P00立即执行；既有自动构建、签名、单测和下载证据继续有效，但不能替代项目所有者真机结论；Android测试APK=NO的版本不适用

## 用户确认

如果打包了apk安装包，可以保留到电脑桌面，我真机测试，后续每个版本的apk均由我真机测试

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T04:11:58Z`
- 说明：仅强化Android测试APK=YES版本的桌面交付、项目所有者真机验收与证据门禁；不改变产品功能、API、数据库、版本号或签名策略

## 状态记录 · 2026-07-17T04:14:32Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：按批准合同写入全局APK持续交付规则、P00真机验收门禁和项目所有者待办

## 状态记录 · 2026-07-17T04:19:49Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：真机验收长期规则、P00任务门禁和严格文档验证已落地

## 状态记录 · 2026-07-17T04:19:50Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：独立审批合同已由f34af6a实现；P00具体真机结果仍按TASK-P00-007等待项目所有者反馈
