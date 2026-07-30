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

`AC-R12-004 PASS`

冻结实现 Commit `4365e1c790e3792812cfd62bb415e1075eb00149` 已在 `obx-test` 的全新 Compose 项目 `hhy-r12-staging-a2` 完成 223.7 秒现场验收：

- Java 21 后端镜像构建成功；PostgreSQL 17.10 空库应用 41 项迁移并到达 Flyway `V041`，共 201 张含 Flyway 表、200 张业务表。
- liveness、readiness、公开状态 HTTP 200、Prometheus target、RED 指标和七项 R12 业务 Gauge 全部通过，业务指标查询失败累计值为零。
- `HhyR12BackendDown` 与 `HhyR12OutboxBacklog` 均取得 firing、resolved 和 alert-sink 回执；四条测试 Outbox 事件以 `attempts=1` 合法终结为 `PUBLISHED`。
- TraceId 与结构化完成日志可关联，授权头、Cookie、查询探针及检查哨兵未进入日志或证据，`log_redaction=PASS`。
- 当前镜像 `sha256:12e73089033a1f59082e08b0f85232a69c8a6a7ac4938c1e7e524e1830195074` 回切到功能基线 `sha256:49bc8ff7ab451137d7221b9fec8f46c9ee9f03dbdb7b596cee6663cc00524601` 后恢复；PostgreSQL 容器、卷和 Flyway `V041` 全程不变。
- 内容状态、版本、状态历史、审核记录、媒体、联系方式和 R12 Outbox 的前后快照逐行一致，`publishing_facts_preserved=PASS`。
- 现场证据已归档至 `artifacts/validation/r12-task006-staging/`，25 个文件（含 `SHA256SUMS`）已完成远端与本地逐文件 SHA-256 复核；`SHA256SUMS` 自身哈希为 `7180b33251308b1b77f15a1faafb51d440d65513ad81c34dab765d7c818bb23e`。

## 现场尝试记录

- Attempt 1：Commit `3205187edfec06e5055312a228f4f51ca55db9c4` 完成镜像构建、六容器启动和 PostgreSQL V041 迁移；基线证据首行正确输出 `041`，但脚本错误与 `41` 比较后退出。该 Commit 不重跑，修复由 `CR-0337` 跟踪。
- 原始服务器日志：`obx-test:/tmp/hhy-r12-task006-3205187e.log`。
- Attempt 1 证据归档：`obx-test:/tmp/hhy-r12-task006-3205187e-failed-evidence.tgz`，SHA-256 `eeea1751e5a860bf2eb0ace2830326fcbf81de52bf30bed62193f3c7218a0e1f`。
- Attempt 2：新 Commit `4365e1c790e3792812cfd62bb415e1075eb00149` 使用新项目 `hhy-r12-staging-a2` 一次完整通过；服务器日志为 `obx-test:/tmp/hhy-r12-task006-4365e1c7.log`。

Android 模拟器、真实页面截图、AI 视觉复核和测试 APK 未在本任务触发，按大版本最终候选规则留给 `TASK-R12-007`。
