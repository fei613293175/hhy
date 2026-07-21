# TASK-R07-001 开发就绪核验

- Release：`R07` — 搜索、发布者主页与联系方式保护
- Session：`SES-20260721T082026Z-E6763DFE`
- Story：`STORY-R07-005`（契约、数据、配置、测试与交接）
- 入口变更：`CR-0174`

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | `REQ-CONTACT-001`、`REQ-SEARCH-001`、`REQ-PUBLISHER-001`，另含 APK 通用门禁 |
| 页面/交互面 | 5 个 Android 面：全局搜索、搜索结果、发布者主页、联系方式面板、清空搜索历史确认 |
| API | 7 个冻结 operationId：搜索 4 个、发布者主页 2 个、联系方式访问 1 个 |
| Story | 5 个，均为 `READY_FOR_IMPLEMENTATION` |
| 数据 | 33 张相关表；R07 新增核心为 `search_histories`、`hot_search_terms`，并复用内容、用户、会员与账务只读事实 |
| DoR | `PASS_DOCUMENTATION_READY`，全部适用项为 PASS |
| 文档门禁 | `check_v122_documentation.py --release R07` 为 PASS，0 errors / 0 warnings / 0 open gaps |
| Release 产物结构 | `check_release_artifacts.py --release R07` 为 PASS |
| API 合同 | `check_api_contract.py` 为 PASS，client 131 / admin 184 / websocket 10 |
| 全项目执行计划 | `check_program_execution_plan.py` 为 PASS，31 个 Release / 0 errors |

## R06 依赖与异步真机边界

- R06 的机器开发、候选 APK、自动化、视觉验收和机器关闭均为 PASS。
- `TASK-R06-008` 仅因项目所有者真机反馈尚未到达而处于 `BLOCKED_EXTERNAL_GATE`；`owner_physical_test` 必须保持 `PENDING`。
- R06 正式验收继续为 `PENDING_OWNER_PHYSICAL_TEST`，生产激活继续为 `BLOCKED_OWNER_PHYSICAL_TEST`。
- `CR-0172` 与 `CR-0173` 只允许在上述严格事实全部成立时进入独立 R07；不会把 R06 或 owner 门禁伪标为完成。
- `TASK-R02-007`、`TASK-R03-007` 保留历史阻断记录，不改变 R07 已被仓库依赖图声明为 READY 的事实。

## R07 实施顺序

1. `TASK-R07-002`：搜索历史、热词及相关内容访问数据迁移、唯一约束、状态历史、幂等与回滚验证。
2. `TASK-R07-003`：实现 R07 冻结后端应用服务和接口，覆盖权限、错误码、审计与 Outbox。
3. `TASK-R07-004`：按精确视觉合同实现搜索、发布者主页、联系方式面板和清空历史确认。
4. `TASK-R07-005`：执行主路径、拒绝、幂等、并发、超时和故障注入。
5. `TASK-R07-006` 至 `008`：观测与 Staging、Android 最终候选、桌面交付、异步真机验收、版本关闭与无状态交接。

## 不可降级规则

- 页面结构、字号、行高、间距、宽高、圆角、颜色、阴影、动效和状态必须来自冻结效果图、Design Token、页面规格和精确视觉合同。
- 普通任务只运行受影响的 FAST/MODULE 验证；完整集成、Android 模拟器、截图和候选 APK 仅在 R07 最终候选阶段执行。
- Android 必须使用真实 Navigation Compose 返回栈、`HhyIcons` 和 `HhyMotion`，并通过全项目 Android UI 基础门禁。
- 测试 APK 必须注入 `https://api.orbexa.cc`，使用稳定测试签名、递增 versionCode，并完成仓库、桌面、服务器、公网四方 SHA-256 核对。
- 机器候选 PASS 后由 AI 判断截图与功能证据是否合格；项目所有者真机反馈为异步输入，不得因尚未反馈停止后续独立版本开发。

## 结论

R07 的页面、字段、状态、动作、API、配置、数据和测试输入无阻断性 TBD。允许关闭 `TASK-R07-001`，并按依赖进入 `TASK-R07-002` 数据迁移与领域不变量开发。
