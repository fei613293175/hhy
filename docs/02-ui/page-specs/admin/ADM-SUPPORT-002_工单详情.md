# ADM-SUPPORT-002 · 工单详情

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 客服 | /support/tickets/:id | PAGE | ADM-DETAIL | R15 | support.read | HIGH | READY |

**业务目标：** 会话式工单详情、附件、分配、内部备注、回复、关闭与重开记录

**主要角色：** 具备 support.read 的后台员工；写操作另需 support.manage

**入口：** 从对应列表行、待办、审计记录或直接授权链接进入

**退出/返回：** 返回来源列表并保持筛选、排序、页码和滚动位置

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-DETAIL | 标准详情与时间线 | 摘要头\|状态/主操作\|Tab详情\|时间线\|关联对象\|审计 | 版本、敏感字段访问、按钮条件、状态历史 | 所有写操作使用最新version；高敏原文临时授权并审计 | Tab和时间线语义化 | 双栏转单栏 | 状态按钮矩阵、敏感访问、冲突、审计测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04821 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04822 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-04823 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-04824 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminSupportPostSupportTicketsByIdAssign.path | 必填 | 执行“分配客服”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04825 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminSupportPostSupportTicketsByIdAssign.header | 必填 | 执行“分配客服”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04826 | 表单输入 | assigneeId | assignee Id | INPUT | string | TEXT_INPUT | adminSupportPostSupportTicketsByIdAssign.request | 必填 | 执行“分配客服”且字段适用时显示 | 具备 support.assign 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | assignee Id不符合要求 |
| FLD-04827 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminSupportPostSupportTicketsByIdAssign.request | 可选；按业务条件或页面状态决定 | 执行“分配客服”且字段适用时显示 | 具备 support.assign 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-04828 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminSupportPostSupportTicketsByIdAssign.request | 必填 | 执行“分配客服”且字段适用时显示 | 具备 support.assign 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-04829 | 表单输入 | content | 正文 | INPUT | string | RICH_TEXT_OR_TEXTAREA | adminSupportPostSupportTicketsByIdMessages.request | 必填 | 执行“客服回复”且字段适用时显示 | 具备 support.reply 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文不符合要求 |
| FLD-04830 | 表单输入 | attachments | 附件 | INPUT | array<string> | MEDIA_UPLOADER | adminSupportPostSupportTicketsByIdMessages.request | 可选；按业务条件或页面状态决定 | 执行“客服回复”且字段适用时显示 | 具备 support.reply 且资源状态允许 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 附件不符合要求 |
| FLD-04831 | 表单输入 | payload | 消息内容 | INPUT | object | STRUCTURED_EDITOR | adminSupportPostSupportTicketsByIdClose.request | 可选；按业务条件或页面状态决定 | 执行“关闭工单”且字段适用时显示 | 具备 support.close 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息内容不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| STALE | 后台详情版本已过期 | 保留只读快照并提示刷新 | 重新加载 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得覆盖服务端新版本 | 否 |
| NOT_FOUND | 资源不存在、已删除或不再可访问 | 显示资源状态，移除无效入口 | 返回来源列表 | 与当前状态、权限或服务端version冲突的所有写操作 | 来源列表同步更新 | 是 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-024 | 分配客服 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 support.assign，且资源状态允许“分配客服”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,assigneeId,reason,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/support/tickets/{id}/assign；请求模型 AdminSupportPostSupportTicketsByIdAssignRequest；响应模型 AdminSupportPostSupportTicketsByIdAssignResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“分配客服成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-025 | 客服回复 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 support.reply，且资源状态允许“客服回复”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,content,attachments；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/support/tickets/{id}/messages；请求模型 AdminSupportPostSupportTicketsByIdMessagesRequest；响应模型 AdminSupportPostSupportTicketsByIdMessagesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“客服回复成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-026 | 关闭工单 | CONTROLLED_COMMAND | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 support.close，且资源状态允许“关闭工单”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,reason,expectedVersion,payload；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/support/tickets/{id}/close；请求模型 AdminSupportPostSupportTicketsByIdCloseRequest；响应模型 AdminSupportPostSupportTicketsByIdCloseResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 按状态机更新资源并移除或禁用不再可用的操作；展示“关闭工单成功”；保留操作记录入口 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从对应列表行、待办、审计记录或直接授权链接进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源列表并保持筛选、排序、页码和滚动位置 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-SUPPORT-001;REQ-ADMIN-OPS-001`
- API：`POST /admin-api/v1/support/tickets/{id}/assign;POST /admin-api/v1/support/tickets/{id}/messages;POST /admin-api/v1/support/tickets/{id}/close`
- operationId：`adminSupportPostSupportTicketsByIdAssign;adminSupportPostSupportTicketsByIdMessages;adminSupportPostSupportTicketsByIdClose`
- 配置组：`operations;notification;storage`
- 关键配置：`ops.backup.rpo_minutes;ops.recovery.rto_minutes;ops.audit_log.retention_days;ops.security_event.retention_days;ops.alert.error_rate_percent;ops.alert.api_p95_ms;notification.marketing.default_enabled;notification.quiet_hours.start;notification.quiet_hours.end;storage.default_provider;storage.scope.public_media.provider;storage.scope.private_kyc.provider;storage.scope.private_chat.provider;storage.scope.audit_evidence.provider;storage.scope.apk_release.provider;storage.scope.backup.provider;storage.r2.account_id`
- 测试：`TST-V122-023;TST-V122-024;TST-V122-025`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 不适用 | priority:desc,createdAt:asc | 名称/标识;状态;更新时间;版本;操作 | 批量分配（仅未领取项）;批量标记优先级（不允许批量作出最终决定） | 分配客服;客服回复;关闭工单 | 用户会话;附件;内部备注;分配记录;状态历史;关联业务 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
