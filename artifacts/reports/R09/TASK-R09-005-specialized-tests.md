# TASK-R09-005 专项测试与故障注入证据

## 权威范围

- `TST-APP_PROMO_001-HAPPY`
- `TST-APP_PROMO_001-REJECT`
- `TST-APP_PROMO_001-IDEMPOTENT`

三项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID；`tests/r09` 仅保存可机检适配器，不建立平行测试清单。

## 故障覆盖

- 模拟网络超时后的创建请求重放：返回首次加密快照，不重复创建 App、查询详情或写 Outbox。
- 同一幂等键绑定不同请求体时返回 `COMMON-409-IDEMPOTENCY_CONFLICT`，不进入媒体、配置或业务写入。
- 8 路真实 PostgreSQL 并发争用同一 App 创建幂等键时只有一个所有者，其余请求读取同一记录。
- 媒体存储提供方超时在草稿额度、App 写入、Outbox 和完成快照之前失败。
- H5 域名配置提供方超时不虚构回退地址，不产生消息或完成快照。
- 乐观锁竞争失败返回稳定版本冲突，不产生重复更新消息。
- Android 同一操作和请求体复用幂等键，请求体变化或成功消费后轮换键。

“供应商异常”只映射为 R09 生产域真实存在的媒体存储和域名配置提供方，不新增虚构的第三方 App 供应商。

## 执行结论

- 结果：`PASS`
- 精确源码 Commit：`450d8758a2ba2ad42b4906f8911495815ac29213`
- 环境：本机 Temurin `21.0.11`、`obx-test` PostgreSQL `17.10` 一次性容器、Android Gradle JDK `21.0.11`
- 关键产品缺陷：P0 = 0，P1 = 0

| 验证项 | 结果 | 摘要 |
|---|---|---|
| 后端受影响 MODULE | PASS | 358 tests，0 failures，0 errors，13 个既有显式条件跳过；`R09ServiceTest` 11/11 通过 |
| PostgreSQL 17 真实 Store | PASS | `R09PostgresStoreTest` 2 tests，0 failures，0 errors，0 skipped；包含 8 路真实并发唯一幂等归属 |
| Android App推广 MODULE | PASS | `:feature:app-promotion:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:compileDebugKotlin`；182 tasks，`BUILD SUCCESSFUL` |
| 三项权威矩阵 | PASS | 3 个既有测试 ID 全部执行，选择器、证据绑定及哈希校验无错误 |

## 冻结证据

| 证据 | SHA-256 |
|---|---|
| `artifacts/validation/r09-test-evidence/backend-java21.log` | `1b04b0ee61fcd96c29cabe5ceb38d2035fb26fcf358d68644e255d2b9621a308` |
| `artifacts/validation/r09-test-evidence/postgresql17.log` | `38abf1e1dbe7ea1ae36462948c5c607abdf8c14ded1885f4f7b79423ffe9ad09` |
| `artifacts/validation/r09-test-evidence/android-module.log` | `7b3a95bd9f635868ccc68c371462d32e67eb7c43e7906fdf07d45942f4a35cfc` |
| 后端精确源码归档 | `0a5e135a0516a4774428ec83f95e3e530b362d81925118203969ac9b5d64165c` |

`evidence.json` 对每个测试 ID 记录断言、退出码和原始日志哈希。本任务只运行受影响 MODULE 和一次性 PostgreSQL 17，没有触发 GitHub 模拟器、候选 APK 或大版本关闭门禁。
