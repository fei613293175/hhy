# SCR-ME-001 · 我的首页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 我的 | /me | PAGE | MOB-HOME | R12 | 是 | NORMAL | READY |

**业务目标：** 账号、会员、收益、功能入口

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 登录后由底部导航、业务通知或深链进入

**退出/返回：** 切换底部导航；返回键按应用根导航策略处理

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-HOME | 聚合首页 | 顶栏\|状态/资产摘要\|模块化内容区\|底部导航 | 骨架、空模块降级、模块追踪上下文 | 模块独立失败不得拖垮整页；顺序由服务端模块合同控制 | 模块标题语义化；轮播可暂停 | 卡片宽度按Token缩放 | 模块容错、埋点上下文、CMS兼容测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01371 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01372 | 主要内容 | id | ID | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01373 | 主要内容 | phoneMasked | phone Masked | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | phone Masked加载失败时显示字段级占位或隐藏 |
| FLD-01374 | 主要内容 | nickname | 昵称 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多255字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 昵称加载失败时显示字段级占位或隐藏 |
| FLD-01375 | 主要内容 | avatarUrl | 头像 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 头像加载失败时显示字段级占位或隐藏 |
| FLD-01376 | 主要内容 | bio | 个人简介 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多255字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 个人简介加载失败时显示字段级占位或隐藏 |
| FLD-01377 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-01378 | 主要内容 | identityStatus | identity Status | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | identity Status加载失败时显示字段级占位或隐藏 |
| FLD-01379 | 主要内容 | membershipStatus | membership Status | DISPLAY | string | TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | membership Status加载失败时显示字段级占位或隐藏 |
| FLD-01380 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-01381 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | userGetMe.response.data.UserResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-01382 | 主要内容 | skuId | SKU | DISPLAY | string | TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | SKU加载失败时显示字段级占位或隐藏 |
| FLD-01383 | 主要内容 | name | 名称 | DISPLAY | string | TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称加载失败时显示字段级占位或隐藏 |
| FLD-01384 | 主要内容 | startsAt | starts At | DISPLAY | string | DATETIME_TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | starts At加载失败时显示字段级占位或隐藏 |
| FLD-01385 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-01386 | 主要内容 | benefits | 权益 | DISPLAY | array<object> | LIST | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 权益加载失败时显示字段级占位或隐藏 |
| FLD-01387 | 主要内容 | paidValueCent | paid Value Cent | DISPLAY | integer | MONEY_TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | paid Value Cent加载失败时显示字段级占位或隐藏 |
| FLD-01388 | 主要内容 | remainingValueCent | remaining Value Cent | DISPLAY | integer | MONEY_TEXT | membershipGetMeMembership.response.data.MembershipResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | remaining Value Cent加载失败时显示字段级占位或隐藏 |
| FLD-01389 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-01390 | 主要内容 | pendingCent | pending Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | pending Cent加载失败时显示字段级占位或隐藏 |
| FLD-01391 | 主要内容 | availableCent | available Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | available Cent加载失败时显示字段级占位或隐藏 |
| FLD-01392 | 主要内容 | frozenCent | frozen Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | frozen Cent加载失败时显示字段级占位或隐藏 |
| FLD-01393 | 主要内容 | withdrawnCent | withdrawn Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | withdrawn Cent加载失败时显示字段级占位或隐藏 |
| FLD-01394 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| PARTIAL_CONTENT | 部分模块成功、部分模块失败 | 保留成功模块，失败模块显示局部重试 | 重试失败模块 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得将局部失败升级为全页失败 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| ERROR | 三项请求均失败且无法展示可靠内容 | 展示商业化失败说明和重试操作；技术诊断仅保留在受控日志 | 重试 | 与当前状态、权限或服务端version冲突的所有写操作 | 按模块恢复；不得展示错误码、requestId或traceId | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ME-001-01 | 当前用户资料 | DETAIL_QUERY | 蓝色身份头与个人资料入口 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且会话有效 | 不存在同模块进行中的请求 | 会话有效 | 不需要 | GET /api/v1/me；请求模型 UserGetMeParameters；响应模型 UserGetMeResponse | 同模块单请求锁；401只触发一次根会话失效 | 首次进入使用身份头骨架；刷新保留当前内容 | 以服务端 version 和状态覆盖资料模块并回接根用户事实 | 404仅标记资料模块不可用；403局部受限；409只重拉资料；网络/5xx保留缓存并标记可能过期 | GET 可人工重试；缓存仅作明确标记的只读降级 | 留在当前页；只有三模块均无可靠内容时进入全页恢复态 | 记录必要业务埋点和高敏读取审计；技术诊断只进受控日志 | 不展示requestId、traceId、错误码或接口名 |
| ACT-ME-001-02 | 当前会员 | DETAIL_QUERY | 会员状态与真实权益模块 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且会话有效 | 不存在同模块进行中的请求 | 会话有效 | 不需要 | GET /api/v1/me/membership；请求模型 MembershipGetMeMembershipParameters；响应模型 MembershipGetMeMembershipResponse | 同模块单请求锁；401只触发一次根会话失效 | 首次进入使用会员骨架；刷新保留当前内容 | 以服务端 version 和状态覆盖会员模块；更新时间优先响应timestamp | 404局部显示暂未开通会员；403局部受限；409只重拉会员；网络/5xx保留缓存并标记可能过期 | GET 可人工重试；缓存仅作明确标记的只读降级 | 留在当前页；404不得返回上一级 | 记录必要业务埋点；技术诊断只进受控日志 | 复杂权益值不直接展示为原始JSON；不展示技术字段 |
| ACT-ME-001-03 | 奖励账户 | DETAIL_QUERY | 奖励资产双主双次摘要 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且会话有效 | 不存在同模块进行中的请求 | 会话有效；余额仅按整数分格式化 | 不需要 | GET /api/v1/me/reward-account；请求模型 RewardGetMeRewardAccountParameters；响应模型 RewardGetMeRewardAccountResponse | 同模块单请求锁；401只触发一次根会话失效 | 首次进入使用资产骨架；刷新保留当前内容 | 以整数分响应覆盖奖励模块；不得用会员价值冒充余额 | 404局部显示暂无奖励账户；422显示实名引导；423显示风险审核受限；409只重拉奖励；网络/5xx保留缓存 | GET 可人工重试；429按Retry-After局部提示 | 留在当前页；404/422/423不得返回上一级 | 记录必要业务埋点和高敏读取审计；技术诊断只进受控日志 | 金额只显示业务含义；不展示requestId、traceId或错误码 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 登录后由typed底部Me目的地、业务通知或已登记深链进入 | 按真实功能入口进入子页；发布中心由底部中央入口进入 | 顶栏、系统键和手势操作同一NavController；子页返回实际Me来源 | 保存原目标并进入登录；公开H5除外 | 对应模块局部受限且不泄露资源存在性 | 会员/奖励404保留当前页并显示业务空态 | 仅允许已登记App Link/内部路由；登录前保存原目标 | Home为根目的地；Home/Me切换使用popUpTo Home saveState、launchSingleTop、restoreState与HhyMotion.peerContent；禁止selectedIndex伪栈 |

- 刷新策略：模块独立加载和刷新；局部失败不清空成功模块；展示数据更新时间
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-PUBLISHER-001`
- API：`GET /api/v1/me;GET /api/v1/me/membership;GET /api/v1/me/reward-account`
- operationId：`userGetMe;membershipGetMeMembership;rewardGetMeRewardAccount`
- 配置组：`auth;membership;withdrawal`
- 关键配置：`auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;membership.upgrade.time_precision;membership.upgrade.rounding;membership.upgrade.preserve_gift_days;membership.public_badge.text;membership.public_badge.enabled;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month`
- 测试：`TST-PUBLISHER_001-HAPPY`
- UI参考：`B08/P01`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
