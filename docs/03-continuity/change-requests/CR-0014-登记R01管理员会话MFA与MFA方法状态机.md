---
cr_id: CR-0014
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-R01-002
session_id: SES-20260717T080633Z-7A2C9226
created_at: 2026-07-17T08:25:01Z
updated_at: 2026-07-17T08:34:44Z
---
# CR-0014 — 登记R01管理员会话MFA与MFA方法状态机

## 用户需求摘要

完整推进R01版本开发，每个版本APK放桌面供真机测试

## 原规则

database/state_machines.yaml未登记管理员会话MFA等级和管理员MFA方法状态机，V009仅冻结字段值与基础CHECK

## 新规则

登记ADMIN_SESSION_MFA_LEVEL与ADMIN_MFA_METHOD_STATUS；会话强制从NONE起始且仅上升，MFA方法仅沿既有PENDING/ACTIVE/DISABLED转换；不新增状态、表、列或API

## 修改原因

冻结表已定义状态值但状态机唯一事实源缺少对应可执行转换，数据库约束和契约追踪无法闭环

## 影响摘要

完整补齐R01既有状态的可执行转换、MFA单事实源、会话/恢复码/登录日志不变量及原子升级回滚；业务范围与198表冻结契约不变

## 影响文件

- `database/state_machines.yaml`
- `database/migrations/V012__r01_admin_security_invariants.sql`
- `services/backend/boot/src/main/resources/db/migration/V012__r01_admin_security_invariants.sql`
- `database/rollback/U012__r01_admin_security_invariants.sql`
- `database/tests/r01_admin_security_invariants.sql`
- `database/verification/verify_baseline.sql`
- `scripts/check_db_schema.py`
- `scripts/run_postgres_migration_smoke.sh`
- `scripts/run_r01_database_invariants.sh`
- `docs/01-architecture/adr/ADR-008-R01管理员认证安全不变量.md`
- `artifacts/reports/R01/TASK-R01-002-database-invariants.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `admin_users：旧MFA引用无冲突迁移后恒为空且version非负`
- `admin_sessions：从NONE起始、保证等级只升不降、refresh唯一与乐观吊销终态`
- `admin_mfa_methods：唯一MFA事实源及既有状态生命周期`
- `admin_recovery_codes：原子一次消费、只缩短有效期作废和不可删除`
- `admin_login_logs：成功失败配对、锁定窗口索引和不可变安全事实`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `本地DB schema与运行时SHA、R01严格文档和连续性门禁；PostgreSQL 17空库/V011升级/冲突原子回滚/U012重升/长引用无损/会话MFA恢复码幂等并发/P00回归`

## 版本

- `R01`

## 迁移与兼容策略

V012只增加约束、索引、函数与触发器，Flyway及验证脚本单事务执行；V001至V011不修改；旧MFA冲突或重复refresh hash在写入前失败；U012不截断超过255字符的规范化密钥引用

## 用户确认

用户已授权完整推进R01并要求每版APK桌面真机测试

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T08:34:44Z`
- 说明：复查确认CR已完整登记admin_users、admin_sessions、admin_mfa_methods、admin_recovery_codes、admin_login_logs及全部实现/验证文件；V012仅使用V009既有状态与字段，不新增表、列、状态或API，并强制会话从NONE起始。旧MFA与重复refresh冲突在写入前失败，验证脚本单事务防半迁移；U012对超过255字符引用保留规范化事实且可重升。PostgreSQL17报告覆盖空库、V011升级、冲突回滚、U012重升、长引用、并发及P00回归；本地schema、运行时SHA、状态机与diff门禁复核通过

## 状态记录 · 2026-07-17T08:35:26Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T080633Z-7A2C9226`
- Note：按批准边界补齐既有状态机与V012安全不变量，不新增表列状态API

## 状态记录 · 2026-07-17T08:35:28Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T080633Z-7A2C9226`
- Note：V012/U012/状态机/单事务升级与PostgreSQL17空库升级回滚并发验证全部完成

## 状态记录 · 2026-07-17T08:39:53Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260717T080633Z-7A2C9226`
- Note：实现Commit已通过本地严格门禁与PostgreSQL17真实验证
