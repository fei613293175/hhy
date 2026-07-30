# TASK-R08-005 专项测试与故障注入证据

## 权威范围

- `TST-PROJECT_001-HAPPY`
- `TST-PROJECT_001-REJECT`
- `TST-PROJECT_001-IDEMPOTENT`

三项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID；`tests/r08` 仅保存可机检适配器，不建立平行测试清单。

## 新增故障覆盖

- 完成后的创建请求延迟重放返回首次加密快照，不重复创建项目、查询详情或写 Outbox。
- 8 路真实 PostgreSQL 并发争用同一幂等键时只有一个所有者，其他请求读取同一记录。
- 媒体存储超时在草稿额度、项目写入、Outbox 和完成快照之前失败。
- H5 域名配置提供方超时不产生分享记录、Outbox 或完成快照。
- 未实名、双向拉黑、冲突和离线恢复继续复用既有服务与 Android 回归。

## 执行结论

- 结果：`PASS`
- 精确源码 Commit：`f3421114b124f4b1d69cd20de7078a022db019e9`
- 环境：`obx-test`、Temurin `21.0.9`、PostgreSQL `17.10`、固定镜像 `hhy-android-toolchain:r01-46fb273`
- 关键产品缺陷：P0 = 0，P1 = 0

| 验证项 | 结果 | 摘要 |
|---|---|---|
| 后端受影响 MODULE | PASS | 344 tests，0 failures，0 errors，9 个既有显式条件跳过；`R08ServiceTest` 7/7 通过 |
| PostgreSQL 17 真实 Store | PASS | `R08PostgresStoreTest` 4 tests，0 failures，0 errors，0 skipped；包含 8 路真实并发唯一幂等归属 |
| Android 项目 MODULE | PASS | `:core:network:test`、`:feature:project:test`、`:app:testDebugUnitTest`、`:app:compileDebugKotlin`；172 tasks，`BUILD SUCCESSFUL` |
| 三项权威矩阵 | PASS | 3 个既有测试 ID 全部执行，证据绑定及哈希校验无错误 |

## 冻结证据

| 证据 | SHA-256 |
|---|---|
| `artifacts/validation/r08-test-evidence/backend-java21.log` | `2fc8f9479a2d0d15ce15b99f0cc69206522ff7e33b592606198b4f7cbc625e37` |
| `artifacts/validation/r08-test-evidence/postgresql17.log` | `bd27a1cdf804b256ef756755555ac8b623be05333891ff0aba16da19a741c75f` |
| `artifacts/validation/r08-test-evidence/android-module.log` | `e8715c5d1a503a445e0d829a9ef854ad22737aaec9f793cbf69c52754598c930` |
| 后端精确源码归档 | `0eaf09cf49d3b7e8a16a43a45fa223e8094a14733cb382523fa8ced8f9244227` |
| Android 精确源码归档 | `00199cf370b7fc69d3cf992934be53392d4dd42957e649191b30cd3e6b8f2e2b` |

`evidence.json` 对每个测试 ID 记录断言、退出码和原始日志哈希。统一矩阵已以 `--check` 执行并返回 `PASS`；本任务只运行受影响 MODULE，未触发 GitHub 模拟器、候选 APK 或大版本关闭门禁。
