# TASK-R08-001 开发就绪核验

- Release：`R08` — 项目推广完整闭环
- Session：`SES-20260722T002444Z-8BEC3CA6`
- Story：`STORY-R08-004`（契约、数据、配置、测试与交接）
- 入口变更：`CR-0206`、`CR-0207`

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | Release 治理需求为 `REQ-CONTACT-001`、`REQ-PROJECT-001`；页面故事还复用 `REQ-CONTENT-001`、`REQ-PUBLISH-001` |
| 页面/交互面 | 3 个 Android 面：项目列表、项目详情、项目发布/编辑 |
| Story | 4 个，均为 `READY_FOR_IMPLEMENTATION` |
| 数据 | Manifest 登记 20 张相关表；项目详情和发布故事按冻结目录复用内容、会话、CMS 与版本事实 |
| DoR | `PASS_DOCUMENTATION_READY`，全部适用项为 PASS |
| 文档门禁 | `check_v122_documentation.py --release R08` 为 PASS，0 errors / 0 warnings / 0 open gaps |
| Git 传输 | `restore_git_transport.py push-preflight` 为 PASS，分支与远端 0 ahead / 0 behind |
| 云环境 | `verify_cloud_environment.py` 为 PASS，既有 `obx-test` 环境可用 |

## 九端点与八个 Android operationId 的口径

- `TASK-R08-003` 和 `catalogs/release_artifact_index.csv` 的工程端点数量为 9。
- 三个 Android 页面故事直接引用 8 个唯一 operationId：内容列表、详情、联系方式访问、直接会话、收藏、分享、发布和编辑。
- 第 9 个是 `publicGetShareContentsById`（`GET /public-api/v1/share/contents/{id}`），来自 `REQ-PROJECT-001` 追踪矩阵；其 OpenAPI `x-release` 仍为 `R28`。
- R08 只验证该公共分享合同与项目分享链路兼容，不得改写其 R28 归属，不得据此发明第 4 个 R08 页面，也不得提前扩张产品范围。
- `RELEASE_MANIFEST.contracts.client_api` 为空表示 R08 没有新增版本自有合同，不表示页面不调用既有冻结接口。

## 历史依赖与异步真机边界

- R05 已完成并满足 R08 依赖。
- R06、R07 的机器开发、候选 APK、自动化、视觉验收和机器关闭事实均已归档；各自最后关闭任务仅因项目所有者真机反馈未到而保持 `BLOCKED_EXTERNAL_GATE`。
- R06、R07 的正式验收与生产激活继续阻断；仅后续独立开发被允许，不把任何 owner 门禁伪标为完成。
- `TASK-R02-007`、`TASK-R03-007` 保留历史阻断记录，不改变 R08 已经由依赖校验器合法进入 `IN_PROGRESS` 的事实。

## R08 实施顺序

1. `TASK-R08-002`：补齐项目详情数据、不变量、唯一约束、幂等和回滚验证。
2. `TASK-R08-003`：核对并实现九端点工程范围，复用 R06/R07 已落地能力，补齐项目发布、编辑、收藏、分享和会话等缺口。
3. `TASK-R08-004`：按精确视觉合同实现项目列表、详情和发布/编辑三页，不新增未登记字段或按钮。
4. `TASK-R08-005`：执行主路径、拒绝、幂等、并发、超时和故障注入。
5. `TASK-R08-006` 至 `008`：观测与 Staging、Android 最终候选、桌面交付、异步真机验收、版本关闭与无状态交接。

## 不可降级规则

- 页面结构、字号、行高、间距、宽高、圆角、颜色、阴影、动效和状态必须来自冻结效果图、Design Token、页面规格和精确视觉合同。
- 普通任务只运行受影响的 FAST/MODULE 验证；完整集成、Android 模拟器、截图和候选 APK 仅在 R08 最终候选阶段执行。
- Android 正式界面必须使用现有 Design Token、`HhyIcons`、`HhyMotion` 和真实 Navigation 返回栈，不得展示技术字段或内部错误细节。
- 测试 APK 必须注入 `https://api.orbexa.cc`，使用稳定测试签名、递增 versionCode，并完成仓库、桌面、服务器、公网四方 SHA-256 核对。
- 机器候选 PASS 后由 AI 判断截图与功能证据是否合格；项目所有者真机反馈为异步输入，不得因尚未反馈停止后续独立版本开发。

## 结论

R08 的页面、字段、状态、动作、API、配置、数据和测试输入无阻断性 TBD。允许关闭 `TASK-R08-001`，并按依赖进入 `TASK-R08-002` 数据迁移与领域不变量开发。
