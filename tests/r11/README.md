# R11 三项专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 中既有的三个 `TST-TEAM_001-*` 测试 ID，不创建第二套测试清单。

- `req-team-001_happy`：团队长创建、编辑、真实 PostgreSQL、控制器合同和 Android 冻结表单。
- `req-team-001_reject`：权限、非法字段、版本竞争、配置超时及共享存储供应商失败的零副作用边界。
- `req-team-001_idempotent`：响应丢失重放、Outbox 去重、真实并发唯一归属、同键异体冲突，以及 Android/后台客户端幂等隔离。

R11 领域代码不直接调用供应商 SDK，也没有消息消费者；供应商异常通过共享媒体存储回归验证，消息重复通过事务 Outbox 单次写入验证，不虚构 R11 私有供应商或消费者。

统一门禁：`python scripts/run_r11_specialized_matrix.py --check --evidence artifacts/validation/r11-test-evidence/evidence.json`。
