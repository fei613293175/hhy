# TASK-R06-006 可观测性、Staging 部署与回滚演练

状态：`PASS`（`AC-R06-004`）。本任务只签署 R06 内容、首页与 CMS 的机器侧可观测性和隔离预发布门禁；Android 候选 APK、桌面交付和项目所有者异步真机反馈由 `TASK-R06-007` 单独完成。

## 被测基线

- 冻结 Commit：`0e528d17d7cd5c6d972958bd566d6e91f05f37f7`
- 隔离 Compose project：`hhy-r06-be62b727`
- 当前后端镜像 ID：`sha256:d0e5e77fbb8d7856861d8b32688cf4740c19d49e18b6d012d3d92d25ea1f5db7`
- 兼容回切镜像 ID：`sha256:63cff314b2e6397226fcc87c4d5d1778e7d762662916828b261706aa3bd0bb07`
- PostgreSQL 容器 ID：`55b6f8199b63fb5c0ad3aed005c2fdb71691394ee74179da1b00430147abb80d`
- PostgreSQL 卷：`hhy-r06-be62b727_pgdata`
- 网络：`172.31.246.0/24`
- 回环端口：API `38106`、Prometheus `39590`、Alertmanager `39593`

`be62b727` 构建了后端镜像；其后提交仅固化现场验收脚本和连续性事实。Git 对 `be62b727..0e528d17` 的 `services/backend/**` 与 `infra/staging/r06-smoke/**` 精确比较为零差异，因此后端镜像摘要被审计复用，不重复 Maven 构建。

## 自动门禁

| 门禁 | 结果 |
|---|---|
| `scripts/check_r06_observability.py` | `R06_OBSERVABILITY_CONFIG_OK` |
| Java 21 R06 Gauge 与 Prometheus 端点定向测试 | 6 项，0 failure，0 error，`BUILD SUCCESS` |
| Compose / Prometheus / Alertmanager | Compose 可渲染；Prometheus 配置和 6 条规则有效；Alertmanager 1 个接收器有效 |
| PostgreSQL 17.10 | Flyway `030`；200 张业务表，含 Flyway 历史表共 201 张 |
| 严格连续性 Doctor 与 V1.2.3 组合门禁 | 0 error，0 warning |
| 现场验收脚本 | `R06_STAGING_ACCEPTANCE_PASS` |

## 日志、TraceId 与指标

- 公共状态请求返回 HTTP 200，响应头包含 `X-Request-Id: r06-stage-request-001` 与 `X-Trace-Id: 11223344556677889900aabbccddeeff`。
- 同一请求可关联到结构化 `http_request_completed` 日志；日志只记录路由模板、状态、延迟、RequestId 和 TraceId。
- Authorization、Cookie 和查询参数同时注入探针 `R06_SENSITIVE_PROBE_7F3A`，容器日志扫描结果为 `R06_LOG_REDACTION_OK`。
- Prometheus target `hhy-backend-r06` 为 UP，RED count/bucket 已产生真实流量。
- `hhy_content_online_count`、`hhy_content_review_pending`、`hhy_content_outbox_backlog`、`hhy_home_enabled_modules` 四项业务 Gauge 均存在；全部 `hhy_business_metric_query_failures_total` 为 0。

## 告警与不可变事件演练

- 停止并恢复隔离 API 后，`HhyR06BackendDown` 取得 firing 与 resolved 回执。
- 四条按冻结 Commit 唯一命名的 `CONTENT/PENDING` 事件使 `hhy_content_outbox_backlog` 超过 3，`HhyR06ContentOutboxBacklog` 取得 firing 回执。
- V010 触发器正确拒绝删除 Outbox 事实；测试事件随后严格按 `PENDING → PUBLISHING（attempts+1）→ PUBLISHED（published_at）` 终结，四条均为 `PUBLISHED|1|true`，Gauge 恢复为 0并取得 resolved 回执。
- Alert sink 只保存告警名、版本、严重度、状态与请求体 SHA-256，不保存 Header、Secret 或业务正文。

## 回切演练

1. 当前镜像切换为已验证且兼容 V030 的旧镜像，readiness 和公共状态接口恢复。
2. 回切期间 PostgreSQL 容器 ID、数据卷与 Flyway `030` 完全不变。
3. 恢复当前镜像后 readiness、公共状态、Prometheus target 和四项 R06 Gauge 再次通过。
4. 全程未执行 U030/U029、降版本 DDL、数据库重建、卷删除或 Outbox 事实删除。

## 证据与结论

机器证据位于 `artifacts/validation/r06-task006-staging/`。远端 `SHA256SUMS` 全部通过，复制回本机后按文件名再次计算 SHA-256 也全部一致。主入口为 `machine-summary.txt`、`alert-exercise.txt`、`alert-deliveries.jsonl`、`outbox-test-events.txt`、`rollback-rehearsal.txt` 和 `source-sha256.txt`。

`TASK-R06-006` 的结构化日志、TraceId、RED、四项 R06 业务 Gauge、告警 firing/resolved、隔离 Staging 与同库卷应用回切要求全部通过，P0/P1 缺陷为 0。未收到项目所有者真机反馈不会阻断后续任务，但任何版本的 `owner_physical_test` 仍不得伪造为 `PASS`，且继续阻断对应正式验收和生产激活。
