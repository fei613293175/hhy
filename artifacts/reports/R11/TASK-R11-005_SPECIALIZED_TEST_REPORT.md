# TASK-R11-005 团队长专项测试与故障注入证据

## 权威范围

- `TST-TEAM_001-HAPPY`
- `TST-TEAM_001-REJECT`
- `TST-TEAM_001-IDEMPOTENT`

三项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID；`tests/r11` 只保存可机检适配器，不建立平行测试清单。

## 故障覆盖

- 网络超时后重试返回首次完成快照，不重复创建资料、查询详情或写入 Outbox。
- 同一幂等键绑定不同请求体时返回 `COMMON-409-IDEMPOTENCY_CONFLICT`，不进入业务写入。
- 真实 PostgreSQL 并发争用同一幂等键时只有一个所有者，其余请求读取同一记录。
- 配置提供方超时、非法属性和乐观锁竞争均保持零业务副作用。
- Android 详情全局单写锁阻止重复并发动作，失败保留已加载详情并允许稳定重试。
- 管理后台按操作与资源隔离幂等键，成功只清除对应资源作用域。
- 共享媒体存储故障复用生产已有 `StorageMigrationServiceTest`；R11 不虚构私有供应商 SDK 或消息消费者。

## 执行结论

- 结果：`PASS`
- 精确源码 Commit：`5013a9a042026328bb9e6602f1931ed32d6632ed`
- 环境：`obx-test` Temurin `21.0.9`、PostgreSQL `17.10` 一次性容器、固定 Android 镜像 `hhy-android-toolchain:r01-46fb273`，以及本机后台 Web 工具链
- 关键产品缺陷：P0 = 0，P1 = 0

| 验证项 | 结果 | 摘要 |
|---|---|---|
| 后端受影响 MODULE | PASS | `R11ServiceTest` 8 项、`ContentServiceTest` 13 项、`StorageMigrationServiceTest` 3 项、`R11ControllerContractTest` 1 项，共 25 项零失败零跳过 |
| PostgreSQL 17 真实 Store | PASS | `R11PostgresStoreTest` 1 项和 `R08PostgresStoreTest` 4 项，共 5 项零失败零跳过，38 个迁移执行到 V038 |
| Android 团队长 MODULE | PASS | 网络单测、团队长单测、团队长 Lint、App Kotlin 编译；220 个任务，`BUILD SUCCESSFUL` |
| 后台 Web MODULE | PASS | 23 个测试文件、101 项测试和 TypeScript 检查通过 |
| 三项权威矩阵 | PASS | 3 个既有测试 ID 全部执行，选择器、证据绑定及哈希校验无错误 |

## 冻结证据

| 证据 | SHA-256 |
|---|---|
| `artifacts/validation/r11-test-evidence/backend-java21.log` | `ead2ed97f2256e56437c3aec966d312fa7fa08ea4de22a5cc765f32ff7596c9e` |
| `artifacts/validation/r11-test-evidence/postgresql17.log` | `497a07270517747b6bca1030900a5bfdc31d81a03fcbe39eefdf53e1dfa73985` |
| `artifacts/validation/r11-test-evidence/android-module.log` | `8e1b26e6aa0272143aba86e7444ada147bf601b6547897f6f6b67bbe8a583f4e` |
| `artifacts/validation/r11-test-evidence/admin-web.log` | `71497580748dd8d65332e5f080da3b9e288e5ec68f7342997f8092790be8f255` |

`evidence.json` 对每个测试 ID 记录断言、退出码和原始日志哈希。本任务只运行受影响 MODULE 和一次性 PostgreSQL 17，没有触发 GitHub 模拟器、候选 APK 或大版本关闭门禁。
