# ADM-ID-001 · 实名列表

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 实名 | /identity | PAGE | ADM-LIST | R05 | identity.read | HIGH | READY |

**业务目标：** 状态与风险

**主要角色：** 具备 identity.read 的后台员工；写操作另需 identity.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-LIST | 标准列表运营页 | 页标题/说明\|筛选区\|统计/批量操作\|表格\|分页 | 筛选回显、列设置、默认排序、行操作、导出入口 | 筛选写入URL；批量操作先显示影响预览；翻页不丢筛选 | 表头语义、键盘操作、行操作菜单标签 | 表格横滚，关键列冻结 | 筛选、排序、分页、批量、导出、权限测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-02521 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-02522 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-02523 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-02524 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-02525 | 表格工具栏 | columnPreference | 列显示设置 | UI_META | ui | COLUMN_PICKER | LOCAL_UI | 可选 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 关键列不可隐藏 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 列显示设置不符合页面规格 |
| FLD-02526 | 表格 | batchSelection | 批量选择 | UI_META | ui | ROW_SELECTION | LOCAL_UI | 具备批量权限时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 跨页选择必须显式 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 批量选择不符合页面规格 |
| FLD-02527 | 隐私说明 | privacyPurpose | 敏感信息使用目的 | UI_META | ui | NOTICE | LOCAL_UI | 输入/查看敏感信息时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 敏感信息使用目的不符合页面规格 |
| FLD-02528 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-02529 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-02530 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-02531 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-02532 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-02533 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminIdentityGetIdentities.query | 可选；仅在对应操作/筛选时提交 | 执行“实名列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-02534 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-02535 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-02536 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-02537 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-02538 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-02539 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminIdentityGetIdentities.request | 可选；按业务条件或页面状态决定 | 执行“实名列表”且字段适用时显示 | 具备 identity.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-02540 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-02541 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-02542 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-02543 | 主要内容 | provider | 供应商 | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 供应商加载失败时显示字段级占位或隐藏 |
| FLD-02544 | 主要内容 | livenessUrl | liveness Url | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | liveness Url加载失败时显示字段级占位或隐藏 |
| FLD-02545 | 主要内容 | failureCode | 失败代码 | DISPLAY | string | TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 失败代码加载失败时显示字段级占位或隐藏 |
| FLD-02546 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-02547 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminIdentityGetIdentities.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-02548 | 路由与筛选 | userId | 用户ID | PATH_PARAM | string | TEXT_INPUT | adminIdentityGetIdentitiesByUserid.path | 必填 | 执行“实名详情”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID格式或范围不正确 |
| FLD-02549 | 表单输入 | userId | 用户ID | INPUT | string | TEXT_INPUT | adminIdentityGetIdentitiesByUserid.request | 必填 | 执行“实名详情”且字段适用时显示 | 具备 identity.read 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| PARTIAL_ERROR | 非首屏请求或单模块失败 | 保留现有内容并在对应区域提示 | 局部重试 | 与当前状态、权限或服务端version冲突的所有写操作 | 成功后回到CONTENT | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| PENDING | 结果等待审核、支付或异步处理 | 展示当前阶段、预计动作和刷新入口 | 刷新/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 通过查询更新 | 否 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| COMPLETED | 任务条件满足或奖励已领取 | 展示结果和账本入口 | 查看记录/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 重复进入查询已有结果 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-ID-001-01 | 实名列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/identities；请求模型 AdminIdentityGetIdentitiesParameters；响应模型 AdminIdentityGetIdentitiesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-ID-001-02 | 实名详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 userId | 不需要 | GET /admin-api/v1/identities/{userId}；请求模型 AdminIdentityGetIdentitiesByUseridParameters；响应模型 AdminIdentityGetIdentitiesByUseridResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ID-001;REQ-ID-002`
- API：`GET /admin-api/v1/identities;GET /admin-api/v1/identities/{userId}`
- operationId：`adminIdentityGetIdentities;adminIdentityGetIdentitiesByUserid`
- 配置组：`identity`
- 关键配置：`identity.active_provider;identity.provider.appcode;identity.liveness.token_url;identity.liveness.result_url;identity.face_compare.url;identity.max_daily_attempts;identity.one_id_one_account;identity.liveness.poll_interval_ms`
- 测试：`TST-ID_001-HAPPY;TST-ID_002-HAPPY`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | createdAt:desc | ID;用户ID;状态;供应商;liveness Url;失败代码;过期时间;版本 | 无批量写操作 | 查看详情 | 基本信息;活体/人证结果;敏感媒体;风险;复核记录;访问审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
