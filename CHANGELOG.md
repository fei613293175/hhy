# CHANGELOG

## R02 认证启动首切片 · 2026-07-18

- H5 邀请注册页完成真实四端点闭环：公开配置验证邀请码和当前协议版本，页面覆盖安全挑战、短信限流、密码注册、失效邀请、离线重试与注册成功跳转下载页；写请求不自动重试，敏感表单只保留在内存。
- Android APK 打包现在强制校验 `HHY_API_BASE_URL`；未注入、`.invalid` 占位域名、非 HTTPS 或携带凭据的地址会直接使构建失败，防止再交付必然显示“暂时无法连接”的 APK。
- 管理后台用户列表与详情已接入真实分页、筛选和脱敏数据，并补齐限制、解除限制、双人复核冻结、解冻与强制下线的权限化确认流程。
- 新增 `user_restrictions` 持久化、细粒度用户管控权限、幂等加密快照和限制到期自动恢复；数据库门禁升级为 V001—V018、199 表完整迁移与历史回滚重放。
- 新增账号注销申请后端闭环：敏感操作短信验证、数据版本乐观锁、状态历史与全会话失效在同一事务中完成。
- 补齐冻结账号受限自助通道：受限会话只可读取本人脱敏资料和提交幂等申诉工单，不能访问其他登录后 API。
- 已登录用户新增登录设备列表、远端设备二次确认下线和修改登录密码入口；修改密码成功后清除本机刷新凭据并强制重新登录。
- 用户 Bearer 令牌现在必须同时通过签名与数据库活跃会话校验；改密、设备下线或会话版本变化会立即使旧访问令牌失效。
- 登录设备接口改用不含 accessToken/refreshToken 的安全会话摘要，Android 页面与生成客户端类型同步阻止凭据回显。
- Android 新增密码登录、短信登录、注册、忘记密码与图形安全验证入口；启动完成后未认证用户进入认证页，安全验证图片在端内渲染。
- 后端新增认证挑战、密码/短信登录、邀请码校验、注册、重置密码和刷新令牌接口，并补齐密码失败锁定、幂等快照、验证码尝试限制和 V017 数据库不变量。
- `api.orbexa.cc` 已作为受控开发 API 入口接入服务器 loopback 后端；测试 APK 默认使用已发布的 `official/STAGING` 版本策略，修复真机“暂时无法连接”启动兜底。
- Android 认证请求已统一使用网络层冻结契约模型，并使用随机安装指纹建立最小设备会话上下文；不再采集 Android 硬件标识。
- 认证页现在按冻结错误码展示校验失败、账号受限和限流恢复提示，并保留服务端请求编号，便于真实联调排查。
- 登录后的刷新令牌现通过 Android Keystore 加密保存；应用重启会用冻结 refresh 接口恢复会话，访问令牌不落盘，恢复失败会安全回到登录页。
- 注册流程现在要求邀请码通过服务端校验；修改邀请码会立即使之前的校验失效，防止未经验证的注册提交。
- 注册页现在只读取服务器当前生效的协议版本并要求用户确认，不再允许手动输入协议版本 ID；未配置生效协议时不会提交注册。
- 认证请求提交期间现在冻结路由和输入字段；邀请码校验结果只绑定发起校验时的原始值，避免异步返回造成前端误显示为已验证。
- 启动门禁补齐强制更新下载内容暂不可用时仍阻断进入、以及版本检查失败保留请求编号的回归保障。
- 启动更新下载现在仅接受受信的 HTTPS APK 域名；异常协议、伪造主机、携带凭据、非默认端口或片段的地址会被拒绝且不会打开系统浏览器。
- 补齐认证挑战、短信发送、邀请码校验、密码重置和短信登录的 HTTP 成功契约回归，冻结请求字段与幂等头均有云端 Maven 验证。
- 补齐公开认证接口的拒绝契约：缺失或过短幂等键、畸形注册体及非法邀请码请求均返回安全校验信封，且不会调用认证服务。
- Android 认证失败提示不再直接展示服务端错误码；页面继续保留面向用户的提示与请求编号，供客服排障使用。
- 未配置短信供应商时服务端显式失败且不伪造验证码；阿里云短信 SecretRef、模板、连接测试和真实回执继续由 R03 配置生命周期完成。

## 跨电脑 Git、模型分级与云端既有环境门禁 · 2026-07-18

