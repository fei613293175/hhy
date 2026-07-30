# TASK-R01-005 · 专项测试与故障注入报告

- Session：`SES-20260717T122518Z-91E6F4D6`
- Story：`STORY-R01-003`
- 被测源码：`79bbd45d22d16b334330b4b36f2fbb489d5e73f0`
- 变更审批：`CR-0016` 已关闭；`CR-0015` 保持拒绝归档
- 结论：权威 Manifest 声明的 27 项测试全部通过，失败或缺失为 0

## 权威矩阵

| 范围 | 数量 | 结果 |
| --- | ---: | --- |
| Manifest / catalog 匹配 | 27 / 27 | PASS |
| 本地契约、文档、UI、前端测试 | 24 | PASS |
| Java 21 + PostgreSQL 17 + 真实 API 外部测试 | 3 | PASS |
| 失败或缺失 | 0 | PASS |

机器可读结果：`artifacts/validation/r01-task005-matrix.json`。

## 云端运行证据

| 证据 | 结果 | SHA-256 |
| --- | --- | --- |
| `maven-java21.log` | Maven 全模块 74 tests，0 failure，0 error；2 个条件式 PG 测试按预期跳过 | `6C46EB9324C737D7C6D7E1083EE72B829B3C148638008E69D19804054B7623B1` |
| `maven-postgres17.log` | PostgreSQL 17.10 应用 16 个迁移，真实 Store 集成测试 1/1 PASS | `129A3F0ADE68D357E3F4CF2114A65CE445D952621CF8DB7E65418F27EF564EC4` |
| `postgres17-invariants.log` | 11 个 R01 并发、迁移、回滚与原子性不变量 PASS | `6799F7F56860B5340C8927DB15644D8F13B0EDF7003BFE1DBFB4226E89704957` |
| `real-api.log` | 隔离真实 API：7 POST 首次响应快照、撤销 Bearer 重放、摘要冲突、零副作用及 PG 并发 20 全部 PASS | `D20FF1E4086CCF87FA64AD51601465198CF675CFC77A79DD81214B843D0FE3BA` |

证据位于 `artifacts/validation/r01-test-evidence/`，外部三项结果由 `r01-79bbd45.evidence.json` 按 `hhy.r01.test-evidence/v1` 绑定源码提交和日志摘要。

## 故障注入与缺陷清零

- 配置布尔值严格解析，拒绝把非空字符串误判为 `true`。
- Design System token、可访问性、窄屏与重复 DTO/手写 API 漂移纳入门禁。
- 覆盖超时、429 冷却、离线敏感弹窗终止与清理、409 冲突重取、供应商/网络异常。
- 幂等记录原子保存七个 POST 的版本化 AES-256-GCM 首次响应快照。
- 撤销会话仅可通过严格白名单、sid/sub/jti 与摘要匹配的 replay-only 通道重放；新请求和越界请求闭锁且零副作用。
- 覆盖随机 nonce、AAD/密文/响应类型篡改、当前/previous 密钥轮换、未知版本闭锁、旧记录兼容和 U016 回滚阻断。
- 首轮云端运行暴露并修复 SQL `FROM` 缺失、保留认证标记碰撞、Servlet path 归一化和 Java Time 序列化依赖问题；失败日志归档于 `artifacts/validation/r01-task005-maven-failure-cbf3a6d.log`，对应 `PROB-0007`。

## 验收结论

- 无生产 TODO 或 Mock 被引入。
- 代码、契约、数据库、文档、测试与追踪已同步。
- 关键缺陷清零，`CR-0016` 在真实环境门禁通过后关闭。
- TASK-R01-005 满足关闭条件；下一任务为 `TASK-R01-006` 可观测性与预发布验收。
