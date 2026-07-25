---
cr_id: CR-0337
status: APPROVED
requester_actor_id: codex-root-r12-observability-20260726
approver_actor_id: codex-reviewer-r12-governance
task_id: TASK-R12-006
session_id: SES-20260725T180922Z-D7231210
created_at: 2026-07-25T18:51:04Z
updated_at: 2026-07-25T18:51:29Z
---
# CR-0337 — 修正R12现场验收Flyway V041格式误判

## 用户需求摘要

按事实源连续开发并自行解决现场验收问题

## 原规则

R12现场验收必须确认Flyway V041且数据库连续性成立

## 新规则

按Flyway权威输出的三位版本041验证V041，并用静态回归锁定脚本断言；失败Commit不得原样重跑

## 修改原因

obx-test精确Commit现场证据显示数据库版本为041，脚本错误比较为41并在基线阶段误退出

## 影响摘要

修正单个验收脚本断言，新增回归与唯一Problem Registry记录，不修改数据库或产品运行时

## 影响文件

- `scripts/run_r12_staging_acceptance.sh`
- `tests/test_r12_staging_acceptance.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/reports/R12/TASK-R12-006-staging.md`

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

- `python -m unittest tests.test_r12_staging_acceptance; obx-test exact-commit staging acceptance`

## 版本

- `R12`

## 迁移与兼容策略

仅验收工具修正；数据库仍为V041且无迁移、降级或数据变更

## 用户确认

项目所有者已授权自行解决现场验收问题并连续推进

## 审批

- 审批人：`codex-reviewer-r12-governance`
- 决定：`APPROVED`
- 时间：`2026-07-25T18:51:29Z`
- 说明：041是Flyway对V041的权威版本输出，修正断言且保留失败证据符合不盲目重跑规则
