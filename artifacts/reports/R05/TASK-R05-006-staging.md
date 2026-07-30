# TASK-R05-006 实名认证可观测性与隔离预发布验收

状态：PASS（`AC-R05-004`）。本任务完成 R05 实名认证结构化日志、TraceId、RED 与业务指标、告警链路、隔离 Staging、运行手册和同库卷回切演练。真实供应商凭据未进入本次环境，也未把隔离故障注入冒充为真实供应商联调。

## 被测基线

- 冻结 Commit：`c2bc1ab632c9b64c4292c325653415a8259a8e51`
- Compose project：`hhy-r05-c2bc1ab`
- 隔离网段：`172.31.249.0/24`（服务器默认网段已有占用，按 Runbook 使用未占用网段）
- 当前镜像：`sha256:5dadec588e5676b430dc4899b9b7221f2d1c371af54fb196211a05de2c1a74d4`
- 上一兼容镜像：`sha256:c2854a85df9e54888e0f2689a82ef694daebd73b06efcd80239f8b58f2791a79`
- PostgreSQL 容器：`f7bf36008c86e9d99a9c04b4fb5c4e6da2b9196591d5c954e58266e0e4d3311f`
- PostgreSQL 卷：`hhy-r05-c2bc1ab_pgdata`

## 自动门禁

| 门禁 | 结果 |
|---|---|
| `scripts/check_r05_observability.py` | `R05_OBSERVABILITY_CONFIG_OK` |
| R05 文档门禁 | PASS，318 个 REST operation，0 个未分配 operation，0 个文档缺口 |
| `BusinessGaugeBinderTest,ObservabilityEndpointsTest` | 6 项，0 failure，0 error，0 skip |
| Compose / Prometheus / Alertmanager | Compose 可解析；Prometheus 配置和 7 条规则有效；Alertmanager 配置有效 |
| PostgreSQL 17.10 | Flyway `028`；201 张含 Flyway 历史表，即 200 张业务表 |
| 构建上下文 | 本地冻结文件与远端构建文件 8 项 SHA-256 全部一致 |

## 指标、链路与脱敏

- 公共状态接口返回 HTTP 200；`X-Request-Id: r05-stage-request-001` 和 `X-Trace-Id: 11223344556677889900aabbccddeeff` 与 `http_request_completed` 日志一致。
- 日志只记录路由模板、状态、耗时、RequestId 和 TraceId；Authorization、Cookie 和查询参数中的敏感探针均未进入容器日志。
- Prometheus target `hhy-backend-r05` 为 UP。
- `hhy_identity_active_sessions`、`hhy_identity_provider_failures_5m`、`hhy_identity_manual_review_pending`、`hhy_identity_private_media_invalid` 四项指标全部存在，空载基线均为 0。

## 告警与故障演练

- 停止隔离 API 后，`HhyR05BackendDown` 进入 firing；恢复 API 后 readiness 为 UP，告警进入 resolved。
- 通过固定负数 ID 注入 1 个隔离用户、1 个已结束实名会话和 4 条 FAILED 供应商请求，指标升至 4，`HhyR05IdentityProviderFailureBurst` 进入 firing。
- 精确删除 4 条请求、1 个会话和 1 个用户后，指标归零、活动告警清空并收到 resolved。
- alert-sink 仅保留告警名、版本、严重度、状态和请求体 SHA-256，不保存 Header、密钥、签名 URL 或供应商响应。

## 回切演练

1. 当前镜像切换到 R05-006 前的 V028 兼容镜像，readiness 为 UP且公共状态接口可用。
2. 回切期间 PostgreSQL 容器 ID、数据卷和 Flyway `028` 完全不变。
3. 恢复当前镜像后 readiness 为 UP、Prometheus target 为 UP，四项 R05 业务指标重新正常采集。
4. 全程未执行降版本 DDL、数据库重建、卷删除或真实供应商操作。

## 环境发现

- 默认 `172.31.248.0/24` 与服务器既有 R03 网络重叠，改用未占用的 `172.31.249.0/24` 后通过；业务代码无需修改。
- 首次测试密钥包含弱标记，安全启动守卫按设计拒绝；改用高强度隔离临时密钥后通过。该结果验证了启动安全门禁有效。

## 证据

机器摘要、源码哈希、告警回执、链路脱敏、指标和回切记录位于 `artifacts/validation/r05-task006-staging/`。

## 结论与边界

`TASK-R05-006` 和 `AC-R05-004` 通过。R05 的正式 Android 测试 APK、桌面交付和真机验收由下一任务 `TASK-R05-007` 完成；本报告不把预发布后端验收冒充 APK 交付。
