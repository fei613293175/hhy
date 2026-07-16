# ADM-RISK-003 · 风控规则版本

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 风控 | /governance/risk/rules | PAGE | ADM-CONFIG | R31 | risk.rule.read | CRITICAL | READY |

**业务目标：** 规则草稿、版本差异、测试样本、审批、激活、回滚和命中统计

**主要角色：** 具备 risk.rule.read 的后台员工；写操作另需 risk.rule.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-CONFIG | 配置与版本管理 | 环境/版本选择\|配置表单\|差异\|校验/测试\|审批/激活\|历史 | 单位范围、依赖、影响预览、SecretRef、回滚 | 编辑产生新版本；秘密不回显；激活和回滚需审批 | 字段帮助和错误关联 | 表单与差异并排/堆叠 | 跨字段校验、连接测试、审批、回滚测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04945 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04946 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-04947 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-04948 | 版本与差异 | activeVersionDiff | 当前与草稿差异 | UI_META | ui | DIFF_VIEW | LOCAL_UI | 存在草稿时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前与草稿差异不符合页面规格 |
| FLD-04949 | 版本与差异 | impactPreview | 生效影响预览 | UI_META | ui | IMPACT_PREVIEW | LOCAL_UI | 提交审批前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效影响预览不符合页面规格 |
| FLD-04950 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04951 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04952 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04953 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04954 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04955 | 路由与筛选 | ruleSetCode | 规则集代码 | FILTER | string | FILTER_INPUT | adminRiskRulesGetVersions.query | 可选；仅在对应操作/筛选时提交 | 执行“风控规则版本列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则集代码格式或范围不正确 |
| FLD-04956 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04957 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04958 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04959 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04960 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04961 | 表单输入 | ruleSetCode | 规则集代码 | INPUT | string | TEXT_INPUT | adminRiskRulesGetVersions.request | 可选；按业务条件或页面状态决定 | 执行“风控规则版本列表”且字段适用时显示 | 具备 risk.rule.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则集代码不符合要求 |
| FLD-04962 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04963 | 主要内容 | ruleSetCode | 规则集代码 | DISPLAY | string | TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则集代码加载失败时显示字段级占位或隐藏 |
| FLD-04964 | 主要内容 | versionNo | 版本号 | DISPLAY | integer | NUMBER_TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本号加载失败时显示字段级占位或隐藏 |
| FLD-04965 | 主要内容 | status | 状态 | DISPLAY | string | STATUS_TAG | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:DRAFT/VALIDATED/PENDING_APPROVAL/ACTIVE/INACTIVE/REJECTED | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04966 | 主要内容 | name | 名称 | DISPLAY | string | TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多160字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称加载失败时显示字段级占位或隐藏 |
| FLD-04967 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多1000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-04968 | 主要内容 | ruleDefinition | 规则定义 | DISPLAY | JsonObject | STRUCTURED_SECTION | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则定义加载失败时显示字段级占位或隐藏 |
| FLD-04969 | 主要内容 | testSummary | 测试摘要 | DISPLAY | object | STRUCTURED_SECTION | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 测试摘要加载失败时显示字段级占位或隐藏 |
| FLD-04970 | 主要内容 | effectiveAt | 生效时间 | DISPLAY | string | DATETIME_TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间加载失败时显示字段级占位或隐藏 |
| FLD-04971 | 主要内容 | createdBy | created By | DISPLAY | string | TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | created By加载失败时显示字段级占位或隐藏 |
| FLD-04972 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04973 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminRiskRulesGetVersions.response.data.RiskRuleVersionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04974 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminRiskRulesPostVersion.header | 必填 | 执行“创建风控规则版本”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04975 | 表单输入 | name | 名称 | INPUT | string | TEXT_INPUT | adminRiskRulesPostVersion.request | 必填 | 执行“创建风控规则版本”且字段适用时显示 | 具备 risk.rule.manage 且资源状态允许 | 必填；最多160字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称不符合要求 |
| FLD-04976 | 表单输入 | description | 详细说明 | INPUT | string | RICH_TEXT_OR_TEXTAREA | adminRiskRulesPostVersion.request | 可选；按业务条件或页面状态决定 | 执行“创建风控规则版本”且字段适用时显示 | 具备 risk.rule.manage 且资源状态允许 | 最多1000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明不符合要求 |
| FLD-04977 | 表单输入 | ruleDefinition | 规则定义 | INPUT | JsonObject | STRUCTURED_SECTION | adminRiskRulesPostVersion.request | 必填 | 执行“创建风控规则版本”且字段适用时显示 | 具备 risk.rule.manage 且资源状态允许 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则定义不符合要求 |
| FLD-04978 | 表单输入 | effectiveAt | 生效时间 | INPUT | string | DATETIME_PICKER | adminRiskRulesPostVersion.request | 可选；按业务条件或页面状态决定 | 执行“创建风控规则版本”且字段适用时显示 | 具备 risk.rule.manage 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间不符合要求 |
| FLD-04979 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminRiskRulesPostActivate.path | 必填 | 执行“激活风控规则版本”时显示 | 用户具备权限且页面状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04980 | 表单输入 | approvalId | 审批单 | INPUT | string | TEXT_INPUT | adminRiskRulesPostActivate.request | 必填 | 执行“激活风控规则版本”且字段适用时显示 | 具备 risk.rule.activate 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审批单不符合要求 |
| FLD-04981 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminRiskRulesPostActivate.request | 必填 | 执行“激活风控规则版本”且字段适用时显示 | 具备 risk.rule.activate 且资源状态允许 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| ACTIVE_VERSION | 配置当前生效版本 | 只读展示值、来源、生效时间和审计 | 创建草稿/回滚申请 | 与当前状态、权限或服务端version冲突的所有写操作 | 秘密仅显示配置状态 | 否 |
| DRAFT | 存在未生效配置/规则/模板草稿 | 显示与当前版本差异 | 编辑/校验/提交审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得影响运行时 | 否 |
| VALIDATING | 正在验证一次性state或输入 | 显示处理中，不暴露敏感参数 | 返回（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 参数立即从URL清理 | 否 |
| PENDING_APPROVAL | 敏感变更等待双人复核 | 锁定关键字段，展示审批进度 | 撤回（允许时）/查看审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 申请人不得自审 | 否 |
| ACTIVATING | 配置版本原子激活中 | 显示步骤和回滚点 | 查询状态 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得并行激活 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-044 | 风控规则版本列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,keyword,status,sort,ruleSetCode | 不需要 | GET /admin-api/v1/risk-rule-versions；请求模型 AdminRiskRulesGetVersionsParameters；响应模型 AdminRiskRulesGetVersionsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-045 | 创建风控规则版本 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 risk.rule.manage，且资源状态允许“创建风控规则版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,ruleSetCode,name,description,ruleDefinition,effectiveAt；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/risk-rule-versions；请求模型 RiskRuleCreateRequest；响应模型 AdminRiskRulesPostVersionResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建风控规则版本成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-047 | 激活风控规则版本 | CONTROLLED_COMMAND | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 risk.rule.activate，且资源状态允许“激活风控规则版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,id,approvalId,effectiveAt,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/risk-rule-versions/{id}/activate；请求模型 RiskRuleActivateRequest；响应模型 AdminRiskRulesPostActivateResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 更新审批/业务状态、版本和审计时间线；展示“激活风控规则版本成功”；从待办队列移除并刷新计数 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RISK-RULE-001;REQ-SEC-001`
- API：`GET /admin-api/v1/risk-rule-versions;POST /admin-api/v1/risk-rule-versions;POST /admin-api/v1/risk-rule-versions/{id}/activate`
- operationId：`adminRiskRulesGetVersions;adminRiskRulesPostVersion;adminRiskRulesPostActivate`
- 配置组：`system;auth;red_packet;withdrawal`
- 关键配置：`system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled;auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month`
- 测试：`TST-V122-043;TST-V122-044;TST-V122-046`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;关键词;状态;排序;规则集代码 | updatedAt:desc | ID;规则集代码;版本号;状态;名称;详细说明;规则定义;测试摘要;生效时间;created By;创建时间;版本 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 创建风控规则版本;激活风控规则版本 | 概览;业务数据;状态历史;关联对象;操作审计 | 默认禁止直接同步导出；通过数据导出中心创建任务，字段最小化、强脱敏、双人审批、一次性下载令牌、24小时内过期并记录下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 以下操作必须绑定双人审批：激活风控规则版本 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
