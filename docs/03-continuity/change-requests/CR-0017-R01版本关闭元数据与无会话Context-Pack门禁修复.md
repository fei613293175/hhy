---
cr_id: CR-0017
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: r01-cr-review
task_id: TASK-R01-008
session_id: SES-20260717T171412Z-7CD86701
created_at: 2026-07-17T17:22:49Z
updated_at: 2026-07-17T17:25:50Z
---
# CR-0017 — R01版本关闭元数据与无会话Context Pack门禁修复

## 用户需求摘要

项目所有者要求立即完成R01版本收尾并暂停

## 原规则

R01 Release Manifest保持IN_PROGRESS，关闭验收矩阵未关联各任务的具体证据；无会话Context Pack把STATE.yaml计入仓库树指纹。

## 新规则

R01关闭时以既有8个接口中的3个代表operationId绑定Release Commit与Tag，验收矩阵引用既有报告；Context Pack仓库树指纹排除运行时STATE.yaml。

## 修改原因

将已完成的R01验收证据、APK来源提交和发布标签写入终态元数据，并修复无会话Context Pack生成后立即被判过期的关闭门禁缺陷。

## 影响摘要

仅变更R01关闭元数据、证据指针、连续性运行时门禁和其隔离回归；不增加、删除或改变页面、API、数据库、配置、资金或业务语义。

## 影响文件

- `releases/R01/RELEASE_MANIFEST.yaml`
- `releases/R01/ACCEPTANCE_MATRIX.csv`
- `scripts/continuity_lib.py`
- `tests/test_continuity_cross_release_close.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `R01既有8个admin API路径不变`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `无配置语义变更`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests/test_continuity_cross_release_close.py`
- `tests/test_release_close_gate.py`

## 版本

- `R01`

## 迁移与兼容策略

无需数据库迁移或消费者迁移。保留原有8条API路径；仅将Manifest中的表示方式改为带operationId和paths的结构。旧Context Pack可重新生成。

## 用户确认

项目所有者于2026-07-18明确要求立即完成R01收尾并暂停；此前已确认R01 APK真机安装与启动通过。

## 审批

- 审批人：`r01-cr-review`
- 决定：`APPROVED`
- 时间：`2026-07-17T17:25:50Z`
- 说明：独立审阅：实际差异仅含R01关闭元数据、验收证据指针、Context Pack运行时指纹排除与隔离回归；8条既有admin API路径逐项与HEAD基线一致，且首3个operationId与contracts/admin-openapi.yaml一致。未变更页面、API、数据库、配置、资金或业务语义；tests.test_continuity_cross_release_close与tests.test_release_close_gate共6项通过。
