# SCR-MYC-004 · 审核记录

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 内容管理 | /me/contents/{id}/reviews | PAGE | MOB-LIST | R12 | 是 | NORMAL | READY |

**业务目标：** 历史和申诉

**主要角色：** 已登录且满足对应发布能力的内容发布者

**入口：** 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整

**退出/返回：** 返回来源页并同步资源最新状态；来源失效时回到所属模块首页

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-LIST | 标准业务列表 | 标题/统计\|筛选/排序\|列表\|分页/加载\|空状态 | 筛选回显、刷新、翻页错误、列表项状态标签 | 翻页去重；状态变化后局部更新；删除后保持滚动位置 | 列表项可整体聚焦且按钮有独立标签 | 卡片密度按屏宽调整 | 分页去重、筛选、状态更新、错误恢复测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01113 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01114 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-01115 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-01116 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-01117 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | contentGetContentsByIdReviews.path | 必填 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-01118 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-01119 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-01120 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-01121 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-01122 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-01123 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-01124 | 路由与筛选 | contentType | 内容类型 | FILTER | string | SELECT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型格式或范围不正确 |
| FLD-01125 | 路由与筛选 | categoryCode | 分类 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类格式或范围不正确 |
| FLD-01126 | 路由与筛选 | regionCode | 地区 | FILTER | string | FILTER_INPUT | contentGetContentsByIdReviews.query | 可选；仅在对应操作/筛选时提交 | 执行“审核记录”时显示 | 用户具备权限且页面状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区格式或范围不正确 |
| FLD-01127 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 必填 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-01128 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-01129 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-01130 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-01131 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-01132 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-01133 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-01134 | 表单输入 | contentType | 内容类型 | INPUT | string | SELECT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型不符合要求 |
| FLD-01135 | 表单输入 | categoryCode | 分类 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类不符合要求 |
| FLD-01136 | 表单输入 | regionCode | 地区 | INPUT | string | TEXT_INPUT | contentGetContentsByIdReviews.request | 可选；按业务条件或页面状态决定 | 执行“审核记录”且字段适用时显示 | 具备 登录+所有者 且资源状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区不符合要求 |
| FLD-01137 | 主要内容 | id | ID | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01138 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-01139 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-01140 | 主要内容 | summary | 摘要 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 摘要加载失败时显示字段级占位或隐藏 |
| FLD-01141 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-01142 | 主要内容 | categoryCode | 分类 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类加载失败时显示字段级占位或隐藏 |
| FLD-01143 | 主要内容 | regionCode | 地区 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区加载失败时显示字段级占位或隐藏 |
| FLD-01144 | 主要内容 | media | 媒体 | DISPLAY | array<MediaItemResource> | MEDIA_GALLERY | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多50项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 媒体加载失败时显示字段级占位或隐藏 |
| FLD-01145 | 主要内容 | publisher | 发布者 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发布者加载失败时显示字段级占位或隐藏 |
| FLD-01146 | 主要内容 | contactsMasked | 脱敏联系方式 | DISPLAY | array<ContactChannelSummaryResource> | LIST | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多20项 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 脱敏联系方式加载失败时显示字段级占位或隐藏 |
| FLD-01147 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-01148 | 主要内容 | reviewStatus | 审核状态 | DISPLAY | string | TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审核状态加载失败时显示字段级占位或隐藏 |
| FLD-01149 | 主要内容 | statistics | 统计 | DISPLAY | ContentStatisticsResource | STRUCTURED_SECTION | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 统计加载失败时显示字段级占位或隐藏 |
| FLD-01150 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-01151 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-01152 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-01153 | 主要内容 | attributes | 扩展属性 | DISPLAY | JsonObject | STRUCTURED_SECTION | contentGetContentsByIdReviews.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 扩展属性加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| APPENDING | 列表加载下一页 | 列表尾部加载器；已加载项保持可用 | 取消翻页 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败提供尾部重试 | 否 |
| PARTIAL_ERROR | 非首屏请求或单模块失败 | 保留现有内容并在对应区域提示 | 局部重试 | 与当前状态、权限或服务端version冲突的所有写操作 | 成功后回到CONTENT | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示业务化错误说明、重试和返回；技术诊断信息仅保留在受控日志 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按业务错误类型恢复 | 否 |
| QUEUE | 审核/工单队列加载完成 | 展示筛选、优先级和分配状态 | 领取/分配/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 并发分配使用版本锁 | 否 |
| ASSIGNED | 任务已分配给当前或其他处理人 | 展示处理人和SLA | 开始处理/转派 | 与当前状态、权限或服务端version冲突的所有写操作 | 无权限者只读 | 否 |
| REVIEWING | 处理人正在查看证据和规则 | 展示证据、历史和决定表单 | 决定/保存备注 | 与当前状态、权限或服务端version冲突的所有写操作 | 原图访问有时效 | 否 |
| DECIDED | 审核决定已提交 | 显示结果、通知和审计时间线 | 下一条/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 决定不可直接修改 | 是 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-MYC-004-01 | 审核记录 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 id,page,pageSize,cursor,status,keyword,sort,contentType,categoryCode,regionCode | 不需要 | GET /api/v1/contents/{id}/reviews；请求模型 ContentGetContentsByIdReviewsParameters；响应模型 ContentGetContentsByIdReviewsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：移动端优先cursor；每页20，最大100；下拉刷新重置cursor；item id去重
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-PUBLISH-001;REQ-CONTENT-002`
- API：`GET /api/v1/contents/{id}/reviews`
- operationId：`contentGetContentsByIdReviews`
- 配置组：`content`
- 关键配置：`content.limit.normal.online;content.limit.month.online;content.limit.quarter.online;content.limit.year.online;content.team_leader_per_account;content.limit.normal.pending;content.limit.normal.drafts;content.limit.normal.daily_submissions`
- 测试：`TST-PUBLISH_001-HAPPY;TST-CONTENT_002-HAPPY`
- UI参考：`SPEC:design/R12-UI-FROZEN/specs/SCR-MYC-004.md`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
