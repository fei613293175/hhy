# CHANGELOG

## R01 管理员认证与个人安全前端 · 2026-07-17

- 管理后台新增真实登录、MFA 二次验证与管理员安全设置三页面，联通 R01 冻结的 8 个后台安全接口。
- 管理员访问令牌、MFA 票据和绑定信息仅保存在页面内存；刷新、退出、改密、权限撤销和弹窗关闭均执行敏感信息清理。
- 补齐请求内容感知幂等、结构化字段错误、限流倒计时、受限账号、无权限、离线和网络瞬断重试状态。
- 所有后台目录路由统一纳入认证守卫，高风险弹窗补齐键盘焦点圈定、Escape 关闭与关闭还焦。
- 新增同源本地交互验证服务及管理端路由、页面、服务、HTML 入口回归测试。
- 管理端写操作统一处理超时、429 冷却、离线清理、冲突刷新与重复提交，补齐故障注入回归。
- 7 个管理员安全 POST 持久化加密保存首次响应；改密、退出或 MFA 状态变化后仍可凭原幂等键安全重放。
- 新增 27 项 R01 权威测试矩阵、设计 Token 防漂移、配置注册表与 PostgreSQL 迁移不变量门禁。

## P00 工程基线修复 · 2026-07-17

- 产品版本保持 V1.2.2，修正 Android 内残留的 V1.2.1 展示和重复版本常量校验。
- 后台页面生成/测试同步为 74 页，Android 页面生成同步为 102 页，保留冻结的页面富化规格。
- 后端运行时同步完整的 198 表 V001—V009 迁移、184 个后台 API 及 10 个 WebSocket 事件契约。
- 修复 pnpm 锁文件中不存在的 `source-map-js@1.2.2`，并为递归 JSON 类型增加确定性 TypeScript 后处理。
- 修复首次 Git Bootstrap 与预领取 P00 会话之间的连续性死锁，并将其接入 CI 回归。

## V1.2.2 · 页面与运营规格冻结版 · 2026-07-16

- 新增 189 个页面/交互面施工合同、5159 个字段、1633 个状态和 606 个动作规格。
- 管理后台从 60 页扩展至 74 页，补齐认证、举报申诉、工单详情、会计、审批、风控规则、通知、导出和管理员安全。
- 315 个 REST 接口和 10 个 WebSocket 事件全部明确 UI 或系统所有者。
- 核心 OpenAPI/WS 资源类型化，移除文字型 `additionalProperties: true`。
- 314 个配置项补齐控件、范围、角色、审批、依赖和回滚；新增 58 条跨字段规则。
- 全部需求显式追踪到配置或 N/A；新增 162 个故事和 396 项逐版本 DoR。
- 风险重新分类；P00—R32 实现不再被误写为文档缺口。
- 新增 `scripts/check_v122_documentation.py` 文档防漂移门禁。

# Changelog

## V1.2.2 — 2026-07-16

- 修复 H5 邀请注册错误关联 R27、设计 Token 引用、后台路由 TBD 和状态命名冲突；
- OpenAPI 升级为类型化冻结契约，增加启动维护/公开版本检查/公开帮助接口；
- 页面映射升级为动作级精确契约，新增 operationId、权限、幂等、错误状态、埋点和测试绑定；
- 数据字典补齐技术字段，新增 Flyway 迁移、索引约束、验证和回滚脚本；
- 新增统一复式记账、Outbox/Inbox、余额投影与统一对账模型；
- 配置注册表增加生命周期、激活门禁、SecretRef、重启/热更和回滚策略；
- 发布顺序改为依赖图，基础 RBAC、Provider 框架、版本检查前置；
- 测试计划扩展为场景级并加入语义门禁；
- 新增后端、Android、Admin、H5、基础设施、CI 和代码生成骨架；
- Project Doctor 升级为严格语义检查。

## V1.2 — 2026-07-15

原工程执行强化基线，保存在 `docs/99-archive/` 供追溯。

## V1.2.2 正式开发基线补丁 — 2026-07-16

