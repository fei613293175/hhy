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
| FLD-01241 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01242 | 会话头 | connectionState | 连接状态 | UI_META | ui | STATUS_TAG | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 连接状态不符合页面规格 |
| FLD-01243 | 输入区 | messageComposer | 消息输入 | UI_META | ui | MESSAGE_COMPOSER | LOCAL_UI | 未拉黑且会话可发送时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 长度、类型、附件按配置限制 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 消息输入不符合页面规格 |
| FLD-01244 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | chatGetConversationsByIdMessages.path | 必填 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-01245 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-01246 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-01247 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-01248 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-01249 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-01250 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | chatGetConversationsByIdMessages.query | 可选；仅在对应操作/筛选时提交 | 执行“消息分页”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-01251 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | chatGetConversationsByIdMessages.request | 必填 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-01252 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-01253 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-01254 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-01255 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-01256 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-01257 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | chatGetConversationsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“消息分页”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-01258 | 主要内容 | id | ID | DISPLAY | string | TEXT | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01259 | 主要内容 | peer | 对方 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 对方加载失败时显示字段级占位或隐藏 |
| FLD-01260 | 主要内容 | lastMessage | 最后消息 | DISPLAY | object | STRUCTURED_SECTION | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后消息加载失败时显示字段级占位或隐藏 |
| FLD-01261 | 主要内容 | unreadCount | 未读数 | DISPLAY | integer | NUMBER_TEXT | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未读数加载失败时显示字段级占位或隐藏 |
| FLD-01262 | 主要内容 | lastReadMessageId | 最后已读消息 | DISPLAY | string | TEXT | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后已读消息加载失败时显示字段级占位或隐藏 |
| FLD-01263 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-01264 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | chatGetConversationsByIdMessages.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-01265 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | chatPostConversationsByIdMessages.header | 必填 | 执行“发送消息”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-01266 | 表单输入 | clientMessageId | 客户端消息ID | INPUT | string | TEXT_INPUT | chatPostConversationsByIdMessages.request | 必填 | 执行“发送消息”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 客户端消息ID不符合要求 |
| FLD-01267 | 表单输入 | messageType | 消息类型 | INPUT | string | SELECT | chatPostConversationsByIdMessages.request | 必填 | 执行“发送消息”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 必填；最多2000字符；枚举:TEXT/IMAGE/CONTENT_CARD/CONTACT_CARD | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息类型不符合要求 |
| FLD-01268 | 表单输入 | payload | 消息内容 | INPUT | object | STRUCTURED_EDITOR | chatPostConversationsByIdMessages.request | 必填 | 执行“发送消息”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息内容不符合要求 |
| FLD-01269 | 表单输入 | lastReadMessageId | 最后已读消息 | INPUT | string | TEXT_INPUT | chatPostConversationsByIdRead.request | 必填 | 执行“更新已读”且字段适用时显示 | 具备 登录+成员 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后已读消息不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| CONNECTING | 即时连接建立或恢复中 | 显示连接状态；允许浏览已加载消息 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 连接成功进入CONTENT | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| SENDING | 消息发送中 | 消息气泡显示发送中和clientMessageId | 取消/等待 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败显示单条重试 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| BLOCKED | 系统维护、强制更新或账号限制阻断 | 展示阻断原因和唯一允许动作 | 更新/重试/联系客服 | 与当前状态、权限或服务端version冲突的所有写操作 | 阻断优先级高于普通导航 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-CHAT-002-01 | 消息分页 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 id,page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/conversations/{id}/messages；请求模型 ChatGetConversationsByIdMessagesParameters；响应模型 ChatGetConversationsByIdMessagesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-CHAT-002-02 | 发送消息 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+成员，且资源状态允许“发送消息”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,clientMessageId,messageType,payload；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/conversations/{id}/messages；请求模型 ChatPostConversationsByIdMessagesRequest；响应模型 ChatPostConversationsByIdMessagesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“发送消息成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-CHAT-002-03 | 更新已读 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+成员，且资源状态允许“更新已读”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,lastReadMessageId；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/conversations/{id}/read；请求模型 ChatPostConversationsByIdReadRequest；响应模型 ChatPostConversationsByIdReadResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“更新已读成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

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
- UI参考：`B07/P01-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
