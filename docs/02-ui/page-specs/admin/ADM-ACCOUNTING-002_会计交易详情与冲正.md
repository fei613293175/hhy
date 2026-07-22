# ADM-ACCOUNTING-002 · 会计交易详情与冲正

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 财务 | /finance/accounting/transactions/:id | PAGE | ADM-FINANCE | R24 | accounting.read | CRITICAL | READY |

**业务目标：** 交易头、借贷分录、业务凭证、关联订单/红包/奖励/提现、冲正申请与审批进度

**主要角色：** 具备 accounting.read 的后台员工；写操作另需 accounting.reversal.request

**入口：** 从对应列表行、待办、审计记录或直接授权链接进入

**退出/返回：** 返回来源列表并保持筛选、排序、页码和滚动位置

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-FINANCE | 财务与不可变账本 | 指标/告警\|筛选\|流水表\|对账/分录\|受控操作\|审计 | 金额单位、业务幂等键、借贷平衡、审批单、不可变提示 | 禁止直接修改已过账事实；调整通过反向交易；导出受控 | 金额方向和状态文本化 | 关键金额和状态列冻结 | 平衡、重复回调、冲正、审批、导出测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04916 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04917 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-04918 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-04919 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-04920 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-04921 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-04922 | 金额摘要 | amountDisplay | 最终金额 | UI_META | ui | MONEY_TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 以服务端整数分为事实源；界面格式化为元 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 最终金额不符合页面规格 |
| FLD-04923 | 金额摘要 | quoteVersion | 报价版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 下单/支付时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 提交时必须仍有效 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 报价版本不符合页面规格 |
| FLD-04924 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminAccountingGetTransactionById.path | 必填 | 执行“会计交易详情”时显示 | 用户具备权限且页面状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04925 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04926 | 主要内容 | transactionNo | 交易号 | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 交易号加载失败时显示字段级占位或隐藏 |
| FLD-04927 | 主要内容 | businessType | 业务类型 | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务类型加载失败时显示字段级占位或隐藏 |
| FLD-04928 | 主要内容 | businessId | 业务ID | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务ID加载失败时显示字段级占位或隐藏 |
| FLD-04929 | 主要内容 | idempotencyKey | 业务幂等键 | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务幂等键加载失败时显示字段级占位或隐藏 |
| FLD-04930 | 主要内容 | status | 状态 | DISPLAY | string | STATUS_TAG | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:PENDING/POSTED/REVERSED | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04931 | 主要内容 | currency | 币种 | DISPLAY | string | STATUS_TAG | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多3字符；枚举:CNY | NORMAL | 无需特殊掩码；仍遵守最小展示 | 币种加载失败时显示字段级占位或隐藏 |
| FLD-04932 | 主要内容 | debitTotalCent | 借方合计 | DISPLAY | integer | MONEY_TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 借方合计加载失败时显示字段级占位或隐藏 |
| FLD-04933 | 主要内容 | creditTotalCent | 贷方合计 | DISPLAY | integer | MONEY_TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 贷方合计加载失败时显示字段级占位或隐藏 |
| FLD-04934 | 主要内容 | entries | 会计分录 | DISPLAY | array<AccountingEntryResource> | LIST | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 会计分录加载失败时显示字段级占位或隐藏 |
| FLD-04935 | 主要内容 | reversalOfTransactionId | reversal Of Transaction Id | DISPLAY | string | TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | reversal Of Transaction Id加载失败时显示字段级占位或隐藏 |
| FLD-04936 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04937 | 主要内容 | postedAt | posted At | DISPLAY | string | DATETIME_TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | posted At加载失败时显示字段级占位或隐藏 |
| FLD-04938 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminAccountingGetTransactionById.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04939 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminAccountingPostReversal.header | 必填 | 执行“申请会计冲正”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04940 | 表单输入 | reasonCode | 原因代码 | INPUT | string | TEXT_INPUT | adminAccountingPostReversal.request | 必填 | 执行“申请会计冲正”且字段适用时显示 | 具备 accounting.reversal.request 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因代码不符合要求 |
| FLD-04941 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminAccountingPostReversal.request | 必填 | 执行“申请会计冲正”且字段适用时显示 | 具备 accounting.reversal.request 且资源状态允许 | 必填；最多1000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-04942 | 表单输入 | evidenceIds | evidence Ids | INPUT | array<string> | MULTI_SELECT_OR_LIST | adminAccountingPostReversal.request | 可选；按业务条件或页面状态决定 | 执行“申请会计冲正”且字段适用时显示 | 具备 accounting.reversal.request 且资源状态允许 | 最多20项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | evidence Ids不符合要求 |
| FLD-04943 | 表单输入 | approvalId | 审批单 | INPUT | string | TEXT_INPUT | adminAccountingPostReversal.request | 必填 | 执行“申请会计冲正”且字段适用时显示 | 具备 accounting.reversal.request 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审批单不符合要求 |
| FLD-04944 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminAccountingPostReversal.request | 必填 | 执行“申请会计冲正”且字段适用时显示 | 具备 accounting.reversal.request 且资源状态允许 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| RECONCILING | 对账任务执行中 | 展示渠道、账期和进度 | 查询/取消（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 重复触发应幂等 | 否 |
| PENDING_APPROVAL | 敏感变更等待双人复核 | 锁定关键字段，展示审批进度 | 撤回（允许时）/查看审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 申请人不得自审 | 否 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-041 | 会计交易详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /admin-api/v1/accounting/transactions/{id}；请求模型 AdminAccountingGetTransactionByIdParameters；响应模型 AdminAccountingGetTransactionByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-043 | 申请会计冲正 | CONTROLLED_COMMAND | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 accounting.reversal.request，且资源状态允许“申请会计冲正”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,id,reasonCode,reason,evidenceIds,approvalId,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/accounting/transactions/{id}/reversals；请求模型 AccountingReversalRequest；响应模型 AdminAccountingPostReversalResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 更新审批/业务状态、版本和审计时间线；展示“申请会计冲正成功”；从待办队列移除并刷新计数 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从对应列表行、待办、审计记录或直接授权链接进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源列表并保持筛选、排序、页码和滚动位置 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ACCOUNTING-OPS-001;REQ-LEDGER-001`
- API：`GET /admin-api/v1/accounting/transactions/{id};POST /admin-api/v1/accounting/transactions/{id}/reversals`
- operationId：`adminAccountingGetTransactionById;adminAccountingPostReversal`
- 配置组：`payment;withdrawal;payout;system`
- 关键配置：`payment.active_gateway;payment.caihong.base_url;payment.caihong.merchant_id;payment.caihong.merchant_key;payment.caihong.sign_type;payment.caihong.enabled_channels;payment.notify_url;payment.return_url;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month;payout.active_gateway;payout.alipay.app_id;payout.alipay.merchant_id;payout.alipay.private_key_certificate_id;payout.alipay.app_public_certificate_id;payout.alipay.alipay_public_certificate_id;payout.alipay.root_certificate_id;payout.alipay.gateway_url;system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-V122-040;TST-V122-042`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 关键词;状态;创建时间范围 | createdAt:desc | ID;交易号;业务类型;业务ID;业务幂等键;状态;币种;借方合计;贷方合计;会计分录;reversal Of Transaction Id;创建时间;posted At;版本 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 申请会计冲正 | 交易头;借方分录;贷方分录;业务凭证;关联对象;冲正与审批;审计 | 默认禁止直接同步导出；通过数据导出中心创建任务，字段最小化、强脱敏、双人审批、一次性下载令牌、24小时内过期并记录下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 以下操作必须绑定双人审批：申请会计冲正 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
