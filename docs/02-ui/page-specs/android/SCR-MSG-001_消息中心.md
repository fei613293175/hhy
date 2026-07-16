# SCR-MSG-001 · 消息中心

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 消息 | /messages | PAGE | MOB-HOME | R15 | 是 | NORMAL | READY |

**业务目标：** 聊天/通知/公告

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 登录后由底部导航、业务通知或深链进入

**退出/返回：** 切换底部导航；返回键按应用根导航策略处理

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-HOME | 聚合首页 | 顶栏\|状态/资产摘要\|模块化内容区\|底部导航 | 骨架、空模块降级、模块追踪上下文 | 模块独立失败不得拖垮整页；顺序由服务端模块合同控制 | 模块标题语义化；轮播可暂停 | 卡片宽度按Token缩放 | 模块容错、埋点上下文、CMS兼容测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01195 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01196 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-01197 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-01198 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-01199 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-01200 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-01201 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | chatGetConversations.query | 可选；仅在对应操作/筛选时提交 | 执行“会话列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-01202 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-01203 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-01204 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-01205 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-01206 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-01207 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | chatGetConversations.request | 可选；按业务条件或页面状态决定 | 执行“会话列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-01208 | 主要内容 | id | ID | DISPLAY | string | TEXT | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01209 | 主要内容 | peer | 对方 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 对方加载失败时显示字段级占位或隐藏 |
| FLD-01210 | 主要内容 | lastMessage | 最后消息 | DISPLAY | object | STRUCTURED_SECTION | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后消息加载失败时显示字段级占位或隐藏 |
| FLD-01211 | 主要内容 | unreadCount | 未读数 | DISPLAY | integer | NUMBER_TEXT | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未读数加载失败时显示字段级占位或隐藏 |
| FLD-01212 | 主要内容 | lastReadMessageId | 最后已读消息 | DISPLAY | string | TEXT | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后已读消息加载失败时显示字段级占位或隐藏 |
| FLD-01213 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-01214 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | chatGetConversations.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-01215 | 主要内容 | type | 类型 | DISPLAY | string | TEXT | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 类型加载失败时显示字段级占位或隐藏 |
| FLD-01216 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-01217 | 主要内容 | body | 内容 | DISPLAY | string | TEXT | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容加载失败时显示字段级占位或隐藏 |
| FLD-01218 | 主要内容 | target | 目标 | DISPLAY | NavigationTargetResource | STRUCTURED_SECTION | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 目标加载失败时显示字段级占位或隐藏 |
| FLD-01219 | 主要内容 | readAt | 已读时间 | DISPLAY | string | DATETIME_TEXT | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 已读时间加载失败时显示字段级占位或隐藏 |
| FLD-01220 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | notificationGetNotifications.response.data.NotificationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| PARTIAL_CONTENT | 部分模块成功、部分模块失败 | 保留成功模块，失败模块显示局部重试 | 重试失败模块 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得将局部失败升级为全页失败 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-MSG-001-01 | 会话列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/conversations；请求模型 ChatGetConversationsParameters；响应模型 ChatGetConversationsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-MSG-001-02 | 通知列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/notifications；请求模型 NotificationGetNotificationsParameters；响应模型 NotificationGetNotificationsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-MSG-001-03 | 公告列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/announcements；请求模型 NotificationGetAnnouncementsParameters；响应模型 NotificationGetAnnouncementsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 登录后由底部导航、业务通知或深链进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 切换底部导航；返回键按应用根导航策略处理 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：模块独立加载和刷新；局部失败不清空成功模块；展示数据更新时间
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-CHAT-001;REQ-NOTIFY-001`
- API：`GET /api/v1/conversations;GET /api/v1/notifications;GET /api/v1/announcements`
- operationId：`chatGetConversations;notificationGetNotifications;notificationGetAnnouncements`
- 配置组：`chat;notification`
- 关键配置：`chat.stranger.daily_conversation_limit;chat.message.per_minute_limit;chat.image.max_mb;chat.history.retention_days;notification.marketing.default_enabled;notification.quiet_hours.start;notification.quiet_hours.end`
- 测试：`TST-CHAT_001-HAPPY;TST-NOTIFY_001-HAPPY`
- UI参考：`TOKENS_ONLY/-`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
