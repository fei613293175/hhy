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

### R01 管理端幂等响应密钥轮换

- V016 起，七个管理端安全 POST 的首次成功响应会以 AES-256-GCM 密文写入 `hhy.idempotency_records`。`response_type` 与 `response_payload_ciphertext` 必须作为一对同时写入；任一列有数据时，U016 会硬阻断回滚。
- 快照密钥由 MFA root secret 通过独立固定域派生，但密钥版本由 `hhy.admin-security.mfa-root-secret-version` 明确标识。轮换时先把旧版本和旧 root secret 写入 `hhy.admin-security.mfa-previous-root-secrets`（格式 `version=secret`，多项用分号分隔），再切换当前版本与当前 root secret。
- 旧 root secret 至少保留 24 小时，且不得短于最长幂等 TTL；确认旧版本快照全部过期后才能移除。未知版本、认证标签失败、类型不匹配或无法反序列化都必须闭锁为 5xx，禁止改写原快照或退回业务重算。
- 被密码修改或登出撤销的旧 ACCESS Bearer 只允许在原令牌仍未过期、数据库 `sid/sub/jti` 严格匹配、请求为批准的安全 POST 且携带原 `X-Idempotency-Key` 时读取已完成快照。该认证只有 `admin.idempotency.replay` 标记，不具备读权限或业务写权限；无快照/未完成快照返回 401，摘要冲突返回 409，均不得产生副作用。
- 日志、工单和验收证据不得包含 root secret、previous root secret、完整快照密文、nonce、认证标签、MFA QR secret、Bearer 或幂等请求摘要。
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
- `HhyHighServerErrorRate`：1 分钟 5xx 比例超过冻结配置 `2%`。
- `HhyHighP95Latency`：P95 连续 2 分钟超过冻结配置 `500ms`。
- Outbox backlog 超过 100 持续 2 分钟、任意 dead letter、任意账务不平立即处理。
- 任意未解决对账差异持续 1 分钟触发告警。

验收证据至少保存：镜像摘要与 Commit、Compose 配置校验结果、迁移结果、liveness/readiness、Prometheus target、四项 Gauge、告警演练、结构化日志脱敏抽样、回滚演练结果。证据必须脱敏并写入批准的 P00 报告路径。

## 5. 停止条件

出现以下任一情况立即停止流量切换并进入回滚手册：迁移失败、readiness 非 UP、5xx/延迟越线、dead letter、账务不平、未知对账差异、日志无法解析、敏感字段进入日志、镜像摘要与批准记录不一致。

## 6. R01 隔离预发布验收

R01 使用 `infra/staging/r01-smoke/docker-compose.yml`，不得用 P00 的历史证据代替。部署时用独立 Compose project、数据库卷、端口和可配置子网；Nginx 的实际静态地址必须与 `HHY_R01_TRUSTED_PROXY_CIDRS` 的精确 `/32` 一致。示例：

```bash
export HHY_SMOKE_ID='<approved-r01-commit>'
export HHY_R01_SMOKE_SUBNET='172.31.251.0/24'
export HHY_R01_NGINX_IP='172.31.251.10'
export HHY_R01_API_IP='172.31.251.20'
export HHY_R01_POSTGRES_IP='172.31.251.40'
export HHY_R01_TRUSTED_PROXY_CIDRS='172.31.251.10/32'
docker compose -p hhy-r01-staging -f infra/staging/r01-smoke/docker-compose.yml config --quiet
docker compose -p hhy-r01-staging -f infra/staging/r01-smoke/docker-compose.yml up -d --build
```

必须执行并归档：

1. `promtool check config /etc/prometheus/prometheus.yml` 和 `promtool check rules /etc/prometheus/r01-alerts.yml`。
2. 2xx 与 Spring Security 401/403 响应的 `X-Request-Id`、`X-Trace-Id`、JSON `error.traceId` 和 `http_request_completed` 日志一致；RequestId 与 TraceId 不得互相冒充。
3. RED count/bucket 有真实流量，八项 `hhy_*` 业务 Gauge 存在；不变量 Gauge 和 `hhy_business_metric_query_failures_total` 必须为 0。
4. 真实停止/恢复 `api`，取得 `HhyR01BackendDown` 的 firing/resolved 回执；对认证失败告警使用测试管理员产生受控失败，恢复窗口后取得 resolved，禁止攻击生产账号。
5. 日志敏感探针不得出现 Authorization、Cookie、密码、MFA code、root secret、密文快照或幂等摘要。
6. R01 没有资金入口，资金业务单号端到端追踪在本版本明确记为 N/A；账务不变量 Gauge 仍必须保持 0。

