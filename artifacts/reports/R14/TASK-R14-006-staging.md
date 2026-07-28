# TASK-R14-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R14 会话、近五分钟消息、未读、待处理举报、未 ACK 投递、重试耗尽、补洞用户和 Outbox 增加八项只读、低基数业务 Gauge。
- 在独立 Compose project 中验证结构化日志与协议头脱敏、TraceId、RED、Prometheus、告警送达和应用镜像回切。
- 回切前后逐项比较聊天与 WebSocket 相关十一张表的业务事实。
- Android TEST_APK、固定签名和桌面交付留给 `TASK-R14-007`；公网 WebSocket 激活不属于本次隔离验收。

## 冻结验收合同

- 精确源码 Commit：`f58be15f82ed1e9eb95355d8e4df363417fff023`。
- 回滚基线：`f4b7d4854e10dedd40d4b40cdb2d79e57687e9b7` / `hhy-backend-r14-baseline:f4b7d485`。
- Compose project：`hhy-r14-staging`；隔离网络：`172.31.238.0/24`。
- 固定回环端口：HTTP `38114`、Prometheus `39618`、Alertmanager `39619`。
- 现场入口：`scripts/run_r14_staging_acceptance.sh`，必须绑定精确源码 Commit。

## 验收状态

`AC-R14-004 PASS`

精确 Commit 已在 `obx-test` 的全新隔离 project 完成第三轮现场验收：

- Java 21 当前镜像构建成功；R14 Compose 在应用启动前固定 `SPRING_FLYWAY_TARGET=44`，PostgreSQL 17 空库只应用 Flyway `V001` 至 `V044`，共 204 张含 Flyway 表、203 张业务表，主分支保留的后续迁移未侵入 R14 候选。
- 举报原因目录源 Commit `054da178f90b752e23b33b8de49c22c6c8421026` 是冻结 Commit 的祖先；OpenAPI、Java 和 Kotlin 三份目录消费者通过 `PASS_SOURCE_BYTES_UNCHANGED` 校验并进入本次 Staging 镜像。
- liveness、readiness、公开状态 HTTP 200、Prometheus target、RED 与八项 R14 业务 Gauge 全部通过。
- `HhyR14BackendDown`、`HhyR14WebsocketRetryExhausted` 与 `HhyR14WebsocketGapUsers` 均取得 firing、resolved 和 alert-sink 回执。
- TraceId 与结构化完成日志可关联；Authorization、Cookie、查询探针及 `Sec-WebSocket-Protocol` 探针均未泄漏，日志与协议头脱敏为 PASS。
- 当前镜像 `sha256:c17e691a8d32a80fd5c5b8e4fe187a725dcb224fc2f4f49b4478937efaf8b10d` 回切到基线镜像 `sha256:671986eb6f31984efba73334de9dc9237500b4b2de35be6ba00716d7ea6d51c9` 后恢复；PostgreSQL 容器、卷和 Flyway `V044` 全程不变。
- 十一张聊天与 WebSocket 表的回切前后快照逐行一致，且没有执行向下迁移或删除数据卷。
- 29 个清单文件在远端和本地逐项 SHA-256 一致；证据目录共 30 个文件（含 `SHA256SUMS`），清单文件自身 SHA-256 为 `fe79df8f9cc24b85934b7374efed12e8544fd1a557ffc437154f30108c1f802a`。

## 边界

- `TASK-R14-005` 已完成；本报告签署 `TASK-R14-006` 的 Staging 与可观测性验收，任务可按连续性协议关闭并仅进入 `TASK-R14-007`。
- `ws.orbexa.cc` 仍为 `BLOCKED_EXTERNAL_DNS`，未修改公网 DNS、TLS、Nginx 或后端路由。
- 举报原因产品目录已由 `CR-0464` 关闭缺口并在本次冻结 Staging 中验证；该结论不代表旧 TEST_APK 已包含目录，最终 APK 重建仍属于 `TASK-R14-007`。
- 邀请码与注册不属于本次验收范围。

原始机器证据位于 `artifacts/validation/r14-task006-staging/`。
