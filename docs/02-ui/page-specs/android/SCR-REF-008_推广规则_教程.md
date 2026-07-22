# SCR-REF-008 · 推广规则/教程

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 推广 | /me/referral/rules | PAGE | MOB-DETAIL | R25 | 是 | NORMAL | READY |

**业务目标：** 规则和违规公告

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-DETAIL | 标准业务详情 | 媒体/标题\|主体信息\|状态与统计\|分区详情\|固定操作区 | 资源状态、发布者/归属、操作按钮条件、时间线 | 服务端 version 驱动按钮；高风险操作二次确认；缓存仅只读 | 标题层级、图片替代文本、固定按钮不遮挡 | 长内容支持锚点和折叠 | 按钮状态矩阵、404/下架、版本冲突测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01836 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01837 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-01838 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-01839 | 路由与筛选 | code | 代码 | PATH_PARAM | string | OTP_INPUT | publicGetAgreementsByCode.path | 必填 | 执行“协议内容”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码格式或范围不正确 |
| FLD-01840 | 表单输入 | code | 代码 | INPUT | string | OTP_INPUT | publicGetAgreementsByCode.request | 必填 | 执行“协议内容”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码不符合要求 |
| FLD-01841 | 主要内容 | code | 代码 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最少4字符；最多10字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码加载失败时显示字段级占位或隐藏 |
| FLD-01842 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-01843 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-01844 | 主要内容 | content | 正文 | DISPLAY | array<PublicPageBlockResource> | LIST | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文加载失败时显示字段级占位或隐藏 |
| FLD-01845 | 主要内容 | seoMetadata | seo Metadata | DISPLAY | SeoMetadataResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | seo Metadata加载失败时显示字段级占位或隐藏 |
| FLD-01846 | 主要内容 | download | download | DISPLAY | DownloadInfoResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download加载失败时显示字段级占位或隐藏 |
| FLD-01847 | 主要内容 | trackingContext | 追踪上下文 | DISPLAY | TrackingContextResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 追踪上下文加载失败时显示字段级占位或隐藏 |
| FLD-01848 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

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
| ACT-REF-008-01 | 协议内容 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 code；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 不需要 | GET /public-api/v1/agreements/{code}；请求模型 PublicGetAgreementsByCodeParameters；响应模型 PublicGetAgreementsByCodeResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-REF-001;REQ-REF-002;REQ-REF-ACT-001;REQ-REF-ACT-002`
- API：`GET /public-api/v1/agreements/{code}`
- operationId：`publicGetAgreementsByCode`
- 配置组：`platform`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-REF_001-HAPPY;TST-REF_002-HAPPY;TST-REF_ACT_001-HAPPY;TST-REF_ACT_002-HAPPY`
- UI参考：`B10/B08/P06-P07/P01`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
