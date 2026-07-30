# R12 TASK-002 数据库与领域不变量门禁

- Release：`R12`
- Task：`TASK-R12-002`
- Story：`STORY-R12-008`
- Session：`SES-20260724T235749Z-6EC9DB09`
- 变更控制：`CR-0316`、`CR-0317`
- 结果：`PASS`
- 环境：`obx-test`，PostgreSQL `17.10`，Java `21`

## 实现范围

- `V039/U039` 将 `database/state_machines.yaml` 的唯一 `CONTENT_STATUS` 状态机完整投影到数据库：11 个状态、29 条合法边，`BANNED/DELETED` 无出边。
- 新内容强制从 `DRAFT/version=0` 开始；任何更新必须恰好 `version+1`，同版本状态历史、提交快照、审核命令和事务 Outbox 在提交前完整绑定。
- 联系方式顺序/渠道、内容媒体和迁移版本唯一；内容、类型详情、版本、审核、历史、媒体和联系方式禁止物理删除。媒体及联系方式使用 `removed_at` 软移除，`media_objects` 级联不能绕过守卫。
- V039 在任何 DDL 前锁定父子表并审计旧数据；U039 保留新增兼容列和全部业务数据，可无损重放 V039。
- `DELETE /api/v1/contents/{id}` 冻结合同补齐必填 `expectedVersion`；`OFFLINE_BY_PLATFORM` 不得由用户直接恢复 `ONLINE`。

## 验证证据

| 门禁 | 结果 | 权威证据 |
|---|---|---|
| 空库与历史升级链 | PASS | `run_postgres_migration_smoke.sh`：V001→V039、200 表、最终基线全部通过 |
| 状态机防漂移 | PASS | `CONTENT_STATE_MACHINE_PROJECTION_OK states=11 edges=29 terminals=BANNED,DELETED` |
| 脏升级原子阻断 | PASS | 9/9：详情基数、非法状态边、非法联系/媒体、三类重复、版本断层、审核版本不可绑定 |
| 正反例不变量 | PASS | `R12_PUBLISH_MANAGEMENT_INVARIANTS PASS` |
| U039/V039 回滚重放 | PASS | 业务状态、初始历史和 Outbox 数量重放前后相同 |
| 双会话乐观并发 | PASS | 相同旧版本写入结果 `1:0`；最终 `version=1` 且版本 1 Outbox 恰好一条 |
| 数据库对象投影 | PASS | 20 个触发器、6 个索引、5 个约束；根迁移与运行时副本 SHA 一致 |
| 后端 MODULE | PASS | Maven reactor：380 tests，0 failures，0 errors，0 skipped；Flyway 实际应用 39 个迁移 |
| Web 受影响 MODULE | PASS | Admin 101 tests、H5 29 tests；两端 typecheck 通过 |
| Android 受影响 MODULE | PASS | 固定远端镜像执行 `testDebugUnitTest lintDebug`，571 tasks，`BUILD SUCCESSFUL` |
| 静态合同 | PASS | API 131/184/10、Schema 200 表/39 迁移、43 个运行时资产、生成资产、R12 严格文档和 Release 结构全部通过 |

## 边界与下一步

- 本任务未执行 `assembleDebug`、模拟器、截图或 APK；这些完整候选门禁仅在 `TASK-R12-007` 执行。
- 项目所有者真机反馈保持异步，不阻断机器开发。
- `TASK-R12-002` 关闭后立即进入 `TASK-R12-003`，实现统一发布、复制、审核、上下架、草稿和列表接口，不在数据库任务中伪造控制器完成状态。
