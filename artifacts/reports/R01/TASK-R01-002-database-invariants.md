# TASK-R01-002 数据迁移与领域不变量验证

- Session：`SES-20260717T080633Z-7A2C9226`
- Story：`STORY-R01-003`
- 受控变更：`CR-0014`（`codex-engineering-audit` 独立批准）
- 基线 Commit：`fdcc8e2e3b4989e89db8884cbc9a8d4f62372fe0`
- 验证日期：2026-07-17
- 结果：PASS

## 实现边界

- 新增前向迁移 `V012__r01_admin_security_invariants.sql`，未修改 V001..V011。
- 未新增表或字段，终态仍为冻结的 198 张表。
- `admin_mfa_methods` 成为唯一可写 MFA 事实源；旧 `admin_users.mfa_secret_ref` 在无冲突升级后清空并约束为空。
- 管理员会话执行乐观版本、令牌唯一、MFA 只升不降、吊销终态和安全事实保留。
- MFA 方法、恢复码和登录日志执行数据库状态/时间/不可变约束。
- 恢复码通过 `consume_admin_recovery_code` 原子一次性消费，同时允许只缩短有效期完成未使用码作废。
- 登录失败锁定从不可变日志时间窗和配置派生，不新增锁定字段。

## 产物完整性

| 产物 | SHA256 |
| --- | --- |
| `database/migrations/V012__r01_admin_security_invariants.sql` | `199ca16e44c1b7d46d8f871bf7695d69cf9804abb87ed020d4ab142b72a90d1c` |
| Boot 运行时 V012 副本 | `199ca16e44c1b7d46d8f871bf7695d69cf9804abb87ed020d4ab142b72a90d1c` |
| `database/rollback/U012__r01_admin_security_invariants.sql` | `03a8d83ec76058d3289fbb7e637713643b21ba75d148e74c524daa93391f786b` |
| `database/tests/r01_admin_security_invariants.sql` | `190b9b6f60e8a6982da1ae68a432118905c7bcf42e638b34fd962c3e52a049b6` |

## 本地门禁

- `python scripts/check_db_schema.py`：`DB_SCHEMA_OK tables=198 migrations=12 runtime_hashes=PASS`
- `python scripts/check_v122_documentation.py --release R01 --strict`：PASS，0 errors，0 warnings。
- `python scripts/check_v123_continuity.py --strict`：PASS，9 个重建检查和 14 个生命周期检查通过。
- `database/state_machines.yaml`：YAML 解析通过，登记 `ADMIN_SESSION_MFA_LEVEL` 和 `ADMIN_MFA_METHOD_STATUS`。
- `git diff --check`：PASS。

## PostgreSQL 17.10 真实验证

在云服务器独立临时容器 `hhy-r01-dbtest-20260717-1632` 执行，数据库与现有线上/测试服务隔离：

- V001→V012 空库迁移：PASS。
- V011→V012 旧 MFA 单源升级：PASS。
- 旧列与方法表冲突：`ADMIN_MFA_LEGACY_CONFLICT` 明确拒绝，原始两侧数据保持不变。
- 重复 refresh hash 升级冲突：单事务明确拒绝，旧 MFA 值与前序 DDL 均未发生半迁移。
- V012→U012→V012：PASS，旧 MFA 引用无数据丢失。
- 300 字符规范化密钥引用经过 U012 后保持完整，未回填或截断到 255 字符旧列。
- 会话以 `VERIFIED/STEP_UP` 直接创建：明确拒绝，强制从 `NONE` 起始。
- 会话 refresh hash 并发唯一：恰一写入成功。
- 同 expected version 并发吊销：恰一写入成功。
- 同管理员 TOTP 并发注册：恰一写入成功。
- 同 scope/key 幂等领取：恰一写入成功。
- 同恢复码并发消费：恰一 `true`、一 `false`。
- R01 正反例 SQL：PASS。
- P00 回归与 U010 重升：PASS。
- 最终基线：`POSTGRESQL_MIGRATION_SMOKE PASS`，198 表。

验证结束后，已核对并删除上述唯一命名临时容器和 `/tmp/hhy-r01-dbtest-20260717-1632`；线上数据未改动。
