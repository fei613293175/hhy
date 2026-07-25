# SCR-AUTH-005 · 账号冻结

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 账号 | /account-blocked | PAGE | MOB-GATE | R02 | 是 | NORMAL | READY |

**业务目标：** 原因、期限、申诉

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-GATE | 启动与全局门禁 | 品牌区\|状态说明\|主操作\|次操作\|诊断信息 | 安全区、主状态、恢复入口、版本/requestId | 并行检查结果必须按优先级合并；强制更新和维护高于登录导航 | 状态变化需读屏播报；主操作触控区≥48dp | 适配小屏和横屏，主操作保持可见 | 状态优先级单测、错误态截图、导航测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00179 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00180 | 主要内容 | id | ID | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00181 | 主要内容 | phoneMasked | phone Masked | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | phone Masked加载失败时显示字段级占位或隐藏 |
| FLD-00182 | 主要内容 | nickname | 昵称 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多255字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 昵称加载失败时显示字段级占位或隐藏 |
| FLD-00183 | 主要内容 | avatarUrl | 头像 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 头像加载失败时显示字段级占位或隐藏 |
| FLD-00184 | 主要内容 | bio | 个人简介 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多255字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 个人简介加载失败时显示字段级占位或隐藏 |
| FLD-00185 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-00186 | 主要内容 | identityStatus | identity Status | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | identity Status加载失败时显示字段级占位或隐藏 |
| FLD-00187 | 主要内容 | membershipStatus | membership Status | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | membership Status加载失败时显示字段级占位或隐藏 |
| FLD-00188 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-00189 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00190 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | supportPostSupportTickets.header | 必填 | 执行“创建工单”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00191 | 表单输入 | category | 问题分类 | INPUT | string | TEXT_INPUT | supportPostSupportTickets.request | 必填 | 执行“创建工单”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 问题分类不符合要求 |
| FLD-00192 | 表单输入 | subject | 主题 | INPUT | string | TEXT_INPUT | supportPostSupportTickets.request | 必填 | 执行“创建工单”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 主题不符合要求 |
| FLD-00193 | 表单输入 | content | 正文 | INPUT | string | RICH_TEXT_OR_TEXTAREA | supportPostSupportTickets.request | 必填 | 执行“创建工单”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文不符合要求 |
| FLD-00194 | 表单输入 | attachments | 附件 | INPUT | array<string> | MEDIA_UPLOADER | supportPostSupportTickets.request | 可选；按业务条件或页面状态决定 | 执行“创建工单”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 附件不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| INITIAL | 页面首次创建，尚未发起请求 | 显示稳定布局占位，禁止空白闪烁 | 发起初始化 | 与当前状态、权限或服务端version冲突的所有写操作 | 自动进入下一状态 | 否 |
| CHECKING | 正在执行版本、维护、资格或安全检查 | 显示检查进度和当前步骤，禁止提前导航 | 取消/重试（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 超时后进入可恢复错误 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| BLOCKED | 系统维护、强制更新或账号限制阻断 | 展示阻断原因和唯一允许动作 | 更新/重试/联系客服 | 与当前状态、权限或服务端version冲突的所有写操作 | 阻断优先级高于普通导航 | 是 |
| RETRYABLE_ERROR | 网络、限流或临时依赖故障 | 说明可恢复原因和最早重试时间 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得丢失安全可保留输入 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-AUTH-005-01 | 当前用户资料 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 路由参数、权限和页面状态有效 | 不需要 | GET /api/v1/me；请求模型 UserGetMeParameters；响应模型 UserGetMeResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-AUTH-005-02 | 创建工单 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“创建工单”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,category,subject,content,attachments；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/support/tickets；请求模型 SupportPostSupportTicketsRequest；响应模型 SupportPostSupportTicketsResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建工单成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-AUTH-001;REQ-AUTH-002;REQ-AUTH-003;REQ-AUTH-004`
- API：`GET /api/v1/me;POST /api/v1/support/tickets`
- operationId：`userGetMe;supportPostSupportTickets`
- 配置组：`auth;system`
- 关键配置：`auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-AUTH_001-HAPPY;TST-AUTH_002-HAPPY;TST-AUTH_003-HAPPY;TST-AUTH_004-HAPPY;TST-AUTH_001-IDEMPOTENT;TST-AUTH_002-IDEMPOTENT;TST-AUTH_003-IDEMPOTENT;TST-AUTH_004-IDEMPOTENT`
- UI参考：`B01/P02-P07`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
