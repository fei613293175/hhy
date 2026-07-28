# TASK-R14-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R14 会话、近五分钟消息、未读、待处理举报、未 ACK 投递、重试耗尽、补洞用户和 Outbox 增加八项只读、低基数业务 Gauge。
- 在独立 Compose project 中验证结构化日志与协议头脱敏、TraceId、RED、Prometheus、告警送达和应用镜像回切。
- 回切前后逐项比较聊天与 WebSocket 相关十一张表的业务事实。
- Android TEST_APK、固定签名和桌面交付留给 `TASK-R14-007`；公网 WebSocket 激活不属于本次隔离验收。

## 冻结验收合同

- 精确源码 Commit：`b6f5e285faabbcd2ce7539d01e502b199ade7580`。
- 回滚基线：`f4b7d4854e10dedd40d4b40cdb2d79e57687e9b7` / `hhy-backend-r14-baseline:f4b7d485`。
- Compose project：`hhy-r14-staging`；隔离网络：`172.31.238.0/24`。
- 固定回环端口：HTTP `38114`、Prometheus `39618`、Alertmanager `39619`。
- 现场入口：`scripts/run_r14_staging_acceptance.sh`，必须绑定精确源码 Commit。

## 验收状态

`AC-R14-004 PASS`

精确 Commit 已在 `obx-test` 的全新隔离 project 完成现场验收：

- Java 21 当前镜像构建成功；PostgreSQL 17 空库应用 Flyway `V044`，共 204 张含 Flyway 表、203 张业务表。
- liveness、readiness、公开状态 HTTP 200、Prometheus target、RED 与八项 R14 业务 Gauge 全部通过。
- `HhyR14BackendDown`、`HhyR14WebsocketRetryExhausted` 与 `HhyR14WebsocketGapUsers` 均取得 firing、resolved 和 alert-sink 回执。
- TraceId 与结构化完成日志可关联；Authorization、Cookie、查询探针及 `Sec-WebSocket-Protocol` 探针均未泄漏，日志与协议头脱敏为 PASS。
- 当前镜像 `sha256:4ed75c60c1fce091e5d552ff0dc4bf9fb6eb7da24541eaacb3ff64a15509aca0` 回切到基线镜像 `sha256:671986eb6f31984efba73334de9dc9237500b4b2de35be6ba00716d7ea6d51c9` 后恢复；PostgreSQL 容器、卷和 Flyway `V044` 全程不变。
- 十一张聊天与 WebSocket 表的回切前后快照逐行一致，且没有执行向下迁移或删除数据卷。
- 28 个清单文件在远端和本地逐项 SHA-256 一致；证据目录共 29 个文件（含 `SHA256SUMS`），清单文件自身 SHA-256 为 `83eaf089e18cf7e43fb3723a84353cbaa778989177f1b2c4b100ce17825e7af8`。

## 边界

- `TASK-R14-006` 依赖的 `TASK-R14-005` 尚未满足，因此任务状态继续保持 `BLOCKED`；本报告只签署独立可复用的 `AC-R14-004`，不伪造任务或版本关闭。
- `ws.orbexa.cc` 仍为 `BLOCKED_EXTERNAL_DNS`，未修改公网 DNS、TLS、Nginx 或后端路由。
- `PROB-0135` 仍为 `OPEN`；举报原因产品目录缺失时不得关闭 `SHEET-CHAT-002`、`STORY-R14-003` 或 R14 机器验收。
- 邀请码与注册不属于本次验收范围。

原始机器证据位于 `artifacts/validation/r14-task006-staging/`。
