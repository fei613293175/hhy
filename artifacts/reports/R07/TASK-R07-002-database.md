# TASK-R07-002 数据迁移与领域不变量

## 范围

- `search_histories`：新写入关键词非空且不超过冻结搜索输入上限，增加用户最近搜索复合索引。
- `hot_search_terms`：规范化关键词唯一、权重非负、排期有效，并增加启用与权重排序索引。
- `content_contact_access_logs`：访问对象、渠道和动作完整，访问审计不可更新或删除，并增加用户/内容最近访问索引。
- `idempotency_records`：复用既有 `(scope, idem_key)` 唯一约束承载 `searchDeleteSearchHistory`，不新增重复幂等表。

## 兼容与回滚

- 新检查约束使用 `NOT VALID`：保留历史存量并约束所有新写入。
- 热词规范化唯一索引创建前检测历史重复；发现歧义时迁移明确失败，不自动删除或合并业务数据。
- `U031` 只移除 R07 新增约束、索引和触发器，并恢复 `hot_search_terms.weight` 原默认值。
- 搜索域在冻结状态机目录中为 `N/A_STATE_MACHINE_NOT_REQUIRED`；本任务不发明状态历史表。

## 验证

- 静态迁移/回滚回归：`6 tests PASS`。
- 数据库结构门禁：`DB_SCHEMA_OK tables=200 migrations=31 runtime_hashes=PASS`。
- 运行时资产镜像：`35 assets PASS`，V031 源文件与 Spring Boot Flyway 副本 SHA-256 一致。
- PostgreSQL：`17.10-alpine`，在 `obx-test` 隔离临时容器运行，未发布数据库端口。
- 空库迁移：`R07_EMPTY_DATABASE_TO_V031 PASS tables=200`。
- 升级库迁移：`R07_UPGRADE_DATABASE_TO_V031 PASS state=1|1|4`，V030 存量搜索历史和热词均保留。
- 负向不变量：`R07_SEARCH_INVARIANTS PASS`。
- 回滚：`R07_U031_ROLLBACK PASS`，R07 约束、索引、触发器和权重默认值均恢复。
- 重放：`R07_V031_REAPPLY PASS constraints=4 indexes=4 triggers=1`。
- 结论：`R07_DISPOSABLE_POSTGRES_CONTAINER PASS`。

## 后端模块边界基线问题

- 基线提交：`f0e45bae374b71359b91cc3f63e8628007bf97dc`。
- 隔离环境：`obx-test` + `maven:3.9.11-eclipse-temurin-21`，仅使用该提交的 `services/backend` 归档。
- 基线结果：`ModuleBoundaryTest` 共 2 项，1 项失败；`ContentPostgresStore` 的 Spring `@Repository` 注解被旧谓词误判为项目跨模块仓库依赖。
- 判定：该失败早于 V031 且与 R07 数据迁移无关，登记为 `PROB-0068`，通过 `CR-0177` 修复测试规则而非跳过门禁。
- 修复规则：只匹配 `cc.orbexa.hhy` 项目命名空间内、非 `shared` 包且类名以 `Repository` 结尾的依赖目标；Spring/第三方注解不匹配，项目仓库仍匹配。
- 定向回归：`ModuleBoundaryTest` 共 3 项，`Failures=0 Errors=0 Skipped=0`。
- 受影响后端 MODULE：`mvn -B -pl access,boot -am test`，共 311 项，`Failures=0 Errors=0 Skipped=5`，`BUILD SUCCESS`；5 项为既有显式外部数据库条件跳过，R07 数据库行为由本报告中的 PostgreSQL 17.10 隔离容器门禁覆盖。
- 同批合同与数据库检查：`6 tests PASS`、`DB_SCHEMA_OK tables=200 migrations=31 runtime_hashes=PASS`、`35 assets PASS`、`API_CONTRACT_OK client=131 admin=184 websocket=10 runtime_hashes=PASS`。
