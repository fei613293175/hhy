# TASK-R09-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R09 App 域增加七项只读、无敏感标签的业务 Gauge。
- 在独立 R09 Compose project 中验证 RED、TraceId、日志脱敏、Prometheus 采集、告警 firing/resolved 与 alert-sink 送达。
- 仅回切 `api` 镜像并证明 PostgreSQL 容器和数据卷连续，数据库保持 V035 前向兼容。
- Android 模拟器、截图与候选 APK 留给 TASK-R09-007，不在本任务重复执行。

## 已实现配置

- App 总量、在线量、待审核量、收藏量、近 5 分钟联系方式访问量、拒绝量及 R09 Outbox 积压共七项 Gauge。
- `HhyR09BackendDown`、`HhyR09OutboxBacklog`、App 联系方式拒绝突增、待审核积压以及通用错误率和延迟告警。
- 独立 PostgreSQL、API、Nginx、Prometheus、Alertmanager 和内部 alert-sink；管理端口和 alert-sink 均不向宿主机公开。
- 单一现场验收入口 `scripts/run_r09_staging_acceptance.sh`，强制绑定精确冻结 Commit，自行完成空项目保护、构建、启动、状态等待、采证与逐文件 SHA-256。
- 默认子网 `172.31.243.0/24`、HTTP `38109`、Prometheus `39597` 和 Alertmanager `39596` 均经 `obx-test` 现有 Docker 资源清单核对。

## 验收状态

`AC-R09-004` 已通过，冻结源码 Commit 为 `d056c54fb4a9e92f299a3ba78f0bc043706bd58b`：

- Java 21 后端受影响 MODULE：358 项测试，0 失败、0 错误、13 项既有条件跳过，BUILD SUCCESS；可观测性定向测试另有 6 项全部通过。
- R09 自启动、网络端口和 Prometheus 首次抓取顺序回归 5 项全部通过；静态配置门禁通过。
- Prometheus 配置和 7 条规则均通过 `promtool`；7 项 R09 App Gauge、RED、liveness、readiness 和 Prometheus target 均通过，业务指标查询失败累计值为 0。
- `HhyR09BackendDown` 与 `HhyR09OutboxBacklog` 均取得 firing、resolved 和 alert-sink 送达回执；4 条测试 Outbox 事实按 `PENDING → PUBLISHING → PUBLISHED` 合法终结且 attempts 为 1。
- 结构化日志与响应 TraceId 可关联，敏感探针未进入证据或日志；结果为 `log_redaction=PASS`。
- 当前镜像 `sha256:f4fd97a43940ab5842ef6e1ff7567e51bd9cf29f871533eea2637ba96103c02d` 回切至已验证 R08 兼容镜像 `sha256:6c1bdefff8231693d67e75d804f769c18960e817b99de506bf7fd15c39731dea` 后再恢复；PostgreSQL 容器 `a2067335c8125c3d9b3ee6c9536f7e0de6e35f91eb81fe5f456c9eff5eed684f`、卷 `hhy-r09-staging_pgdata` 和 Flyway V035 全程保持不变。
- 现场证据已归档至 `artifacts/validation/r09-task006-staging/`，`SHA256SUMS` 对 22 个证据文件复核通过，敏感词扫描零命中。

## 防复发结论

首次全新环境演练暴露的入口未自启动、复制子网/端口冲突和 Prometheus ready/首次抓取竞态均收敛到既有单一入口及 `PROB-0095`，没有新增人工前置流程。失败轮次只创建并清理带 `hhy-r09-staging` 标签的临时资源，未触碰公网或 R01—R08 环境。

Android 模拟器、截图和候选 APK 未在本任务触发，按冻结门禁留给 TASK-R09-007。
