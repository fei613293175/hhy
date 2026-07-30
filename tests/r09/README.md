# R09 三项专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 中既有的三个 `TST-APP_PROMO_001-*` 测试 ID，不创建第二套测试清单。

- `req-app-promo-001_happy`：App 主路径、真实 PostgreSQL、控制器合同与 Android 冻结表单。
- `req-app-promo-001_reject`：实名/媒体/链接/版本拒绝，以及媒体存储和域名配置提供方异常的零副作用。
- `req-app-promo-001_idempotent`：网络超时重放、Outbox 去重、8 路并发唯一归属、同键异体冲突和客户端稳定幂等键。

统一门禁：`python scripts/run_r09_specialized_matrix.py --check --evidence artifacts/validation/r09-test-evidence/evidence.json`。