- 跟踪不含秘密的仓库 transport descriptor，支持在新电脑、完整仓库或已验证 Git Bundle 中恢复 `origin`/upstream，并拒绝 force、错误 remote、behind/diverged 和含凭据 URL 的推送。
- 固化任务模型路由：复杂/高风险使用 Sol，中等使用 Terra，轻量/机械/只读使用 Luna；目标模型不可用时必须记录实际回退且不得伪称。
- 固化 Codex 默认已连接 `obx-test` 和项目既有环境的前提；启动预检失败必须阻断，禁止按无服务器状态开发或重建本地 Android SDK，Android 继续复用固定镜像和 Gradle 缓存。

## 跨 AI / 跨设备默认多代理授权 · 2026-07-18

- 将项目所有者长期授权写入权威连续性策略、AGENTS、START_HERE、导出模板、Context Pack、R02—R32 总计划和 R02—R04 并行计划；后续无需逐 Task 再确认是否启用执行代理。
- 固定调度模型为 1 个事实主控加最多 3 个隔离执行代理；未委托必须在 Checkpoint 记录原因，不支持代理的 AI 禁止伪造并行证据。
- 新增已批准 CR 精确范围应用、Checkpoint 结构化并行决策、第 4 个代理拒绝、路径租约与模板/Context Pack 防漂移门禁。

## R02 并行加速开发基线 · 2026-07-18

- 保留唯一主控 Session，引入最多 3 个受托执行代理、独立 scratch worktree、互斥路径租约和主控统一集成，避免事件哈希链及 Release 事实冲突。
- 将 R02 后续实施改为 4 个可并行纵向 Story 批次，再统一进入专项测试、预发布、APK 和封板流程。
- 建立 FAST、MODULE、INTEGRATION、RELEASE 四级门禁、受影响测试映射、确定性生成漂移检查和每日一次全量集成。
- APK 交付基线要求稳定测试签名、单调 versionCode、桌面/仓库/服务器/公网四方哈希，并把机器交付与用户真机验收分开记录。

## R01 Android 测试 APK 交付准备 · 2026-07-18

- Debug 测试包使用独立包名、测试签名，并启用 AGP 9 的资源覆盖功能，使应用名称明确显示“合伙云 Pro 测试”，避免与正式包混淆。
- 新增基于 Java 21、Android SDK Platform 37.0 与 Build Tools 36.0.0 的可复现 Docker 工具链，供后续测试 APK 复用。

## R01 管理员认证与个人安全前端 · 2026-07-17

- 管理后台新增真实登录、MFA 二次验证与管理员安全设置三页面，联通 R01 冻结的 8 个后台安全接口。
- 管理员访问令牌、MFA 票据和绑定信息仅保存在页面内存；刷新、退出、改密、权限撤销和弹窗关闭均执行敏感信息清理。
- 补齐请求内容感知幂等、结构化字段错误、限流倒计时、受限账号、无权限、离线和网络瞬断重试状态。
- 所有后台目录路由统一纳入认证守卫，高风险弹窗补齐键盘焦点圈定、Escape 关闭与关闭还焦。
- 新增同源本地交互验证服务及管理端路由、页面、服务、HTML 入口回归测试。
- 管理端写操作统一处理超时、429 冷却、离线清理、冲突刷新与重复提交，补齐故障注入回归。
- 7 个管理员安全 POST 持久化加密保存首次响应；改密、退出或 MFA 状态变化后仍可凭原幂等键安全重放。
- 新增 27 项 R01 权威测试矩阵、设计 Token 防漂移、配置注册表与 PostgreSQL 迁移不变量门禁。
- R01 隔离预发布栈接入 Prometheus、Alertmanager 与隐私最小化告警回执，错误率/P95 阈值与冻结配置统一为 2%/500ms。
- 新增管理员活跃会话、五分钟认证失败、MFA 启用、幂等快照不变量及 Gauge 查询失败指标和告警。
- 修正 4xx/5xx 错误体把 RequestId 冒充 TraceId 的问题，安全拒绝路径现在端到端回传真实 TraceId；未知业务端口资源按 404 返回，不再污染 5xx RED 指标。
- 不受支持的请求媒体类型统一返回冻结的 400 校验错误，不再落入 500 并污染服务端故障率。
- 加密幂等快照损坏测试改为确定性修改有效密文字节，消除 Base64 尾位导致的概率性假阴性。

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

