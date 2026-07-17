# P00 Staging 回滚与前向修复手册

## 1. 原则

- 应用镜像可以回切到上一个已验证的不可变 Tag；数据库只允许前向修复，不执行 Flyway `undo`、降版本 DDL 或数据删除。
- 资金、账务、Outbox、Inbox、对账和审计事实不得通过删除记录“恢复正常”。
- 禁止执行 `docker compose down -v`、删除 PostgreSQL volume、清空业务表或用旧备份覆盖仍在写入的数据库。
- 操作前先开启维护/停止写流量，记录 requestId/traceId、当前镜像摘要、告警、四项业务 Gauge 和数据库恢复点。

## 2. 应用回切

1. 确认上一个镜像 Tag、摘要和兼容的数据库最低版本；若旧应用不兼容已执行的迁移，不得盲目回切，改走前向修复。
2. 设置已批准的旧 Tag，仅替换 backend，不重建 PostgreSQL：

   ```bash
   export HHY_IMAGE_TAG='<previous-approved-tag>'
   docker compose -f infra/staging/docker-compose.p00.yml config --quiet
   docker compose -f infra/staging/docker-compose.p00.yml up -d --no-deps backend
   ```

3. 在容器内部等待 liveness/readiness 均为 UP：

   ```bash
   docker compose -f infra/staging/docker-compose.p00.yml exec -T backend \
     curl -fsS http://127.0.0.1:9091/actuator/health/readiness
   ```

4. 验证版本检查、平台状态、认证拒绝路径和数据库读写；确认 Prometheus target 为 UP。
5. 四项业务 Gauge 必须回到已批准基线。`hhy_outbox_dead_letter` 或 `hhy_ledger_unbalanced_transactions` 非零时不得恢复写流量。

## 3. 数据库前向修复

1. 保持维护状态，冻结新写入；保存失败 SQLState、迁移版本和脱敏日志。
2. 由数据库负责人编写新的、更高版本 Flyway 修复迁移，包含升级库与空库验证；不修改已经执行的迁移文件。
3. 对账务差异通过冲正交易/补偿分录处理；Outbox 通过批准的重放流程处理，禁止直接改 payload、event_id 或把死信强制标为已发布。
4. 在独立恢复副本验证备份可恢复性和修复迁移，再部署 Staging。

## 4. 回滚失败与升级

若旧镜像无法启动、readiness 未恢复、迁移不兼容、账务/对账指标恶化或日志出现敏感数据：保持维护，停止自动重启循环，保全数据库与日志证据，升级给应用负责人、数据库负责人和安全负责人。不得为追求绿色监控而关闭 Gauge 或告警规则。

## 5. 关闭事件

记录触发原因、时间线、操作者、旧/新镜像摘要、Commit/Tag、数据库迁移版本、健康与指标前后值、受影响请求的 requestId/traceId、采取的前向修复、恢复时间和后续行动。所有凭据、Token、密码、Cookie、手机号、证件和支付信息必须脱敏。
