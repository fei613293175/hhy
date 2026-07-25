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

## 6. R01 隔离预发布回滚演练

R01 的服务名为 `api`，演练必须保持同一个 PostgreSQL 容器和数据卷，只替换应用镜像：

```bash
export HHY_SMOKE_ID='<previous-approved-compatible-tag>'
docker compose -p hhy-r01-staging -f infra/staging/r01-smoke/docker-compose.yml up -d --no-deps api
docker compose -p hhy-r01-staging -f infra/staging/r01-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

若旧镜像不兼容已执行的 V016 或更高迁移，停止应用回切并走更高版本 Flyway 前向修复；禁止 U016、降版本 DDL、`down -v` 或重建 PostgreSQL。回切旧镜像和恢复当前镜像前后都要记录数据库容器 ID、卷名、Flyway 版本、Prometheus target、RED、全部业务 Gauge、告警与恢复时间，且 `hhy_admin_idempotency_incomplete_snapshots` 和 `hhy_business_metric_query_failures_total` 必须为 0。

## 7. R03 配置中心隔离回滚演练

R03 回滚包含两个互不替代的层次：供应商配置版本回滚使用已批准的管理端命令，应用镜像回切使用 Compose；两者都不得回滚数据库结构。

配置版本回滚前必须确认目标历史版本已有成功连接测试、审批状态为 `APPROVED`、申请人与审核人不同且审批资源精确绑定目标版本。回滚后验证当前版本为 `ROLLED_BACK`、目标版本恢复为 `ACTIVE`、审计事件可按 requestId/traceId 追踪，重复提交不产生第二次状态变化。任一连接测试或审批检查失败时保持原 ACTIVE 版本不变。

应用镜像演练保持同一个 PostgreSQL 容器和卷，只切换 `api`：

```bash
export HHY_SMOKE_ID='<previous-approved-v019-compatible-tag>'
docker compose -p hhy-r03-staging -f infra/staging/r03-smoke/docker-compose.yml up -d --no-deps api
docker compose -p hhy-r03-staging -f infra/staging/r03-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V019、200 表基线、Prometheus target、RED 和四项 R03 Gauge。随后恢复当前镜像并重复 readiness 与指标验证。若旧镜像不兼容 V019，停止回切并走更高版本 Flyway 前向修复；禁止 U019、降版本 DDL、删除配置/审计记录或重建数据库。

## 8. R04 媒体与存储隔离回滚演练

R04 应用回切必须保持同一个 PostgreSQL 容器和数据卷，只切换 `api` 镜像。回切目标必须是已通过测试且兼容 V022 的不可变镜像；若没有兼容镜像，则停止回切并采用更高版本 Flyway 与应用前向修复，禁止以历史镜像强行启动。

```bash
export HHY_SMOKE_ID='<previous-approved-v022-compatible-tag>'
docker compose -p hhy-r04-staging -f infra/staging/r04-smoke/docker-compose.yml up -d --no-deps api
docker compose -p hhy-r04-staging -f infra/staging/r04-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V022、200 表基线、Prometheus target、RED 与四项 R04 Gauge。验证同一测试媒体元数据仍可读取、未完成上传会话状态未被篡改、私有对象未变为公开访问；随后恢复当前镜像并重复 readiness 与指标验证。禁止 U022、U021、降版本 DDL、删除媒体/访问审计记录、重建数据库或对真实供应商对象执行破坏性清理。

## 9. R05 实名认证隔离回滚演练

R05 应用回切保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标必须是已经验证且兼容 V028 的不可变镜像；若没有兼容镜像，停止回切并采用更高版本应用前向修复，禁止强行启动历史镜像。

```bash
export HHY_SMOKE_ID='<previous-approved-v028-compatible-tag>'
docker compose -p hhy-r05-staging -f infra/staging/r05-smoke/docker-compose.yml up -d --no-deps api
docker compose -p hhy-r05-staging -f infra/staging/r05-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V028、200 表基线、Prometheus target、RED 与四项 R05 Gauge。验证进行中会话、人工复核队列、私有媒体绑定、不可变复核和敏感访问审计均未改变；随后恢复当前镜像并重复 readiness、指标与告警验证。禁止 U028–U023、降版本 DDL、删除实名会话/媒体/复核/访问审计、重建数据库或对真实供应商执行破坏性请求。

## 10. R06 内容与首页隔离回滚演练

