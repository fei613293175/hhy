# TASK-R16-002 数据迁移与领域不变量报告

- Release：R16
- Task：TASK-R16-002
- Story：STORY-R16-004（工程治理）
- Change Request：CR-0441
- 数据库：PostgreSQL 17.10
- 结论：PASS

## 实施边界

V045 只做前向追加，不改写 V001 至 V044。它把 CR-0440 的
`ProductResource`、`ProductSkuResource`、`OrderResource` 显式合同投影到
现有八张 R16 业务表，并继续复用既有 `outbox_events` 状态历史事实层。

历史数据不允许通过占位文案或推测值补齐：

- `product_code` 只由既有主键确定性生成 `LEGACY-<id>`。
- 历史 SKU 名称和权益只从 `attributes_json.name/benefits` 提取。
- 历史订单项名称只从 `snapshot_json.name` 提取。
- 历史规则版本字符串原样映射为单元素 JSON 数组。
- 历史订单保留 `confirmed=false`、协议版本和确认时间为空。
- 无法从既有事实确定必填字段时，V045 在单事务内拒绝升级。

## 已冻结不变量

- 新订单只能以 `PENDING_PAYMENT` 创建，必须携带 16 至 128 字符幂等键和
  64 位小写 SHA-256 请求哈希。
- 创建唯一键为 `(user_id, biz_type, idempotency_key)`；同键不得产生第二订单。
- 状态边严格等于 `database/state_machines.yaml` 的七条 `ORDER_STATUS`
  迁移；进入 `PAYMENT_PROCESSING` 前必须有完整不退款证据。
- 每次合法状态变化由唯一触发器在同事务写一条
  `platform.status.changed.v1` Outbox；失败事务不留历史。
- `BenefitResource` 数组字段、数量、长度和额外字段均受数据库校验。
- 订单项小计必须等于数量乘单价；订单项和报价快照写入后不可变。
- 每个订单恰好一份报价快照，且 `orders.amount_cent = snapshot.payable`，
  在事务提交前由延期约束验证。
- U045 仅限 DEV/TEST；存在任一相关业务事实时原子拒绝回滚。

## 验证证据

| 验证 | 结果 | 证据 |
|---|---|---|
| 运行时迁移精确副本 | PASS | `scripts/sync_runtime_assets.py`，49 项资产一致 |
| Python 数据合同 | PASS | `tests/test_r16_database_contract.py`，6 项测试 |
| Schema 总门禁 | PASS | `scripts/check_db_schema.py`，203 表、45 个迁移、runtime hashes PASS |
| V001→V045 空库 | PASS | `R16_EMPTY_DATABASE_MIGRATION PASS state=22\|6` |
| V044 有效升级且不伪造 | PASS | `R16_V044_UPGRADE_NO_FABRICATION PASS` |
| 五类历史脏数据原子拒绝 | PASS | `R16_DIRTY_UPGRADE_ATOMIC_MATRIX PASS cases=5` |
| 有业务事实回滚拒绝 | PASS | `R16_U045_ROLLBACK_WITH_FACTS_REJECTED_ATOMICALLY PASS` |
| 空库 U045→V045 重放 | PASS | `R16_U045_ROLLBACK_V045_REPLAY PASS columns=22` |
| 订单/商品属性矩阵 | PASS | `R16_COMMERCE_ORDER_INVARIANT_PROPERTY_MATRIX PASS` |
| 八路并发创建幂等 | PASS | `R16_ORDER_CONCURRENT_IDEMPOTENCY PASS success=1 state=1\|1` |

PostgreSQL 验证在 `obx-test` 的独立一次性 `postgres:17.10-alpine` 容器中执行，
没有连接或修改线上业务数据库。普通全版本迁移总回归按大版本候选策略留到
R16 最终门禁；本任务已执行覆盖 V001 至 V045 的专用空库、升级库和属性矩阵。

## 后续

数据库基线已经满足 R16 后端应用服务开发条件。下一任务
`TASK-R16-003` 应使用本迁移中的显式字段、幂等查询和状态机，不得建立手写
DTO、第二套订单状态历史或绕过报价/不退款证据约束。
