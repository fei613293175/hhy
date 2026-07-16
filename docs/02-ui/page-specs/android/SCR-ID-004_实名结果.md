# SCR-ID-004 · 实名结果

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 实名 | /me/identity/result | PAGE | MOB-STATUS | R05 | 是 | NORMAL | READY |

**业务目标：** 成功/失败/人工

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-STATUS | 结果与状态页 | 状态图标\|主结论\|原因/下一步\|主操作\|辅助信息 | 稳定状态码、requestId、可恢复动作 | 未知状态必须查询服务端；不可仅依赖回跳参数 | 状态与原因文本化 | 主结论首屏可见 | 各终态、未知态、重试、返回栈测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-02022 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-02023 | 隐私说明 | privacyPurpose | 敏感信息使用目的 | UI_META | ui | NOTICE | LOCAL_UI | 输入/查看敏感信息时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 敏感信息使用目的不符合页面规格 |
| FLD-02024 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | identityGetIdentitySessionsById.path | 必填 | 执行“查询实名状态”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-02025 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | identityGetIdentitySessionsById.request | 必填 | 执行“查询实名状态”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-02026 | 主要内容 | id | ID | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-02027 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-02028 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-02029 | 主要内容 | provider | 供应商 | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 供应商加载失败时显示字段级占位或隐藏 |
| FLD-02030 | 主要内容 | livenessUrl | liveness Url | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | liveness Url加载失败时显示字段级占位或隐藏 |
| FLD-02031 | 主要内容 | failureCode | 失败代码 | DISPLAY | string | TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 失败代码加载失败时显示字段级占位或隐藏 |
| FLD-02032 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-02033 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | identityGetIdentitySessionsById.response.data.IdentitySessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-02034 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | identityPostIdentitySessionsByIdRetry.header | 必填 | 执行“重新认证”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-02035 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | identityPostIdentitySessionsByIdRetry.request | 可选；按业务条件或页面状态决定 | 执行“重新认证”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-02036 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | identityPostIdentitySessionsByIdRetry.request | 可选；按业务条件或页面状态决定 | 执行“重新认证”且字段适用时显示 | 具备 登录 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-02037 | 表单输入 | payload | 消息内容 | INPUT | object | STRUCTURED_EDITOR | identityPostIdentitySessionsByIdRetry.request | 可选；按业务条件或页面状态决定 | 执行“重新认证”且字段适用时显示 | 具备 登录 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息内容不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| PENDING | 结果等待审核、支付或异步处理 | 展示当前阶段、预计动作和刷新入口 | 刷新/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 通过查询更新 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| FAILED | 业务操作明确失败 | 展示业务原因和可执行下一步 | 修改后重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 保留非敏感上下文 | 是 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| UNKNOWN | 客户端无法确认服务端最终结果 | 显示处理中并调用查单/查询接口 | 继续查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止直接判定失败并重复扣款 | 否 |
| RETRYING | 由页面业务状态机进入 | 按“RETRYING”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| COMPLETED | 任务条件满足或奖励已领取 | 展示结果和账本入口 | 查看记录/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 重复进入查询已有结果 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ID-004-01 | 查询实名状态 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /api/v1/identity/sessions/{id}；请求模型 IdentityGetIdentitySessionsByIdParameters；响应模型 IdentityGetIdentitySessionsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ID-004-02 | 重新认证 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录，且资源状态允许“重新认证”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,reason,expectedVersion,payload；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /api/v1/identity/sessions/{id}/retry；请求模型 IdentityPostIdentitySessionsByIdRetryRequest；响应模型 IdentityPostIdentitySessionsByIdRetryResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“重新认证成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ID-001;REQ-ID-002`
- API：`GET /api/v1/identity/sessions/{id};POST /api/v1/identity/sessions/{id}/retry`
- operationId：`identityGetIdentitySessionsById;identityPostIdentitySessionsByIdRetry`
- 配置组：`identity`
- 关键配置：`identity.active_provider;identity.provider.appcode;identity.liveness.token_url;identity.liveness.result_url;identity.face_compare.url;identity.max_daily_attempts;identity.one_id_one_account;identity.liveness.poll_interval_ms`
- 测试：`TST-ID_001-HAPPY;TST-ID_002-HAPPY;TST-ID_001-IDEMPOTENT;TST-ID_002-IDEMPOTENT`
- UI参考：`B12/P01-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
