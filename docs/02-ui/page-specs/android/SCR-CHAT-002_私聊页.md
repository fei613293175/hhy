# SCR-CHAT-002 · 私聊页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 消息 | /messages/chat/{conversationId} | PAGE | MOB-CHAT-DETAIL | R14 | 是 | HIGH | READY |

**业务目标：** 文本、图片、卡片

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整

**退出/返回：** 返回来源页并同步资源最新状态；来源失效时回到所属模块首页

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-CHAT-DETAIL | 即时聊天详情 | 会话头\|消息流\|状态提示\|输入区\|附件/联系卡片 | 发送状态、重试、已读、拉黑、举报、敏感提示 | clientMessageId去重；失败消息显式重试；拉黑后禁发 | 消息发送者和时间可读；输入附件按钮有标签 | 键盘/图片预览适配 | ACK、重连、乱序、重复、拉黑测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01241 | 页面头部 | pageTitle | 对方名称 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 始终显示 | 只读 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 对方信息暂时无法显示 |
| FLD-01242 | 会话头 | connectionState | 连接状态 | UI_META | ui | STATUS_TAG | LOCAL_UI | 必需 | 连接中、离线或恢复中显示 | 只读 | 由连接状态映射 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 连接状态暂时无法更新 |
| FLD-01243 | 输入区 | messageComposer | 消息输入 | UI_META | ui | MESSAGE_COMPOSER | LOCAL_UI | 会话允许发送时必需 | 未拉黑且会话可发送时显示 | 用户可编辑 | 长度、类型和附件按配置限制 | HIGH | 只展示业务所需内容；禁止写入日志、埋点或非必要缓存 | 消息内容不符合发送要求 |
| FLD-01259 | 消息流 | sender | 发送者 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | chatGetConversationsByIdMessages.response.data.ChatMessageResource | 每条消息必需 | 消息可见时显示 | 只读 | 按服务端Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发送者信息暂时无法显示 |
| FLD-01260 | 消息流 | messageType | 消息类型 | DISPLAY | string | MESSAGE_VARIANT | chatGetConversationsByIdMessages.response.data.ChatMessageResource | 每条消息必需 | 消息可见时按类型渲染 | 只读 | TEXT、IMAGE、CONTENT_CARD、CONTACT_CARD | NORMAL | 无需特殊掩码；仍遵守最小展示 | 暂不支持此消息类型 |
| FLD-01261 | 消息流 | payload | 消息内容 | DISPLAY | ChatMessagePayload | MESSAGE_BUBBLE_OR_CARD | chatGetConversationsByIdMessages.response.data.ChatMessageResource | 每条消息必需 | 消息可见时显示 | 只读 | 按消息类型对应封闭Schema校验 | HIGH | 只展示业务所需内容；禁止写入日志、埋点或非必要缓存 | 消息内容暂时无法显示 |
| FLD-01262 | 消息流 | deliveryState | 送达状态 | DISPLAY | ui | STATUS_TEXT | LOCAL_UI<-ChatMessageResource.status | 发送方消息显示 | 发送中、失败、已送达或已读时显示 | 只读 | 由本地发送态和服务端状态映射 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息状态暂时无法更新 |
| FLD-01263 | 消息流 | createdAt | 发送时间 | DISPLAY | string | DATETIME_TEXT | chatGetConversationsByIdMessages.response.data.ChatMessageResource | 每条消息必需 | 按时间分组规则显示 | 只读 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发送时间暂时无法显示 |
| FLD-01264 | 消息流 | readAt | 已读时间 | DISPLAY | string | DATETIME_TEXT | chatGetConversationsByIdMessages.response.data.ChatMessageResource | 服务端返回时展示 | 发送方消息已读时显示 | 只读 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 已读时间暂时无法显示 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| CONNECTING | 即时连接建立或恢复中 | 显示连接状态；允许浏览已加载消息 | 返回 | 与当前状态或权限冲突的写操作 | 连接成功进入CONTENT | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态或权限冲突的写操作 | 刷新或执行操作 | 否 |
| SENDING | 消息发送中 | 对应消息气泡显示发送中状态 | 等待；失败后可重试或删除本地失败项 | 与当前状态或权限冲突的写操作 | 服务端确认后进入已发送状态；失败时保留单条重试 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态或权限冲突的写操作 | 恢复网络后主动刷新 | 否 |
| BLOCKED | 系统维护、强制更新或账号限制阻断 | 显示当前无法发送消息及可执行的解除拉黑或返回操作 | 解除拉黑（允许时）/返回 | 与当前状态或权限冲突的写操作 | 服务端确认解除后恢复输入区 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示面向用户的失败原因、重试和返回，不显示内部诊断信息 | 重试/返回 | 与当前状态或权限冲突的写操作 | 按用户可见错误类型恢复 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-CHAT-002-01 | 消息分页 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉加载历史、恢复连接或滚动到历史边界时触发；相同查询避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 内部校验会话与分页参数，不向用户展示 | 不需要 | GET /api/v1/conversations/{id}/messages；请求模型 ChatGetConversationsByIdMessagesParameters；响应模型 ChatGetConversationsByIdMessagesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 按服务端顺序渲染消息并去重合并；保持当前阅读位置；无消息显示会话起始空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前私聊页 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-CHAT-002-02 | 发送消息 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+成员，且资源状态允许“发送消息”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端状态未被本地标记过期 | 校验 id,X-Idempotency-Key,clientMessageId,messageType,payload；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 普通消息不二次确认；联系方式在专用面板确认发送字段 | POST /api/v1/conversations/{id}/messages；请求模型 ChatPostConversationsByIdMessagesRequest；响应模型 ChatPostConversationsByIdMessagesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以服务端消息资源新增或更新对应消息气泡；同步送达状态；不显示多余成功Toast | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留可安全恢复的上下文并提示稍后重试 | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 留在当前私聊页并定位到对应消息 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-CHAT-002-03 | 更新已读 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 消息进入可见区域且会话未读位置推进时由客户端自动触发 | 具备权限 登录+成员，且资源状态允许“更新已读”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端状态未被本地标记过期 | 校验 id,X-Idempotency-Key,lastReadMessageId；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 不需要；该动作由阅读行为自动触发 | POST /api/v1/conversations/{id}/read；请求模型 ChatPostConversationsByIdReadRequest；响应模型 ChatPostConversationsByIdReadResponse | X-Idempotency-Key + 服务端结果查询 | 后台轻量提交，不锁定页面、不显示全页加载 | 后台更新当前会话已读位置并同步会话列表未读数；不显示成功Toast | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留可安全恢复的上下文并提示稍后重试 | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 留在当前私聊页 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-CHAT-001;REQ-CHAT-002`
- API：`GET /api/v1/conversations/{id}/messages;POST /api/v1/conversations/{id}/messages;POST /api/v1/conversations/{id}/read`
- operationId：`chatGetConversationsByIdMessages;chatPostConversationsByIdMessages;chatPostConversationsByIdRead`
- 配置组：`chat`
- 关键配置：`chat.stranger.daily_conversation_limit;chat.message.per_minute_limit;chat.image.max_mb;chat.history.retention_days`
- 测试：`TST-CHAT_001-HAPPY;TST-CHAT_002-HAPPY;TST-CHAT_001-IDEMPOTENT;TST-CHAT_002-IDEMPOTENT`
- UI参考：`SPEC:design/R14-UI-FROZEN/specs/SCR-CHAT-002.md`
- 视觉覆盖状态：`IN_REVIEW`（编码完成并取得实现截图前不得标记 PASS）

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