- R02 后台用户读取闭环：实现用户列表、筛选、分页、服务端手机号脱敏与详情查询，`ADM-USER-001/002` 替换占位页并覆盖加载、空、离线、无权限、404、重试和详情导航。

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

## TASK-R01-005 · COMPLETED · 2026-07-17T13:55:41Z

- Task close: TASK-R01-005 / SES-20260717T122518Z-91E6F4D6
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：TASK-R01-005完成：权威27项测试27/27 PASS，Java21、PostgreSQL17和隔离真实API证据归档，关键缺陷清零，CR-0016关闭。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T122518Z-91E6F4D6.md`

## TASK-R01-006 · COMPLETED · 2026-07-17T15:20:29Z

- Task close: TASK-R01-006 / SES-20260717T141717Z-A01412D7
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01独立预发布栈、结构化日志与TraceId、RED和九类业务监控序列、双告警firing/resolved、日志脱敏、最终镜像回滚及AC-R01-004现场证据全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T141717Z-A01412D7.md`

## TASK-R01-007 · COMPLETED · 2026-07-17T17:06:16Z

- Task close: TASK-R01-007 / SES-20260717T152721Z-016DB4B2
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01 Android测试APK已按固定工具链构建、签名和归档；项目所有者于2026-07-18提供真机截图确认安装、启动、首页和五项导航均通过。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T152721Z-016DB4B2.md`

## TASK-R01-008 · COMPLETED · 2026-07-17T17:45:20Z

- Task close: TASK-R01-008 / SES-20260717T171412Z-7CD86701
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01全部验收证据、真机APK、HTTPS下载、发布标签和无状态交接均已完成。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T171412Z-7CD86701.md`

## TASK-R02-001 · COMPLETED · 2026-07-17T20:02:10Z

- Task close: TASK-R02-001 / SES-20260717T183459Z-D64E7407
- Release：`R02`
- Story：`STORY-R02-009`
- Actor：`codex-master`
- 摘要：完成R02开发就绪与并行加速基线：主控与隔离执行机制、纵向切片任务图、分层测试、确定性代码生成、APK持续交付和CI全量集成已落地并验证
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T183459Z-D64E7407.md`

## TASK-R02-002 · COMPLETED · 2026-07-18T05:31:33Z

- Task close: TASK-R02-002 / SES-20260717T210927Z-13B07A7D
- Release：`R02`
- Story：`STORY-R02-003`
- Actor：`codex-root`
- 摘要：TASK-R02-002 completed: startup, password/SMS authentication, invite registration, controlled agreement versions, source/runtime contract synchronization, cloud backend test, fixed-image Android tests, WIP APK, and trace records delivered.
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T210927Z-13B07A7D.md`

## TASK-R02-003 · COMPLETED · 2026-07-18T07:05:22Z

- Task close: TASK-R02-003 / SES-20260718T053331Z-F0ED92BF
- Release：`R02`
- Story：`STORY-R02-005`
- Actor：`codex-root`
- 摘要：完成R02账户安全批次：受限账户申诉、账户注销与短信敏感操作闭环；修复测试APK因遗漏API地址导致暂时无法连接的问题，并建立打包阻断与自动回归测试。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T053331Z-F0ED92BF.md`

## TASK-R02-004 · COMPLETED · 2026-07-18T08:14:54Z

- Task close: TASK-R02-004 / SES-20260718T070836Z-25E39817
- Release：`R02`
- Story：`STORY-R02-001`
- Actor：`codex-root`
- 摘要：完成TASK-R02-004：用户列表、详情、限制、双人冻结、解冻、强制下线、到期恢复、V018迁移和全部模块门禁真实闭环。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T070836Z-25E39817.md`

## TASK-R02-005 · COMPLETED · 2026-07-18T08:44:58Z

- Task close: TASK-R02-005 / SES-20260718T081744Z-71EAAA84
- Release：`R02`
- Story：`STORY-R02-008`
- Actor：`codex-root`
- 摘要：完成TASK-R02-005：公开邀请注册配置、H5四端点安全调用链、MOB-AUTH-FORM页面、加载/失效/离线/重试/限流/成功导航与幂等自动化全部闭环。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T081744Z-71EAAA84.md`

