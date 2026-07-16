# SCR-RP-ADV-003 · 预审核结果

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 红包广告 | /me/red-packet-campaigns/{id}/review | PAGE | MOB-STATUS | R20 | 是+实名 | NORMAL | READY |

**业务目标：** 审核状态和原因

**主要角色：** 已登录且满足实名/发布能力的广告主

**入口：** 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整

**退出/返回：** 返回来源页并同步资源最新状态；来源失效时回到所属模块首页

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-STATUS | 结果与状态页 | 状态图标\|主结论\|原因/下一步\|主操作\|辅助信息 | 稳定状态码、requestId、可恢复动作 | 未知状态必须查询服务端；不可仅依赖回跳参数 | 状态与原因文本化 | 主结论首屏可见 | 各终态、未知态、重试、返回栈测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00781 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00782 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | redPacketGetRedPacketCampaignsById.path | 必填 | 执行“红包活动详情”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-00783 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaignsById.request | 必填 | 执行“红包活动详情”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-00784 | 主要内容 | id | ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00785 | 主要内容 | contentId | 内容ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容ID加载失败时显示字段级占位或隐藏 |
| FLD-00786 | 主要内容 | ownerUserId | owner User Id | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | owner User Id加载失败时显示字段级占位或隐藏 |
| FLD-00787 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-00788 | 主要内容 | totalCount | 红包总数量 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 红包总数量加载失败时显示字段级占位或隐藏 |
| FLD-00789 | 主要内容 | remainingCount | remaining Count | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | remaining Count加载失败时显示字段级占位或隐藏 |
| FLD-00790 | 主要内容 | amountPerClaimCent | 单个红包金额 | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 单个红包金额加载失败时显示字段级占位或隐藏 |
| FLD-00791 | 主要内容 | principalCent | principal Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | principal Cent加载失败时显示字段级占位或隐藏 |
| FLD-00792 | 主要内容 | serviceFeeCent | service Fee Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | service Fee Cent加载失败时显示字段级占位或隐藏 |
| FLD-00793 | 主要内容 | startAt | 开始时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间加载失败时显示字段级占位或隐藏 |
| FLD-00794 | 主要内容 | endAt | 结束时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间加载失败时显示字段级占位或隐藏 |
| FLD-00795 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaignsById.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00796 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | redPacketPostRedPacketCampaignsByIdSubmitReview.header | 必填 | 执行“提交预审核”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00797 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | redPacketPostRedPacketCampaignsByIdSubmitReview.request | 必填 | 执行“提交预审核”且字段适用时显示 | 具备 登录+实名+所有者 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-00798 | 表单输入 | remark | 备注 | INPUT | string | TEXT_INPUT | redPacketPostRedPacketCampaignsByIdSubmitReview.request | 可选；按业务条件或页面状态决定 | 执行“提交预审核”且字段适用时显示 | 具备 登录+实名+所有者 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 备注不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| PENDING | 结果等待审核、支付或异步处理 | 展示当前阶段、预计动作和刷新入口 | 刷新/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 通过查询更新 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| FAILED | 业务操作明确失败 | 展示业务原因和可执行下一步 | 修改后重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 保留非敏感上下文 | 是 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| UNKNOWN | 客户端无法确认服务端最终结果 | 显示处理中并调用查单/查询接口 | 继续查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止直接判定失败并重复扣款 | 否 |
| RETRYING | 由页面业务状态机进入 | 按“RETRYING”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| QUEUE | 审核/工单队列加载完成 | 展示筛选、优先级和分配状态 | 领取/分配/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 并发分配使用版本锁 | 否 |
| ASSIGNED | 任务已分配给当前或其他处理人 | 展示处理人和SLA | 开始处理/转派 | 与当前状态、权限或服务端version冲突的所有写操作 | 无权限者只读 | 否 |
| REVIEWING | 处理人正在查看证据和规则 | 展示证据、历史和决定表单 | 决定/保存备注 | 与当前状态、权限或服务端version冲突的所有写操作 | 原图访问有时效 | 否 |
| DECIDED | 审核决定已提交 | 显示结果、通知和审计时间线 | 下一条/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 决定不可直接修改 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-RP-ADV-003-01 | 红包活动详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /api/v1/red-packet-campaigns/{id}；请求模型 RedPacketGetRedPacketCampaignsByIdParameters；响应模型 RedPacketGetRedPacketCampaignsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-RP-ADV-003-02 | 提交预审核 | CONTROLLED_COMMAND | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+实名+所有者，且资源状态允许“提交预审核”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,expectedVersion,remark；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /api/v1/red-packet-campaigns/{id}/submit-review；请求模型 RedPacketPostRedPacketCampaignsByIdSubmitReviewRequest；响应模型 RedPacketPostRedPacketCampaignsByIdSubmitReviewResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“提交预审核成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从列表、通知、分享深链或关联业务入口进入，路由参数必须完整 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源页并同步资源最新状态；来源失效时回到所属模块首页 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RP-001;REQ-RP-004;REQ-RP-005`
- API：`GET /api/v1/red-packet-campaigns/{id};POST /api/v1/red-packet-campaigns/{id}/submit-review`
- operationId：`redPacketGetRedPacketCampaignsById;redPacketPostRedPacketCampaignsByIdSubmitReview`
- 配置组：`red_packet`
- 关键配置：`red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount`
- 测试：`TST-RP_001-HAPPY;TST-RP_004-HAPPY;TST-RP_005-HAPPY;TST-RP_001-IDEMPOTENT;TST-RP_004-IDEMPOTENT;TST-RP_005-IDEMPOTENT`
- UI参考：`B03/B09/P01/P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
