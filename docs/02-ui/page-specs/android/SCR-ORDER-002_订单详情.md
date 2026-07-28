# SCR-ORDER-002 · 订单详情

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 订单 | /me/orders/{id} | PAGE | MOB-DETAIL | R16 | 是 | NORMAL | READY |

**业务目标：** 价格快照和状态

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
| FLD-01514 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01515 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-01516 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-01517 | 路由与筛选 | orderNo | order No | PATH_PARAM | string | TEXT_INPUT | orderGetMeOrdersByOrderno.path | 必填 | 执行“订单详情”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | order No格式或范围不正确 |
| FLD-01518 | 表单输入 | orderNo | order No | INPUT | string | TEXT_INPUT | orderGetMeOrdersByOrderno.request | 必填 | 执行“订单详情”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | order No不符合要求 |
| FLD-01510 | 主要内容 | orderNo | 订单号 | DISPLAY | string | TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 订单号暂时无法显示 |
| FLD-01511 | 主要内容 | orderType | 订单类型 | DISPLAY | string | STATUS_TAG | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 订单类型暂时无法显示 |
| FLD-01512 | 主要内容 | status | 订单状态 | DISPLAY | ORDER_STATUS | STATUS_TAG | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 订单状态暂时无法显示 |
| FLD-01513 | 主要内容 | currency | 币种 | DISPLAY | string | TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 币种暂时无法显示 |
| FLD-01519 | 主要内容 | items | 订单项 | DISPLAY | OrderItemResource[] | STRUCTURED_LIST | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 订单项暂时无法显示 |
| FLD-01520 | 主要内容 | priceSnapshot.originalAmountCent | 原价 | DISPLAY | integer | MONEY_CENT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原价暂时无法显示 |
| FLD-01521 | 主要内容 | priceSnapshot.discountAmountCent | 优惠金额 | DISPLAY | integer | MONEY_CENT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 优惠金额暂时无法显示 |
| FLD-01522 | 主要内容 | priceSnapshot.serviceFeeCent | 服务费 | DISPLAY | integer | MONEY_CENT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 服务费暂时无法显示 |
| FLD-01523 | 主要内容 | priceSnapshot.payableAmountCent | 应付金额 | DISPLAY | integer | MONEY_CENT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 应付金额暂时无法显示 |
| FLD-01524 | 主要内容 | paidAmountCent | 实付金额 | DISPLAY | integer | MONEY_CENT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 实付金额暂时无法显示 |
| FLD-01525 | 主要内容 | noRefundEvidence.confirmed | 不退款确认 | DISPLAY | boolean | STATUS_TAG | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 不退款确认暂时无法显示 |
| FLD-01526 | 主要内容 | noRefundEvidence.agreementVersion | 不退款协议版本 | DISPLAY | string | TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 不退款协议版本暂时无法显示 |
| FLD-01527 | 主要内容 | noRefundEvidence.confirmedAt | 确认时间 | DISPLAY | string | DATETIME_TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 确认时间暂时无法显示 |
| FLD-01528 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间暂时无法显示 |
| FLD-01529 | 主要内容 | paidAt | 支付时间 | DISPLAY | string | DATETIME_TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 支付时间暂时无法显示 |
| FLD-01530 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | orderGetMeOrdersByOrderno.response.data.OrderResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按显式OpenAPI Schema校验 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本暂时无法显示 |

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
| ACT-ORDER-002-01 | 订单详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 orderNo | 不需要 | GET /api/v1/me/orders/{orderNo}；请求模型 OrderGetMeOrdersByOrdernoParameters；响应模型 OrderGetMeOrdersByOrdernoResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-ORDER-001;REQ-NOREFUND-001`
- API：`GET /api/v1/me/orders/{orderNo}`
- operationId：`orderGetMeOrdersByOrderno`
- 配置组：`payment`
- 关键配置：`payment.active_gateway;payment.caihong.base_url;payment.caihong.merchant_id;payment.caihong.merchant_key;payment.caihong.sign_type;payment.caihong.enabled_channels;payment.notify_url;payment.return_url`
- 测试：`TST-ORDER_001-HAPPY;TST-NOREFUND_001-HAPPY`
- UI参考：`SPEC:design/R16-UI-FROZEN/specs/SCR-ORDER-002.md`
- 视觉覆盖状态：`IN_REVIEW`（实现和真实候选截图完成前不得标记 PASS）

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_R16_CR0440`
