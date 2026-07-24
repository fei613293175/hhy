# TASK-R11-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R11 团队长域增加七项只读、无敏感或高基数标签的业务 Gauge。
- 在独立 Compose project 中验证 RED、TraceId、日志脱敏、Prometheus、告警送达和回滚。
- 回切前后比较团队长资料、详情、收藏、联系方式审计和 Outbox 业务事实。
- Android 模拟器、截图和候选 APK 留给 TASK-R11-007。

## 已实现配置

- 七项指标：团队长总量、在线量、待审核量、收藏量、近五分钟联系方式访问量、拒绝量及 R11 Outbox 积压。
- 七条 R11 专属告警，包括后端不可用、错误率、P95 延迟、Outbox 积压、联系方式拒绝突增、审核积压和业务指标查询失败。
- 独立 PostgreSQL、API、Nginx、Prometheus、Alertmanager 与内部 alert-sink；管理端口和 alert-sink 不向宿主机公开。
- 单一入口 `scripts/run_r11_staging_acceptance.sh` 强制绑定精确冻结 Commit，并生成逐文件 SHA-256。

## 验收状态

`AC-R11-004` 已在冻结实现 Commit `b190476f72bd0c0de12dc0f29820a72d352caffc` 上通过：

- 后端 Docker Java 21 构建成功；PostgreSQL 17.10 空库应用 38 项迁移并到达 V038。
- 完整隔离现场演练耗时 208.1 秒，liveness、readiness、公开状态、Prometheus target、RED 与七项 R11 Gauge 全部通过，业务指标查询失败累计值为零。
- `HhyR11BackendDown` 和 `HhyR11OutboxBacklog` 均取得 firing、resolved 与 alert-sink 回执；四条测试 Outbox 事实以 `attempts=1` 合法终结为 `PUBLISHED`。
- 响应 TraceId 与结构化完成日志可关联，敏感探针和检查哨兵未进入日志或证据，`log_redaction=PASS`。
- 当前镜像 `sha256:3a87e43f14de2e87c83dc343dc8c93bfca893db823e0ea8e36f3c3e8ad47e2f5` 回切到 R11-005 功能基线 `sha256:319a95e65ca17ec60bbe127e85c48ebd6fc6e5db739974ff95a4dbe08129e60a` 后恢复；PostgreSQL 容器、卷和 Flyway V038 全程不变。
- 团队长内容、详情、收藏、联系方式审计和 R11 Outbox 的前后快照逐行一致，`team_leader_facts_preserved=PASS`。
- 现场证据已归档至 `artifacts/validation/r11-task006-staging/`，25 个文件（含 `SHA256SUMS`）已在本地完成 SHA-256 复核。

首次启动暴露检查哨兵短于 32 字节，第二次暴露管理员安全密钥不得复用；两次均在清理隔离项目后修复。`PROB-0104` 已登记根因，回归锁定八个哨兵长度合规且全部唯一。第三次精确 Commit 现场演练一次通过。

Android 模拟器、页面截图和候选 APK 未在本任务触发，按门禁留给 TASK-R11-007。
