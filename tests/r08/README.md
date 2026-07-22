# R08 三项专项测试适配器

本目录只映射 `catalogs/test_cases.csv` 中既有的三个 `TST-PROJECT_001-*` 测试 ID，不创建第二套测试清单。

- `req-project-001_happy`：项目主路径、真实 PostgreSQL、控制器合同与 Android 冻结表单。
- `req-project-001_reject`：权限、拉黑、媒体存储超时、配置提供方超时和零副作用。
- `req-project-001_idempotent`：延迟重放、Outbox 去重、8 路并发唯一归属和客户端稳定幂等键。

统一门禁：`python scripts/run_r08_specialized_matrix.py --check --evidence artifacts/validation/r08-test-evidence/evidence.json`。
