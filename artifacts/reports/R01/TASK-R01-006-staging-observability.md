# TASK-R01-006 · 可观测性与预发布验收报告

- Session：`SES-20260717T141717Z-A01412D7`
- Story：`STORY-R01-003`
- 被测源码：`72de42c90f71438cdf5a2cc274277fecfa7a6cbd`
- 最终镜像：`hhy-backend-r01-smoke:72de42c`
- 镜像 ID：`sha256:94f510ac0beaf0a15ddfeae233e0a4bd99a33c4ac9d3a95282d40b4492e4a85d`
- 结论：`AC-R01-004` PASS；`AC-R01-005`、`AC-R01-006` 保持 NOT_RUN，分别由版本关闭和 APK 任务签署

## 独立预发布环境

- 使用独立 Compose project `hhy-r01-99f7302`、PostgreSQL 卷、Prometheus/Alertmanager 数据卷、回环端口和 `172.31.252.0/24` 子网，未复用 P00 现场证据。
- API、Nginx、PostgreSQL、Prometheus、Alertmanager、隐私最小化告警接收器均为 Up；API、PostgreSQL 和告警接收器健康检查通过。
- PostgreSQL 17.10 已验证 17 个迁移文件，当前 schema 为 `016`；liveness/readiness 均为 `UP`。
- API 以 UID `10001` 运行，MFA secret 目录为 `10001:10001`、模式 `700` 且可写。

## 日志、TraceId 与错误边界

- 200、Spring Security 401 和协议 400 均返回独立的 `X-Request-Id`、`X-Trace-Id`；401/400 JSON `error.traceId` 与响应头一致，RequestId 未冒充 TraceId。
- 业务端口直接访问 `/actuator/health` 返回冻结的 `COMMON-404-NOT_FOUND`，管理端口才暴露健康端点。
- 结构化 `http_request_completed` 样本包含 requestId、traceId、路由模板、status、latency。
- Authorization、Cookie、query password 和错误请求体探针均未进入日志；证据只保存最小化结构化字段。

## RED、业务指标与告警

- Prometheus target `hhy-backend-r01` 为 `up=1`，`promtool` 配置与 8 条规则校验通过。
- HTTP count 存在 1 个聚合序列，histogram bucket 存在 69 个序列，均来自真实流量。
- 8 项业务 Gauge 均存在并为 `0`：Outbox backlog/dead letter、账务不平、对账差异、管理员活跃会话、五分钟认证失败、启用 MFA、幂等快照不完整。
- 8 个 `hhy_business_metric_query_failures_total` 标签序列均为 `0`，业务 Gauge 查询链路可信。
- 真实停止/恢复 API 已取得 `HhyR01BackendDown` 的 `firing` 和 `resolved` 接收回执。
- 使用隔离测试管理员和隔离网络来源产生受控认证失败，已取得 `HhyR01AdminAuthFailureBurst` 的 `firing` 和 `resolved` 接收回执；账户/IP/设备限流同时生效。
- 演练后 4 个隔离测试管理员全部置为 `DISABLED`，剩余 ACTIVE 测试管理员计数为 `0`；不可变失败事实保留用于审计。
- R01 没有资金入口，资金业务单号端到端追踪明确为 N/A；账务和对账不变量 Gauge 仍保持 `0`。

## 回滚与数据库连续性

- 将最终镜像回滚至 `79bbd45` 后 API 健康且平台状态 200，再恢复至 `72de42c` 后健康和平台状态继续通过。
- 回滚前、回滚中、恢复后 PostgreSQL 容器 ID 均为 `0b3c910845409b29a7ba6bc04706d9995cfcc8b5b5f731631cb801203bb58357`。
- 数据卷始终为 `hhy-r01-99f7302_pgdata`，挂载点未变化；演练未回滚数据库迁移或重建数据卷。

## 测试与问题闭环

- Java 21 可观测性专项：18 tests，0 failure，0 error。
- Java 21 加密快照专项：7 tests，0 failure，0 error。
- Java 21 全模块：78 tests，0 failure，0 error，2 个真实 PostgreSQL 条件式测试按预期跳过；PostgreSQL 17 和真实 API 已在 TASK-R01-005 独立通过。
- `PROB-0008`：R01 监控隔离、冻结阈值、真实 TraceId、Gauge 查询失败告警及业务端口 404 已闭环。
- `PROB-0009`：不受支持媒体类型不再误报 500，按冻结契约返回 `COMMON-400-VALIDATION`。
- `PROB-0010`：Base64 尾位导致的加密损坏测试概率性假阴性已改为确定性修改有效密文字节。
- 三份首轮失败日志与修复后的专项/全量日志均已归档，未隐藏失败过程。

## 证据索引

完整现场证据位于 `artifacts/validation/r01-task006-staging/`，包括：

- 源码/镜像/容器/数据卷身份、Compose 校验、迁移、健康和权限；
- 200/401/400/404 响应、结构化日志脱敏样本和敏感探针；
- Prometheus targets/rules、RED、业务 Gauge、查询失败 Counter；
- 两类告警的 firing/resolved 回执、最终 Commit 回滚记录；
- Java 21 专项、全模块和首轮失败日志；
- `SHA256SUMS.txt` 文件级摘要。

TASK-R01-006 满足关闭条件；下一任务为 `TASK-R01-007` Android 测试 APK 与产物追溯。
