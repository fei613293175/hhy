# SHEET-RP-001 · 红包领取面板

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 红包 | sheet://red-packet-claim | SHEET | MOB-SHEET | R22 | 是 | NORMAL | READY |

**业务目标：** 领取成功/失败/待审核

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 由拥有该动作的页面显式打开，携带不可变目标快照

**退出/返回：** 成功后向调用页回传结果并关闭；取消恢复触发控件焦点

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-SHEET | 标准底部面板/确认弹层 | 拖拽柄/标题\|说明\|字段/选项\|主次操作 | 影响说明、取消、错误就地反馈 | 不可逆动作不能点击遮罩直接确认；成功后回传结果 | 焦点限制在弹层；关闭后返回触发控件 | 大屏切换居中对话框 | 焦点、返回键、重复提交、取消测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00696 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00697 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | redPacketPostRedPacketViewSessionsByIdClaim.path | 必填 | 执行“领取红包”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-00698 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | redPacketPostRedPacketViewSessionsByIdClaim.header | 必填 | 执行“领取红包”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00699 | 表单输入 | clientNonce | 客户端随机数 | INPUT | string | TEXT_INPUT | redPacketPostRedPacketViewSessionsByIdClaim.request | 必填 | 执行“领取红包”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 客户端随机数不符合要求 |
| FLD-00700 | 表单输入 | finalHeartbeatSequence | 最终心跳序号 | INPUT | integer | NUMBER_INPUT | redPacketPostRedPacketViewSessionsByIdClaim.request | 必填 | 执行“领取红包”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最终心跳序号不符合要求 |
| FLD-00701 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-00702 | 主要内容 | pendingCent | pending Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | pending Cent加载失败时显示字段级占位或隐藏 |
| FLD-00703 | 主要内容 | availableCent | available Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | available Cent加载失败时显示字段级占位或隐藏 |
| FLD-00704 | 主要内容 | frozenCent | frozen Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | frozen Cent加载失败时显示字段级占位或隐藏 |
| FLD-00705 | 主要内容 | withdrawnCent | withdrawn Cent | DISPLAY | integer | MONEY_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | withdrawn Cent加载失败时显示字段级占位或隐藏 |
| FLD-00706 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00707 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | rewardGetMeRewardAccount.response.data.RewardAccountResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| OPEN | 由页面业务状态机进入 | 按“OPEN”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| INELIGIBLE | 不满足任务或红包资格 | 展示具体原因和可改善条件 | 返回/查看规则 | 与当前状态、权限或服务端version冲突的所有写操作 | 不创建浏览会话 | 是 |
| READY | 资格检查通过，可开始任务 | 展示任务规则和开始确认 | 开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 开始后由服务端创建会话 | 否 |
| RUNNING | 任务有效进行中 | 展示服务端进度、心跳和可见性 | 继续/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 本地计时不决定奖励 | 否 |
| RESERVED | 奖励名额已短期预留 | 显示剩余预留时间并完成领取条件 | 领取/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 超时进入EXPIRED | 否 |
| COMPLETED | 任务条件满足或奖励已领取 | 展示结果和账本入口 | 查看记录/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 重复进入查询已有结果 | 是 |
| EXPIRED | 资格、报价、验证码或预留过期 | 说明过期原因 | 重新开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 生成新的会话/报价/挑战 | 是 |
| RISK_BLOCKED | 风控拒绝继续任务 | 展示通用原因和申诉入口，不泄露规则细节 | 申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 记录风险事件 | 是 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-SHEET-RP-001-01 | 领取红包 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+实名，且资源状态允许“领取红包”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,clientNonce,finalHeartbeatSequence；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/red-packet-view-sessions/{id}/claim；请求模型 RedPacketPostRedPacketViewSessionsByIdClaimRequest；响应模型 RedPacketPostRedPacketViewSessionsByIdClaimResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“领取红包成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-SHEET-RP-001-02 | 奖励账户 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 路由参数、权限和页面状态有效 | 不需要 | GET /api/v1/me/reward-account；请求模型 RewardGetMeRewardAccountParameters；响应模型 RewardGetMeRewardAccountResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 由拥有该动作的页面显式打开，携带不可变目标快照 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 成功后向调用页回传结果并关闭；取消恢复触发控件焦点 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 禁止外部深链直接打开；必须由受信任调用页创建上下文 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RP-003;REQ-REWARD-001`
- API：`POST /api/v1/red-packet-view-sessions/{id}/claim;GET /api/v1/me/reward-account`
- operationId：`redPacketPostRedPacketViewSessionsByIdClaim;rewardGetMeRewardAccount`
- 配置组：`red_packet;withdrawal`
- 关键配置：`red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month`
- 测试：`TST-RP_003-HAPPY;TST-REWARD_001-HAPPY;TST-RP_003-IDEMPOTENT;TST-REWARD_001-IDEMPOTENT`
- UI参考：`TOKENS_ONLY/-`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