R06 应用回切保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标必须是已验证且兼容 V030 的不可变镜像；若没有兼容镜像，停止回切并采用更高版本应用或 Flyway 前向修复，禁止强行启动不兼容历史镜像。

```bash
export HHY_SMOKE_ID='<previous-approved-v030-compatible-tag>'
docker compose -p hhy-r06-staging -f infra/staging/r06-smoke/docker-compose.yml up -d --no-deps api
docker compose -p hhy-r06-staging -f infra/staging/r06-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V030、业务表数量、Prometheus target、RED 与四项 R06 Gauge。验证内容状态、不可变版本和状态历史、内容 Outbox、首页模块配置均未被回切篡改；随后恢复当前镜像并重复 readiness、指标与告警验证。禁止 U030、U029、降版本 DDL、删除内容/版本/状态历史/审计/Outbox 记录、重建数据库或通过业务端口写入测试数据。

## 11. R07 搜索与联系方式隔离回滚演练

R07 应用回切保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标必须是已验证且兼容 V032、联系方式安全信封和 R07 七项接口的不可变镜像；若没有兼容镜像，停止回切并采用更高版本应用或 Flyway 前向修复，禁止强行启动不兼容历史镜像。

```bash
export HHY_SMOKE_ID='<previous-approved-v032-compatible-tag>'
docker compose -p hhy-r07-staging -f infra/staging/r07-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r07-staging -f infra/staging/r07-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V032、业务表数量、Prometheus target、RED 与六项 R07 Gauge。验证搜索历史、热词、发布者公开资料、联系方式密文、不可变访问审计和 Outbox 均未被篡改；随后恢复当前镜像并重复 readiness、指标与告警验证。禁止 U032、U031、降版本 DDL、删除搜索/联系方式审计/Outbox 事实、重建数据库或通过业务端口写入测试数据。

## 12. R08 项目隔离回滚演练

R08 应用回切保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标必须是已验证且兼容 V034、项目状态不变量、联系方式安全信封及 R08 Outbox 事件的不可变镜像；若没有兼容镜像，停止回切并采用更高版本应用或 Flyway 前向修复，禁止强行启动不兼容历史镜像。

```bash
export HHY_SMOKE_ID='<previous-approved-v034-compatible-tag>'
docker compose -p hhy-r08-staging -f infra/staging/r08-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r08-staging -f infra/staging/r08-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后必须记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V034、业务表数量、Prometheus target、RED 与七项 R08 Gauge。验证项目内容、收藏事实、联系方式不可变访问审计和测试 Outbox 事实未被回切篡改；随后恢复冻结 Commit 镜像并重复 readiness、指标和告警验证。禁止 U034、禁止 U033、禁止降版本 DDL、删除项目/收藏/联系方式审计/Outbox 事实、删除卷、重建数据库或通过业务端口伪造验收数据。

## 13. R09 App隔离回滚演练

R09 App隔离回滚演练保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标必须是已经在 V035 数据库上实际验证兼容的不可变 R08 应用镜像；若兼容性验证失败，停止回切并采用更高版本应用或 Flyway 前向修复，禁止强行启动不兼容历史镜像。

```bash
export HHY_R09_ROLLBACK_IMAGE='hhy-backend-r08-smoke:7d6eedf4'
export HHY_R09_ROLLBACK_TAG='rollback-7d6eedf4'
docker tag "$HHY_R09_ROLLBACK_IMAGE" "hhy-backend-r09-smoke:$HHY_R09_ROLLBACK_TAG"
export HHY_SMOKE_ID="$HHY_R09_ROLLBACK_TAG"
docker compose -p hhy-r09-staging -f infra/staging/r09-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r09-staging -f infra/staging/r09-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后必须记录 `api` 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V035、业务表数量、Prometheus target、RED 与七项 R09 Gauge。验证 App 内容、收藏事实、联系方式不可变访问审计和测试 Outbox 事实未被回切篡改；随后恢复冻结 Commit 镜像并重复 readiness、指标和告警验证。禁止 U035、禁止 U034、禁止降版本 DDL、删除 App/收藏/联系方式审计/Outbox 事实、删除卷、重建数据库或通过业务端口伪造验收数据。

## 14. R10 群聊隔离回滚演练

