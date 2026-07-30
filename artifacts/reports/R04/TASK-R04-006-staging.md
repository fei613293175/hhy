# TASK-R04-006 可观测性、Staging 部署与回滚演练

状态：PASS（`AC-R04-004`）。真实 R2/OSS 凭据未提供，因此本报告只签署隔离适配器与受控供应商故障演练，不把它冒充真实外部供应商激活；Android APK 由 `TASK-R04-007` 单独完成。

## 被测基线

- 被测 Commit：`4bbf6e23c0a6f1b2773e60182fde4683deeb6d0d`
- 隔离 Compose project：`hhy-r04-4bbf6e2`
- 当前后端镜像 ID：`sha256:88585b641d4247aeb8ac03589d3e38e8d85d7b254ca590e90bc9a5a9473c2d63`
- 回切后端镜像 ID：`sha256:22a0d334b53b924a6ef8a07331795fccec3fddb3854cf3d32d285b52df62b801`
- PostgreSQL 容器 ID：`45fe0be4c1014685d0d8a8098ed70c31ad05d30ba7665344de6adbe0ad0f1ed4`
- PostgreSQL 卷：`hhy-r04-4bbf6e2_pgdata`
- 源码归档 SHA-256：`d240a1b88dc2b4a43d7628b8e7f18783f7fca4c7f53759b8ae2ba7697d3c4b71`
- 现场证据归档 SHA-256：`a1d8a384c329dfb28b4f56172f0602f0357d7156f857330ea36e810eef6ac666`

## 自动门禁

| 门禁 | 结果 |
|---|---|
| `scripts/check_r04_observability.py` | `R04_OBSERVABILITY_CONFIG_OK` |
| Java 21 / Maven 全模块 | 217 项，0 failure，0 error，2 项环境条件式 skip，BUILD SUCCESS |
| R04 Gauge、端点与异常脱敏定向测试 | 7 项，0 failure，0 error |
| PostgreSQL 17.10 | Flyway `022`，200 张业务表，含 Flyway 历史表共 201 张 |
| Prometheus | target `hhy-backend-r04` 为 UP；配置与 8 条规则均由 `promtool` 验证成功 |
| R04 文档与六项专项矩阵 | 文档门禁 PASS；TASK-R04-005 权威矩阵 `6/6 PASS` |

## 指标、链路与脱敏

- 公共状态请求返回 HTTP 200，并关联 `X-Request-Id: r04-stage-request-001` 与 `X-Trace-Id: 0123456789abcdef0123456789abcdef`。
- `http_request_completed` 只记录路由模板、状态、耗时、RequestId 和 TraceId；Authorization、Cookie、查询参数、签名 URL、Secret、对象键和私有内容探针均未进入日志。
- 未处理异常日志只记录事件名、requestId 与异常类型，不记录 Throwable 消息或堆栈；自动测试用伪造签名 URL 验证了该边界。
- RED count/bucket 在真实流量后非空；`hhy_media_upload_failures_5m`、`hhy_media_upload_expired_open`、`hhy_media_delete_pending`、`hhy_storage_migration_blocked` 空载基线均为 0。
- 19 组 `hhy_business_metric_query_failures_total` 均为 0。

## 告警与故障演练

- 停止/恢复隔离 API 后，`HhyR04BackendDown` 分别取得 firing 与 resolved，API 恢复 healthy。
- 创建隔离测试用户和 4 条 FAILED 上传会话后，`HhyR04MediaUploadFailureBurst` 取得 firing；精确删除测试会话与测试用户后指标恢复 0，并取得 resolved。
- R2/OSS 合同、私有签名 URL TTL、跨 Scope 拒绝和供应商异常暂停迁移/游标恢复由 TASK-R04-005 自动化证据覆盖；没有伪造真实供应商成功回执。
- alert-sink 只保存告警名、版本、严重度、状态与请求体 SHA-256，不保存 Header、Secret、签名 URL 或供应商响应。

## 回切演练

1. 当前镜像切换为上一版兼容镜像，API readiness 为 UP且公共状态接口可用。
2. 回切期间 PostgreSQL 容器 ID 与数据卷名称完全未变，Flyway 保持 `022`，业务表保持 200 张。
3. 恢复当前 R04 镜像后 API healthy、Prometheus target 为 UP，PostgreSQL 容器和卷仍未变化。
4. 全程未执行 U022/U021、降版本 DDL、数据库重建、卷删除或真实供应商对象清理。

## 证据

机器证据和逐文件 SHA-256 清单位于 `artifacts/validation/r04-task006-staging/remote/`；主入口为 `machine-summary.txt`、`SHA256SUMS`、`alert-exercise.txt`、`rollback-rehearsal.txt` 和 `log-redaction.txt`。

## 结论与边界

`TASK-R04-006` 的结构化日志、TraceId、RED、R04 业务指标、告警、隔离 Staging 和同库卷应用回切要求均已通过。真实 R2/OSS 激活仍需项目所有者提供隔离凭据后单独验证；这不影响当前机器侧 Staging 门禁关闭，也不替代 `TASK-R04-007` 的正式测试 APK 和真机验收。
