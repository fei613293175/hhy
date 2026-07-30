# TASK-R16-003 后端应用服务与接口报告

- Release：R16
- Task：TASK-R16-003
- Session：SES-20260728T102501Z-3189682B
- Change Request：CR-0442、CR-0443、CR-0444、CR-0445
- 验证环境：`obx-test`，Java 21，Maven 3.9.11，PostgreSQL 17.10 一次性隔离容器
- 结论：PASS

## 已落地能力

- 用户订单：
  - `orderGetMeOrders`
  - `orderGetMeOrdersByOrderno`
  - 列表和详情均在 SQL 层绑定认证 `userId`；跨用户和不存在订单统一返回 `COMMON-404-NOT_FOUND`。
- 后台商品、SKU与订单：
  - `adminProductsGetProducts`
  - `adminProductsPostProducts`
  - `adminProductsPatchProductsById`
  - `adminProductsGetSkus`
  - `adminProductsPostSkus`
  - `adminProductsPatchSkusById`
  - `adminOrdersGetOrders`
  - `adminOrdersGetOrdersByOrderno`
- 后台权限精确使用 `product.read`、`product.write`、`order.read`；V046将既有`product.manage`角色安全映射到细粒度权限，并确保活动`SUPER_ADMIN`具备两项商品权限。
- 商品/SKU写、管理员审计、Outbox、幂等领取与加密首次响应快照位于同一数据库事务。重放不重复业务写、审计或事件；同键异请求哈希返回409。
- `scope`固定为`r16adm:`加长度前缀输入的SHA-256 Base64URL编码，最长输入仍小于64字符，并绑定管理员、operationId和资源。
- 商品与SKU使用原子乐观锁；创建SKU先锁定商品并执行100项上限检查。订单、SKU、权益和报价快照超过冻结合同上限或数据缺失/重复/非法时拒绝，不截断、不补造。
- V047前向修正V045与冻结OpenAPI的边界漂移：权益名称255、单位64、`durationDays=0`合法；256、65和负数仍拒绝。旧迁移未被改写，DEV回滚遇到新合法事实时原子拒绝。

## 验证结果

| 门禁 | 结果 |
|---|---|
| `python scripts/check_db_schema.py` | PASS，203张表、47个迁移、运行时迁移同字节 |
| `python tests/test_r16_backend_contract.py` | PASS，10个operationId、SQL owner隔离、模块边界 |
| `python scripts/check_api_contract.py` | PASS，client 131、admin 184、WebSocket 12 |
| Java 21 R16应用服务 | 5项PASS，0失败 |
| 控制器与模块边界 | 3项PASS，0失败 |
| 管理员AES-256-GCM快照 | 4项PASS，覆盖AAD篡改拒绝与旧密钥轮换 |
| PostgreSQL Store | 3项PASS，0跳过；覆盖重放、乐观锁、精确快照、owner隔离和故障回滚 |
| R16 PostgreSQL 17总矩阵 | PASS；V045/V046/V047空库、升级、脏数据拒绝、安全回滚、回滚重放、权限和8路订单幂等并发全部通过 |
| R14 WebSocket测试上下文隔离 | 2项PASS；生产默认保持10MiB文本/二进制缓冲与75000毫秒空闲超时，test profile关闭容器工厂时`/ws`、处理器和握手拦截器仍注册 |
| Java 21后端全量回归 | 491项，0失败、0错误、27项按外部PostgreSQL条件跳过；BUILD SUCCESS |

## 回归基础设施修正

- Spring代理管理的R16后台与用户控制器保持可代理，禁止声明为`final`，并由合同测试防回归。
- `ServletServerContainerFactoryBean`仅在`hhy.websocket.container.enabled=true`或未配置时创建；生产默认行为不变，Mock测试上下文不再要求伪造生产容器。
- 默认test profile使用`hhy-${random.uuid}`独立H2数据库，避免不同ApplicationContext在`DB_CLOSE_DELAY=-1`下重复建表和状态串扰；显式测试URL与生产PostgreSQL配置不变。

## 非范围项

- 本任务没有实现客户端、H5或后台页面；这些由`TASK-R16-004`承接。
- 本任务没有创建订单、支付或退款写接口；冻结R16合同仅包含订单读取，支付状态机由后续版本承接。
- 邀请码和注册链明确不在本任务范围内，未修改、未验证。
