# TASK-R03-006 可观测性、Staging 部署与回滚演练

状态：PASS（`AC-R03-004`）。外部供应商、DNS/TLS 和 Android APK 仍由 `TASK-R03-007` 完成，本报告不把隔离测试冒充外部激活证据。

## 被测基线

- 被测 Commit：`2950cb64d0ed5887725bc20afe4c6d148c22335a`
- 隔离 Compose project：`hhy-r03-2950cb6`
- 当前后端镜像：`hhy-backend-r03-smoke:2950cb6`，ID `sha256:84e546cb54c21185db1b3f70079f00698d8c28eecf16dd1067ad6c724165936e`
- 回切后端镜像：`hhy-backend-r03-smoke:650fdee`，ID `sha256:6885120055ea8f1eeb13478a501e16794b958464781045ecfe88ceb2108e7314`
- PostgreSQL 容器 ID：`c813d3a23357a22137f8184311c734004ee06ec06fa7fec3e81babfdac8661a8`
- PostgreSQL 卷：`hhy-r03-2950cb6_pgdata`
- 源码归档 SHA-256：`94825cf58309b2cc38f2688c9f0d4923ad0e22dec2d32feab9dadb3d2f93cf9b`
- 回切源码归档 SHA-256：`039dfda6d107a18db0c61d2d409141504655368aa128269559b4ac3b5b269f68`

## 自动门禁

| 门禁 | 结果 |
|---|---|
| `scripts/check_r03_observability.py` | `R03_OBSERVABILITY_CONFIG_OK` |
| Java 21 / Maven 全模块 | 193 项，0 failure，0 error，2 项环境条件式 skip，BUILD SUCCESS |
| R03 Gauge 与接口定向测试 | 6 项，0 failure，0 error |
| Admin Web | 12 files / 73 tests，typecheck 与 production build PASS |
| PostgreSQL 17.10 | Flyway `019`，200 张业务表，含 Flyway 历史表共 201 张 |
| Prometheus | target `hhy-backend-r03` 为 UP；配置与 8 条规则均由 `promtool` 验证成功 |

## 指标、链路与脱敏

- 真实公共状态请求返回 HTTP 200，并原样关联 `X-Request-Id: r03-stage-request-001` 和 `X-Trace-Id: 0123456789abcdef0123456789abcdef`。
- `http_request_completed` 结构化日志只记录路由模板、状态、耗时、RequestId 和 TraceId；Authorization 和查询参数中的敏感探针未进入日志。
- 真实流量后 RED count 非空，histogram bucket 数为 69。
- `hhy_provider_connection_test_failures_5m`、`hhy_provider_config_untested_active`、`hhy_provider_certificates_expiring_30d`、`hhy_domain_verification_failures` 空载基线均为 0。
- 15 组 `hhy_business_metric_query_failures_total` 均为 0。

## 告警演练

- 停止/恢复隔离 API 后，`HhyR03BackendDown` 分别取得 firing 与 resolved，Alertmanager 活动告警恢复为 0，API 恢复 healthy。
- 插入 4 条 `R03_ALERT_DRILL` 失败探针后，连接失败 Gauge 为 4，`HhyR03ProviderConnectionFailureBurst` 取得 firing；清理这 4 条隔离测试夹具后 Gauge 恢复 0，并取得 resolved。
- alert-sink 只保存告警名、版本、严重度、状态和请求体 SHA-256；未保存 Secret、Header 或供应商响应。

## 回切演练

1. 当前镜像 `2950cb6` 切换为上一个 V019 兼容实现 `650fdee`，API readiness 为 UP。
2. 回切期间 PostgreSQL 容器 ID 与数据卷名称完全未变，Flyway 保持 `019`，业务表保持 200 张。
3. 恢复当前镜像 `2950cb6` 后 API healthy，PostgreSQL 容器/卷仍未变化，`hhy_provider_config_untested_active` 恢复可见且值为 0。
4. 全程未执行 U019、降版本 DDL、数据库重建或卷删除。

## 结论与边界

`TASK-R03-006` 的结构化日志、TraceId、RED、供应商连接指标、业务告警、隔离 Staging 和应用回切要求均已通过。真实短信、实名、支付、出款、存储、证书和 DNS/TLS 状态仍必须在 `TASK-R03-007` 以 VERIFIED 或明确阻断形式登记；连接失败不得激活配置。
