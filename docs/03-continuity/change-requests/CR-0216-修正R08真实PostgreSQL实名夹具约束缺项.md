---
cr_id: CR-0216
status: APPROVED
requester_actor_id: codex-root-r08-003
approver_actor_id: codex-r08-db-reviewer
task_id: TASK-R08-003
session_id: SES-20260722T012224Z-E70EA3B7
created_at: 2026-07-22T02:59:11Z
updated_at: 2026-07-22T02:59:32Z
---
# CR-0216 — 修正R08真实PostgreSQL实名夹具约束缺项

## 用户需求摘要

持续完成R08真实Java21与PostgreSQL门禁，发现问题后由AI自行最小修复并继续，不要求项目所有者介入。

## 原规则

R08真实Store测试必须插入满足全部历史数据库不变量的合法实名用户；数据库约束不得因测试便利被绕过。

## 新规则

补齐测试夹具的id_no_cipher、64位id_hash和verified_at，使VERIFIED测试用户满足V023冻结约束；保留约束和产品代码不变，并以同一真实PostgreSQL 17.10测试作为回归。

## 修改原因

R08PostgresStoreTest把身份状态写为VERIFIED却未提供R05冻结约束要求的id_no_cipher、id_hash和verified_at，真实PostgreSQL正确拒绝了不合法夹具。

## 影响摘要

仅修正R08测试数据构造并登记问题与证据，不改API、生产数据模型、业务实现或配置。

## 影响文件

- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R08PostgresStoreTest.java`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/reports/R08/TASK-R08-003-backend.md`
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

- `R08PostgresStoreTest真实PostgreSQL17.10`
- `后端受影响MODULE Java21 Maven`

## 版本

- `R08`

## 迁移与兼容策略

无迁移；测试夹具向后兼容，生产约束保持原样并继续严格生效。

## 用户确认

项目所有者已长期授权持续开发中由AI判断测试结果、最小修复并继续推进。

## 审批

- 审批人：`codex-r08-db-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-22T02:59:32Z`
- 说明：确认失败由测试夹具违反既有V023约束造成；批准只补齐合法实名字段，禁止放宽数据库约束或跳过真实测试。

## 状态记录 · 2026-07-22T02:59:34Z

- Actor：`codex-root-r08-003`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T012224Z-E70EA3B7`
- Note：开始修正夹具并重跑真实PostgreSQL与受影响MODULE。
