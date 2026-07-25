# TASK-R12-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R12 统一发布与审核闭环增加七项只读、低基数且不含敏感标签的业务 Gauge。
- 在独立 Compose project 中验证 RED、TraceId、日志脱敏、Prometheus、告警送达与应用回切。
- 回切前后比较内容状态、版本、状态历史、审核记录、媒体、联系方式和 R12 Outbox 业务事实。
- Android 模拟器、真实页面截图和候选 APK 留给 TASK-R12-007。

## 冻结验收合同

- 指标：统一发布总量、草稿、待审核、审核中、驳回、在线及 R12 Outbox 积压。
- 告警：后端不可用、错误率、P95、Outbox、审核积压、驳回积压和指标查询失败。
- 回滚基线：`hhy-backend-r12-baseline:5419d682`，必须兼容 PostgreSQL 17 / Flyway V041。
- 隔离网络：`172.31.240.0/24`；`172.31.243.0/24` 已由 R09 占用，禁止复用。
- 现场入口：`scripts/run_r12_staging_acceptance.sh`，必须绑定精确源码 Commit。

## 验收状态

`PENDING_FROZEN_COMMIT_STAGING_REHEARSAL`

现场证据完成后回填精确 Commit、镜像 ID、数据库连续性、告警回执、业务事实比较和 SHA-256；此前 `AC-R12-004` 保持 `NOT_RUN`。

## 现场尝试记录

- Attempt 1：Commit `3205187edfec06e5055312a228f4f51ca55db9c4` 完成镜像构建、六容器启动和 PostgreSQL V041 迁移；基线证据首行正确输出 `041`，但脚本错误与 `41` 比较后退出。该 Commit 不重跑，修复由 `CR-0337` 跟踪。
- 原始服务器日志：`obx-test:/tmp/hhy-r12-task006-3205187e.log`。
