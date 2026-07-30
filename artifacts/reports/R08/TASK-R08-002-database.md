# TASK-R08-002 数据迁移与领域不变量

## 范围

- `project_details`：项目合作说明必填且不得为空白；条件、地区和网址有值时不得为空白。
- `content_stats`：浏览、收藏、沟通和联系方式统计只接受非负整数字符串。
- `content_versions`：版本号只接受规范的非负整数字符串。
- 项目详情删除：内容未进入 `DELETED` 软删除状态时，数据库拒绝直接删除项目详情。
- 项目读取：增加公开项目时间倒序索引和地区检索索引。
- 复用 V007 已冻结的内容状态枚举、V029 已有历史不变量，以及全局 `idempotency_records` 唯一键，不创建重复约束或重复表。

## 兼容与回滚

- 三项检查约束使用 `NOT VALID`：保留历史存量，但对迁移后的所有新写入立即生效。
- V033 不清洗、不改写、不删除业务数据。
- U033 只移除三项 R08 约束、两项索引、一个触发器和一个函数；回滚前后业务行逐字段一致。
- V033 在 U033 后可原子重放，恢复 `3` 项约束、`2` 项索引、`1` 个触发器和 `1` 个函数。

## 验证

- 静态迁移/回滚回归：`6 tests PASS`。
- 受影响后端 MODULE：Java `21.0.9`、Maven `3.9.11`，`mvn -B -pl access,boot -am test` 共 `332` 项，`Failures=0 Errors=0 Skipped=7`，`BUILD SUCCESS`；7 项为既有显式外部数据库条件跳过，本任务数据库行为由下述 PostgreSQL 17.10 门禁覆盖。
- 数据库结构门禁：`DB_SCHEMA_OK tables=200 migrations=33 runtime_hashes=PASS`。
- 运行时资产镜像：`37 assets PASS`；V033 源文件与 Spring Boot Flyway 副本 SHA-256 均为 `c4f3af67e9305241a075cfa3ca2ac58865ff8e9598c9ae2a1bd5eab6c3dfe2ef`。
- PostgreSQL：`17.10-alpine`，在 `obx-test` 精确命名的一次性隔离容器运行，未公开数据库端口。
- 空库迁移：`R08_EMPTY_DATABASE_TO_V033 PASS tables=200 objects=3|2|1`。
- V032 升级库：`R08_UPGRADE_DATABASE_TO_V033 PASS state=DRAFT|legacy upgrade|legacy-value|legacy-v1|3`，证明历史非数字统计值和旧版本号在 `NOT VALID` 迁移后完整保留。
- 负向不变量：`R08_PROJECT_INVARIANTS PASS`，覆盖空白项目字段、非数字统计、非法版本号、未软删除详情移除及缺失先决约束。
- 回滚：`R08_U033_ROLLBACK PASS objects=0|0|0|0`，回滚测试行保持 `rollback verification|12|0` 不变。
- 重放：`R08_V033_REPLAY PASS constraints=3 indexes=2 triggers=1 functions=1`。
- 一次性全链结论：`R08_DISPOSABLE_POSTGRES_CONTAINER PASS`。

## R07 Staging 只读克隆

- 数据来源：运行中的 `hhy-r07-staging-postgres-1`，只通过 `pg_dump --no-owner --no-privileges` 读取。
- 恢复目标：独立临时容器 `hhy-r08-staging-clone-postgres`，PostgreSQL `17.10`；源库未执行迁移、测试或任何写入。
- V033 在克隆库单事务应用成功：`R08_STAGING_CLONE_V033_APPLY_PASS`。
- 克隆库负向不变量：`R08_PROJECT_INVARIANTS PASS`。
- 克隆库无损回滚：`R08_U033_ROLLBACK PASS objects=0|0|0|0`。
- 克隆库重放：`R08_V033_REPLAY PASS constraints=3 indexes=2 triggers=1 functions=1`。
- 验证结束后精确删除临时克隆容器；R07 Staging 容器、数据和路由保持原状。
