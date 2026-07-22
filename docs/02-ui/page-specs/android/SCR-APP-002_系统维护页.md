# SCR-APP-002 · 系统维护页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 启动 | /maintenance | PAGE | MOB-GATE | R02 | 否 | NORMAL | READY |

**业务目标：** 维护说明、预计恢复、重试

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
| FLD-00050 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00051 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-00052 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-00053 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-00054 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-00055 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-00056 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-00057 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-00058 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-00059 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-00060 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-00061 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-00062 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-00063 | 主要内容 | code | 代码 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最少4字符；最多10字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码加载失败时显示字段级占位或隐藏 |
| FLD-00064 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-00065 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-00066 | 主要内容 | content | 正文 | DISPLAY | array<PublicPageBlockResource> | LIST | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文加载失败时显示字段级占位或隐藏 |
| FLD-00067 | 主要内容 | seoMetadata | seo Metadata | DISPLAY | SeoMetadataResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | seo Metadata加载失败时显示字段级占位或隐藏 |
| FLD-00068 | 主要内容 | download | download | DISPLAY | DownloadInfoResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download加载失败时显示字段级占位或隐藏 |
| FLD-00069 | 主要内容 | trackingContext | 追踪上下文 | DISPLAY | TrackingContextResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 追踪上下文加载失败时显示字段级占位或隐藏 |
| FLD-00070 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

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
| ACT-APP-002-01 | 启动维护状态、注册/发布/红包/提现总开关和服务端时间 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /public-api/v1/platform/status；请求模型 PublicGetPlatformStatusParameters；响应模型 PublicGetPlatformStatusResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-AUTH-001;REQ-APP-RELEASE-001;REQ-APK-001`
- API：`GET /public-api/v1/platform/status`
- operationId：`publicGetPlatformStatus`
- 配置组：`platform`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-AUTH_001-HAPPY;TST-APP_RELEASE_001-HAPPY;TST-APK_001-HAPPY`
- UI参考：`B01/P01/P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
