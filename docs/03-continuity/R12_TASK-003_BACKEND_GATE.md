# R12 TASK-003 后端应用服务与接口门禁

- Release：`R12`
- Task：`TASK-R12-003`
- Story：`STORY-R12-008`
- Session：`SES-20260725T025042Z-A53070A0`
- 变更控制：`CR-0318`
- 状态：`PASS`
- 环境：`obx-test`，Java `21`，PostgreSQL `17.10`

## 实现范围

- 10 个发布管理与内容 operationId：详情、复制、数据、我的发布、上下线、草稿、删除、审核记录和提交；写操作使用所有者校验、expectedVersion、稳定幂等键、事务审计和 Outbox。
- 6 个后台审核 operationId：队列、详情、举报、申诉、决定和分配；逐接口绑定后台权限，控制器只传递已认证 `AdminPrincipal`，不采信客户端设备指纹。
- 3 个账户摘要 operationId：资料修改、会员状态和奖励账户；资料修改使用版本与幂等保护，会员和奖励读取使用实名认证与账户事实。
- `V040/U040` 补齐 ESCALATE 二审：同一不可变提交快照最多一次，升级后必须更晚分配二审员，最终决定只能由该管理员完成。ESCALATE 保持 REVIEWING、版本加一、零虚假状态日志；审核、审计和应用 Outbox 以 commandId 精确关联。

## operationId 覆盖

| 分区 | 数量 | 控制器合同 |
|---|---:|---|
| 发布管理与内容 | 10 | `R12PublishingControllerContractTest`，包含既有 `R08Controller.contentGetContentsById` 唯一落点 |
| 后台审核 | 6 | `R12ReviewControllerContractTest`，逐项验证 operationId、路由和权限 |
| 资料、会员、奖励 | 3 | `R12AccountControllerContractTest`，逐项验证 operationId、路由、认证主体和幂等头 |
| 合计 | 19 | 每个冻结 operationId 在运行时 OpenAPI 和控制器中恰好一个落点 |

`userGetMe` 是 R12 页面故事复用的既有接口，不计入本任务新增/补齐的 19 个 operationId。

## 当前验证证据

| 门禁 | 结果 | 证据摘要 |
|---|---|---|
| Java 21 定向 MODULE | PASS | 32 tests，0 failures，0 errors，0 skipped；含服务、三组控制器合同和 ModuleBoundary |
| PostgreSQL Store | PASS | `R12ReviewPostgresStoreTest`、`R12PublishingPostgresStoreTest`，2 tests，0 skipped |
| 空库迁移 | PASS | PostgreSQL 17.10 应用 V001→V040，40/40 成功 |
| 二审事务矩阵 | PASS | 空原因、旧快照、无父更新、缺/错审计、缺/错 Outbox、重复升级、未分配、错误审核员均拒绝；完整二审成功 |
| U040 无事实回滚 | PASS | 回滚后精确恢复 V039 约束，再次应用 V040 成功 |
| U040 有事实阻断 | PASS | `R12_U040_ESCALATION_FACTS_PRESENT`；事实、唯一索引、延迟触发器保持 `1|1|1` |
| Schema 门禁 | PASS | 200 tables，40 migrations，根目录与运行时迁移 SHA-256 一致 |
| Java 21 后端全回归 | PASS | 411 tests，0 failures，0 errors，0 skipped；两项 R12 PostgreSQL Store 测试均实际执行；`obx-test:/tmp/hhy-r12-task003-backend-final-v3.log` |

## 边界与下一步

- 本任务不运行 Android、模拟器、截图或 APK；完整候选仅在 `TASK-R12-007` 执行。
- 后端实现与最终全回归已通过；完成严格文档、连续性和传输门禁后关闭 `TASK-R12-003`，并直接进入 `TASK-R12-004`。
- 项目所有者真机反馈保持异步，不阻断开发。
