# ADM-CHAT-001 · 聊天举报

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 聊天 | /governance/chat-reports | PAGE | ADM-LIST | R15 | chat.report.read | NORMAL | READY |

**业务目标：** 消息证据

**主要角色：** 具备 chat.report.read 的后台员工；写操作另需 chat.report.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-LIST | 标准列表运营页 | 页标题/说明\|筛选区\|统计/批量操作\|表格\|分页 | 筛选回显、列设置、默认排序、行操作、导出入口 | 筛选写入URL；批量操作先显示影响预览；翻页不丢筛选 | 表头语义、键盘操作、行操作菜单标签 | 表格横滚，关键列冻结 | 筛选、排序、分页、批量、导出、权限测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-03685 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-03686 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-03687 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-03688 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-03689 | 表格工具栏 | columnPreference | 列显示设置 | UI_META | ui | COLUMN_PICKER | LOCAL_UI | 可选 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 关键列不可隐藏 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 列显示设置不符合页面规格 |
| FLD-03690 | 表格 | batchSelection | 批量选择 | UI_META | ui | ROW_SELECTION | LOCAL_UI | 具备批量权限时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 跨页选择必须显式 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 批量选择不符合页面规格 |
| FLD-03691 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-03692 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-03693 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-03694 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-03695 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-03696 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminChatGetChatReports.query | 可选；仅在对应操作/筛选时提交 | 执行“聊天举报”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-03697 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-03698 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-03699 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-03700 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-03701 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-03702 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminChatGetChatReports.request | 可选；按业务条件或页面状态决定 | 执行“聊天举报”且字段适用时显示 | 具备 chat.report.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-03703 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-03704 | 主要内容 | peer | 对方 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 对方加载失败时显示字段级占位或隐藏 |
| FLD-03705 | 主要内容 | lastMessage | 最后消息 | DISPLAY | object | STRUCTURED_SECTION | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后消息加载失败时显示字段级占位或隐藏 |
| FLD-03706 | 主要内容 | unreadCount | 未读数 | DISPLAY | integer | NUMBER_TEXT | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未读数加载失败时显示字段级占位或隐藏 |
| FLD-03707 | 主要内容 | lastReadMessageId | 最后已读消息 | DISPLAY | string | TEXT | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后已读消息加载失败时显示字段级占位或隐藏 |
| FLD-03708 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-03709 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminChatGetChatReports.response.data.ConversationResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-03710 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminChatPostChatReportsByIdDecide.path | 必填 | 执行“处理聊天举报”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-03711 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminChatPostChatReportsByIdDecide.header | 必填 | 执行“处理聊天举报”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-03712 | 表单输入 | decision | decision | INPUT | string | SELECT | adminChatPostChatReportsByIdDecide.request | 必填 | 执行“处理聊天举报”且字段适用时显示 | 具备 chat.report.decide 且资源状态允许 | 必填；最多2000字符；枚举:APPROVE/REJECT/ESCALATE | NORMAL | 无需特殊掩码；仍遵守最小展示 | decision不符合要求 |
| FLD-03713 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminChatPostChatReportsByIdDecide.request | 必填 | 执行“处理聊天举报”且字段适用时显示 | 具备 chat.report.decide 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-03714 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminChatPostChatReportsByIdDecide.request | 必填 | 执行“处理聊天举报”且字段适用时显示 | 具备 chat.report.decide 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-03715 | 表单输入 | evidenceIds | evidence Ids | INPUT | array<string> | MULTI_SELECT_OR_LIST | adminChatPostChatReportsByIdDecide.request | 可选；按业务条件或页面状态决定 | 执行“处理聊天举报”且字段适用时显示 | 具备 chat.report.decide 且资源状态允许 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | evidence Ids不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| PARTIAL_ERROR | 非首屏请求或单模块失败 | 保留现有内容并在对应区域提示 | 局部重试 | 与当前状态、权限或服务端version冲突的所有写操作 | 成功后回到CONTENT | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| QUEUE | 审核/工单队列加载完成 | 展示筛选、优先级和分配状态 | 领取/分配/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 并发分配使用版本锁 | 否 |
| ASSIGNED | 任务已分配给当前或其他处理人 | 展示处理人和SLA | 开始处理/转派 | 与当前状态、权限或服务端version冲突的所有写操作 | 无权限者只读 | 否 |
| REVIEWING | 处理人正在查看证据和规则 | 展示证据、历史和决定表单 | 决定/保存备注 | 与当前状态、权限或服务端version冲突的所有写操作 | 原图访问有时效 | 否 |
| DECIDED | 审核决定已提交 | 显示结果、通知和审计时间线 | 下一条/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 决定不可直接修改 | 是 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-CHAT-001-01 | 聊天举报 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/chat-reports；请求模型 AdminChatGetChatReportsParameters；响应模型 AdminChatGetChatReportsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CHAT-001-02 | 处理聊天举报 | CONTROLLED_COMMAND | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 chat.report.decide，且资源状态允许“处理聊天举报”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,decision,reason,expectedVersion,evidenceIds；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/chat-reports/{id}/decide；请求模型 AdminChatPostChatReportsByIdDecideRequest；响应模型 AdminChatPostChatReportsByIdDecideResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 更新审批/业务状态、版本和审计时间线；展示“处理聊天举报成功”；从待办队列移除并刷新计数 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-CHAT-002`
- API：`GET /admin-api/v1/chat-reports;POST /admin-api/v1/chat-reports/{id}/decide`
- operationId：`adminChatGetChatReports;adminChatPostChatReportsByIdDecide`
- 配置组：`chat`
- 关键配置：`chat.stranger.daily_conversation_limit;chat.message.per_minute_limit;chat.image.max_mb;chat.history.retention_days`
- 测试：`TST-CHAT_002-HAPPY;TST-CHAT_002-IDEMPOTENT`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | priority:desc,createdAt:asc | ID;对方;最后消息;未读数;最后已读消息;更新时间;版本 | 批量分配（仅未领取项）;批量标记优先级（不允许批量作出最终决定） | 处理聊天举报 | 概览;业务数据;状态历史;关联对象;操作审计 | 具备页面读取和 export.create 权限可异步导出当前筛选；禁止导出未展示或无权限字段 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