R01 证据统一写入 `artifacts/validation/r01-task006-staging/`，并绑定精确 Commit、镜像 ID、数据库容器/卷连续性与每个证据文件的 SHA-256。只有现场证据齐全后才能把 `AC-R01-004` 签为 PASS。

## 7. R02 隔离预发布验收

R02 使用 `infra/staging/r02-smoke/docker-compose.yml`，必须采用独立 Compose project、端口、子网和数据卷，不复用 R01 的运行证据。执行前先运行 `python3 scripts/check_r02_observability.py`，然后以被测 Commit 作为不可变 `HHY_SMOKE_ID` 渲染配置。除三项管理员密钥外，必须分别注入 `HHY_USER_JWT_SECRET`、`HHY_USER_TOKEN_HMAC_SECRET` 和 `HHY_USER_SNAPSHOT_ROOT_SECRET`；六项应用密钥必须相互独立且满足启动安全策略。测试 Secret 只能注入隔离进程环境，不得写入仓库、报告或 Shell 历史。

R02 现场演练至少覆盖：

1. `mvn verify` 与 PostgreSQL 17 的完整迁移脚本均以退出码 0 结束，日志必须包含 `POSTGRESQL_MIGRATION_SMOKE PASS` 和最终 199 张表。
2. Prometheus target `hhy-backend-r02` 为 UP；RED count/bucket 非空，并存在 `hhy_user_active_sessions`、`hhy_user_security_challenge_failures_5m`、`hhy_user_sms_expired_unused` 三项业务 Gauge。
3. 对安全挑战失败计数使用隔离测试数据触发 `HhyR02UserChallengeFailureBurst`，对 API 停止/恢复触发 `HhyR02BackendDown`；两个告警均必须取得 firing、resolved 和 alert-sink 送达回执。
4. 验证 2xx、401、403、429 和 5xx 的 `requestId`、`traceId`、状态码与结构化完成日志可关联，且密码、验证码、挑战答案、Bearer、Cookie 和 Secret 探针均不出现在日志中。
5. 短信供应商缺失、供应商异常、网络超时、重复提交、相同幂等键不同请求体、验证码重复消费和会话并发撤销均必须闭锁；不得生成测试验证码或伪造外部供应商回执。
6. 演练回滚时只回退应用镜像，数据库坚持前向修复；隔离项目可以在证据归档后 `down -v` 清理。现有公网开发环境不属于本演练范围。

R02 证据统一写入 `artifacts/validation/r02-task006-staging/` 并绑定精确 Commit 和 SHA-256。以上现场证据齐全后才可把 `AC-R02-004` 签为 PASS；APK 安装和四方哈希仍由 TASK-R02-007 单独完成。

## 8. R03 隔离预发布验收

R03 使用 `infra/staging/r03-smoke/docker-compose.yml`，必须采用独立 Compose project、回环端口、`172.31.248.0/24` 默认子网和独立数据卷；不得改动或重启现有公网、R01、R02 环境。执行前运行 `python3 scripts/check_r03_observability.py`，并用被测 Commit 作为不可变 `HHY_SMOKE_ID`。测试 Secret 只在隔离进程环境注入，不写入仓库、命令输出或证据文件。

R03 现场演练至少覆盖：

