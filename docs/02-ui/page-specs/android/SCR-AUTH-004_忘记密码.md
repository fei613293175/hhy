# SCR-AUTH-004 · 忘记密码

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 账号 | /password/reset | PAGE | MOB-AUTH-FORM | R02 | 否 | CRITICAL | READY |

**业务目标：** 安全验证、短信验证码和新密码

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-AUTH-FORM | 认证与安全表单 | 品牌/标题\|表单\|安全挑战\|协议\|主操作\|辅助入口 | 字段错误、倒计时、锁定提示、隐私说明 | 敏感字段禁止持久化；提交前完成本地校验与安全挑战 | 字段标签和错误关联；验证码倒计时可读 | 键盘弹出时主操作可达 | 字段校验表、敏感数据检查、限流与重放测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00162 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00163 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-00164 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-00165 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | authPostAuthSecurityChallenges.header | 必填 | 执行“创建图形/行为安全挑战”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00166 | 表单输入 | scene | scene | INPUT | string | SELECT | authPostAuthSecurityChallenges.request | 必填 | 执行“创建图形/行为安全挑战”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符；枚举:LOGIN/REGISTER/RESET_PASSWORD/SENSITIVE_OPERATION | NORMAL | 无需特殊掩码；仍遵守最小展示 | scene不符合要求 |
| FLD-00167 | 表单输入 | clientNonce | 客户端随机数 | INPUT | string | TEXT_INPUT | authPostAuthSecurityChallenges.request | 必填 | 执行“创建图形/行为安全挑战”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 客户端随机数不符合要求 |
| FLD-00168 | 表单输入 | deviceFingerprint | device Fingerprint | INPUT | string | TEXT_INPUT | authPostAuthSecurityChallenges.request | 可选；按业务条件或页面状态决定 | 执行“创建图形/行为安全挑战”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | device Fingerprint不符合要求 |
| FLD-00169 | 主要内容 | challengeId | 安全挑战ID | DISPLAY | string | TEXT | authPostAuthSecurityChallenges.response.data.ChallengeResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 安全挑战ID加载失败时显示字段级占位或隐藏 |
| FLD-00170 | 主要内容 | challengeType | challenge Type | DISPLAY | string | TEXT | authPostAuthSecurityChallenges.response.data.ChallengeResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | challenge Type加载失败时显示字段级占位或隐藏 |
| FLD-00171 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | authPostAuthSecurityChallenges.response.data.ChallengeResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-00172 | 主要内容 | imageBase64 | image Base64 | DISPLAY | string | TEXT | authPostAuthSecurityChallenges.response.data.ChallengeResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | image Base64加载失败时显示字段级占位或隐藏 |
| FLD-00173 | 主要内容 | token | token | DISPLAY | string | TEXT | authPostAuthSecurityChallenges.response.data.ChallengeResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | token加载失败时显示字段级占位或隐藏 |
| FLD-00174 | 表单输入 | phone | 手机号 | INPUT | string | TEXT_INPUT | authPostAuthSmsSend.request | 必填 | 执行“阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD”且字段适用时显示 | 具备 公开+安全挑战 且资源状态允许 | 必填；最多11字符；正则:^1[3-9]\d{9}$ | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 手机号不符合要求 |
| FLD-00175 | 表单输入 | challengeId | 安全挑战ID | INPUT | string | TEXT_INPUT | authPostAuthSmsSend.request | 必填 | 执行“阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD”且字段适用时显示 | 具备 公开+安全挑战 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 安全挑战ID不符合要求 |
| FLD-00176 | 表单输入 | challengeProof | 安全挑战结果 | INPUT | string | TEXT_INPUT | authPostAuthSmsSend.request | 必填 | 执行“阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD”且字段适用时显示 | 具备 公开+安全挑战 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 安全挑战结果不符合要求 |
| FLD-00177 | 表单输入 | smsCode | 短信验证码 | INPUT | string | OTP_INPUT | authPostAuthPasswordReset.request | 必填 | 执行“短信校验后重置密码”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最少4字符；最多10字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 短信验证码不符合要求 |
| FLD-00178 | 表单输入 | newPassword | 新密码 | INPUT | string | PASSWORD_INPUT | authPostAuthPasswordReset.request | 必填 | 执行“短信校验后重置密码”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最少8字符；最多72字符；格式:password | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 新密码不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| INITIAL | 页面首次创建，尚未发起请求 | 显示稳定布局占位，禁止空白闪烁 | 发起初始化 | 与当前状态、权限或服务端version冲突的所有写操作 | 自动进入下一状态 | 否 |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| VALIDATING | 正在验证一次性state或输入 | 显示处理中，不暴露敏感参数 | 返回（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 参数立即从URL清理 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| RATE_LIMITED | 触发频率限制 | 显示剩余等待时间和限制原因 | 等待/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 倒计时结束后重新获取前置条件 | 否 |
| LOCKED | 账号、验证码或安全流程被锁定 | 展示锁定原因、期限和申诉/客服入口 | 返回/申诉 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止继续提交 | 是 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-AUTH-004-01 | 创建图形/行为安全挑战 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 公开，且资源状态允许“创建图形/行为安全挑战”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,scene,clientNonce,deviceFingerprint；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/auth/security-challenges；请求模型 AuthPostAuthSecurityChallengesRequest；响应模型 AuthPostAuthSecurityChallengesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建图形/行为安全挑战成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-AUTH-004-02 | 阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 公开+安全挑战，且资源状态允许“阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,phone,scene,challengeId,challengeProof；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/auth/sms/send；请求模型 AuthPostAuthSmsSendRequest；响应模型 AuthPostAuthSmsSendResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“阿里云短信验证码：LOGIN/REGISTER/RESET_PASSWORD成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-AUTH-004-03 | 短信校验后重置密码 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 公开，且资源状态允许“短信校验后重置密码”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,phone,smsCode,newPassword；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /api/v1/auth/password/reset；请求模型 AuthPostAuthPasswordResetRequest；响应模型 AuthPostAuthPasswordResetResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“短信校验后重置密码成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-AUTH-001;REQ-AUTH-002;REQ-AUTH-003;REQ-AUTH-004`
- API：`POST /api/v1/auth/security-challenges;POST /api/v1/auth/sms/send;POST /api/v1/auth/password/reset`
- operationId：`authPostAuthSecurityChallenges;authPostAuthSmsSend;authPostAuthPasswordReset`
- 配置组：`auth`
- 关键配置：`auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required`
- 测试：`TST-AUTH_001-HAPPY;TST-AUTH_002-HAPPY;TST-AUTH_003-HAPPY;TST-AUTH_004-HAPPY;TST-AUTH_001-IDEMPOTENT;TST-AUTH_002-IDEMPOTENT;TST-AUTH_003-IDEMPOTENT;TST-AUTH_004-IDEMPOTENT`
- UI参考：`B01/P02-P07`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`

