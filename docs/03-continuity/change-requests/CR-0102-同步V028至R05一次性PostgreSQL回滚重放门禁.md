---
cr_id: CR-0102
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T21:22:10Z
updated_at: 2026-07-19T21:22:25Z
---
# CR-0102 — 同步V028至R05一次性PostgreSQL回滚重放门禁

## 用户需求摘要

用户要求持续推进R05正式商业闭环并严格完成每个版本细节

## 原规则

R05一次性PostgreSQL脚本仅回滚和重放V023至V027，并宣告空库到V027

## 新规则

R05一次性PostgreSQL脚本必须纳入U028回滚、V028重放、唯一索引计数及空库到V028标识

## 修改原因

V028迁移已生效，但R05一次性数据库脚本仍只回滚重放至V027，导致门禁准确检出残留索引

## 影响摘要

同步两份R05数据库脚本及对应静态测试，不改变生产表结构之外的V028既定内容

## 影响文件

- `scripts/run_r05_database_invariants.sh`
- `scripts/run_r05_disposable_postgres_container.sh`
- `tests/test_r05_identity_database_scripts.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `media_objects`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `PostgreSQL17空库V001-V028、U028-U023回滚、V023-V028重放及唯一索引计数`

## 版本

- `R05`

## 迁移与兼容策略

仅修正一次性测试脚本覆盖范围和输出标识；生产V028迁移与U028保持不变

## 用户确认

用户已要求无需逐项确认，持续推进并严格完成开发文档与测试门禁

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T21:22:25Z`
- 说明：数据库门禁必须与当前迁移头同步，属于既定R05要求的必要修正

## 状态记录 · 2026-07-19T21:22:26Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T183335Z-535311E4`
- Note：开始补齐U028和V028的一次性PostgreSQL回滚重放门禁

## 状态记录 · 2026-07-19T21:34:09Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T183335Z-535311E4`
- Note：R05一次性数据库门禁已覆盖V028空库、U028-U023回滚和V023-V028重放
