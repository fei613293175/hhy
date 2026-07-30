---
cr_id: CR-0118
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T03:14:44Z
updated_at: 2026-07-20T03:14:57Z
---
# CR-0118 — 归档R05冻结UI换版前APK交付事实

## 用户需求摘要

项目所有者要求立即推进R05冻结UI补充包开发并交付新测试APK。

## 原规则

R05-007当前只保留一份活动APK Manifest与交付Evidence。

## 新规则

冻结UI测试包替换旧R05测试包时，旧10208 Manifest与交付Evidence按旧提交短哈希归档，活动指针只指向10209。

## 修改原因

交付工具原子替换10208时自动保存旧Manifest和Evidence，必须纳入连续性范围避免丢失追溯。

## 影响摘要

新增两份只读历史交付事实，不改变当前APK、接口、页面或数据库。

## 影响文件

- `artifacts/apk/R05/history/3454ff2/APK_MANIFEST.yaml`
- `artifacts/validation/r05-apk-delivery/history/3454ff2/delivery-evidence.json`

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

- `旧Manifest和Evidence提交哈希、版本号、SHA一致性`

## 版本

- `R05`

## 迁移与兼容策略

旧10208仍可按历史证据追溯；桌面活动包和公网活动链接切换到10209。

## 用户确认

项目所有者要求立即推进并允许必要的交付收尾操作。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T03:14:57Z`
- 说明：批准交付工具的可恢复历史归档。

## 状态记录 · 2026-07-20T03:17:24Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：旧10208交付Manifest与Evidence已生成历史归档。

## 状态记录 · 2026-07-20T03:17:25Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：旧交付事实已按3454ff2归档，活动交付指针为10209。
