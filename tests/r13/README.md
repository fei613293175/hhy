# R13 三项专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 中既有的三个 `TST-ACTIVITY_001-*` 测试 ID，不建立第二套测试清单。

- `HAPPY`：收藏、历史、取消收藏、分享、联系方式审计、失效反馈、冻结控制器合同、真实 PostgreSQL 和 Android 列表状态。
- `REJECT`：参数校验、停用账号、版本竞争、处理中重复请求、配置/联系方式/活动存储超时的零成功副作用。
- `IDEMPOTENT`：取消收藏、分享、联系方式与失效反馈的响应丢失重放，同键异体冲突，共享幂等表真实并发唯一归属和 Android 稳定意图键。

R13 不直接调用新的供应商 SDK，也没有私有消息消费者。供应商异常复用 R08 分享配置提供方故障，敏感存储异常复用 R07 联系方式存储故障；消息重复通过事务 Outbox 在响应重放下只写一次证明。

统一门禁：`python scripts/run_r13_specialized_matrix.py --check --evidence artifacts/validation/r13-test-evidence/evidence.json`。
