# ADM-CONFIG-006 · 支付宝企业付款配置

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 配置中心 | /system/providers/payout | PAGE | ADM-FINANCE | R03 | config.manage | HIGH | READY |

**业务目标：** AppID、商家ID、私钥/证书上传、网关、回调、连接测试

**主要角色：** 具备 config.manage 的后台员工；写操作另需 config.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-FINANCE | 财务与不可变账本 | 指标/告警\|筛选\|流水表\|对账/分录\|受控操作\|审计 | 金额单位、业务幂等键、借贷平衡、审批单、不可变提示 | 禁止直接修改已过账事实；调整通过反向交易；导出受控 | 金额方向和状态文本化 | 关键金额和状态列冻结 | 平衡、重复回调、冲正、审批、导出测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04307 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04308 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-04309 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-04310 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-04311 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-04312 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-04313 | 金额摘要 | amountDisplay | 最终金额 | UI_META | ui | MONEY_TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 以服务端整数分为事实源；界面格式化为元 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 最终金额不符合页面规格 |
| FLD-04314 | 金额摘要 | quoteVersion | 报价版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 下单/支付时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 提交时必须仍有效 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 报价版本不符合页面规格 |
| FLD-04315 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04316 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04317 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-04318 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04319 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04320 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminConfigGetConfigs.query | 可选；仅在对应操作/筛选时提交 | 执行“系统配置”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04321 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04322 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04323 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-04324 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04325 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04326 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminConfigGetConfigs.request | 可选；按业务条件或页面状态决定 | 执行“系统配置”且字段适用时显示 | 具备 config.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04327 | 主要内容 | key | key | DISPLAY | string | TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | key加载失败时显示字段级占位或隐藏 |
| FLD-04328 | 主要内容 | environment | 环境 | DISPLAY | string | TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境加载失败时显示字段级占位或隐藏 |
| FLD-04329 | 主要内容 | valueMasked | 脱敏值 | DISPLAY | MaskedConfigValueResource | STRUCTURED_SECTION | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 脱敏值加载失败时显示字段级占位或隐藏 |
| FLD-04330 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04331 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04332 | 主要内容 | effectiveAt | 生效时间 | DISPLAY | string | DATETIME_TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间加载失败时显示字段级占位或隐藏 |
| FLD-04333 | 主要内容 | requiresRestart | 是否需重启 | DISPLAY | boolean | TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 是否需重启加载失败时显示字段级占位或隐藏 |
| FLD-04334 | 主要内容 | updatedBy | 更新人 | DISPLAY | string | TEXT | adminConfigGetConfigs.response.data.ConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新人加载失败时显示字段级占位或隐藏 |
| FLD-04335 | 路由与筛选 | key | key | PATH_PARAM | string | TEXT_INPUT | adminConfigPutConfigsByKey.path | 必填 | 执行“修改配置并生成版本”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | key格式或范围不正确 |
| FLD-04336 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminConfigPutConfigsByKey.header | 必填 | 执行“修改配置并生成版本”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04337 | 表单输入 | value | value | INPUT | string | TEXT_INPUT | adminConfigPutConfigsByKey.request | 必填 | 执行“修改配置并生成版本”且字段适用时显示 | 具备 config.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | value不符合要求 |
| FLD-04338 | 表单输入 | effectiveAt | 生效时间 | INPUT | string | DATETIME_PICKER | adminConfigPutConfigsByKey.request | 可选；按业务条件或页面状态决定 | 执行“修改配置并生成版本”且字段适用时显示 | 具备 config.write 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间不符合要求 |
| FLD-04339 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminConfigPutConfigsByKey.request | 必填 | 执行“修改配置并生成版本”且字段适用时显示 | 具备 config.write 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-04340 | 表单输入 | approvalId | 审批单 | INPUT | string | TEXT_INPUT | adminConfigPutConfigsByKey.request | 可选；按业务条件或页面状态决定 | 执行“修改配置并生成版本”且字段适用时显示 | 具备 config.write 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审批单不符合要求 |
| FLD-04341 | 主要内容 | provider | 供应商 | DISPLAY | string | TEXT | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 供应商加载失败时显示字段级占位或隐藏 |
| FLD-04342 | 主要内容 | activeVersion | 当前版本 | DISPLAY | string | TEXT | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前版本加载失败时显示字段级占位或隐藏 |
| FLD-04343 | 主要内容 | draftVersion | 草稿版本 | DISPLAY | string | TEXT | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 草稿版本加载失败时显示字段级占位或隐藏 |
| FLD-04344 | 主要内容 | configuredSecrets | 秘密配置状态 | DISPLAY | array<object> | LIST | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 秘密配置状态加载失败时显示字段级占位或隐藏 |
| FLD-04345 | 主要内容 | connectionStatus | 连接状态 | DISPLAY | string | TEXT | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 连接状态加载失败时显示字段级占位或隐藏 |
| FLD-04346 | 主要内容 | lastTestAt | 最后测试时间 | DISPLAY | string | DATETIME_TEXT | adminProviderConfigGetProviderConfigs.response.data.ProviderConfigResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后测试时间加载失败时显示字段级占位或隐藏 |
| FLD-04347 | 路由与筛选 | provider | 供应商 | PATH_PARAM | string | TEXT_INPUT | adminProviderConfigGetProviderConfigsByProvider.path | 必填 | 执行“指定供应商配置详情，秘密字段只返回是否已配置”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 供应商格式或范围不正确 |
| FLD-04348 | 表单输入 | provider | 供应商 | INPUT | string | TEXT_INPUT | adminProviderConfigGetProviderConfigsByProvider.request | 必填 | 执行“指定供应商配置详情，秘密字段只返回是否已配置”且字段适用时显示 | 具备 provider.config.read 且资源状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 供应商不符合要求 |
| FLD-04349 | 表单输入 | environment | 环境 | INPUT | string | TEXT_INPUT | adminProviderConfigPostProviderConfigsByProviderVersions.request | 必填 | 执行“创建供应商配置版本”且字段适用时显示 | 具备 provider.config.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境不符合要求 |
| FLD-04350 | 表单输入 | values | values | INPUT | object | STRUCTURED_EDITOR | adminProviderConfigPostProviderConfigsByProviderVersions.request | 必填 | 执行“创建供应商配置版本”且字段适用时显示 | 具备 provider.config.write 且资源状态允许 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | values不符合要求 |
| FLD-04351 | 表单输入 | secretRefs | secret Refs | INPUT | object | STRUCTURED_EDITOR | adminProviderConfigPostProviderConfigsByProviderVersions.request | 可选；按业务条件或页面状态决定 | 执行“创建供应商配置版本”且字段适用时显示 | 具备 provider.config.write 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | secret Refs不符合要求 |
| FLD-04352 | 表单输入 | remark | 备注 | INPUT | string | TEXT_INPUT | adminProviderConfigPostProviderConfigsByProviderVersions.request | 可选；按业务条件或页面状态决定 | 执行“创建供应商配置版本”且字段适用时显示 | 具备 provider.config.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 备注不符合要求 |
| FLD-04353 | 表单输入 | versionId | version Id | INPUT | string | TEXT_INPUT | adminProviderConfigPostProviderConfigsByProviderTest.request | 必填 | 执行“连接或发送测试”且字段适用时显示 | 具备 provider.config.test 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Id不符合要求 |
| FLD-04354 | 表单输入 | testRecipient | test Recipient | INPUT | string | TEXT_INPUT | adminProviderConfigPostProviderConfigsByProviderTest.request | 可选；按业务条件或页面状态决定 | 执行“连接或发送测试”且字段适用时显示 | 具备 provider.config.test 且资源状态允许 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | test Recipient不符合要求 |

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
| QUOTE_READY | 服务端报价有效 | 展示最终金额、费用、权益和过期时间 | 确认下单/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 过期后必须重新报价 | 否 |
| PAYMENT_PENDING | 支付已创建但未确认最终结果 | 轮询/订阅服务端订单状态 | 查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复创建订单 | 否 |
| UNKNOWN | 客户端无法确认服务端最终结果 | 显示处理中并调用查单/查询接口 | 继续查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止直接判定失败并重复扣款 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-CONFIG-006-01 | 系统配置 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/configs；请求模型 AdminConfigGetConfigsParameters；响应模型 AdminConfigGetConfigsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-02 | 修改配置并生成版本 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 config.write，且资源状态允许“修改配置并生成版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 key,X-Idempotency-Key,value,effectiveAt,expectedVersion,approvalId；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /admin-api/v1/configs/{key}；请求模型 AdminConfigPutConfigsByKeyRequest；响应模型 AdminConfigPutConfigsByKeyResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“修改配置并生成版本成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-03 | 功能开关 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/feature-flags；请求模型 AdminConfigGetFeatureFlagsParameters；响应模型 AdminConfigGetFeatureFlagsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-04 | 灰度开关 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 config.write，且资源状态允许“灰度开关”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 key,X-Idempotency-Key,value,effectiveAt,expectedVersion,approvalId；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /admin-api/v1/feature-flags/{key}；请求模型 AdminConfigPutFeatureFlagsByKeyRequest；响应模型 AdminConfigPutFeatureFlagsByKeyResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“灰度开关成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-05 | 供应商配置定义和当前激活版本 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/provider-configs；请求模型 AdminProviderConfigGetProviderConfigsParameters；响应模型 AdminProviderConfigGetProviderConfigsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-06 | 指定供应商配置详情，秘密字段只返回是否已配置 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 provider | 不需要 | GET /admin-api/v1/provider-configs/{provider}；请求模型 AdminProviderConfigGetProviderConfigsByProviderParameters；响应模型 AdminProviderConfigGetProviderConfigsByProviderResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-07 | 创建供应商配置版本 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 provider.config.write，且资源状态允许“创建供应商配置版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 provider,X-Idempotency-Key,environment,values,secretRefs,remark；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/provider-configs/{provider}/versions；请求模型 AdminProviderConfigPostProviderConfigsByProviderVersionsRequest；响应模型 AdminProviderConfigPostProviderConfigsByProviderVersionsResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建供应商配置版本成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-CONFIG-006-08 | 连接或发送测试 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 provider.config.test，且资源状态允许“连接或发送测试”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 provider,X-Idempotency-Key,versionId,testRecipient；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/provider-configs/{provider}/test；请求模型 AdminProviderConfigPostProviderConfigsByProviderTestRequest；响应模型 AdminProviderConfigPostProviderConfigsByProviderTestResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“连接或发送测试成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-CONFIG-001;REQ-SECRET-001`
- API：`GET /admin-api/v1/configs;PUT /admin-api/v1/configs/{key};GET /admin-api/v1/feature-flags;PUT /admin-api/v1/feature-flags/{key};GET /admin-api/v1/provider-configs;GET /admin-api/v1/provider-configs/{provider};POST /admin-api/v1/provider-configs/{provider}/versions;POST /admin-api/v1/provider-configs/{provider}/test`
- operationId：`adminConfigGetConfigs;adminConfigPutConfigsByKey;adminConfigGetFeatureFlags;adminConfigPutFeatureFlagsByKey;adminProviderConfigGetProviderConfigs;adminProviderConfigGetProviderConfigsByProvider;adminProviderConfigPostProviderConfigsByProviderVersions;adminProviderConfigPostProviderConfigsByProviderTest`
- 配置组：`system`
- 关键配置：`system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-CONFIG_001-HAPPY;TST-SECRET_001-HAPPY;TST-CONFIG_001-IDEMPOTENT;TST-SECRET_001-IDEMPOTENT`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | updatedAt:desc | key;环境;脱敏值;版本;状态;生效时间;是否需重启;更新人;供应商;当前版本;草稿版本;秘密配置状态;连接状态;最后测试时间 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 修改配置并生成版本;灰度开关;创建供应商配置版本;连接或发送测试 | 概览;业务数据;状态历史;关联对象;操作审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
