# 数据库执行基线

- PostgreSQL 16+；所有业务对象位于 `hhy` schema。
- Flyway 顺序执行 `database/migrations/V001...V008`。
- `schema_dictionary.csv` 是字段目录；SQL 迁移是可执行事实；二者由 `scripts/generate_database.py` 和 Project Doctor 双向校验。
- 金额均为整数分；比例为基点；时间为 `timestamptz`；敏感原文不得进入普通字段。
- `accounting_transactions` 与 `accounting_entries` 使用延期约束在提交时校验复式平衡，并禁止常规更新/删除。
- 生产环境禁止运行 `database/rollback/U001...`；生产回滚采用前滚修复或经过演练的备份恢复。
