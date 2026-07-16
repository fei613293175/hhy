# SCR-RP-002 · 红包任务资格

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 红包 | /red-packet/{id}/eligibility | PAGE | MOB-TIMED-TASK | R22 | 是 | NORMAL | READY |

**业务目标：** 实名、重复、库存校验

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整

**退出/返回：** 返回来源页并同步资源最新状态；来源失效时回到所属模块首页

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-TIMED-TASK | 计时/资格任务 | 任务说明\|资格/进度\|主内容\|心跳状态\|完成/失败结果 | 服务端时间、剩余时长、页面可见性、风险提示 | 进度以服务端会话为准；退后台或不可见时暂停/取消；禁止本地计时决定奖励 | 剩余时间节制播报 | 关键状态始终可见 | 心跳中断、加速作弊、退后台、并发领取测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00663 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00664 | 任务进度 | serverElapsedSeconds | 服务端有效时长 | UI_META | ui | PROGRESS | LOCAL_UI | 任务运行时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 仅使用服务端确认值 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 服务端有效时长不符合页面规格 |
| FLD-00665 | 任务进度 | heartbeatState | 心跳状态 | UI_META | ui | STATUS_TAG | LOCAL_UI | 任务运行时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 心跳状态不符合页面规格 |
| FLD-00666 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | redPacketGetRedPacketCampaignsById.path | 必填 | 执行“红包活动详情”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-00667 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaignsById.request | 必填 | 执行“红包活动详情”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-00668 | 主要内容 | id | ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00669 | 主要内容 | contentId | 内容ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容ID加载失败时显示字段级占位或隐藏 |
| FLD-00670 | 主要内容 | ownerUserId | owner User Id | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | owner User Id加载失败时显示字段级占位或隐藏 |
| FLD-00671 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-00672 | 主要内容 | totalCount | 红包总数量 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 红包总数量加载失败时显示字段级占位或隐藏 |
| FLD-00673 | 主要内容 | remainingCount | remaining Count | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | remaining Count加载失败时显示字段级占位或隐藏 |
| FLD-00674 | 主要内容 | amountPerClaimCent | 单个红包金额 | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 单个红包金额加载失败时显示字段级占位或隐藏 |
| FLD-00675 | 主要内容 | principalCent | principal Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | principal Cent加载失败时显示字段级占位或隐藏 |
| FLD-00676 | 主要内容 | serviceFeeCent | service Fee Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | service Fee Cent加载失败时显示字段级占位或隐藏 |
| FLD-00677 | 主要内容 | startAt | 开始时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间加载失败时显示字段级占位或隐藏 |
| FLD-00678 | 主要内容 | endAt | 结束时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间加载失败时显示字段级占位或隐藏 |
| FLD-00679 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00680 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | redPacketPostRedPacketCampaignsByIdViewSessions.header | 必填 | 执行“创建浏览会话并锁定名额”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00681 | 表单输入 | clientNonce | 客户端随机数 | INPUT | string | TEXT_INPUT | redPacketPostRedPacketCampaignsByIdViewSessions.request | 必填 | 执行“创建浏览会话并锁定名额”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 客户端随机数不符合要求 |
| FLD-00682 | 表单输入 | deviceContext | 设备风控上下文 | INPUT | string | TEXT_INPUT | redPacketPostRedPacketCampaignsByIdViewSessions.request | 必填 | 执行“创建浏览会话并锁定名额”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 设备风控上下文不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| CHECKING | 正在执行版本、维护、资格或安全检查 | 显示检查进度和当前步骤，禁止提前导航 | 取消/重试（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 超时后进入可恢复错误 | 否 |
| INELIGIBLE | 不满足任务或红包资格 | 展示具体原因和可改善条件 | 返回/查看规则 | 与当前状态、权限或服务端version冲突的所有写操作 | 不创建浏览会话 | 是 |
| READY | 资格检查通过，可开始任务 | 展示任务规则和开始确认 | 开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 开始后由服务端创建会话 | 否 |
| RUNNING | 任务有效进行中 | 展示服务端进度、心跳和可见性 | 继续/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 本地计时不决定奖励 | 否 |
| PAUSED | 页面不可见或服务端暂停 | 停止有效计时，展示暂停原因 | 恢复/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复需服务端确认 | 否 |
| RESERVED | 奖励名额已短期预留 | 显示剩余预留时间并完成领取条件 | 领取/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 超时进入EXPIRED | 否 |
| COMPLETED | 任务条件满足或奖励已领取 | 展示结果和账本入口 | 查看记录/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 重复进入查询已有结果 | 是 |
| EXPIRED | 资格、报价、验证码或预留过期 | 说明过期原因 | 重新开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 生成新的会话/报价/挑战 | 是 |
| RISK_BLOCKED | 风控拒绝继续任务 | 展示通用原因和申诉入口，不泄露规则细节 | 申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 记录风险事件 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-RP-002-01 | 红包活动详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /api/v1/red-packet-campaigns/{id}；请求模型 RedPacketGetRedPacketCampaignsByIdParameters；响应模型 RedPacketGetRedPacketCampaignsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-RP-002-02 | 创建浏览会话并锁定名额 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+实名，且资源状态允许“创建浏览会话并锁定名额”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,clientNonce,deviceContext；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/red-packet-campaigns/{id}/view-sessions；请求模型 RedPacketPostRedPacketCampaignsByIdViewSessionsRequest；响应模型 RedPacketPostRedPacketCampaignsByIdViewSessionsResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建浏览会话并锁定名额成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RP-003;REQ-RP-006`
- API：`GET /api/v1/red-packet-campaigns/{id};POST /api/v1/red-packet-campaigns/{id}/view-sessions`
- operationId：`redPacketGetRedPacketCampaignsById;redPacketPostRedPacketCampaignsByIdViewSessions`
- 配置组：`red_packet`
- 关键配置：`red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount`
- 测试：`TST-RP_003-HAPPY;TST-RP_006-HAPPY;TST-RP_003-IDEMPOTENT;TST-RP_006-IDEMPOTENT`
- UI参考：`B03/B09/P01/P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
