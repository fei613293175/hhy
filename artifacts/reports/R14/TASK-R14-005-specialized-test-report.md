# TASK-R14-005 一对一聊天核心专项测试报告

- Release：`R14`
- Task：`TASK-R14-005`
- 冻结源码 Commit：`dbd71c6673808e3f8a9e37f7f77efa97bef9ead9`
- 执行环境：`obx-test`
- 结论：`PASS`

## 六项正式测试

| 测试 ID | 结论 | 核心证明 |
|---|---|---|
| TST-CHAT_001-HAPPY | PASS | 四类消息合同、联系方式密文、真实会话/消息/已读生命周期和 Android 状态 |
| TST-CHAT_001-REJECT | PASS | 拉黑、字段、媒体、配置、存储、数据库异常均拒绝且无成功副作用 |
| TST-CHAT_001-IDEMPOTENT | PASS | 响应丢失重放、同键异体冲突、重复 clientMessageId 和真实 PostgreSQL 并发唯一归属 |
| TST-CHAT_002-HAPPY | PASS | 用户级连续序列、ACK、未读/已读、补洞、举报证据和拉黑解除 |
| TST-CHAT_002-REJECT | PASS | 未鉴权、错误序列/ACK、非成员、提交后推送失败和权威分页失败安全处理 |
| TST-CHAT_002-IDEMPOTENT | PASS | 重复消息去重再 ACK、首次 ACK 时间稳定、连续回放和六次重投上限 |

## 固定环境结果

- Java：OpenJDK `21.0.9`，定向后端 `31` 项，`0` 失败、`0` 错误、`0` 跳过。
- PostgreSQL：`17.10`，五个真实用例各使用独立一次性数据库，`5/5 PASS`、`0 skip`。
- Android：固定镜像 `hhy-android-toolchain:r01-46fb273`，网络/聊天单测、Lint 和 App Kotlin 编译共 `247` 个任务，`BUILD SUCCESSFUL`。
- R14 当前没有外部聊天供应商 SDK；供应商异常不得虚构，实际外部边界按配置提供方、媒体存储、数据库和 WebSocket 投递故障注入验证。

## 机器证据

| 文件 | SHA-256 |
|---|---|
| `artifacts/validation/r14-test-evidence/backend-java21.log` | `7d18b8ce6050ac07eff24d9a27597556327ea9cfc489ee2aa29bfa18dd8b9dc7` |
| `artifacts/validation/r14-test-evidence/postgresql17.log` | `962cac3a4d7d2f631153d8981d57cbde979efa7aa831dc39b88a372e6975e1d4` |
| `artifacts/validation/r14-test-evidence/android-module.log` | `52a6c8bd7594f23e94eaee9325661db43f4f7c17d39960c537945a5653630bf3` |
| `artifacts/validation/r14-test-evidence/evidence.json` | 提交时由 Git 事实绑定 |

统一校验命令：

`python scripts/run_r14_specialized_matrix.py --check --evidence artifacts/validation/r14-test-evidence/evidence.json`

本报告不替代 R14 最终 `TEST_APK`、版本专属 GitHub 真实交互候选、Staging 或 machine completion；这些仍由 `TASK-R14-006` 至 `TASK-R14-008` 顺序完成。
