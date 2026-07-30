# ADM-ACCOUNTING-001 · 统一会计交易

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 财务 | /finance/accounting/transactions | PAGE | ADM-FINANCE | R24 | accounting.read | CRITICAL | READY |

**业务目标：** 按业务来源、账户、日期和状态查询复式记账交易，核对借贷平衡与业务幂等键

**主要角色：** 具备 accounting.read 的后台员工；写操作另需 accounting.reversal.request

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-FINANCE | 财务与不可变账本 | 指标/告警\|筛选\|流水表\|对账/分录\|受控操作\|审计 | 金额单位、业务幂等键、借贷平衡、审批单、不可变提示 | 禁止直接修改已过账事实；调整通过反向交易；导出受控 | 金额方向和状态文本化 | 关键金额和状态列冻结 | 平衡、重复回调、冲正、审批、导出测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04864 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04865 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-04866 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-04867 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-04868 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-04869 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-04870 | 金额摘要 | amountDisplay | 最终金额 | UI_META | ui | MONEY_TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 以服务端整数分为事实源；界面格式化为元 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 最终金额不符合页面规格 |
| FLD-04871 | 金额摘要 | quoteVersion | 报价版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 下单/支付时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 提交时必须仍有效 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 报价版本不符合页面规格 |
| FLD-04872 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04873 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04874 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04875 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04876 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04877 | 路由与筛选 | startAt | 开始时间 | FILTER | string | DATETIME_PICKER | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间格式或范围不正确 |
| FLD-04878 | 路由与筛选 | endAt | 结束时间 | FILTER | string | DATETIME_PICKER | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间格式或范围不正确 |
| FLD-04879 | 路由与筛选 | businessType | 业务类型 | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务类型格式或范围不正确 |
| FLD-04880 | 路由与筛选 | businessId | 业务ID | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务ID格式或范围不正确 |
| FLD-04881 | 路由与筛选 | accountCode | 账户代码 | FILTER | string | FILTER_INPUT | adminAccountingGetTransactions.query | 可选；仅在对应操作/筛选时提交 | 执行“会计交易列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 账户代码格式或范围不正确 |
| FLD-04882 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04883 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04884 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04885 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04886 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04887 | 表单输入 | startAt | 开始时间 | INPUT | string | DATETIME_PICKER | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间不符合要求 |
| FLD-04888 | 表单输入 | endAt | 结束时间 | INPUT | string | DATETIME_PICKER | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间不符合要求 |
| FLD-04889 | 表单输入 | businessType | 业务类型 | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务类型不符合要求 |
| FLD-04890 | 表单输入 | businessId | 业务ID | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务ID不符合要求 |
| FLD-04891 | 表单输入 | accountCode | 账户代码 | INPUT | string | TEXT_INPUT | adminAccountingGetTransactions.request | 可选；按业务条件或页面状态决定 | 执行“会计交易列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 账户代码不符合要求 |
| FLD-04892 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04893 | 主要内容 | transactionNo | 交易号 | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 交易号加载失败时显示字段级占位或隐藏 |
| FLD-04894 | 主要内容 | businessType | 业务类型 | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务类型加载失败时显示字段级占位或隐藏 |
| FLD-04895 | 主要内容 | businessId | 业务ID | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务ID加载失败时显示字段级占位或隐藏 |
| FLD-04896 | 主要内容 | idempotencyKey | 业务幂等键 | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 业务幂等键加载失败时显示字段级占位或隐藏 |
| FLD-04897 | 主要内容 | status | 状态 | DISPLAY | string | STATUS_TAG | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:PENDING/POSTED/REVERSED | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04898 | 主要内容 | currency | 币种 | DISPLAY | string | STATUS_TAG | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多3字符；枚举:CNY | NORMAL | 无需特殊掩码；仍遵守最小展示 | 币种加载失败时显示字段级占位或隐藏 |
| FLD-04899 | 主要内容 | debitTotalCent | 借方合计 | DISPLAY | integer | MONEY_TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 借方合计加载失败时显示字段级占位或隐藏 |
| FLD-04900 | 主要内容 | creditTotalCent | 贷方合计 | DISPLAY | integer | MONEY_TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 贷方合计加载失败时显示字段级占位或隐藏 |
| FLD-04901 | 主要内容 | entries | 会计分录 | DISPLAY | array<AccountingEntryResource> | LIST | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 会计分录加载失败时显示字段级占位或隐藏 |
| FLD-04902 | 主要内容 | reversalOfTransactionId | reversal Of Transaction Id | DISPLAY | string | TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | reversal Of Transaction Id加载失败时显示字段级占位或隐藏 |
| FLD-04903 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04904 | 主要内容 | postedAt | posted At | DISPLAY | string | DATETIME_TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | posted At加载失败时显示字段级占位或隐藏 |
| FLD-04905 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminAccountingGetTransactions.response.data.AccountingTransactionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04906 | 路由与筛选 | transactionId | 交易ID | FILTER | string | FILTER_INPUT | adminAccountingGetEntries.query | 可选；仅在对应操作/筛选时提交 | 执行“会计分录列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 交易ID格式或范围不正确 |
| FLD-04907 | 路由与筛选 | direction | 借贷方向 | FILTER | string | SELECT | adminAccountingGetEntries.query | 可选；仅在对应操作/筛选时提交 | 执行“会计分录列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符；枚举:DEBIT/CREDIT | NORMAL | 无需特殊掩码；仍遵守最小展示 | 借贷方向格式或范围不正确 |
| FLD-04908 | 表单输入 | transactionId | 交易ID | INPUT | string | TEXT_INPUT | adminAccountingGetEntries.request | 可选；按业务条件或页面状态决定 | 执行“会计分录列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 交易ID不符合要求 |
| FLD-04909 | 表单输入 | direction | 借贷方向 | INPUT | string | SELECT | adminAccountingGetEntries.request | 可选；按业务条件或页面状态决定 | 执行“会计分录列表”且字段适用时显示 | 具备 accounting.read 且资源状态允许 | 最多256字符；枚举:DEBIT/CREDIT | NORMAL | 无需特殊掩码；仍遵守最小展示 | 借贷方向不符合要求 |
| FLD-04910 | 主要内容 | transactionId | 交易ID | DISPLAY | string | TEXT | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 交易ID加载失败时显示字段级占位或隐藏 |
| FLD-04911 | 主要内容 | accountCode | 账户代码 | DISPLAY | string | TEXT | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 账户代码加载失败时显示字段级占位或隐藏 |
| FLD-04912 | 主要内容 | accountName | 账户名称 | DISPLAY | string | TEXT | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多160字符 | CRITICAL | 默认脱敏；财务权限+会话升级后短时查看 | 账户名称加载失败时显示字段级占位或隐藏 |
| FLD-04913 | 主要内容 | direction | 借贷方向 | DISPLAY | string | STATUS_TAG | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多16字符；枚举:DEBIT/CREDIT | NORMAL | 无需特殊掩码；仍遵守最小展示 | 借贷方向加载失败时显示字段级占位或隐藏 |
| FLD-04914 | 主要内容 | amountCent | 金额 | DISPLAY | integer | MONEY_TEXT | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 金额加载失败时显示字段级占位或隐藏 |
| FLD-04915 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | adminAccountingGetEntries.response.data.AccountingEntryResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |

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
| ACT-V122-040 | 会计交易列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,keyword,status,sort,startAt,endAt,businessType,businessId,accountCode | 不需要 | GET /admin-api/v1/accounting/transactions；请求模型 AdminAccountingGetTransactionsParameters；响应模型 AdminAccountingGetTransactionsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-042 | 会计分录列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,keyword,status,sort,startAt,endAt,transactionId,accountCode,direction | 不需要 | GET /admin-api/v1/accounting/entries；请求模型 AdminAccountingGetEntriesParameters；响应模型 AdminAccountingGetEntriesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ACCOUNTING-OPS-001;REQ-LEDGER-001`
- API：`GET /admin-api/v1/accounting/transactions;GET /admin-api/v1/accounting/entries`
- operationId：`adminAccountingGetTransactions;adminAccountingGetEntries`
- 配置组：`payment;withdrawal;payout;system`
- 关键配置：`payment.active_gateway;payment.caihong.base_url;payment.caihong.merchant_id;payment.caihong.merchant_key;payment.caihong.sign_type;payment.caihong.enabled_channels;payment.notify_url;payment.return_url;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month;payout.active_gateway;payout.alipay.app_id;payout.alipay.merchant_id;payout.alipay.private_key_certificate_id;payout.alipay.app_public_certificate_id;payout.alipay.alipay_public_certificate_id;payout.alipay.root_certificate_id;payout.alipay.gateway_url;system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-V122-039;TST-V122-041`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;关键词;状态;排序;开始时间;结束时间;业务类型;业务ID;账户代码;交易ID;借贷方向 | createdAt:desc | ID;交易号;业务类型;业务ID;业务幂等键;状态;币种;借方合计;贷方合计;会计分录;reversal Of Transaction Id;创建时间;posted At;版本 | 无批量写操作 | 查看详情 | 交易头;借方分录;贷方分录;业务凭证;关联对象;冲正与审批;审计 | 默认禁止直接同步导出；通过数据导出中心创建任务，字段最小化、强脱敏、双人审批、一次性下载令牌、24小时内过期并记录下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
