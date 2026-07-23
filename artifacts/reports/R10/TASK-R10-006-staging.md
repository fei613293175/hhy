# TASK-R10-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R10 群聊域增加七项只读、无敏感标签的业务 Gauge。
- 在独立 R10 Compose project 中验证 RED、TraceId、日志脱敏、Prometheus 采集、告警 firing/resolved 与 alert-sink 送达。
- 仅回切 `api` 镜像并证明 PostgreSQL 容器和数据卷连续，数据库保持 V036 前向兼容。
- Android 模拟器、截图与候选 APK 留给 TASK-R10-007，不在本任务重复执行。

## 已实现配置

- 群聊总量、在线量、待审核量、收藏量、近 5 分钟联系方式访问量、拒绝量及 R10 Outbox 积压共七项 Gauge。
- `HhyR10BackendDown`、`HhyR10OutboxBacklog`、群聊联系方式拒绝突增、待审核积压以及通用错误率和延迟告警。
- 独立 PostgreSQL、API、Nginx、Prometheus、Alertmanager 和内部 alert-sink；管理端口和 alert-sink 均不向宿主机公开。
- 单一现场验收入口 `scripts/run_r10_staging_acceptance.sh`，强制绑定精确冻结 Commit，自行完成空项目保护、构建、启动、状态等待、采证与逐文件 SHA-256。
- 默认子网 `172.31.242.0/24`、HTTP `38110`、Prometheus `39610` 和 Alertmanager `39611` 均经 `obx-test` 现有 Docker 资源清单核对。

## 验收状态

`AC-R10-004` 已通过，冻结源码 Commit 为 `e1edfa980f510f5d566f6efe49e7401703cf50a7`：

- Java 21 Maven 定向测试 6 项，0 失败、0 错误，完整后端 Reactor `BUILD SUCCESS`。
- R10 静态可观测性门禁、全部 YAML 解析和 Shell 语法校验通过。
- 完整隔离现场演练耗时 245.6 秒；7 项 R10 群聊 Gauge、RED、liveness、readiness 和 Prometheus target 全部通过，业务指标查询失败累计值为 0。
- `HhyR10BackendDown` 与 `HhyR10OutboxBacklog` 均取得 firing、resolved 和 alert-sink 送达回执；4 条测试 Outbox 事实按 `PENDING → PUBLISHING → PUBLISHED` 合法终结且 attempts 为 1。
- 结构化日志与响应 TraceId 可关联，敏感探针未进入证据或日志；结果为 `log_redaction=PASS`。
- 当前镜像 `sha256:14b467583bf0ee06f3cdb2487b22a58401ea17aa710e3d0d36f08e4a9253b57e` 回切至已验证 R09 兼容镜像 `sha256:f4fd97a43940ab5842ef6e1ff7567e51bd9cf29f871533eea2637ba96103c02d` 后再恢复；PostgreSQL 容器、卷 `hhy-r10-staging_pgdata` 全程保持不变。
- 隔离库最新迁移为 V037，查询同时确认 `037|true` 与 `036|true`；V037 是先前后台超级管理员权限修复，R10 群聊 V036 已成功应用且回切期间不变。
- 现场证据已归档至 `artifacts/validation/r10-task006-staging/`，23 个文件（含 `SHA256SUMS`）已完成本地哈希复核，敏感探针扫描零命中。

## 防复发结论

本轮首次调用仅因终端 120 秒上限中断，远端已创建的隔离项目经项目标签和六容器计数精确核验后清理；提高终端上限后同一单一入口一次通过。Maven 调试发现的两项问题均为测试夹具预期同步：新增群聊影响全局聚合值，共享 `content.shared.v1` 合法计入 R10 Outbox；业务实现无需返工。

Android 模拟器、截图和候选 APK 未在本任务触发，按冻结门禁留给 TASK-R10-007。

