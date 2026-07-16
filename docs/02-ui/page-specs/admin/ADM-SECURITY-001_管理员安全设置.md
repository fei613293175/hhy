# ADM-SECURITY-001 · 管理员安全设置

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 个人安全 | /me/security | PAGE | ADM-SECURITY | R01 | admin.self.read | CRITICAL | READY |

**业务目标：** 修改密码、MFA绑定/解绑、恢复码、活跃会话、登录历史和安全提醒

**主要角色：** 具备 admin.self.read 的后台员工；写操作另需 admin.self.security

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-SECURITY | 后台安全与审计 | 风险摘要\|筛选/配置\|敏感操作\|证据/日志\|审批 | 数据范围、角色、会话升级、脱敏、审计 | 前端权限仅作体验，后端强制校验；高敏读取也审计 | 风险和权限差异文本化 | 表格/详情适配 | 越权、脱敏、会话升级、审计完整性测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-05133 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-05134 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-05135 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-05136 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-05137 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminAdminAuthPostAuthLogout.header | 必填 | 执行“退出”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-05138 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminAdminAuthPostAuthLogout.request | 可选；按业务条件或页面状态决定 | 执行“退出”且字段适用时显示 | 具备 后台登录 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-05139 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminAdminAuthPostAuthLogout.request | 可选；按业务条件或页面状态决定 | 执行“退出”且字段适用时显示 | 具备 后台登录 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-05140 | 表单输入 | payload | 消息内容 | INPUT | object | STRUCTURED_EDITOR | adminAdminAuthPostAuthLogout.request | 可选；按业务条件或页面状态决定 | 执行“退出”且字段适用时显示 | 具备 后台登录 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 消息内容不符合要求 |
| FLD-05141 | 主要内容 | adminId | admin Id | DISPLAY | string | TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | admin Id加载失败时显示字段级占位或隐藏 |
| FLD-05142 | 主要内容 | username | 管理员账号 | DISPLAY | string | TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 管理员账号加载失败时显示字段级占位或隐藏 |
| FLD-05143 | 主要内容 | mfaEnabled | MFA状态 | DISPLAY | boolean | BOOLEAN_STATUS | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | MFA状态加载失败时显示字段级占位或隐藏 |
| FLD-05144 | 主要内容 | mfaMethods | MFA方式 | DISPLAY | array<string> | LIST | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多10项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | MFA方式加载失败时显示字段级占位或隐藏 |
| FLD-05145 | 主要内容 | activeSessionCount | 活跃会话数 | DISPLAY | integer | NUMBER_TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 活跃会话数加载失败时显示字段级占位或隐藏 |
| FLD-05146 | 主要内容 | lastPasswordChangedAt | 上次修改密码 | DISPLAY | string | DATETIME_TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 上次修改密码加载失败时显示字段级占位或隐藏 |
| FLD-05147 | 主要内容 | lastLoginAt | 上次登录时间 | DISPLAY | string | DATETIME_TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 上次登录时间加载失败时显示字段级占位或隐藏 |
| FLD-05148 | 主要内容 | lastLoginIpMasked | 上次登录IP | DISPLAY | string | MASKED_TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | CRITICAL | 默认脱敏；按字段访问权限和目的短时解密 | 上次登录IP加载失败时显示字段级占位或隐藏 |
| FLD-05149 | 主要内容 | recoveryCodesRemaining | 剩余恢复码 | DISPLAY | integer | NUMBER_TEXT | adminSelfGetSecurity.response.data.AdminSelfSecurityResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 剩余恢复码加载失败时显示字段级占位或隐藏 |
| FLD-05150 | 表单输入 | currentPassword | 当前密码 | INPUT | string | PASSWORD_INPUT | adminSelfPostPasswordChange.request | 必填 | 执行“修改管理员密码”且字段适用时显示 | 具备 admin.self.security 且资源状态允许 | 必填；最多72字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 当前密码不符合要求 |
| FLD-05151 | 表单输入 | newPassword | 新密码 | INPUT | string | PASSWORD_INPUT | adminSelfPostPasswordChange.request | 必填 | 执行“修改管理员密码”且字段适用时显示 | 具备 admin.self.security 且资源状态允许 | 必填；最多72字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 新密码不符合要求 |
| FLD-05152 | 表单输入 | mfaCode | MFA验证码 | INPUT | string | OTP_INPUT | adminSelfPostPasswordChange.request | 必填 | 执行“修改管理员密码”且字段适用时显示 | 具备 admin.self.security 且资源状态允许 | 必填；最多10字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | MFA验证码不符合要求 |
| FLD-05153 | 主要内容 | enrollmentId | 绑定流程ID | DISPLAY | string | TEXT | adminSelfPostMfaEnroll.response.data.MfaEnrollmentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 绑定流程ID加载失败时显示字段级占位或隐藏 |
| FLD-05154 | 主要内容 | method | 方式 | DISPLAY | string | STATUS_TAG | adminSelfPostMfaEnroll.response.data.MfaEnrollmentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:TOTP | NORMAL | 无需特殊掩码；仍遵守最小展示 | 方式加载失败时显示字段级占位或隐藏 |
| FLD-05155 | 主要内容 | secretQrCodeUrl | MFA二维码 | DISPLAY | string | LINK | adminSelfPostMfaEnroll.response.data.MfaEnrollmentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2048字符；格式:uri | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | MFA二维码加载失败时显示字段级占位或隐藏 |
| FLD-05156 | 主要内容 | manualKeyMasked | 手工密钥 | DISPLAY | string | MASKED_TEXT | adminSelfPostMfaEnroll.response.data.MfaEnrollmentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | CRITICAL | 默认脱敏；按字段访问权限和目的短时解密 | 手工密钥加载失败时显示字段级占位或隐藏 |
| FLD-05157 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | adminSelfPostMfaEnroll.response.data.MfaEnrollmentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-05158 | 表单输入 | enrollmentId | 绑定流程ID | INPUT | string | TEXT_INPUT | adminSelfPostMfaConfirm.request | 必填 | 执行“确认绑定MFA”且字段适用时显示 | 具备 admin.self.security 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 绑定流程ID不符合要求 |
| FLD-05159 | 表单输入 | code | 代码 | INPUT | string | OTP_INPUT | adminSelfPostMfaConfirm.request | 必填 | 执行“确认绑定MFA”且字段适用时显示 | 具备 admin.self.security 且资源状态允许 | 必填；最多10字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 代码不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| PENDING_APPROVAL | 敏感变更等待双人复核 | 锁定关键字段，展示审批进度 | 撤回（允许时）/查看审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 申请人不得自审 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-015 | 退出 | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key,reason,expectedVersion,payload；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /admin-api/v1/auth/logout；请求模型 AdminAdminAuthPostAuthLogoutRequest；响应模型 AdminAdminAuthPostAuthLogoutResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-057 | 管理员安全概览 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 路由参数、权限和页面状态有效 | 不需要 | GET /admin-api/v1/me/security；请求模型 无请求体；响应模型 AdminSelfGetSecurityResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-058 | 修改管理员密码 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 admin.self.security，且资源状态允许“修改管理员密码”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,currentPassword,newPassword,mfaCode；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/me/security/password/change；请求模型 AdminPasswordChangeRequest；响应模型 AdminSelfPostPasswordChangeResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“修改管理员密码成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-059 | 开始绑定MFA | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /admin-api/v1/me/security/mfa/enroll；请求模型 AdminSelfPostMfaEnrollParameters；响应模型 AdminSelfPostMfaEnrollResponse | X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-060 | 确认绑定MFA | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key,enrollmentId,code；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /admin-api/v1/me/security/mfa/confirm；请求模型 MfaConfirmRequest；响应模型 AdminSelfPostMfaConfirmResponse | X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-061 | 解绑MFA | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key,code,reason；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /admin-api/v1/me/security/mfa/disable；请求模型 MfaDisableRequest；响应模型 AdminSelfPostMfaDisableResponse | X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ADMIN-SECURITY-001;REQ-RBAC-001`
- API：`POST /admin-api/v1/auth/logout;GET /admin-api/v1/me/security;POST /admin-api/v1/me/security/password/change;POST /admin-api/v1/me/security/mfa/enroll;POST /admin-api/v1/me/security/mfa/confirm;POST /admin-api/v1/me/security/mfa/disable`
- operationId：`adminAdminAuthPostAuthLogout;adminSelfGetSecurity;adminSelfPostPasswordChange;adminSelfPostMfaEnroll;adminSelfPostMfaConfirm;adminSelfPostMfaDisable`
- 配置组：`auth;system`
- 关键配置：`auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-V122-014;TST-V122-056;TST-V122-057;TST-V122-058;TST-V122-059;TST-V122-060`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 关键词;状态;创建时间范围 | createdAt:desc | admin Id;管理员账号;MFA状态;MFA方式;活跃会话数;上次修改密码;上次登录时间;上次登录IP;剩余恢复码 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 退出;修改管理员密码;开始绑定MFA;确认绑定MFA;解绑MFA | 概览;业务数据;状态历史;关联对象;操作审计 | 默认禁止直接同步导出；通过数据导出中心创建任务，字段最小化、强脱敏、双人审批、一次性下载令牌、24小时内过期并记录下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
