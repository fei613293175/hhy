---
cr_id: CR-0193
status: APPROVED
requester_actor_id: codex-r07-candidate
approver_actor_id: codex-release-audit
task_id: TASK-R07-007
session_id: SES-20260721T210252Z-D631F6E4
created_at: 2026-07-21T21:37:07Z
updated_at: 2026-07-21T21:37:12Z
---
# CR-0193 — 修复R07候选夹具psql变量边界

## 用户需求摘要

持续推进R07最终候选，不因确定性问题停止

## 原规则

R07夹具在DO匿名块内直接引用psql变量ci_phone

## 新规则

仅在匿名块外解析敏感客户端变量并建立事务级最小用户上下文，DO块只读取user_id

## 修改原因

真实PostgreSQL 17预发布执行证明psql变量不会在DO匿名块内展开，需在零写入回滚后修复

## 影响摘要

修复隔离夹具语法失败并登记可复用PostgreSQL客户端变量边界，不改变产品接口、数据库结构或生产数据

## 影响文件

- `scripts/prepare_r07_ci_fixture.sh`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `仅Staging专用CI夹具事务，无结构变化`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `bash -n；PostgreSQL 17夹具连续执行两次；输出敏感信息审计`

## 版本

- `R07`

## 迁移与兼容策略

无数据库迁移；首轮失败事务已自动回滚，脚本保持幂等并仅作用于专用CI用户

## 用户确认

项目所有者已授权持续推进并对确定性问题自主修复

## 审批

- 审批人：`codex-release-audit`
- 决定：`APPROVED`
- 时间：`2026-07-21T21:37:12Z`
- 说明：最小事务边界修复，失败零写入且保留敏感信息隔离

## 状态记录 · 2026-07-21T21:39:27Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：已在真实Staging PostgreSQL 17验证匿名块外临时上下文与显式hhy schema，连续两次幂等通过