1. Java 21 全量测试、管理端测试与生产构建通过；PostgreSQL 17 完整迁移以退出码 0 结束，最终基线为 200 张表。
2. Prometheus target `hhy-backend-r03` 为 UP，RED count/bucket 非空，并存在 `hhy_provider_connection_test_failures_5m`、`hhy_provider_config_untested_active`、`hhy_provider_certificates_expiring_30d`、`hhy_domain_verification_failures` 四项业务 Gauge；正常空载时后两项不变量与业务指标查询失败计数必须为 0。
3. 停止/恢复 API 触发 `HhyR03BackendDown`；插入隔离失败探针数据触发 `HhyR03ProviderConnectionFailureBurst`。两项告警均需取得 firing、resolved 和 alert-sink 送达回执。
4. 2xx/401/403/5xx 响应中的 `requestId`、`traceId`、状态码与 `http_request_completed` 日志可关联。日志不得包含 SecretRef 解析值、证书原文/私钥、密码、Bearer、Cookie 或供应商敏感响应。
5. 连接测试超时、秘密不可用、供应商异常和连接器缺失均按安全错误分类失败；连接测试失败不得激活配置，也不得伪造成功回执。
6. 配置激活和回滚演练必须验证双人审批、目标资源绑定、成功连接测试前置、原 ACTIVE 版本状态切换、审计记录和幂等重放。失败步骤不得修改当前 ACTIVE 配置。
7. 应用回切只替换 `api` 镜像，保持同一 PostgreSQL 容器、卷和 V019 迁移；禁止执行 U019 或降版本 DDL。证据归档完成后才允许对本次隔离项目执行 `down -v`。

R03 证据统一写入 `artifacts/validation/r03-task006-staging/`，绑定被测 Commit、两个不可变镜像 ID、数据库容器/卷连续性和证据 SHA-256。现场证据齐全后才能关闭 TASK-R03-006；真实供应商测试环境、外部 DNS/TLS、Android APK 和项目所有者真机验收仍由 TASK-R03-007 单独完成。

## 9. R04 隔离预发布验收

R04 使用 `infra/staging/r04-smoke/docker-compose.yml`，必须采用独立 Compose project、回环端口、`172.31.247.0/24` 默认子网和独立数据卷；不得改动或重启现有公网、R01、R02、R03 环境。执行前运行 `python3 scripts/check_r04_observability.py`，并用被测 Commit 作为不可变 `HHY_SMOKE_ID`。测试 Secret 只在隔离进程环境注入，不写入仓库、命令输出或证据文件。

R04 现场演练至少覆盖：

1. Java 21 全量测试与生产构建通过；PostgreSQL 17 完整迁移以退出码 0 结束，最终基线为 200 张表且 Flyway 已到 V022。
2. Prometheus target `hhy-backend-r04` 为 UP，RED count/bucket 非空，并存在 `hhy_media_upload_failures_5m`、`hhy_media_upload_expired_open`、`hhy_media_delete_pending`、`hhy_storage_migration_blocked` 四项业务 Gauge；正常空载与业务指标查询失败计数必须为 0。
3. 停止/恢复 API 触发 `HhyR04BackendDown`；插入四条隔离的 FAILED 上传会话触发 `HhyR04MediaUploadFailureBurst`，清除隔离数据后取得 resolved。两项告警均需取得 firing、resolved 和 alert-sink 送达回执。
4. 上传会话创建、完成、删除与认证拒绝响应中的 `requestId`、`traceId`、状态码和 `http_request_completed` 日志可关联。日志不得包含签名 URL、查询参数、Secret、对象键、文件内容、SHA-256、Bearer、Cookie、密码或供应商敏感响应；异常日志只允许事件名、requestId 和异常类型。
5. R2/OSS 合同、私有签名 URL TTL、跨 Scope 拒绝、供应商异常暂停迁移以及游标恢复必须由自动化测试验证。未提供真实供应商凭据时只能签署受控适配器故障演练，禁止伪造真实供应商成功回执。
6. 应用回切只替换 `api` 镜像，保持同一 PostgreSQL 容器、卷和 V022 迁移；禁止执行 U022、降版本 DDL或删除媒体/审计记录。恢复当前镜像后再次验证 readiness、RED、四项 R04 Gauge 和数据库容器/卷连续性。
7. 证据统一写入 `artifacts/validation/r04-task006-staging/`，绑定被测 Commit、不可变镜像 ID、数据库容器/卷、Flyway 版本与每个证据文件 SHA-256。TASK-R04-006 只关闭机器侧 Staging 门禁；Android APK 和项目所有者真机验收仍由 TASK-R04-007 单独完成。

