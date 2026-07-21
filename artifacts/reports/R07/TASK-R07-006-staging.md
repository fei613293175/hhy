# TASK-R07-006 可观测性、隔离 Staging 与回滚演练

状态：`PASS`（`AC-R07-004`）。本任务签署 R07 搜索、发布者主页和联系方式访问链路的机器侧可观测性与隔离预发布门禁；Android 模拟器、截图、候选 APK、桌面交付和项目所有者异步真机反馈由后续最终候选任务完成。

## 被测基线

- 冻结 Commit：`417b6cc9bc5f1b56adc280db5375358100bf23ae`
- 隔离 Compose project：`hhy-r07-staging`
- 当前后端镜像 ID：`sha256:00dacf80e349e4f6b4fad0de3493eff39ec56db88d7d96675cfbb2158ff1bd37`
- 兼容回切镜像 ID：`sha256:e4627225681376ad79051bd1af03958687d61aa44366f1a6aaf2ac537fd0c9f9`
- PostgreSQL 容器 ID：`409d15193d92fc672adbdccc2e398d5ceb9f88c66512f9d3e1c3496c6f56df1f`
- PostgreSQL 卷：`hhy-r07-staging_pgdata`
- 网络：`172.31.245.0/24`（默认网段已被保留的 R04 验证环境占用，因此现场显式选用空闲隔离网段）
- 回环端口：API `38107`、Prometheus `39591`、Alertmanager `39594`

## 自动门禁

| 门禁 | 结果 |
|---|---|
| `scripts/check_r07_observability.py` | `R07_OBSERVABILITY_CONFIG_OK` |
| Java 21 Gauge 与 Prometheus 端点定向测试 | 6 项，0 failure，0 error，`BUILD SUCCESS` |
| Compose / Prometheus / Alertmanager | Compose 可部署；Prometheus 配置与 7 条规则有效；Alertmanager 配置有效 |
| PostgreSQL 17.10 | Flyway `032`；200 张业务表，含 Flyway 历史表共 201 张 |
| 现场验收脚本 | `R07_STAGING_ACCEPTANCE_OK` |
| 证据完整性 | 远端 22 个受签文件和本机回收副本 SHA-256 全部一致 |

## 日志、TraceId 与业务指标

- 公共状态请求返回 HTTP 200，响应头包含 `X-Request-Id: r07-stage-request-001` 与 `X-Trace-Id: 223344556677889900aabbccddeeff11`。
- 同一请求可关联到结构化 `http_request_completed` 日志；敏感探针扫描结果为 `R07_LOG_REDACTION_OK`。
- Prometheus target 为 UP，真实请求已产生 RED 指标。
- 六项 R07 业务 Gauge 均存在：搜索历史行数、有效热词、活跃发布者、5 分钟联系方式访问、5 分钟联系方式拒绝、R07 Outbox backlog。
- 所有业务指标查询失败计数均为 0。

## 告警与不可变事件演练

- 停止并恢复隔离 API 后，`HhyR07BackendDown` 取得 firing 与 resolved 回执。
- 四条按冻结 Commit 唯一命名的 `SEARCH_HISTORY/PENDING` 测试事件触发 `HhyR07OutboxBacklog`，随后取得 resolved 回执。
- 测试事件严格按 `PENDING → PUBLISHING（attempts+1）→ PUBLISHED（published_at）` 终结，四条均为 `PUBLISHED|1|true`；未删除 Outbox 事实。
- Alert sink 仅保存受控告警字段与请求摘要，不保存 Secret、认证 Header 或业务正文。

## 回切演练

1. 当前镜像切换为兼容 Flyway V032 的旧镜像，readiness 与公共状态接口恢复。
2. 回切期间 PostgreSQL 容器 ID、数据卷和 Flyway `032` 完全不变。
3. 恢复当前镜像后，健康状态、公共接口、Prometheus target 与 R07 Gauge 再次通过。
4. 全程未执行降版本迁移、破坏性 DDL、数据库重建、卷删除或 Outbox 事实删除。

## 证据与结论

机器证据位于 `artifacts/validation/r07-task006-staging/`，主入口为 `machine-summary.txt`、`alert-exercise.txt`、`alert-deliveries.jsonl`、`outbox-test-events.txt`、`rollback-rehearsal.txt`、`source-sha256.txt` 与 `SHA256SUMS`。

`TASK-R07-006` 的结构化日志、TraceId、RED、六项业务 Gauge、七条告警规则、关键告警 firing/resolved、隔离 Staging 和同库同卷应用回切要求全部通过，P0/P1 缺陷为 0。项目所有者尚未提交真机反馈不会阻断后续开发，也不会被伪造为 `owner_physical_test=PASS`。
