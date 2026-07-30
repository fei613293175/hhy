# R14 六项聊天专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 中既有的六个 `TST-CHAT_001-*` 和 `TST-CHAT_002-*` 测试 ID，不建立第二套测试清单。

- `CHAT_001-HAPPY`：私聊创建、四类消息合同、加密联系方式、会话分页和真实 PostgreSQL 生命周期。
- `CHAT_001-REJECT`：拉黑、非法媒体、消息重复、存储超时和字段边界均在业务写入前拒绝或共同回滚。
- `CHAT_001-IDEMPOTENT`：响应丢失后同键重放只返回冻结快照，同键异体和重复 `clientMessageId` 稳定冲突，真实 PostgreSQL 并发只有一个幂等所有者。
- `CHAT_002-HAPPY`：服务端用户级序列、ACK、未读/已读、断线恢复、举报和拉黑解除形成闭环。
- `CHAT_002-REJECT`：未鉴权握手、未来序列、错误 ACK、非成员输入、事务回滚和投递失败均无伪成功副作用。
- `CHAT_002-IDEMPOTENT`：重复事件不重复展示但重复 ACK，ACK 时间不可被重放改写，最多六次重投并在超限后停止。

R14 没有新增外部聊天供应商 SDK；所谓供应商异常由已有媒体存储、配置提供方、数据库和 WebSocket 投递边界故障注入覆盖，不虚构不存在的供应商调用。

统一门禁：`python scripts/run_r14_specialized_matrix.py --check --evidence artifacts/validation/r14-test-evidence/evidence.json`。
