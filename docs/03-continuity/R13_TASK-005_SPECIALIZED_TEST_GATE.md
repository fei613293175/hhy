# TASK-R13-005 收藏、历史、分享与行为审计专项门禁

## 权威范围

- `TST-ACTIVITY_001-HAPPY`
- `TST-ACTIVITY_001-REJECT`
- `TST-ACTIVITY_001-IDEMPOTENT`

三项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID。`tests/r13` 只保存可机检适配器，不建立平行测试清单。

## 故障覆盖

- 收藏、历史、取消收藏、分享、联系方式审计和失效反馈按冻结合同执行；服务端活动顺序、游标和分页由真实 PostgreSQL 验证。
- 响应丢失后重放首次完成快照，报告、分享审计、联系方式审计、Outbox 和幂等完成均不重复。
- 同键异体、处理中冲突、停用账号、版本竞争、配置超时、联系方式存储超时和活动存储超时均无成功副作用。
- 八路 PostgreSQL 并发争用共享幂等记录时只有一个所有者，其余请求读取同一记录。
- Android 分页按稳定内容 ID 去重，失败保留已加载内容，写操作在完成前保持同一业务意图键。
- R13 不直接调用新的供应商 SDK，也没有私有消息消费者；不得为了测试虚构供应商或消费者。

## 执行结论

- 结果：`PASS`
- 精确源码 Commit：`c97417591cfa5c1b069d0aaef9622e0f3dad8c8f`
- 环境：`obx-test` Temurin 21、PostgreSQL 17.10 一次性容器、固定 Android 镜像 `hhy-android-toolchain:r01-46fb273`
- 关键产品缺陷：P0 = 0，P1 = 0

| 验证项 | 结果 | 摘要 |
|---|---|---|
| 后端 Java 21 MODULE | PASS | `R07ServiceTest`、`R08ServiceTest`、`R13ServiceTest`、`R13ControllerContractTest` 共 37 项，零失败、零错误、零跳过 |
| PostgreSQL 17 真实事务与并发 | PASS | `R13PostgresStoreTest` 与 `R08PostgresStoreTest` 共 5 项，零失败、零错误、零跳过；42 个迁移执行到 V042 |
| Android activity MODULE | PASS | `feature:activity` 单测与 Lint，共 109 个任务，63 executed、46 from cache，`BUILD SUCCESSFUL in 1m 21s` |
| 三项权威矩阵 | PASS | 三个既有测试 ID、selector、环境、非空日志和 SHA-256 全部通过 |

## 数据库隔离

迁移型测试不得共用同一个一次性数据库。V001 会设置数据库级 `search_path`，同一数据库内再次由新的 Flyway 实例迁移会把默认 schema 从 `public` 切换到 `hhy`，产生“非空 schema 但无历史表”的环境假失败。

本门禁在同一个 PostgreSQL 17.10 容器中使用 `hhy_r13`、`hhy_r08_project`、`hhy_r08_concurrent` 三个独立数据库，严格串行执行；静态 R08 Store 测试独立运行。后续复用同类门禁时必须保持“一次 Flyway 迁移型测试一个空数据库”，不得通过 `baselineOnMigrate` 或业务代码改动掩盖隔离错误。

## 冻结证据

| 证据 | SHA-256 |
|---|---|
| `artifacts/validation/r13-test-evidence/backend-java21.log` | `76f79ad79f73d385a3060110bccbe308a52ece9d8059557d4332779958f8d426` |
| `artifacts/validation/r13-test-evidence/postgresql17.log` | `a7905cfb9e9e7de90078d5376c1f593af66c8ac54f31fbbd63d71bda4768846b` |
| `artifacts/validation/r13-test-evidence/android-module.log` | `8dbe2b8f4a614b9ce85f9e27312a1aece93bfa4fa312ccf51fa8a50a9973c2a1` |

远端与仓库日志 SHA-256 完全一致，Android 临时容器已删除。`evidence.json` 对每个测试 ID 记录断言、退出码和原始日志哈希。本任务没有触发 GitHub 模拟器、候选截图或 APK；这些只在 `TASK-R13-007` 最终候选执行。
