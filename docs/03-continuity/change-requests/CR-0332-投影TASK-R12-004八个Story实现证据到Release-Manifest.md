---
cr_id: CR-0332
status: APPROVED
requester_actor_id: codex-root-r12-client-20260725
approver_actor_id: codex-r12-release-reviewer-20260726
task_id: TASK-R12-004
session_id: SES-20260725T053515Z-11D4084D
created_at: 2026-07-25T17:02:17Z
updated_at: 2026-07-25T17:02:57Z
---
# CR-0332 — 投影TASK-R12-004八个Story实现证据到Release Manifest

## 用户需求摘要

按仓库事实源持续完成R12并仅在最终候选执行模拟器与APK。

## 原规则

R12 Release Manifest仅记录到TASK-R12-003后端门禁，TASK-R12-004八个Story的提交、模块证据和十一页IN_REVIEW边界尚未形成版本级证据投影。

## 新规则

在R12 Release Manifest新增task_004_client_gate：登记8/8 Story实现提交、11页实现PASS与视觉IN_REVIEW、后台113测试、后端415测试、Android 534任务及静态门禁；真实截图、AI视觉PASS、APK和安装冒烟继续明确延后到TASK-R12-007。

## 修改原因

八个Story已完成且模块证据已形成，需要把客户端任务门禁结论写入R12唯一Release Manifest；不改变任何业务合同。

## 影响摘要

新增TASK-R12-004客户端门禁报告、Manifest证据段和CHANGELOG摘要；不修改业务、接口、数据库、配置、UI实现或最终视觉状态。

## 影响文件

- `releases/R12/RELEASE_MANIFEST.yaml`
- `docs/03-continuity/R12_TASK-004_CLIENT_GATE.md`
- `CHANGELOG.md`

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

- `check_v122_documentation.py --strict --release R12`
- `check_release_artifacts.py --release R12`
- `check_program_execution_plan.py`
- `check_ui_visual_acceptance.py --release R12 --catalog-only`

## 版本

- `R12`

## 迁移与兼容策略

纯证据投影，无运行时、数据或配置迁移；十一页继续IN_REVIEW，兼容既有最终候选策略。

## 用户确认

项目所有者已授权按仓库R01-R32计划持续开发，要求大版本最终候选才运行完整门禁、模拟器和APK，常规治理决策无需暂停确认。

## 审批

- 审批人：`codex-r12-release-reviewer-20260726`
- 决定：`APPROVED`
- 时间：`2026-07-25T17:02:57Z`
- 说明：只读复核通过：变更仅把已提交的8个Story和既有MODULE证据投影到Release Manifest，十一页保持IN_REVIEW，未扩大接口、页面、数据或提前执行候选门禁。

## 状态记录 · 2026-07-25T17:03:27Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：TASK-R12-004门禁报告、Release Manifest和CHANGELOG投影已写入并通过四项声明门禁。

## 状态记录 · 2026-07-25T17:03:47Z

- Actor：`codex-root-r12-client-20260725`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T053515Z-11D4084D`
- Note：证据投影完成：严格R12文档、Release artifacts、31 Release执行计划、11页视觉目录与YAML解析全部PASS；未提前改变IN_REVIEW或执行候选APK。
