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

当前为实现阶段。`AC-R08-004` 保持 `NOT_RUN`，不得在精确冻结 Commit 的现场证据、告警双向回执和同库同卷回切全部通过前提前签署 PASS。

最终证据将归档到 `artifacts/validation/r08-task006-staging/`，本报告随后补录冻结 Commit、镜像 ID、PostgreSQL 容器/卷、Flyway 版本、告警回执、机器结论与证据 SHA-256。
