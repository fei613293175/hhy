# P00 59 项测试矩阵

`scripts/run_p00_test_matrix.py --list` 只校验 Manifest、全局测试目录和执行映射，所有项目均显示 `NOT_RUN`。

`scripts/run_p00_test_matrix.py --check --evidence-dir <目录> --json-out <报告>` 会执行本机静态、连续性和契约门禁，并递归读取名称以 `.evidence.json` 结尾的外部环境报告（目录中的 JSON 业务响应等产物不会被误当成报告）。外部报告必须符合 `evidence.schema.json`，且必须满足矩阵的更严格执行策略：

- `source_commit` 与当前 `HEAD` 完全一致，或为完整 Git 历史中可证明的祖先且其后只修改发布闭环白名单文件；浅克隆、非祖先以及业务代码、迁移、契约、配置或构建脚本变化均拒绝；
- `status` 为 `PASS`、`executed` 为 `true`、`exit_code` 为 `0`；
- 有起止时间、至少一项通过断言；
- 至少绑定一个位于证据目录内且 SHA256 匹配的真实文件；
- suite 与测试 ID 的矩阵映射一致；
- 59 个 Manifest 测试无额外、遗漏或重复。

`N/A`、`NOT_RUN`、缺失证据、跨 Commit 报告和被篡改产物均为失败。Schema 允许失败状态是为了能保存失败运行记录，不代表这些状态可以通过 P00 门禁。
