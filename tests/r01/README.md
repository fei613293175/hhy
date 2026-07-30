# R01 27 项测试矩阵

`releases/R01/RELEASE_MANIFEST.yaml` 是唯一权威库存。执行器会要求其 27 个测试 ID 与全局 `catalogs/test_cases.csv` 中适用于 R01 的行严格 1:1，并与封闭的本地/外部执行映射完全一致。

Catalog 中每个 R01 `自动化路径` 均指向一个无扩展名的 UTF-8 JSON adapter manifest。库存门禁会验证 27 个路径全部存在、位于仓库内且不重复，并校对 `schema`、`test_id`、`central_runner`、`assignment_source` 和 `runner`。Adapter 只是 catalog 到中央执行器的机器可读路由，它不执行测试、不包含结果，也不能产生或伪造 `PASS`。实际执行和证据验证仍只由 `scripts/run_r01_test_matrix.py` 完成。

```powershell
python scripts/run_r01_test_matrix.py --list --json-out r01-list.json
python scripts/run_r01_test_matrix.py --check --evidence-dir <evidence-dir> --json-out r01-result.json
```

`--list` 只做库存和映射校验，27 项全部输出 `NOT_RUN`，不会启动测试。

`--check` 最多并行运行以下本地 runner：

- R01 严格文档、DoR 与追踪门禁；
- Design Token 与组件目录门禁；
- OpenAPI/生成客户端与数据库迁移契约门禁；
- `admin-web` 的 Vitest、TypeScript 检查与生产构建。

`TST-ADMIN_SECURITY_001-HAPPY/REJECT/SECURITY` 不能由静态检查代替，必须提供符合 `evidence.schema.json` 的 `r01-backend-maven-pg17-real-api` 外部证据。报告必须：

- `source_commit` 精确等于当前 Git `HEAD`；或者是完整、非浅克隆 Git 历史中可证明的祖先，且其后只能增加 R01 测试证据/矩阵报告、R01 任务报告及明确列入白名单的连续性、上下文、会话、状态和发布任务验收元数据；
- 记录 RFC3339 生成/起止时间和非空执行命令；
- 环境明确记录 Java、Maven、PostgreSQL 17 和真实 API Base URL；
- 每项都是 `PASS + executed=true + exit_code=0`，且至少一条 PASS 断言；
- 至少绑定一个位于证据目录内的非空真实产物，并校验 SHA256。

`N/A`、`NOT_RUN`、未执行、缺失、多余/重复证据、路径逃逸、产物篡改、非 PostgreSQL 17 环境或无法证明的陈旧 Commit 均为硬失败。浅克隆、非祖先，或测试后出现任何 `apps/`、`services/`、`packages/`、`contracts/`、`database/`、`config/`、`design/`、`scripts/`、`tests/` 变更都会使证据失效。Schema 保留失败状态只为了存档失败执行，不表示可以通过发布门禁。
