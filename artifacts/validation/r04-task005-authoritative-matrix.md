# R04 TASK-R04-005 权威测试矩阵首阶段证据

## 已落地

- 新增 `scripts/run_r04_test_matrix.py`，只接受 `RELEASE_MANIFEST.yaml` 冻结的六个测试 ID，不允许缺失、额外或重复结果。
- 六个 Catalog 自动化路径均已有唯一 Adapter，并绑定中央执行器与 `r04-java21-pg17-android-contracts` 真实证据套件。
- 证据必须绑定当前提交或仅含允许交接文件变化的祖先提交；Java 21、PostgreSQL 17、固定 Android 镜像、正式 HTTPS API、逐项 PASS、执行状态、产物路径及 SHA-256 均为硬门禁。
- 修复 V022 数据库迁移运行时副本漂移，并重新生成全项目执行计划，使 R03 已完成事实与当前 R04 滚动窗口一致。

## 本阶段验证

- `python -m unittest tests.r04.test_r04_test_matrix`：4 tests，PASS。
- `python scripts/run_r04_test_matrix.py --list`：库存校验 PASS；六项均明确为 `NOT_RUN`，未冒充真实执行结果。
- `python scripts/check_v122_documentation.py --release R04`：PASS，0 errors / 0 warnings。
- `python scripts/check_generated_assets.py`：PASS，运行时契约、Android 资产、前端脚手架及 OpenAPI 类型均无漂移。
- `python scripts/check_program_execution_plan.py --json`：PASS，31 个版本执行计划确定性一致。

## 后续门禁

下一阶段在既有远端环境中执行 Java 21 后端全量验证、PostgreSQL 17 迁移与故障注入、固定 Android 镜像全量门禁，并生成六项逐项可校验证据。真实证据未完成前不得把六项测试标记为 PASS。
