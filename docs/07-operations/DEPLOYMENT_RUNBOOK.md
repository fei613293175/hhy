# Staging 部署与可观测性验收手册

本文只描述经批准后的 Staging 操作。生产部署仍只允许 CI 从受保护且可追溯的 Tag 执行；禁止从开发机直接发布生产。

## 1. 边界与端口

- 应用端口：容器 `8080`，宿主机仅绑定 `127.0.0.1:${HHY_STAGING_HTTP_PORT:-18080}`，由本机反向代理接入。
- 管理端口：容器 `9091`，不映射到宿主机，只允许同一 Docker 网络中的健康检查和 Prometheus 访问。
- Actuator HTTP 白名单只有 `health`、`info`、`prometheus`；JMX 全部关闭，健康响应不展示组件详情。
- PostgreSQL 不映射宿主机端口；Prometheus、Alertmanager 的管理页面只绑定宿主机回环地址。
- 应用日志输出 Logstash JSON。请求完成事件名为 `http_request_completed`，只含 `requestId`、`traceId`、路由模板 `operation`、HTTP `status` 和毫秒 `latency`；不采集请求体、查询参数、Cookie、Authorization、验证码、密码、证件或支付账号。

## 2. 发布前置条件

1. 已批准的不可变镜像 Tag 和代码 Commit 已记录，`HHY_IMAGE_TAG` 禁止使用 `latest`。
2. `POSTGRES_PASSWORD`、`HHY_ADMIN_JWT_SECRET`、`HHY_ADMIN_MFA_ROOT_SECRET`、`HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET` 通过服务器 Secret 管理提供，不写入仓库、Shell 历史或工单正文；三项管理员密钥必须互不相同。
3. 备份任务最近一次成功，且已确认恢复点；数据库迁移只允许前向迁移。
4. 先在仓库根目录执行静态门禁和测试：

   ```bash
   python3 scripts/check_p00_observability.py
   cd services/backend
   ./mvnw -pl boot -am test
   ```

5. 配置渲染必须成功且不出现空密码或 `latest`：

   ```bash
   export HHY_IMAGE_TAG='<approved-immutable-tag>'
   export POSTGRES_PASSWORD='<read-from-secret-manager>'
   export HHY_ADMIN_JWT_SECRET='<read-from-secret-manager>'
   export HHY_ADMIN_MFA_ROOT_SECRET='<read-from-secret-manager>'
   export HHY_ADMIN_IDEMPOTENCY_HMAC_SECRET='<read-from-secret-manager>'
   export HHY_ADMIN_TRUSTED_PROXY_CIDRS='<verified-nginx-direct-peer-ip>/32'
   docker compose -f infra/staging/docker-compose.p00.yml config --quiet
   ```

6. `HHY_ADMIN_TRUSTED_PROXY_CIDRS` 必须来自一次实际 Nginx→后端连接观测，只登记直接代理地址的精确 `/32`（IPv6 使用 `/128`），禁止为省事信任整个 Docker 网段。Nginx 必须清除外部传入的 XFF，并使用 `proxy_set_header X-Forwarded-For $remote_addr;` 重写单层来源。
7. 上线前只读检查 `admin-mfa-secrets` 卷；全新卷由镜像初始化为 UID/GID `10001:10001`。已存在的非空卷必须确认 `/var/lib/hhy/secrets/admin-mfa` 为 `10001:10001`、模式 `700` 且 UID 10001 可写，不合格时停止发布并执行经过审核的一次性属主修正。

## 3. Staging 发布步骤

1. 记录发布前基线：当前镜像摘要、数据库备份/恢复点、健康状态和四项业务 Gauge。
2. 构建镜像时绑定批准 Commit，不复用未知本地缓存：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml build --pull backend alert-sink
   docker image inspect "hhy-backend-p00:${HHY_IMAGE_TAG}" --format '{{json .RepoDigests}}'
   docker image inspect "hhy-alert-sink-p00:${HHY_IMAGE_TAG}" --format '{{json .RepoDigests}}'
   ```

3. 先启动数据库并等待健康，再启动后端。Flyway 在应用启动时执行迁移；迁移失败必须停止发布：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml up -d postgres
   docker compose -f infra/staging/docker-compose.p00.yml up -d --no-deps backend
   docker compose -f infra/staging/docker-compose.p00.yml ps
   ```

