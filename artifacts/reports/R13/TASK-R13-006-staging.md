# TASK-R13-006 隔离 Staging 与可观测性验收

## 当前范围

- 为 R13 收藏、历史、分享、联系方式访问/拒绝、失效反馈和活动 Outbox 增加七项只读、低基数业务 Gauge。
- 在独立 Compose project 中验证 RED、TraceId、结构化日志脱敏、Prometheus、告警送达和应用镜像回切。
- 回切前后比较收藏、浏览记录、分享、联系方式审计、失效反馈和测试 Outbox 事实。
- Android 模拟器、真实页面截图、AI 视觉复核和候选 APK 留给 `TASK-R13-007`。

## 冻结验收合同

- 指标：收藏总量、历史行数、近 5 分钟分享、近 5 分钟联系方式访问/拒绝、待处理失效反馈和 R13 Outbox 积压。
- 告警：后端不可用、错误率、P95、Outbox 积压、失效反馈积压、联系方式拒绝突增和业务指标查询失败。
- 回滚基线：`hhy-backend-r13-baseline:c9741759`，必须兼容 PostgreSQL 17 / Flyway V042。
- 隔离网络：`172.31.239.0/24`，Compose project 固定为 `hhy-r13-staging`。
- 现场入口：`scripts/run_r13_staging_acceptance.sh`，必须绑定精确源码 Commit。

## 验收状态

`AC-R13-004 PASS`

冻结实现 Commit `410aef5327ec460d87775a33b8d1f41927a3f29e` 已在 `obx-test` 的全新 Compose project `hhy-r13-staging` 完成现场验收：

- Java 21 当前镜像构建成功；PostgreSQL 17 空库应用 42 项迁移并到达 Flyway `V042`，共 201 张含 Flyway 表、200 张业务表。
- liveness、readiness、公开状态 HTTP 200、Prometheus target、RED 指标和七项 R13 业务 Gauge 全部通过；七项业务指标查询失败累计值均为零。
- `HhyR13BackendDown` 与 `HhyR13OutboxBacklog` 均取得 firing、resolved 和 alert-sink 回执；四条测试 Outbox 事件以 `attempts=1` 合法终结为 `PUBLISHED`。
- TraceId 与结构化完成日志可关联；授权头、Cookie、查询探针和检查哨兵未进入日志或证据，`log_redaction=PASS`。
- 当前镜像 `sha256:30d4a7b3ab2fac69e27f4019035c050c889f9b7f20ee166fedda26960fae28e0` 回切到功能基线 `sha256:e4b6673ad0dbab261057017d3797051a14f6da403594c6850410b63ad8296717` 后恢复；PostgreSQL 容器、卷和 Flyway `V042` 全程不变。
- 收藏、浏览记录、分享、联系方式审计、失效反馈和活动 Outbox 的前后快照逐行一致，`activity_facts_preserved=PASS`，且没有降级迁移或删除数据卷。
- 现场证据已归档至 `artifacts/validation/r13-task006-staging/`。24 个受清单校验文件在远端及本地逐项 SHA-256 一致；目录共 25 个文件（含 `SHA256SUMS`），`SHA256SUMS` 自身哈希为 `1618a9b38211f1db40f814251964c8c7fd4a144c8c5b868a796a70e34bbb8f28`。

## 现场记录

- 后台状态：`obx-test:/tmp/hhy-r13-006-staging.status`，最终值 `0`。
- 原始日志：`obx-test:/tmp/hhy-r13-006-staging.log`，最终标记 `R13_STAGING_ACCEPTANCE_OK`。
- 证据压缩包：`obx-test:/tmp/hhy-r13-task006-staging-evidence.tgz`，SHA-256 `1009a965abef6889405109d2385a3b1ffe1a3d0dd6232fc3bab537514d798809`。
- 基线镜像源 Commit：`c97417591cfa5c1b069d0aaef9622e0f3dad8c8f`；当前镜像源 Commit：`410aef5327ec460d87775a33b8d1f41927a3f29e`。

Android 模拟器、真实页面截图、AI 视觉复核和测试 APK 未在本任务触发，按大版本最终候选规则留给 `TASK-R13-007`。