<!-- BEGIN R02-CAPTCHA-OVERLAY -->
## 9. R02 安全验证码弹层覆盖规则（强制）

> **覆盖优先级：** 本节与 [`R02安全验证码弹层交互与视觉规格_V1.2.2.md`](../../R02安全验证码弹层交互与视觉规格_V1.2.2.md) 是 R02 认证安全验证的页面级覆盖规则；与本文件自动生成的大表出现展示语义冲突时，以本节和专用规格为准，API、字段 ID 与 operationId 仍以原表为准。

### 用户可见与内部字段边界

- 页面初始状态不显示图形验证码、挑战卡片或任何“创建安全验证”入口。
- 自动生成字段表中的 `challengeId`、`challengeType`、`expiresAt`、`imageBase64`、`token`、`clientNonce`、`deviceFingerprint` 只表示客户端内部绑定、过期判断或内存传递，不是页面文本字段；不得直接渲染键名、原值、Base64、Token、请求编号或技术错误详情。
- 动作“创建图形/行为安全挑战”是原业务按钮触发后的内部编排步骤，不生成独立可见按钮，不显示“创建成功”Toast，也不跳转挑战详情页。
- 挑战答案只在弹层内短暂保留；挑战刷新、过期、取消、页面离开或流程结束时立即清除。
- 统一视觉和状态参考：`design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png`，面板 `P01—P08`。

### 忘记密码覆盖流程

1. 初始页不显示图形安全验证码；用户继续填写手机号、新密码等既有字段。
2. 点击“发送验证码”且手机号和当前页面前置条件通过后，自动创建 `scene=RESET_PASSWORD` 的挑战；显示 `P02/P06`，副标题为 `发送重置短信前，请先完成安全验证`。
3. 点击“验证并继续”直接调用原 `POST /api/v1/auth/sms/send`，`scene=RESET_PASSWORD`。
4. 发送成功后显示 `P07`，自动关闭弹层、启动短信倒计时并聚焦短信验证码输入；最终“重置密码”调用原重置接口，不重复弹图形验证码。
5. 挑战答案错误、过期或网络失败留在弹层恢复；手机号不存在、短信规则和密码规则等非挑战错误回到原页面处理。
6. 取消安全验证保留手机号和新密码的进程内表单状态，但清除挑战、答案和待处理短信意图。

### 忘记密码验收补充

- [ ] 安全挑战仅保护重置短信发送，不改变原重置密码 API 和字段。
- [ ] 新密码、短信码和安全验证码答案不进入日志、埋点、URL、SavedStateHandle 或磁盘。
- [ ] 短信发送成功后不要求用户再次点击“发送验证码”。
- [ ] 网络未知结果、限流和挑战过期均按照专用规格恢复，不能无限自动重试。
<!-- END R02-CAPTCHA-OVERLAY -->
