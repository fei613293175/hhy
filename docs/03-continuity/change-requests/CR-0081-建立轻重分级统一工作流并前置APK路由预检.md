---
cr_id: CR-0081
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-19T05:37:09Z
updated_at: 2026-07-19T05:37:39Z
---
# CR-0081 — 建立轻重分级统一工作流并前置APK路由预检

## 用户需求摘要

项目所有者要求立即落地跨电脑跨AI高效开发方案，并明确简单开发和Bug不得复杂化、不得用大炮打蚊子

## 原规则

FAST、MODULE、INTEGRATION、RELEASE虽已存在，但缺少按任务风险自动选择的统一入口；简单Bug可能被人工升级为完整发布流程，APK精确下载路由也未在构建发布前验证

## 新规则

统一入口必须先按影响范围和高风险触发器选择SIMPLE、STANDARD、CROSS_LAYER、TEST_APK或RELEASE_CLOSE；简单单模块任务默认只跑受影响FAST检查，不得无理由升级；升级必须给出机器可读原因。APK在构建发布前必须独立校验精确Nginx路由，所有阶段记录耗时并支持从失败节点继续

## 修改原因

把现有分层测试、运行时发现、任务复杂度和APK交付预检整合为可执行且可恢复的仓库事实源

## 影响摘要

新增轻量工作流配置和薄编排器，复用既有影响测试引擎；增强跨电脑工具发现和APK路由前置预检，不改变现有正式发布安全门禁

## 影响文件

- `AGENTS.md`
- `CHANGELOG.md`
- `config/development-workflow.yaml`
- `config/test-impact-map.yaml`
- `scripts/hhy_workflow.py`
- `scripts/run_affected_tests.py`
- `scripts/deliver_android_test_apk.py`
- `tests/test_hhy_workflow.py`
- `tests/test_run_affected_tests.py`
- `tests/test_android_apk_delivery.py`
- `docs/09-development/统一开发与交付效率规范.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

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

- `python -m unittest tests.test_hhy_workflow tests.test_run_affected_tests tests.test_android_apk_delivery`
- `python scripts/hhy_workflow.py plan --intent bugfix --changed-file apps/h5/src/views/InviteRegistrationPage.vue`
- `python scripts/deliver_android_test_apk.py preflight-route --help`

## 版本

- `R03`

## 迁移与兼容策略

现有Make目标和deliver prepare/verify/accept保持兼容；新入口为增量能力，旧命令仍可使用；未指定高风险触发器的简单任务不会自动执行全量集成或版本关闭

## 用户确认

立即开始落地这个方案；简单开发和Bug修复不要复杂化，不要用大炮打蚊子

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T05:37:39Z`
- 说明：项目所有者明确要求立即落地，并要求简单任务保持轻量灵活

## 状态记录 · 2026-07-19T06:01:03Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：开始实现统一轻重分级入口、断点复用和APK路由前置预检

## 状态记录 · 2026-07-19T06:01:05Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：统一轻重分级入口、跨电脑运行时注入、断点复用和APK精确路由预检均已实现；针对性40项与项目Python 129项回归通过，当前线上c257aee路由预检PASS
