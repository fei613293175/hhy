---
cr_id: CR-0470
status: APPROVED
requester_actor_id: codex-r14-observability-20260729
approver_actor_id: project-owner-continuous-development-20260729
task_id: TASK-R14-006
session_id: SES-20260728T195020Z-B1DAB2D3
created_at: 2026-07-28T20:32:27Z
updated_at: 2026-07-28T20:33:14Z
---
# CR-0470 — 替代CR-0469锁定R14目录与Flyway发布范围验收

## 用户需求摘要

持续推进R14至R32并确保后续开发不再发生跨版本漂移

## 原规则

CR-0469只补目录源验收，R14 Compose仍让Flyway自动应用主分支全部迁移；存在历史V045至V047后，R14空库先升到V047再被事后V044断言拒绝。

## 新规则

CR-0470唯一替代CR-0469并复用CR-0464目录源与R14既有V044合同。R14专用Compose必须在应用启动前把Flyway target锁定为44，脚本固定导出和验证该值并继续要求数据库现场报告044；后续R15至R32迁移可留在主分支，但不得进入R14隔离候选数据库。目录验收同时要求CR-0464为冻结Commit祖先，并通过生成器检查或三份目录文件相对源Commit零差异。

## 修改原因

CR-0469只覆盖目录源重建，第二轮现场发现当前主分支的历史R16迁移V045至V047会进入R14空库；必须在应用启动前锁定R14 Flyway target 44并建立回归，不能把V047误记为R14通过

## 影响摘要

为R14隔离Staging增加发布范围迁移上限、目录源证明和回归，重新构建证据；不删除或恢复R16代码，不改迁移文件，不改页面/API/生产路由/DNS/公网WebSocket。

## 影响文件

- `scripts/run_r14_staging_acceptance.sh`
- `infra/staging/r14-smoke/docker-compose.yml`
- `tests/test_r14_staging_release_scope.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/validation/r14-task006-staging`
- `artifacts/reports/R14/TASK-R14-006-staging.md`
- `releases/R14/RELEASE_MANIFEST.yaml`

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

- `python -m unittest tests.test_r14_staging_release_scope`
- `python scripts/generate_chat_report_reason_catalog.py --check`
- `HHY_R14_FROZEN_COMMIT=<commit> bash scripts/run_r14_staging_acceptance.sh`
- `python scripts/check_program_execution_plan.py --json`

## 版本

- `R14`

## 迁移与兼容策略

仅重建hhy-r14-staging专用空库卷；SPRING_FLYWAY_TARGET=44使当前镜像只应用V001至V044。公网候选容器继续使用既有V044卷且不受影响。

## 用户确认

用户明确要求从R14持续到R32全过程不得再发生跨版本开发漂移，且无需逐次批准即可持续推进。

## 审批

- 审批人：`project-owner-continuous-development-20260729`
- 决定：`APPROVED`
- 时间：`2026-07-28T20:33:14Z`
- 说明：替代范围必要且唯一：不能把R16迁移进入R14候选后放宽为V047 PASS；必须在R14专用Compose前置锁定V044并用回归覆盖未来R15至R32迁移。

## 状态记录 · 2026-07-28T20:35:21Z

- Actor：`codex-r14-observability-20260729`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T195020Z-B1DAB2D3`
- Note：已在R14 Compose前置固定Flyway target 44，脚本记录target并验证现场044，新增PROB-0143与发布范围回归；第三轮将从全新隔离卷验证当前主分支含V045至V047时仍只应用V001至V044。