## 10. R05 实名认证隔离预发布验收

R05 使用 `infra/staging/r05-smoke/docker-compose.yml`，采用独立 Compose project、仅回环发布端口、`172.31.248.0/24` 默认子网和独立数据卷；不得修改或重启公网及 R01–R04 环境。执行前运行 `python3 scripts/check_r05_observability.py`，并以被测 Commit 作为不可变 `HHY_SMOKE_ID`。测试 Secret 只在隔离进程环境注入，禁止写入仓库、命令输出或证据。

R05 现场演练至少覆盖：

1. Java 21 全量测试与生产构建通过；PostgreSQL 17 完整迁移到 V028，业务表保持 200 张。
2. Prometheus target `hhy-backend-r05` 为 UP，RED count/bucket 非空，并存在 `hhy_identity_active_sessions`、`hhy_identity_provider_failures_5m`、`hhy_identity_manual_review_pending`、`hhy_identity_private_media_invalid`；空载时后三项和业务指标查询失败计数必须为 0。
3. 停止/恢复 API 触发 `HhyR05BackendDown`；插入四条隔离 `FAILED`/`TIMED_OUT` 供应商请求触发 `HhyR05IdentityProviderFailureBurst`，清理隔离数据后取得 resolved；两项均须取得 alert-sink 送达回执。
4. 实名接口和回跳请求的 HTTP 状态、`requestId`、`traceId` 与 `http_request_completed` 可关联；日志不得包含姓名、身份证号、照片地址/内容、供应商回包、APPCODE、Secret、Bearer、Cookie 或请求正文。
5. 供应商超时、订单错配、私有证据摘要错配、重复幂等键和并发重复回跳必须由 TASK-R05-005 自动化证据验证，禁止伪造供应商成功回执。
6. 应用回切只替换 `api` 镜像，保持同一 PostgreSQL 容器、数据卷和 V028；禁止执行 U028–U023、降版本 DDL、删除实名媒体/复核/敏感访问审计。恢复当前镜像后再次验证 readiness、RED、四项 R05 Gauge 和数据库连续性。
7. 证据统一写入 `artifacts/validation/r05-task006-staging/`，绑定被测 Commit、两个不可变镜像 ID、数据库容器/卷、Flyway V028、告警回执和逐文件 SHA-256。Android APK 与真机验收仍由 TASK-R05-007 单独完成。

## 11. R06 内容与首页隔离预发布验收

R06 使用 `infra/staging/r06-smoke/docker-compose.yml`，采用独立 Compose project、仅回环发布端口、`172.31.246.0/24` 默认子网和独立数据卷；不得修改或重启公网及 R01–R05 环境。执行前运行 `python3 scripts/check_r06_observability.py`，并以被测 Commit 作为不可变 `HHY_SMOKE_ID`。六项测试 Secret 只在隔离进程环境生成和注入，不写入仓库、证据或 Shell 历史；实名认证沙箱与 CI 自动登录必须保持关闭。

R06 现场演练至少覆盖：

