# SCR-MEMBER-001 · 会员中心

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 会员 | /me/membership | PAGE | MOB-DETAIL | R18 | 是 | NORMAL | READY |

**业务目标：** 月季年SKU和权益

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
| FLD-01531 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01532 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-01533 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-01534 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-01535 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-01536 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-01537 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-01538 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-01539 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | membershipGetMembershipSkus.query | 可选；仅在对应操作/筛选时提交 | 执行“会员SKU和权益”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-01540 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-01541 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-01542 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-01543 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-01544 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-01545 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | membershipGetMembershipSkus.request | 可选；按业务条件或页面状态决定 | 执行“会员SKU和权益”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-01546 | 主要内容 | id | ID | DISPLAY | string | TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01547 | 主要内容 | skuId | SKU | DISPLAY | string | TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | SKU加载失败时显示字段级占位或隐藏 |
| FLD-01548 | 主要内容 | name | 名称 | DISPLAY | string | TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称加载失败时显示字段级占位或隐藏 |
| FLD-01549 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-01550 | 主要内容 | startsAt | starts At | DISPLAY | string | DATETIME_TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | starts At加载失败时显示字段级占位或隐藏 |
| FLD-01551 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-01552 | 主要内容 | benefits | 权益 | DISPLAY | array<object> | LIST | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 权益加载失败时显示字段级占位或隐藏 |
| FLD-01553 | 主要内容 | paidValueCent | paid Value Cent | DISPLAY | integer | MONEY_TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | paid Value Cent加载失败时显示字段级占位或隐藏 |
| FLD-01554 | 主要内容 | remainingValueCent | remaining Value Cent | DISPLAY | integer | MONEY_TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | remaining Value Cent加载失败时显示字段级占位或隐藏 |
| FLD-01555 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | membershipGetMembershipSkus.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

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
| ACT-MEMBER-001-01 | 会员SKU和权益 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/membership/skus；请求模型 MembershipGetMembershipSkusParameters；响应模型 MembershipGetMembershipSkusResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-MEMBER-001-02 | 当前会员 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 路由参数、权限和页面状态有效 | 不需要 | GET /api/v1/me/membership；请求模型 MembershipGetMeMembershipParameters；响应模型 MembershipGetMeMembershipResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-MEMBER-001;REQ-MEMBER-002;REQ-MEMBER-003`
- API：`GET /api/v1/membership/skus;GET /api/v1/me/membership`
- operationId：`membershipGetMembershipSkus;membershipGetMeMembership`
- 配置组：`membership`
- 关键配置：`membership.upgrade.time_precision;membership.upgrade.rounding;membership.upgrade.preserve_gift_days;membership.public_badge.text;membership.public_badge.enabled`
- 测试：`TST-MEMBER_001-HAPPY;TST-MEMBER_002-HAPPY;TST-MEMBER_003-HAPPY`
- UI参考：`B11/P01-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