R10 群聊隔离回滚演练保持同一 PostgreSQL 容器和数据卷，只切换 `api` 镜像。目标固定为已经登记的 R09 不可变镜像 `hhy-backend-r09-smoke:d056c54f`；若它不能在 V036 数据库上通过 readiness 和基础指标兼容性验证，立即恢复 R10 镜像并采用前向修复，禁止强行运行不兼容镜像。

```bash
export HHY_R10_ROLLBACK_IMAGE='hhy-backend-r09-smoke:d056c54f'
export HHY_R10_ROLLBACK_TAG='rollback-d056c54f'
docker tag "$HHY_R10_ROLLBACK_IMAGE" "hhy-backend-r10-smoke:$HHY_R10_ROLLBACK_TAG"
export HHY_SMOKE_ID="$HHY_R10_ROLLBACK_TAG"
docker compose -p hhy-r10-staging -f infra/staging/r10-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r10-staging -f infra/staging/r10-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后必须记录 API 镜像 ID、PostgreSQL 容器 ID、卷名、Flyway V036、业务表数量、Prometheus target、RED 与七项 R10 Gauge。验证群聊、收藏、入群通道、群主联系方式审计和测试 Outbox 事实未被回切篡改；随后恢复冻结 Commit 镜像并重复 readiness、指标和告警验证。禁止 U036、禁止 U035、禁止降版本 DDL、删除群聊/收藏/联系方式审计/Outbox 事实、删除卷或重建数据库。

## 15. R11 团队长隔离回滚演练

R11 团队长隔离回滚只切换 `api` 镜像，目标固定为已经包含 V038 且通过 R11-005 精确提交测试的功能基线 `hhy-backend-r11-baseline:5013a9a0`。禁止直接使用不包含 V038 的旧 R10 镜像作为成功回滚证据。若基线镜像不能在当前 V038 数据库上通过 readiness，立即恢复冻结实现镜像并采用前向修复。

```bash
export HHY_R11_ROLLBACK_IMAGE='hhy-backend-r11-baseline:5013a9a0'
export HHY_R11_ROLLBACK_TAG='rollback-5013a9a0'
docker tag "$HHY_R11_ROLLBACK_IMAGE" "hhy-backend-r11-smoke:$HHY_R11_ROLLBACK_TAG"
export HHY_SMOKE_ID="$HHY_R11_ROLLBACK_TAG"
docker compose -p hhy-r11-staging -f infra/staging/r11-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r11-staging -f infra/staging/r11-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后必须保持 PostgreSQL 容器 ID、卷名和 Flyway V038 不变，并逐行比较团队长内容、`team_leader_details`、收藏、联系方式访问审计及 R11 Outbox 快照；随后恢复冻结实现镜像并重验 readiness 和七项 R11 Gauge。禁止 U038、禁止降版本 DDL、删除或改写业务事实、删除卷、重建数据库以及在回滚过程中运行数据清理脚本。

## 16. R12 统一发布隔离回滚演练

R12 统一发布隔离回滚只切换 `api` 镜像，目标固定为已经包含 V041 且通过 TASK-R12-005 精确专项测试的功能基线 `hhy-backend-r12-baseline:5419d682`。禁止使用不包含 V041、发布状态机或审核权限对齐迁移的旧镜像作为成功回滚证据；基线若不能通过 readiness，立即恢复冻结实现镜像并采用前向修复。

```bash
export HHY_R12_ROLLBACK_IMAGE='hhy-backend-r12-baseline:5419d682'
export HHY_R12_ROLLBACK_TAG='rollback-5419d682'
docker tag "$HHY_R12_ROLLBACK_IMAGE" "hhy-backend-r12-smoke:$HHY_R12_ROLLBACK_TAG"
export HHY_SMOKE_ID="$HHY_R12_ROLLBACK_TAG"
docker compose -p hhy-r12-staging -f infra/staging/r12-smoke/docker-compose.yml up -d --no-deps --no-build api
docker compose -p hhy-r12-staging -f infra/staging/r12-smoke/docker-compose.yml exec -T api \
  curl -fsS http://127.0.0.1:9091/actuator/health/readiness
```

回切前后必须保持 PostgreSQL 容器 ID、卷名和 Flyway V041 不变，并逐项比较内容状态、不可变版本、状态历史、审核记录、媒体、联系方式及 R12 Outbox 快照；随后恢复冻结实现镜像并重验 readiness 和七项 R12 Gauge。禁止 U041、U040、U039、禁止降版本 DDL、删除或改写业务事实、删除卷、重建数据库或在回滚过程中运行数据清理脚本。
