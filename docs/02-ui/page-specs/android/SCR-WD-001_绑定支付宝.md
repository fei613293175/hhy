# SCR-WD-001 · 绑定支付宝

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 提现 | /me/withdrawal/account | PAGE | MOB-FORM | R24 | 是+实名 | CRITICAL | READY |

**业务目标：** 账户和姓名校验

**主要角色：** 已登录、完成实名且资金能力未被冻结的用户

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-FORM | 标准创建/编辑表单 | 步骤/标题\|字段分组\|媒体上传\|预览\|底部保存/提交 | 草稿状态、字段错误、离开提示、上传状态 | 草稿与提交分离；版本冲突不覆盖服务端；关键字段变更显示影响 | 字段分组和错误摘要；必填标识非仅颜色 | 长表单底部操作固定但不遮挡 | 字段校验、草稿恢复、上传失败、冲突合并测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01931 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01932 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-01933 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-01934 | 主要内容 | id | ID | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-01935 | 主要内容 | withdrawalNo | withdrawal No | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | withdrawal No加载失败时显示字段级占位或隐藏 |
| FLD-01936 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-01937 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-01938 | 主要内容 | amountCent | 金额 | DISPLAY | integer | MONEY_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 金额加载失败时显示字段级占位或隐藏 |
| FLD-01939 | 主要内容 | feeCent | fee Cent | DISPLAY | integer | MONEY_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | fee Cent加载失败时显示字段级占位或隐藏 |
| FLD-01940 | 主要内容 | netAmountCent | net Amount Cent | DISPLAY | integer | MONEY_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | net Amount Cent加载失败时显示字段级占位或隐藏 |
| FLD-01941 | 主要内容 | payoutAccountMasked | payout Account Masked | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | payout Account Masked加载失败时显示字段级占位或隐藏 |
| FLD-01942 | 主要内容 | failureCode | 失败代码 | DISPLAY | string | TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 失败代码加载失败时显示字段级占位或隐藏 |
| FLD-01943 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-01944 | 主要内容 | completedAt | completed At | DISPLAY | string | DATETIME_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | completed At加载失败时显示字段级占位或隐藏 |
| FLD-01945 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | withdrawalGetMePayoutAccount.response.data.WithdrawalResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-01946 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | withdrawalPutMePayoutAccount.header | 必填 | 执行“绑定/修改支付宝”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-01947 | 表单输入 | accountName | 账户名称 | INPUT | string | TEXT_INPUT | withdrawalPutMePayoutAccount.request | 必填 | 执行“绑定/修改支付宝”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；最多2000字符 | CRITICAL | 默认脱敏；财务权限+会话升级后短时查看 | 账户名称不符合要求 |
| FLD-01948 | 表单输入 | alipayAccount | 支付宝账号 | INPUT | integer | NUMBER_INPUT | withdrawalPutMePayoutAccount.request | 必填 | 执行“绑定/修改支付宝”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；≥0；格式:int64 | CRITICAL | 默认脱敏；财务权限+会话升级后短时查看 | 支付宝账号不符合要求 |
| FLD-01949 | 表单输入 | smsCode | 短信验证码 | INPUT | string | OTP_INPUT | withdrawalPutMePayoutAccount.request | 必填 | 执行“绑定/修改支付宝”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 必填；最少4字符；最多10字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | 短信验证码不符合要求 |
| FLD-01950 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | withdrawalPutMePayoutAccount.request | 可选；按业务条件或页面状态决定 | 执行“绑定/修改支付宝”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| DIRTY | 存在未保存变更 | 显示未保存标识和离开确认 | 保存/放弃 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止静默丢失 | 否 |
| AUTOSAVING | 由页面业务状态机进入 | 按“AUTOSAVING”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| VALIDATING | 正在验证一次性state或输入 | 显示处理中，不暴露敏感参数 | 返回（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 参数立即从URL清理 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| QUOTE_READY | 服务端报价有效 | 展示最终金额、费用、权益和过期时间 | 确认下单/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 过期后必须重新报价 | 否 |
| PAYMENT_PENDING | 支付已创建但未确认最终结果 | 轮询/订阅服务端订单状态 | 查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复创建订单 | 否 |
| UNKNOWN | 客户端无法确认服务端最终结果 | 显示处理中并调用查单/查询接口 | 继续查询/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止直接判定失败并重复扣款 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-WD-001-01 | 支付宝账户 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 路由参数、权限和页面状态有效 | 不需要 | GET /api/v1/me/payout-account；请求模型 WithdrawalGetMePayoutAccountParameters；响应模型 WithdrawalGetMePayoutAccountResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-WD-001-02 | 绑定/修改支付宝 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 登录+实名，且资源状态允许“绑定/修改支付宝”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,accountName,alipayAccount,smsCode,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /api/v1/me/payout-account；请求模型 WithdrawalPutMePayoutAccountRequest；响应模型 WithdrawalPutMePayoutAccountResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“绑定/修改支付宝成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-WD-001;REQ-LEDGER-001`
- API：`GET /api/v1/me/payout-account;PUT /api/v1/me/payout-account`
- operationId：`withdrawalGetMePayoutAccount;withdrawalPutMePayoutAccount`
- 配置组：`withdrawal`
- 关键配置：`withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month`
- 测试：`TST-WD_001-HAPPY;TST-LEDGER_001-HAPPY;TST-WD_001-IDEMPOTENT;TST-LEDGER_001-IDEMPOTENT`
- UI参考：`B09/P01/P03-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
