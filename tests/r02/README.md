# R02 25 项权威测试矩阵

`releases/R02/RELEASE_MANIFEST.yaml` 是唯一权威库存。中央执行器要求其中 25 个测试 ID 各自存在适用于 R02 的全局 catalog 行，并与自动化 adapter 和执行映射严格 1:1。

```powershell
python scripts/generate_r02_test_adapters.py
python scripts/run_r02_test_matrix.py --list --json-out r02-list.json
python scripts/run_r02_test_matrix.py --check --evidence-dir <evidence-dir> --json-out r02-result.json
```

`--list` 只验证 Manifest 声明的 25 项库存与路由，结果保持 `NOT_RUN`；它不能代替测试。跨版本 catalog 中未被 R02 Manifest 选入的通用测试不计入本版本 25 项。`--check` 并行执行 R02 严格文档/契约门禁和后台用户管控测试，并校验一份绑定当前 `HEAD` 的外部证据。

外部证据必须按 `evidence.schema.json` 记录 Java 21 Maven 全量测试、PostgreSQL 17 的 V001-V018 迁移/回滚/并发烟测、固定 Android 镜像的 `testDebugUnitTest lintDebug assembleDebug`、HTTPS API 地址和逐项断言。21 个外部测试 ID 必须全部 `PASS + executed=true + exit_code=0`，并绑定证据目录内非空产物及 SHA-256。

缺失、额外或重复结果，`N/A`、`NOT_RUN`、非当前提交、PostgreSQL 非 17、非 HTTPS API、路径逃逸或产物摘要不符均为硬失败。
