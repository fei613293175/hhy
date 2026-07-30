# R07 搜索、发布者与联系方式十项专项测试

`releases/R07/RELEASE_MANIFEST.yaml` 冻结十项权威测试。每个无扩展名 JSON Adapter 绑定真实 Java 或 Kotlin 测试方法；中央校验命令为：

```powershell
python scripts/run_r07_specialized_matrix.py
python scripts/run_r07_specialized_matrix.py --check --evidence artifacts/validation/r07-test-evidence/r07-specialized.evidence.json
```

不带 `--check` 只验证库存、Catalog、Adapter、测试方法与 `DIALOG-SEARCH-001` 源码契约存在性，不声称测试已执行。正式 PASS 必须绑定冻结源码 Commit，并同时提供 Java 21 后端、PostgreSQL 17 真实数据库和 Android Gradle JDK 21 模块日志及 SHA-256。
