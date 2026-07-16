# V1.2.1 正式开发基线最终验证摘要

验证日期：2026-07-16

## 结果

| 范围 | 结果 | 证据 |
|---|---|---|
| Project Doctor | PASS：0 errors / 0 warnings | `artifacts/validation/project-doctor-final.json` |
| REST 契约 | PASS：293 operations / 293 unique operationIds / 0 GET-DELETE bodies | `contracts/contract_status.csv` |
| Web 契约生成 | PASS | `artifacts/validation/web-verify.log` |
| Web TypeScript | PASS | `artifacts/validation/web-verify.log` |
| Web tests | PASS：管理后台 1、公开 H5 1；其他包无测试文件且显式允许 | `artifacts/validation/web-verify.log` |
| Web production build | PASS：管理后台与公开 H5 | `artifacts/validation/web-verify.log` |
| Maven Reactor | PASS：8 projects | `artifacts/validation/backend-maven-verify.log` |
| Backend tests | PASS：5 / 0 failures / 0 errors / 0 skipped | `artifacts/validation/backend-maven-verify.log` |
| PostgreSQL dialect parse | PASS：8 migrations | Project Doctor database metrics |
| PGlite execution | PASS：8 migrations / 188 tables / accounting invariants | `artifacts/validation/database-pglite-smoke.log` |
| Secret/internal-path scan | PASS | Project Doctor security-portability section |

## 外部环境门禁

- PostgreSQL 17.10 真实执行：CI job 和脚本已配置，交付环境未运行；
- Android SDK 37 编译：CI job 已配置，交付环境无 Android SDK；
- Docker Compose：需在开发机或用户 CI 执行；
- 供应商、DNS、证书、生产签名、压测和合规：见 `catalogs/development_risk_register.csv`。

这些门禁不阻止开始 P00，但在对应版本退出或生产发布前必须取得真实证据。
