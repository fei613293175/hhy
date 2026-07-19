# R05 单一实名认证八项专项测试

`releases/R05/RELEASE_MANIFEST.yaml` 与 `catalogs/test_cases.csv` 共同冻结八项核心实名测试。每个无扩展名 JSON Adapter 绑定到真实 Java 测试方法，中央校验命令为：

```powershell
python scripts/run_r05_specialized_matrix.py
python scripts/run_r05_specialized_matrix.py --check --evidence artifacts/validation/r05-test-evidence/r05-specialized.evidence.json
```

不带 `--check` 只验证库存、Catalog、Adapter 和测试方法存在性，不声称测试已经执行。正式 PASS 必须同时提供 Java 21、PostgreSQL 17、每项断言和日志 SHA-256 证据。
