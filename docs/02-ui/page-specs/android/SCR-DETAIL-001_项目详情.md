# SCR-DETAIL-001 · 项目详情

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 内容 | /content/project/{id} | PAGE | MOB-DETAIL | R08 | 是 | NORMAL | READY |

**业务目标：** 项目详情、联系、私聊、红包

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整

**退出/返回：** 返回来源页并同步资源最新状态；来源失效时回到所属模块首页

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-DETAIL | 标准业务详情 | 媒体/标题\|主体信息\|状态与统计\|分区详情\|固定操作区 | 资源状态、发布者/归属、操作按钮条件、时间线 | 服务端 version 驱动按钮；高风险操作二次确认；缓存仅只读 | 标题层级、图片替代文本、固定按钮不遮挡 | 长内容支持锚点和折叠 | 按钮状态矩阵、404/下架、版本冲突测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00451 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00452 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-00453 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-00454 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | contentGetContentsById.path | 必填 | 执行“内容详情”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-00455 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | contentGetContentsById.request | 必填 | 执行“内容详情”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-00456 | 主要内容 | id | ID | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00457 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-00458 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-00459 | 主要内容 | summary | 摘要 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 摘要加载失败时显示字段级占位或隐藏 |
| FLD-00460 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-00461 | 主要内容 | categoryCode | 分类 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类加载失败时显示字段级占位或隐藏 |
| FLD-00462 | 主要内容 | regionCode | 地区 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区加载失败时显示字段级占位或隐藏 |
| FLD-00463 | 主要内容 | media | 媒体 | DISPLAY | array<MediaItemResource> | MEDIA_GALLERY | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多50项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 媒体加载失败时显示字段级占位或隐藏 |
| FLD-00464 | 主要内容 | publisher | 发布者 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发布者加载失败时显示字段级占位或隐藏 |
| FLD-00465 | 主要内容 | contactsMasked | 脱敏联系方式 | DISPLAY | array<ContactChannelSummaryResource> | LIST | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多20项 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 脱敏联系方式加载失败时显示字段级占位或隐藏 |
| FLD-00466 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-00467 | 主要内容 | reviewStatus | 审核状态 | DISPLAY | string | TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审核状态加载失败时显示字段级占位或隐藏 |
| FLD-00468 | 主要内容 | statistics | 统计 | DISPLAY | ContentStatisticsResource | STRUCTURED_SECTION | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 统计加载失败时显示字段级占位或隐藏 |
| FLD-00469 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-00470 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-00471 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00472 | 主要内容 | attributes | 扩展属性 | DISPLAY | JsonObject | STRUCTURED_SECTION | contentGetContentsById.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 扩展属性加载失败时显示字段级占位或隐藏 |
| FLD-00473 | 路由与筛选 | channel | 渠道 | PATH_PARAM | string | TEXT_INPUT | contentPostContentsByIdContactsByChannelAccess.path | 必填 | 执行“获取/复制联系方式”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道格式或范围不正确 |
| FLD-00474 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | contentPostContentsByIdContactsByChannelAccess.header | 必填 | 执行“获取/复制联系方式”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00475 | 表单输入 | clientContext | 客户端上下文 | INPUT | object | STRUCTURED_EDITOR | contentPostContentsByIdContactsByChannelAccess.request | 可选；按业务条件或页面状态决定 | 执行“获取/复制联系方式”且字段适用时显示 | 具备 登录 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 客户端上下文不符合要求 |
| FLD-00476 | 表单输入 | peerUserId | 对方用户 | INPUT | string | TEXT_INPUT | chatPostConversationsDirect.request | 必填 | 执行“创建/获取私聊会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 对方用户不符合要求 |
| FLD-00477 | 表单输入 | sourceContentId | 来源内容 | INPUT | string | TEXT_INPUT | chatPostConversationsDirect.request | 可选；按业务条件或页面状态决定 | 执行“创建/获取私聊会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 来源内容不符合要求 |
| FLD-00478 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | contentPostContentsByIdFavorite.request | 可选；按业务条件或页面状态决定 | 执行“收藏”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-00479 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | contentPostContentsByIdFavorite.request | 可选；按业务条件或页面状态决定 | 执行“收藏”且字段适用时显示 | 具备 登录 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-00480 | 表单输入 | payload | 消息内容 | INPUT | object | STRUCTURED_EDITOR | contentPostContentsByIdFavorite.request | 可选；按业务条件或页面状态决定 | 执行“收藏”且字段适用时显示 | 具备 登录 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息内容不符合要求 |
| FLD-00481 | 表单输入 | channel | 渠道 | INPUT | string | SELECT | contentPostContentsByIdShare.request | 必填 | 执行“记录分享并返回分享地址”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符；枚举:WECHAT/WECHAT_MOMENTS/COPY_LINK/OTHER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| STALE_CACHE | 展示最近缓存但未验证最新版本 | 明确标记可能过期，禁用依赖版本的写操作 | 刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 成功后进入CONTENT | 否 |
| NOT_FOUND | 资源不存在、已删除或不再可访问 | 显示资源状态，移除无效入口 | 返回来源列表 | 与当前状态、权限或服务端version冲突的所有写操作 | 来源列表同步更新 | 是 |
| FORBIDDEN | 已认证但无页面或字段权限 | 展示无权限原因，不泄露资源是否存在 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 权限变化后重新进入 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-DETAIL-001-01 | 内容详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /api/v1/contents/{id}；请求模型 ContentGetContentsByIdParameters；响应模型 ContentGetContentsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-DETAIL-001-02 | 获取/复制联系方式 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“获取/复制联系方式”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,channel,X-Idempotency-Key,clientContext；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/contents/{id}/contacts/{channel}/access；请求模型 ContentPostContentsByIdContactsByChannelAccessRequest；响应模型 ContentPostContentsByIdContactsByChannelAccessResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“获取/复制联系方式成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-DETAIL-001-03 | 创建/获取私聊会话 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“创建/获取私聊会话”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,peerUserId,sourceContentId；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/conversations/direct；请求模型 ChatPostConversationsDirectRequest；响应模型 ChatPostConversationsDirectResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建/获取私聊会话成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-DETAIL-001-04 | 收藏 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“收藏”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,reason,expectedVersion,payload；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /api/v1/contents/{id}/favorite；请求模型 ContentPostContentsByIdFavoriteRequest；响应模型 ContentPostContentsByIdFavoriteResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“收藏成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-DETAIL-001-05 | 记录分享并返回分享地址 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“记录分享并返回分享地址”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,channel；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/contents/{id}/share；请求模型 ContentPostContentsByIdShareRequest；响应模型 ContentPostContentsByIdShareResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“记录分享并返回分享地址成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-CONTENT-001;REQ-CONTACT-001;REQ-PROJECT-001`
- API：`GET /api/v1/contents/{id};POST /api/v1/contents/{id}/contacts/{channel}/access;POST /api/v1/conversations/direct;POST /api/v1/contents/{id}/favorite;POST /api/v1/contents/{id}/share`
- operationId：`contentGetContentsById;contentPostContentsByIdContactsByChannelAccess;chatPostConversationsDirect;contentPostContentsByIdFavorite;contentPostContentsByIdShare`
- 配置组：`content;chat`
- 关键配置：`content.limit.normal.online;content.limit.month.online;content.limit.quarter.online;content.limit.year.online;content.team_leader_per_account;content.limit.normal.pending;content.limit.normal.drafts;content.limit.normal.daily_submissions;chat.stranger.daily_conversation_limit;chat.message.per_minute_limit;chat.image.max_mb;chat.history.retention_days`
- 测试：`TST-CONTENT_001-HAPPY;TST-CONTACT_001-HAPPY;TST-PROJECT_001-HAPPY;TST-CONTENT_001-IDEMPOTENT;TST-CONTACT_001-IDEMPOTENT;TST-PROJECT_001-IDEMPOTENT`
- UI参考：`B03/P01-P07`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
