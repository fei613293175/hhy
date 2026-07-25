# TASK-R12-005 统一发布专项测试与故障注入门禁

## 权威范围

- `TST-CONTENT_002-HAPPY`
- `TST-CONTENT_002-REJECT`
- `TST-CONTENT_002-IDEMPOTENT`
- `TST-PUBLISH_001-HAPPY`
- `TST-PUBLISH_001-REJECT`
- `TST-PUBLISH_001-IDEMPOTENT`

六项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID。`tests/r12` 仅保存可机检适配器，不建立平行测试清单。

## 故障覆盖

- 相同请求和幂等键在响应丢失后重放首次完成快照，内容迁移、状态历史、提交快照、Outbox 和幂等完成各只执行一次。
- 同一幂等键绑定不同请求体返回 `COMMON-409-IDEMPOTENCY_CONFLICT`，不进入内容锁和业务写入。
- PostgreSQL 17.10 八路并发争用共享幂等记录时只有一个所有者，其余请求读取同一记录。
- 配置提供方超时、外部账号操作、未实名、非法状态和乐观锁失败均无成功状态历史、快照、Outbox 或幂等完成。
- 共享媒体存储故障保持迁移游标不前进且可恢复；R12 不虚构私有供应商 SDK。
- 消息重复只通过生产事务 Outbox 单次写入证明；R12 没有私有消息消费者。
- Android 使用稳定业务意图键，拒绝状态和未知结果不盲目重试，也不展示技术字段。

## 执行结论

- 结果：`PASS`
- 精确源码 Commit：`5419d68255dcc965c2c31fcb3006f616d9f8c38a`
- 环境：`obx-test` Temurin 21、PostgreSQL 17.10 一次性容器、固定 Android 镜像 `hhy-android-toolchain:r01-46fb273`
- 关键产品缺陷：P0 = 0，P1 = 0

| 验证项 | 结果 | 摘要 |
|---|---|---|
| 后端 Java 21 MODULE | PASS | `R12PublishingServiceTest` 10 项、`R12PublishingControllerContractTest` 2 项、`StorageMigrationServiceTest` 3 项，共 15 项零失败零错误零跳过 |
| PostgreSQL 17 真实事务与并发 | PASS | `R12PublishingPostgresStoreTest` 1 项、`R08PostgresStoreTest` 4 项，共 5 项零失败零错误零跳过；41 个迁移执行到 V041 |
| Android 发布管理 MODULE | PASS | 网络与内容管理单测、内容管理 Lint、App Kotlin 编译，共 202 个任务，`BUILD SUCCESSFUL` |
| 六项权威矩阵 | PASS | 六个既有测试 ID、selector、环境、非空日志和 SHA-256 全部通过 |

## 冻结证据

| 证据 | SHA-256 |
|---|---|
| `artifacts/validation/r12-test-evidence/backend-java21.log` | `8526f1421842de9bc5619eb6afa20fae33a2d6798c84c7101ccd0b2f1ebf1389` |
| `artifacts/validation/r12-test-evidence/postgresql17.log` | `4ff88496ab06bdf8f5a02d821aa7b5adeb4c6c7add3cc0c0e16d932098209c03` |
| `artifacts/validation/r12-test-evidence/android-module.log` | `52b816b32718a11db57f6479571c438dd48bb89dfcd27b2d58e263e48a926c2e` |

`evidence.json` 对每个测试 ID 记录断言、退出码和原始日志哈希。本任务只运行受影响 MODULE 与一次性 PostgreSQL 17，没有触发 GitHub 模拟器、候选截图或 APK。
