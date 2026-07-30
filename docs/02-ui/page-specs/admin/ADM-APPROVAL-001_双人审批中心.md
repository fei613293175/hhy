# ADM-APPROVAL-001 · 双人审批中心

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 系统 | /system/approvals | PAGE | ADM-APPROVAL | R30 | approval.read | CRITICAL | READY |

**业务目标：** 集中处理资金、配置、发布、签名和敏感操作的双人复核，禁止申请人自审

**主要角色：** 具备 approval.read 的后台员工；写操作另需 approval.decide

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-APPROVAL | 双人审批中心 | 筛选\|申请摘要\|影响差异\|证据\|决定\|时间线 | 申请人、审批人冲突、过期、风险、原请求哈希 | 申请人不得自审；决定绑定不可变请求快照 | 差异和风险文本化 | 详情抽屉转全页 | 自审拒绝、并发决定、过期、审计测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04832 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04833 | 审批证据 | immutableRequestHash | 原请求哈希 | UI_META | ui | HASH_TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 审批决定必须绑定该哈希 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 原请求哈希不符合页面规格 |
| FLD-04834 | 审批证据 | conflictOfInterest | 利益冲突检查 | UI_META | ui | STATUS_TAG | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 申请人不得自审 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 利益冲突检查不符合页面规格 |
| FLD-04835 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04836 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04837 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-04838 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04839 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04840 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminApprovalsGetApprovals.query | 可选；仅在对应操作/筛选时提交 | 执行“复核申请”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04841 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04842 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04843 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-04844 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04845 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04846 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminApprovalsGetApprovals.request | 可选；按业务条件或页面状态决定 | 执行“复核申请”且字段适用时显示 | 具备 approval.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04847 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04848 | 主要内容 | approvalType | approval Type | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | approval Type加载失败时显示字段级占位或隐藏 |
| FLD-04849 | 主要内容 | resourceType | resource Type | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | resource Type加载失败时显示字段级占位或隐藏 |
| FLD-04850 | 主要内容 | resourceId | resource Id | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | resource Id加载失败时显示字段级占位或隐藏 |
| FLD-04851 | 主要内容 | requesterId | requester Id | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | requester Id加载失败时显示字段级占位或隐藏 |
| FLD-04852 | 主要内容 | reviewerId | reviewer Id | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | reviewer Id加载失败时显示字段级占位或隐藏 |
| FLD-04853 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04854 | 主要内容 | reason | 原因 | DISPLAY | string | TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因加载失败时显示字段级占位或隐藏 |
| FLD-04855 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04856 | 主要内容 | decidedAt | decided At | DISPLAY | string | DATETIME_TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | decided At加载失败时显示字段级占位或隐藏 |
| FLD-04857 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminApprovalsGetApprovals.response.data.ApprovalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04858 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminApprovalsPostApprovalsByIdDecide.path | 必填 | 执行“同意/拒绝”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04859 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminApprovalsPostApprovalsByIdDecide.header | 必填 | 执行“同意/拒绝”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04860 | 表单输入 | decision | decision | INPUT | string | SELECT | adminApprovalsPostApprovalsByIdDecide.request | 必填 | 执行“同意/拒绝”且字段适用时显示 | 具备 approval.decide 且资源状态允许 | 必填；最多2000字符；枚举:APPROVE/REJECT/ESCALATE | NORMAL | 无需特殊掩码；仍遵守最小展示 | decision不符合要求 |
| FLD-04861 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminApprovalsPostApprovalsByIdDecide.request | 必填 | 执行“同意/拒绝”且字段适用时显示 | 具备 approval.decide 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-04862 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminApprovalsPostApprovalsByIdDecide.request | 必填 | 执行“同意/拒绝”且字段适用时显示 | 具备 approval.decide 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-04863 | 表单输入 | evidenceIds | evidence Ids | INPUT | array<string> | MULTI_SELECT_OR_LIST | adminApprovalsPostApprovalsByIdDecide.request | 可选；按业务条件或页面状态决定 | 执行“同意/拒绝”且字段适用时显示 | 具备 approval.decide 且资源状态允许 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | evidence Ids不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| PENDING | 结果等待审核、支付或异步处理 | 展示当前阶段、预计动作和刷新入口 | 刷新/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 通过查询更新 | 否 |
| DECIDING | 由页面业务状态机进入 | 按“DECIDING”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| APPROVED | 审批通过 | 显示审批人、时间和绑定请求哈希 | 执行/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 审批只能用于绑定操作 | 是 |
| REJECTED | 审核或审批被拒绝 | 展示结构化原因和可编辑范围 | 修改/申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 原版本保持不可用 | 是 |
| EXPIRED | 资格、报价、验证码或预留过期 | 说明过期原因 | 重新开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 生成新的会话/报价/挑战 | 是 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| EXPIRED_APPROVAL | 审批单过期 | 禁止继续使用 | 重新申请 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得延长原审批 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-029 | 复核申请 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/approvals；请求模型 AdminApprovalsGetApprovalsParameters；响应模型 AdminApprovalsGetApprovalsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-030 | 同意/拒绝 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 approval.decide，且资源状态允许“同意/拒绝”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,decision,reason,expectedVersion,evidenceIds；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/approvals/{id}/decide；请求模型 AdminApprovalsPostApprovalsByIdDecideRequest；响应模型 AdminApprovalsPostApprovalsByIdDecideResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“同意/拒绝成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RBAC-001;REQ-ADMIN-OPS-001`
- API：`GET /admin-api/v1/approvals;POST /admin-api/v1/approvals/{id}/decide`
- operationId：`adminApprovalsGetApprovals;adminApprovalsPostApprovalsByIdDecide`
- 配置组：`system;auth;analytics`
- 关键配置：`system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled;auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;analytics.event.retention_days;analytics.traffic_types`
- 测试：`TST-V122-028;TST-V122-029`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | createdAt:desc | ID;approval Type;resource Type;resource Id;requester Id;reviewer Id;状态;原因;创建时间;decided At;版本 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 同意/拒绝 | 概览;业务数据;状态历史;关联对象;操作审计 | 默认禁止直接同步导出；通过数据导出中心创建任务，字段最小化、强脱敏、双人审批、一次性下载令牌、24小时内过期并记录下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