4. 在容器内部验证管理端口，不把端口临时发布到公网：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml exec -T backend \
     curl -fsS http://127.0.0.1:9091/actuator/health/liveness
   docker compose -f infra/staging/docker-compose.p00.yml exec -T backend \
     curl -fsS http://127.0.0.1:9091/actuator/health/readiness
   docker compose -f infra/staging/docker-compose.p00.yml exec -T backend \
     sh -ec 'test "$(id -u)" = 10001; test -w /var/lib/hhy/secrets/admin-mfa; stat -c "%u:%g %a" /var/lib/hhy/secrets/admin-mfa'
   ```

5. 启动 Prometheus 与 Alertmanager，并验证抓取目标：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml up -d alert-sink alertmanager prometheus
   curl -fsS 'http://127.0.0.1:19090/api/v1/query?query=up%7Bjob%3D%22hhy-backend-p00%22%7D'
   ```

6. 逐项查询业务 Gauge；正常空载基线应为 `0`，非零值必须关联工单后才能继续：

   ```text
   hhy_outbox_backlog
   hhy_outbox_dead_letter
   hhy_ledger_unbalanced_transactions
   hhy_reconciliation_open_differences
   ```

7. 从宿主机回环端口执行平台状态、版本检查和一条受保护接口的冒烟，核对 2xx/4xx、`X-Request-Id`、`X-Trace-Id`。
8. 验证结构化日志：每行能被 JSON 解析，包含五个请求字段，且敏感探针值没有出现在输出中。禁止把完整日志粘贴到公开工单。

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml logs --since=10m --no-log-prefix backend \
     | jq -c 'select(.message == "http_request_completed") | {requestId,traceId,operation,status,latency}'
   ```

9. 产生冒烟流量后确认 HTTP 计数和 P95 所需 histogram bucket 均真实存在；查询结果为空、`NaN` 或为零时不得通过：

   ```bash
   curl -fsS --get 'http://127.0.0.1:19090/api/v1/query' \
     --data-urlencode 'query=count(http_server_requests_seconds_count{job="hhy-backend-p00"})'
   curl -fsS --get 'http://127.0.0.1:19090/api/v1/query' \
     --data-urlencode 'query=count(http_server_requests_seconds_bucket{job="hhy-backend-p00"})'
   ```

10. 每次告警演练都必须同时取得 Alertmanager 的 `firing/resolved` 状态和接收器送达回执。接收器仅保存告警名、版本、严重度、状态与原始请求 SHA256，不保存完整标签、Header 或 Secret：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml exec -T alert-sink \
     tail -n 20 /data/deliveries.jsonl | jq -c .
   ```

   通过条件是同一告警至少存在一条 `firing` 和一条 `resolved` 回执；只有 Alertmanager 页面状态、没有 `deliveries.jsonl` 送达记录时不得签字。

## 4. 告警与验收

- `HhyBackendDown`：15 秒无抓取，立即阻断发布。
- `HhyHighServerErrorRate`：1 分钟 5xx 比例超过 5%。
- `HhyHighP95Latency`：P95 连续 2 分钟超过 1 秒。
- Outbox backlog 超过 100 持续 2 分钟、任意 dead letter、任意账务不平立即处理。
- 任意未解决对账差异持续 1 分钟触发告警。

验收证据至少保存：镜像摘要与 Commit、Compose 配置校验结果、迁移结果、liveness/readiness、Prometheus target、四项 Gauge、告警演练、结构化日志脱敏抽样、回滚演练结果。证据必须脱敏并写入批准的 P00 报告路径。

## 5. 停止条件

出现以下任一情况立即停止流量切换并进入回滚手册：迁移失败、readiness 非 UP、5xx/延迟越线、dead letter、账务不平、未知对账差异、日志无法解析、敏感字段进入日志、镜像摘要与批准记录不一致。
