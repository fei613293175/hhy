# TASK-R08-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R08 项目域增加七项只读、无敏感标签的业务 Gauge。
- 在独立 R08 Compose project 中验证 RED、TraceId、日志脱敏、Prometheus 采集、告警 firing/resolved 与 alert-sink 送达。
- 仅回切 `api` 镜像并证明 PostgreSQL 容器和数据卷连续，数据库保持 V034 前向兼容。
- Android 模拟器、截图与候选 APK 留给 TASK-R08-007，不在本任务重复执行。

## 已实现配置

- 项目总量、在线量、待审核量、收藏量、近 5 分钟联系方式访问量、拒绝量及 R08 Outbox 积压共七项 Gauge。
- `HhyR08BackendDown`、`HhyR08OutboxBacklog`、项目联系方式拒绝突增、待审核积压以及通用错误率和延迟告警。
- 独立 PostgreSQL、API、Nginx、Prometheus、Alertmanager 和内部 alert-sink；管理端口和 alert-sink 均不向宿主机公开。
- 单一现场验收入口 `scripts/run_r08_staging_acceptance.sh`，强制绑定精确冻结 Commit 并生成逐文件 SHA-256。

## 验收状态

`AC-R08-004` 已通过，冻结源码 Commit 为 `7d6eedf4fdfc4b5b2143f28eafc682bb1c72445e`：

- Java 21 后端定向 MODULE：6 项测试，0 失败、0 错误、0 跳过，BUILD SUCCESS；原始日志 SHA-256 为 `a4cca86a499543495e1fd78e8fc755b781d1c096c36d90a226f86dbc0697d428`。
- Prometheus 配置和 7 条规则均通过 `promtool`；7 项 R08 项目 Gauge、RED、liveness、readiness 和 Prometheus target 均通过。
- `HhyR08BackendDown` 与 `HhyR08OutboxBacklog` 均取得 firing、resolved 和 alert-sink 送达回执；4 条测试 Outbox 事实按 `PENDING → PUBLISHING → PUBLISHED` 合法终结且 attempts 为 1。
- 结构化日志与响应 TraceId 可关联，敏感探针未进入日志；结果为 `log_redaction=PASS`。
- 当前镜像 `sha256:6c1bdefff8231693d67e75d804f769c18960e817b99de506bf7fd15c39731dea` 回切至已验证兼容镜像后再恢复；PostgreSQL 容器 `12545e4d6bfa6593bcc5f7def02fbd101c86ba541b70004b1675e8761104d110`、卷 `hhy-r08-staging_pgdata` 和 Flyway V034 全程保持不变。
- 现场证据已归档至 `artifacts/validation/r08-task006-staging/`，`SHA256SUMS` 对 22 个证据文件复核通过。

Android 模拟器、截图和候选 APK 未在本任务触发，按冻结门禁留给 TASK-R08-007。
