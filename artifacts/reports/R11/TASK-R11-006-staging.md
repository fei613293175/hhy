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

等待精确实现 Commit 完成模块门禁和 `obx-test` 单次隔离现场演练。现场证据完整前，`AC-R11-004` 保持 `NOT_RUN`，不得提前签字。
