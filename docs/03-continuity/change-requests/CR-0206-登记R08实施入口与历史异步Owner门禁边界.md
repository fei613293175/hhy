---
cr_id: CR-0206
status: APPROVED
requester_actor_id: codex-root-r08-001
approver_actor_id: codex-reviewer-r08-entry
task_id: TASK-R08-001
session_id: SES-20260722T002444Z-8BEC3CA6
created_at: 2026-07-22T00:29:16Z
updated_at: 2026-07-22T00:31:55Z
---
# CR-0206 — 登记R08实施入口与历史异步Owner门禁边界

## 用户需求摘要

按仓库R02至R32计划持续开发；历史版本真机反馈为异步输入，不得阻断R08推进；每版候选APK与交付文档放桌面

## 原规则

R08仅为READY_WHEN_DEPENDENCIES_GREEN，尚未登记R05至R07机器完成但owner真机异步PENDING的合法依赖状态、当前实施Session和逐版候选交付规则。

## 新规则

登记R08实施入口：R05至R07机器开发和交付事实可追溯，R06与R07最后关闭任务仅因owner异步真机PENDING挂起且不阻断R08；R08四个故事按TASK-R08-001至008顺序实施，完整门禁和模拟器仅在最终候选阶段执行，候选APK与交付文档放桌面。

## 修改原因

R08 Release Manifest需要登记当前Session、R05至R07机器完成与异步owner门禁边界、四个故事就绪事实及最终候选交付规则

## 影响摘要

只更新R08治理基线和就绪核验记录，不改变冻结页面、接口、数据库、配置或业务语义。

## 影响文件

- `releases/R08/RELEASE_MANIFEST.yaml`
- `docs/03-continuity/R08_TASK-001_ENTRY_GATE.md`

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

- `python scripts/check_v122_documentation.py --release R08`
- `python scripts/check_release_artifacts.py --release R08`
- `python scripts/check_api_contract.py`
- `python scripts/check_program_execution_plan.py`

## 版本

- `R08`

## 迁移与兼容策略

无数据迁移；保留历史Release正式验收和生产激活阻断，R08开发独立推进并继续执行自身最终候选与owner异步真机验收。

## 用户确认

项目所有者明确要求持续推进、真机反馈异步、不得因此停止，候选APK和文档放桌面。

## 审批

- 审批人：`codex-reviewer-r08-entry`
- 决定：`APPROVED`
- 时间：`2026-07-22T00:31:55Z`
- 说明：R08入口只登记已验证历史机器事实、owner异步边界和既定逐版候选策略，不修改产品冻结契约。

## 状态记录 · 2026-07-22T00:32:57Z

- Actor：`codex-root-r08-001`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T002444Z-8BEC3CA6`
- Note：开始写入R08入口Manifest与开发就绪核验记录
