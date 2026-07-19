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

已在既有 `obx-test` 隔离目录执行三路真实集成：

- Java 21 / Maven 3.9.11 后端 `mvn -B verify`：PASS，216 tests，0 failures / 0 errors，日志 SHA-256 `2bfdf704b14ae925fc0cf08e5a1af2ac9609603a5299f6a4a992afc33dae5e69`。
- PostgreSQL 17.10：空库升级至 V022、200 表校验、R04 不变量、U022/U021 回滚及 V021/V022 重放全部 PASS，日志 SHA-256 `e5deff9c54256746c64c721b002aff3d034dfcf98996f6069586083f88f312ca`。
- 固定 Android 镜像 `hhy-android-toolchain:r01-46fb273`：`lintDebug testDebugUnitTest assembleDebug` PASS，331 Gradle tasks，日志 SHA-256 `834c522abe7c5dd606db6399ec6c43f07af217c176350581f3af642a2102a767`。

首次长串远端命令被 PowerShell 提前解释且残留同名数据库清理进程；已将三路执行封装为 `scripts/run_r04_remote_integration.sh`，使用唯一数据库容器名并把 Android Wrapper 固定为跨平台 `sh ./gradlew` 调用。下一阶段把远端原始日志、测试产物和六项逐项断言生成机器可校验证据；在中央执行器验签前不把六项测试标记为 PASS。
