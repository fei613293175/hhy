# R12 六项专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 已登记的六个 `TST-CONTENT_002-*` 与
`TST-PUBLISH_001-*` 测试 ID，不建立第二套测试清单。

- `HAPPY`：统一发布、内容管理、真实 PostgreSQL 事务、冻结控制器与 Android 状态合同。
- `REJECT`：权限、实名、非法状态、版本竞争、配置超时和共享存储失败的零成功副作用。
- `IDEMPOTENT`：响应丢失重放、同键异体冲突、Outbox 单写、真实 PostgreSQL 并发唯一归属和 Android 稳定意图键。

R12 发布服务不直接调用供应商 SDK，也没有私有消息消费者。供应商异常复用共享媒体存储回归，消息重复由事务 Outbox 单次写入证明。

统一门禁：`python scripts/run_r12_specialized_matrix.py --check --evidence artifacts/validation/r12-test-evidence/evidence.json`。