1. Java 21 定向测试与生产构建通过；PostgreSQL 17 完整迁移到 V030，业务表数量和 Flyway 历史均归档。
2. Prometheus target `hhy-backend-r06` 为 UP，RED count/bucket 非空，并存在 `hhy_content_online_count`、`hhy_content_review_pending`、`hhy_content_outbox_backlog`、`hhy_home_enabled_modules` 四项业务 Gauge；空载时内容 Outbox 积压和业务指标查询失败计数必须为 0。
3. 停止/恢复 API 触发 `HhyR06BackendDown`；插入四条按冻结 Commit 唯一命名的隔离 `CONTENT/PENDING` Outbox 事件触发 `HhyR06ContentOutboxBacklog`，随后必须按 `PENDING → PUBLISHING（attempts+1）→ PUBLISHED（published_at）` 合法状态机终结并取得 resolved；禁止删除测试事件或修改其身份和 payload，两项告警均须取得 alert-sink 送达回执。
4. 首页与公共状态请求的 HTTP 状态、`requestId`、`traceId` 与 `http_request_completed` 可关联；日志不得包含内容正文、联系方式密文、Secret、Bearer、Cookie、请求正文或查询参数。
5. CMS 状态机、`expectedVersion`、幂等重放、Outbox 原子写入、首页只读稳定性和供应方超时失败关闭由 TASK-R06-005 自动化证据验证；不得通过直接修改业务记录伪造接口成功。
6. 应用回切只替换 `api` 镜像，保持同一 PostgreSQL 容器、数据卷和 V030；禁止执行 U030–U029、降版本 DDL、删除内容版本/状态历史/审计或重建数据库。恢复当前镜像后再次验证 readiness、RED、四项 R06 Gauge 和数据库连续性。
7. 证据统一写入 `artifacts/validation/r06-task006-staging/`，绑定被测 Commit、两个不可变镜像 ID、数据库容器/卷、Flyway V030、告警回执和逐文件 SHA-256。Android 候选 APK 与真机异步验收仍由 TASK-R06-007 单独完成，未收到真机反馈不得伪造 `owner_physical_test=PASS`，也不得据此停止后续依赖已满足的开发。

## 12. R07 搜索与联系方式隔离预发布验收

R07 使用 `infra/staging/r07-smoke/docker-compose.yml`，采用独立 Compose project、仅回环发布端口、`172.31.247.0/24` 默认子网和独立数据卷；不得修改或重启公网及 R01–R06 环境。执行前运行 `python3 scripts/check_r07_observability.py`，并把精确被测 Commit 注入 `HHY_R07_FROZEN_COMMIT`。七项测试 Secret（含独立的 `HHY_CONTENT_CONTACT_ROOT_SECRET`）只在隔离进程环境生成和注入，禁止写入仓库、证据或 Shell 历史；实名认证沙箱与 CI 自动登录保持关闭。

R07 现场演练至少覆盖：

1. Java 21 定向测试和生产构建通过；PostgreSQL 17 完整迁移到 V032，Flyway 历史与业务表数量归档。
2. Prometheus target `hhy-backend-r07` 为 UP，RED count/bucket 非空；`hhy_search_history_rows`、`hhy_search_hot_terms_active`、`hhy_publisher_active_count`、`hhy_contact_accesses_5m`、`hhy_contact_rejections_5m`、`hhy_r07_outbox_backlog` 六项业务 Gauge 全部存在，业务指标查询失败累计值必须为 0。
3. 停止/恢复 API 触发 `HhyR07BackendDown`；插入四条带冻结 Commit 唯一前缀的 `SEARCH_HISTORY` Outbox 测试事实触发 `HhyR07OutboxBacklog`。两项告警均必须取得 firing、resolved 和 alert-sink 送达回执。
4. 测试 Outbox 事实禁止删除，只允许 `PENDING → PUBLISHING → PUBLISHED`，同时递增 attempts 并写 published_at；失败恢复重复执行同一合法终结流程。
5. 2xx 响应中的 `requestId`、`traceId`、状态码与 `http_request_completed` 可关联；日志不得包含搜索词探针、联系方式、密文、安全根密钥、Bearer、Cookie 或请求正文。
6. 应用回切只替换 `api` 镜像，回切目标必须兼容 V032；保持同一 PostgreSQL 容器和卷，恢复当前镜像后重复 readiness、六项 Gauge 与数据库连续性验证。
7. 证据统一写入 `artifacts/validation/r07-task006-staging/`，绑定冻结 Commit、两个不可变镜像、数据库容器/卷、Flyway V032、告警回执和逐文件 SHA-256。机器证据齐全后才能把 `AC-R07-004` 签为 PASS。

现场步骤由 `scripts/run_r07_staging_acceptance.sh` 单一入口执行。脚本首先强制 `git rev-parse HEAD` 等于 `HHY_R07_FROZEN_COMMIT`，再采集基线、演练告警与回滚并生成 `SHA256SUMS`；禁止从聊天记录重组长 SSH 命令。Android 模拟器、截图和候选 APK 不属于本任务，只在 TASK-R07-007 最终候选阶段执行。
