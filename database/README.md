# 数据库执行基线

- PostgreSQL 16+；所有业务对象位于 `hhy` schema。
- Flyway 按版本顺序执行 `database/migrations/V001...V011`（其中 V010 为本闭环），当前冻结基线为 198 张表；已应用的旧迁移禁止改写 checksum。
- 后端运行时副本由 `python3 scripts/sync_runtime_assets.py` 同步；`--check` 只校验哈希，不写文件。
- `schema_dictionary.csv` 是字段目录；SQL 迁移是可执行事实；二者由 `scripts/generate_database.py` 和 Project Doctor 双向校验。
- 金额均为整数分；比例为基点；时间为 `timestamptz`；敏感原文不得进入普通字段。
- `accounting_transactions` 与 `accounting_entries` 使用延期约束在提交时校验复式平衡、单币种、账户币种和冲正对称，并无条件禁止更新/删除。
- `V010` 收口 Outbox/Inbox 状态、账户版本、余额游标/可重建总额及对账窗口；冻结目录未定义的专用状态历史表和余额方向语义按 `ADR-007`/`CR-0006` 保守记录，审批前不得扩大解释。
- 在一次性 PostgreSQL 16+ 数据库执行 `DATABASE_URL=... HHY_DB_SMOKE_CONFIRM=YES bash scripts/run_postgres_migration_smoke.sh`，会覆盖 V009→V010、正反例、并发幂等、U010 后重升和最终基线校验。
- `database/rollback/U010...` 仅供可丢弃开发/测试库；生产纠错必须新增前向迁移。
- 生产环境禁止运行 `database/rollback/U001...`；生产回滚采用前滚修复或经过演练的备份恢复。
