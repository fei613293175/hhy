---
cr_id: CR-0243
status: APPROVED
requester_actor_id: codex-root-r08-008
approver_actor_id: codex-reviewer-r08-close-order
task_id: TASK-R08-008
session_id: SES-20260722T093645Z-C7DB8EF0
created_at: 2026-07-22T14:59:51Z
updated_at: 2026-07-22T15:00:32Z
---
# CR-0243 — 使用已通过候选完成R08机器关闭并登记Staging重启漂移

## 用户需求摘要

项目所有者明确要求先把R08全面收尾，再开展过往至当前全部前端UI全局审计与返工；GitHub不得反复浪费时间。

## 原规则

沿用Release关闭既有规则：Android候选身份以android_delivery.source_commit、APK Manifest、候选与晋升报告一致为准，候选提交允许早于后续证据/文档关闭提交；owner_physical_test保持PENDING但不阻断下一阶段开发。瞬时重试最多一次，重复失败必须停止并诊断基础设施。

## 新规则

不新增平行规则。R08以已通过的49f40f2候选、Run 29905158793采集、Run 29906167593轻量晋升、三页AI基线和桌面APK完成机器关闭；Run 29929926432保留为当前HEAD构建通过及Staging 502失败证据，不再重跑。服务器专用CI容器恢复后公网状态200，历史UI仍IN_REVIEW并在R08关闭后立即独立整治。

## 修改原因

R08候选Run 29906167593及同源晋升已PASS并交付桌面APK；当前HEAD Run 29929926432的构建/lint/单测/打包通过，模拟器仅因服务器重启后专用CI容器未自启导致公网502，受控重试同因失败后已按规则停止并恢复服务器。

## 影响摘要

补齐TASK-R08-008机器关闭报告、验收矩阵、Release Manifest、任务状态、问题登记与无状态交接；诚实区分已通过产品候选、后续历史UI提交和外部Staging漂移。

## 影响文件

- `artifacts/reports/R08/TASK-R08-008-machine-close.md`
- `releases/R08/ACCEPTANCE_MATRIX.csv`
- `releases/R08/RELEASE_MANIFEST.yaml`
- `releases/R08/TASKS.yaml`
- `CURRENT_STATUS.yaml`
- `NEXT_TASK.yaml`
- `catalogs/task_transition_ledger.csv`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-LIST-001;SCR-DETAIL-001;SCR-PUB-002`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `api.orbexa.cc dedicated CI upstream runtime status`

## 资金/账本与历史数据

- `TASK-R08-008 completion and R08 machine close`

## 测试

- `R08 candidate evidence consistency; release artifact close gate; strict continuity; desktop APK SHA; public API status 200; GitHub run 29929926432 failure log root cause review`

## 版本

- `R08`

## 迁移与兼容策略

只更新Release治理、证据与连续性状态，不改变API、数据库、生产激活或用户真机状态；49f40f2桌面APK继续作为R08测试包，后续全局UI完成后会产生新的跨版本视觉候选而不覆盖R08历史证据。

## 用户确认

OWNER_EXPLICIT_CLOSE_R08_FIRST_AND_NO_REPEATED_GITHUB_WASTE

## 审批

- 审批人：`codex-reviewer-r08-close-order`
- 决定：`APPROVED`
- 时间：`2026-07-22T15:00:32Z`
- 说明：依据所有者先全面收尾R08、禁止反复GitHub浪费和异步真机反馈规则批准；必须保留502失败事实且不得把历史UI或真机状态伪报PASS。

## 状态记录 · 2026-07-22T15:00:36Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：开始补R08机器关闭报告、验收矩阵、问题登记和无状态交接。

## 状态记录 · 2026-07-22T15:08:20Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：R08机器关闭报告、验收矩阵、Manifest、问题登记与交付事实已落地并通过发布结构、文档和APK哈希检查。