- 修复 GET/DELETE 请求体泄漏和 Parameters Schema 缺失；
- 消除客户端/后台 operationId 冲突；
- 统一配置生命周期、SecretRef 词汇和生产签名安全策略；
- 补齐 R03 外部激活任务与阻断验收；
- 优化 Project Doctor 性能并消除语义误报；
- 增加 UTF-8 构建环境、PostgreSQL 17.10 CI smoke 和开发风险登记；
- 复验后端 8 模块/5 测试、Web 类型/测试/构建、8 个数据库迁移及关键会计不变量。

## TASK-P00-001 · COMPLETED · 2026-07-17T02:26:52Z

- Task close: TASK-P00-001 / SES-20260716T232809Z-B4A980AF
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：完成P00开发就绪、故事领取、冻结契约纠正、工程基线与无状态接续门禁
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md`

## TASK-P00-002 · COMPLETED · 2026-07-17T02:33:56Z

- Task close: TASK-P00-002 / SES-20260717T023226Z-06841AFC
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：PostgreSQL 17.10空库/升级、并发不变量、Flyway重复迁移与U010/U011回滚重放全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023226Z-06841AFC.md`

## TASK-P00-003 · COMPLETED · 2026-07-17T02:37:21Z

- Task close: TASK-P00-003 / SES-20260717T023511Z-C6513BB0
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：三个P00 operationId、权限/错误码/审计、模块边界与真实数据库版本策略全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023511Z-C6513BB0.md`

## TASK-P00-004 · COMPLETED · 2026-07-17T02:42:30Z

- Task close: TASK-P00-004 / SES-20260717T023848Z-9F352CA9
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00零页面N/A边界、两个故事、生成API类型、H5/Admin与Android客户端契约全部验证通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023848Z-9F352CA9.md`

## TASK-P00-005 · COMPLETED · 2026-07-17T05:31:12Z

- Task close: TASK-P00-005 / SES-20260717T024407Z-B03C9375
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00精确59项测试矩阵全部通过并归档10套可验证证据，关键缺陷清零
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T024407Z-B03C9375.md`

## TASK-P00-006 · COMPLETED · 2026-07-17T05:37:35Z

- Task close: TASK-P00-006 / SES-20260717T053626Z-25451510
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00可观测性、预发布环境、告警故障注入和回滚验收全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T053626Z-25451510.md`

## TASK-P00-007 · COMPLETED · 2026-07-17T05:40:19Z

- Task close: TASK-P00-007 / SES-20260717T053909Z-3A8B6A51
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00 Android APK可下载、清洁构建、签名、SHA256和版本信息完整，项目所有者真机安装启动验收通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T053909Z-3A8B6A51.md`

## TASK-P00-008 · COMPLETED · 2026-07-17T05:51:00Z

- Task close: TASK-P00-008 / SES-20260717T054147Z-7AE51A86
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00八项任务、六项验收、59项测试、Staging可观测性、APK真机验收及无状态交接全部完成；按项目所有者要求封板后暂停
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T054147Z-7AE51A86.md`

## TASK-R01-001 · COMPLETED · 2026-07-17T08:03:00Z

- Task close: TASK-R01-001 / SES-20260717T074317Z-C575A687
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：完成R01开发就绪、三故事领取、验证器语义修复、NEXT_TASK命令集和8接口/27测试任务合同对齐
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T074317Z-C575A687.md`

## TASK-R01-002 · COMPLETED · 2026-07-17T08:41:19Z

- Task close: TASK-R01-002 / SES-20260717T080633Z-7A2C9226
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01管理员认证V012数据迁移、状态机、领域不变量和PostgreSQL17空库升级回滚并发验证全部完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T080633Z-7A2C9226.md`

## TASK-R01-003 · COMPLETED · 2026-07-17T11:11:58Z

- Task close: TASK-R01-003 / SES-20260717T084524Z-9FE47D9F
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01管理员认证安全8接口完成，独立审计ACCEPT，PG17/API/代理/卷/重启/文档与连续性门禁全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T084524Z-9FE47D9F.md`

## TASK-R01-004 · COMPLETED · 2026-07-17T12:20:19Z

- Task close: TASK-R01-004 / SES-20260717T111928Z-C383F7A2
- Release：`R01`
- Story：`STORY-R01-001`
- Actor：`codex-root`
- 摘要：R01 管理端登录、MFA 与管理员自身安全设置前端已完成；类型化接口、内存会话、安全错误态、离线与权限收敛、自动化及浏览器验收均通过。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T111928Z-C383F7A2.md`
