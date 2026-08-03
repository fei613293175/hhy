# TASK-R16-006 远程运行与预发布验收报告

## 部署

- 环境：`api.orbexa.cc` 长期 owner-test 持久测试环境。
- 后端：`hhy-owner-test-api-4debca13` / `hhy-backend-r16-owner-test:4debca13`。
- 数据库：`hhy-owner-test-postgres`，持续使用 `hhy-owner-test-postgres-data`。
- 升级：先生成 1,149,613 字节、权限 0600 的一致性快照，再蓝绿切换到 `127.0.0.1:28150`。
- 数据：升级前后用户、凭证和邀请码计数保持一致；没有重建数据库、替换卷或清空业务表。

## 数据库与 API

- Flyway V045、V046、V047、V048、V049 均为成功状态。
- 公网平台状态 HTTP 200，容器 readiness 为 `UP`。
- 使用现有有效会话生成短期诊断 Token，在远程进程内完成请求且未输出/保存 Token 或密钥。
- `GET /api/v1/me/orders` 返回 HTTP 200。
- `GET /api/v1/me/orders/NO_SUCH_ORDER` 返回 HTTP 404 和 `COMMON-404-NOT_FOUND`。
- 持久库没有后台管理员账号，因此未伪造管理员登录；后台接口由真实 PostgreSQL Store 测试、控制器合同测试和同镜像启动覆盖。

## 可观测性

- Prometheus 端点可访问，HTTP RED 指标和业务 Gauge 正常导出。
- 两个订单请求均产生结构化 `http_request_completed` 日志。
- 日志包含 operation、status、latency、requestId 和 traceId；404 响应中的 traceId 与日志一致。
- 通用后端不可用、5xx 比例、P95 延迟、Outbox 积压/死信告警规则已存在。

## 回滚条件

R16 应用异常时只回切 Nginx 到保留的 R15 容器 `127.0.0.1:28149`；禁止回滚已成功的前向数据库迁移、替换持久卷或恢复覆盖新数据的旧快照。
