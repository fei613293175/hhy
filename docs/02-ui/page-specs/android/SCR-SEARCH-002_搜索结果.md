# SCR-SEARCH-002 · 搜索结果

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 搜索 | /search/result | PAGE | MOB-SEARCH | R07 | 是 | NORMAL | READY |

**业务目标：** 筛选、排序、分页

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-SEARCH | 搜索与筛选 | 搜索框\|历史/热词\|筛选条\|结果列表\|空状态 | 清空确认、联想词、结果计数、筛选回显 | 输入防抖；新查询取消旧请求；清空历史不可撤销需确认 | 结果数量播报；筛选标签可聚焦 | 筛选支持横向滚动或抽屉 | 防抖、取消、空状态、清空历史测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00275 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00276 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-00277 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-00278 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-00279 | 路由与筛选 | q | q | FILTER | string | FILTER_INPUT | searchGetSearch.query | 必填 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | q格式或范围不正确 |
| FLD-00280 | 路由与筛选 | contentType | 内容类型 | FILTER | string | SELECT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型格式或范围不正确 |
| FLD-00281 | 路由与筛选 | categoryCode | 分类 | FILTER | string | FILTER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类格式或范围不正确 |
| FLD-00282 | 路由与筛选 | regionCode | 地区 | FILTER | string | FILTER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区格式或范围不正确 |
| FLD-00283 | 表单输入 | q | q | INPUT | string | TEXT_INPUT | searchGetSearch.request | 必填 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最少1字符；最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | q不符合要求 |
| FLD-00284 | 表单输入 | contentType | 内容类型 | INPUT | string | SELECT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型不符合要求 |
| FLD-00285 | 表单输入 | categoryCode | 分类 | INPUT | string | TEXT_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类不符合要求 |
| FLD-00286 | 表单输入 | regionCode | 地区 | INPUT | string | TEXT_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区不符合要求 |
| FLD-00287 | 主要内容 | id | ID | DISPLAY | string | TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00288 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-00289 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-00290 | 主要内容 | summary | 摘要 | DISPLAY | string | TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 摘要加载失败时显示字段级占位或隐藏 |
| FLD-00291 | 主要内容 | coverUrl | cover Url | DISPLAY | string | TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | cover Url加载失败时显示字段级占位或隐藏 |
| FLD-00292 | 主要内容 | publisher | 发布者 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发布者加载失败时显示字段级占位或隐藏 |
| FLD-00293 | 主要内容 | score | 匹配分 | DISPLAY | number | NUMBER_TEXT | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 匹配分加载失败时显示字段级占位或隐藏 |
| FLD-00294 | 主要内容 | badges | badges | DISPLAY | array<string> | LIST | searchGetSearch.response.data.SearchResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | badges加载失败时显示字段级占位或隐藏 |
| FLD-05168 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-05169 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-05170 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-05171 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | searchGetSearch.query | 可选；仅在对应操作/筛选时提交 | 执行“全局搜索”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-05172 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-05173 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-05174 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-05175 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | searchGetSearch.request | 可选；按业务条件或页面状态决定 | 执行“全局搜索”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| IDLE | 尚未输入或执行搜索 | 展示历史、热词和推荐 | 输入/选择 | 与当前状态、权限或服务端version冲突的所有写操作 | 输入后进入SUGGESTING | 否 |
| SUGGESTING | 搜索联想请求中或已返回 | 展示联想词并允许键盘选择 | 选择/提交 | 与当前状态、权限或服务端version冲突的所有写操作 | 新输入取消旧请求 | 否 |
| SEARCHING | 搜索请求进行中 | 保留查询词，展示结果骨架 | 取消/修改 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败不清空查询条件 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-SEARCH-002-01 | 全局搜索 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 q,contentType,categoryCode,regionCode | 不需要 | GET /api/v1/search；请求模型 SearchGetSearchParameters；响应模型 SearchGetSearchResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：移动端优先cursor；每页20，最大100；下拉刷新重置cursor；item id去重
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-SEARCH-001`
- API：`GET /api/v1/search`
- operationId：`searchGetSearch`
- 配置组：`search`
- 关键配置：`search.default_page_size;search.hot_keywords.max_count`
- 测试：`TST-SEARCH_001-HAPPY`
- UI参考：`B02/P01-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
