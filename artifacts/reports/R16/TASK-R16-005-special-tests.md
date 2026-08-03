# TASK-R16-005 专项测试与故障验证报告

## 结论

R16 商品、SKU 和订单专项测试为 PASS，关键缺陷为 0。测试在 GitHub Actions、obx-test Java 21/PostgreSQL 17 和 Android 模拟器环境完成。

## 覆盖矩阵

- HAPPY：10 个冻结 operationId、商品/SKU 读写、订单 owner 隔离、精确价格与不退款快照。
- IDEMPOTENT：相同管理员、operationId、资源和请求摘要重放不重复业务写、审计或 Outbox。
- REJECT：同幂等键不同请求返回冲突；非法排序、分页、权益、期限和分佣边界拒绝。
- CONCURRENCY：八路并发创建只产生一条订单和一套状态事实。
- TRANSACTION：审计写入后注入故障，业务、幂等、审计和 Outbox 同事务回滚。
- SECURITY：用户订单 SQL 强制绑定 userId；跨用户与不存在订单统一 404。
- DATABASE：五类历史脏数据升级原子拒绝；有业务事实时 DEV 回滚原子拒绝。
- CLIENT：订单列表筛选、分页、刷新、错误/空状态、详情金额和不退款凭证通过 Compose 交互。
- RUNTIME：公网持久环境真实登录态列表 200，不存在详情 404，结构化日志与 TraceId 对应。

## 证据

- `services/backend/boot/src/test/java/cc/orbexa/hhy/commerce/R16CommerceServiceTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/commerce/R16CommercePostgresStoreTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/R16CommerceControllerContractTest.java`
- `tests/test_r16_database_contract.py`
- `database/tests/r16_commerce_order_invariants.sql`
- `apps/android/feature/order/src/androidTest/java/cc/orbexa/hhy/order/R16OrderScreensTest.kt`
- GitHub Actions `30831992663`
