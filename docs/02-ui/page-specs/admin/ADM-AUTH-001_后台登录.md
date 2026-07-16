# ADM-AUTH-001 · 后台登录

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 后台认证 | /auth/login | PAGE | ADM-AUTH | R01 | public | HIGH | READY |

**业务目标：** 账号密码、验证码、登录风险提示和登录失败锁定策略

**主要角色：** 具备 public 的后台员工；写操作另需 admin.auth.login

**入口：** 未认证访问后台或会话过期时由路由守卫进入

**退出/返回：** 认证成功进入原目标或数据驾驶舱；失败留在当前页；取消返回登录

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-AUTH | 后台认证 | 品牌\|安全说明\|认证表单\|风险/锁定提示 | 验证码/MFA、失败次数、合规提示 | 管理会话与用户会话隔离；高风险登录触发MFA | 完整键盘操作和错误摘要 | 桌面优先，小屏可用 | 锁定、MFA、会话隔离、审计测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04777 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04778 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-04779 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-04780 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminAdminAuthPostAuthLogin.header | 必填 | 执行“后台登录”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04781 | 表单输入 | username | 管理员账号 | INPUT | string | TEXT_INPUT | adminAdminAuthPostAuthLogin.request | 必填 | 执行“后台登录”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 管理员账号不符合要求 |
| FLD-04782 | 表单输入 | password | 密码 | INPUT | string | PASSWORD_INPUT | adminAdminAuthPostAuthLogin.request | 必填 | 执行“后台登录”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最少8字符；最多72字符；格式:password | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 密码不符合要求 |
| FLD-04783 | 表单输入 | captchaToken | captcha Token | INPUT | string | TEXT_INPUT | adminAdminAuthPostAuthLogin.request | 可选；按业务条件或页面状态决定 | 执行“后台登录”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | captcha Token不符合要求 |
| FLD-04784 | 主要内容 | accessToken | access Token | DISPLAY | string | TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | access Token加载失败时显示字段级占位或隐藏 |
| FLD-04785 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-04786 | 主要内容 | adminUserId | admin User Id | DISPLAY | string | TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | admin User Id加载失败时显示字段级占位或隐藏 |
| FLD-04787 | 主要内容 | displayName | display Name | DISPLAY | string | TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | display Name加载失败时显示字段级占位或隐藏 |
| FLD-04788 | 主要内容 | permissionCodes | permission Codes | DISPLAY | array<string> | LIST | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | permission Codes加载失败时显示字段级占位或隐藏 |
| FLD-04789 | 主要内容 | mfaRequired | mfa Required | DISPLAY | string | TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | mfa Required加载失败时显示字段级占位或隐藏 |
| FLD-04790 | 主要内容 | mfaTicket | mfa Ticket | DISPLAY | string | TEXT | adminAdminAuthPostAuthLogin.response.data.AdminSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | mfa Ticket加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| MFA_REQUIRED | 由页面业务状态机进入 | 按“MFA_REQUIRED”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| LOCKED | 账号、验证码或安全流程被锁定 | 展示锁定原因、期限和申诉/客服入口 | 返回/申诉 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止继续提交 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| RATE_LIMITED | 触发频率限制 | 显示剩余等待时间和限制原因 | 等待/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 倒计时结束后重新获取前置条件 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-013 | 后台登录 | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key,username,password,captchaToken；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /admin-api/v1/auth/login；请求模型 AdminAdminAuthPostAuthLoginRequest；响应模型 AdminAdminAuthPostAuthLoginResponse | X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 未认证访问后台或会话过期时由路由守卫进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 认证成功进入原目标或数据驾驶舱；失败留在当前页；取消返回登录 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-ADMIN-BASE-001;REQ-ADMIN-SECURITY-001`
- API：`POST /admin-api/v1/auth/login`
- operationId：`adminAdminAuthPostAuthLogin`
- 配置组：`auth;system`
- 关键配置：`auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled`
- 测试：`TST-V122-012`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 不适用 | createdAt:desc | 名称/标识;状态;更新时间;版本;操作 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 后台登录 | 概览;业务数据;状态历史;关联对象;操作审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
