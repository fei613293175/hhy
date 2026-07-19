# R04 六项权威测试矩阵

`releases/R04/RELEASE_MANIFEST.yaml` 是唯一权威库存。中央执行器严格要求六个测试 ID、Catalog 行、自动化 Adapter 与真实外部证据一一对应。

```powershell
python scripts/run_r04_test_matrix.py --list
python scripts/run_r04_test_matrix.py --check --evidence-dir artifacts/validation/r04-test-evidence
```

`--list` 只核对库存，不代表执行通过。`--check` 要求 Java 21、PostgreSQL 17、固定 Android 镜像 `hhy-android-toolchain:r01-46fb273` 与正式 HTTPS API 地址，并验证每项结果、断言、产物路径和 SHA-256。缺项、重复项、`N/A`、`NOT_RUN`、错误环境、过期提交或证据哈希不符均为硬失败。
