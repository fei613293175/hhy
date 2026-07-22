# ADM-RISK-004 · 风控规则模拟器

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 风控 | /governance/risk/simulator | PAGE | ADM-LIST | R31 | risk.rule.simulate | HIGH | READY |

**业务目标：** 使用脱敏样本或手工输入模拟规则命中、动作和解释，不产生真实业务副作用

**主要角色：** 具备 risk.rule.simulate 的后台员工；写操作另需 risk.rule.simulate

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-LIST | 标准列表运营页 | 页标题/说明\|筛选区\|统计/批量操作\|表格\|分页 | 筛选回显、列设置、默认排序、行操作、导出入口 | 筛选写入URL；批量操作先显示影响预览；翻页不丢筛选 | 表头语义、键盘操作、行操作菜单标签 | 表格横滚，关键列冻结 | 筛选、排序、分页、批量、导出、权限测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04982 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04983 | 筛选区 | filterSummary | 当前筛选条件 | UI_META | ui | FILTER_SUMMARY | LOCAL_UI | 有筛选时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前筛选条件不符合页面规格 |
| FLD-04984 | 列表摘要 | resultCount | 结果数量 | UI_META | ui | NUMBER_TEXT | LOCAL_UI | 服务端提供时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | ≥0 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结果数量不符合页面规格 |
| FLD-04985 | 内容区 | emptyStateReason | 空状态原因 | UI_META | ui | EMPTY_STATE | LOCAL_UI | 结果为空时必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 空状态原因不符合页面规格 |
| FLD-04986 | 表格工具栏 | columnPreference | 列显示设置 | UI_META | ui | COLUMN_PICKER | LOCAL_UI | 可选 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 关键列不可隐藏 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 列显示设置不符合页面规格 |
| FLD-04987 | 表格 | batchSelection | 批量选择 | UI_META | ui | ROW_SELECTION | LOCAL_UI | 具备批量权限时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 跨页选择必须显式 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 批量选择不符合页面规格 |
| FLD-04988 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminRiskRulesPostSimulation.header | 必填 | 执行“模拟风控规则”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04989 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminRiskRulesPostSimulation.path | 必填 | 执行“模拟风控规则”时显示 | 用户具备权限且页面状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04990 | 表单输入 | sampleMode | 样本方式 | INPUT | string | SELECT | adminRiskRulesPostSimulation.request | 必填 | 执行“模拟风控规则”且字段适用时显示 | 具备 risk.rule.simulate 且资源状态允许 | 必填；最多32字符；枚举:MANUAL/MASKED_SNAPSHOT/TEST_DATASET | NORMAL | 无需特殊掩码；仍遵守最小展示 | 样本方式不符合要求 |
| FLD-04991 | 表单输入 | subjectType | 主体类型 | INPUT | string | TEXT_INPUT | adminRiskRulesPostSimulation.request | 必填 | 执行“模拟风控规则”且字段适用时显示 | 具备 risk.rule.simulate 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 主体类型不符合要求 |
| FLD-04992 | 表单输入 | samplePayload | 模拟样本 | INPUT | JsonObject | STRUCTURED_SECTION | adminRiskRulesPostSimulation.request | 可选；按业务条件或页面状态决定 | 执行“模拟风控规则”且字段适用时显示 | 具备 risk.rule.simulate 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 模拟样本不符合要求 |
| FLD-04993 | 表单输入 | datasetId | 测试数据集 | INPUT | string | TEXT_INPUT | adminRiskRulesPostSimulation.request | 可选；按业务条件或页面状态决定 | 执行“模拟风控规则”且字段适用时显示 | 具备 risk.rule.simulate 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 测试数据集不符合要求 |
| FLD-04994 | 表单输入 | explain | 输出命中解释 | INPUT | boolean | TEXT_INPUT | adminRiskRulesPostSimulation.request | 可选；按业务条件或页面状态决定 | 执行“模拟风控规则”且字段适用时显示 | 具备 risk.rule.simulate 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 输出命中解释不符合要求 |
| FLD-04995 | 主要内容 | simulationId | simulation Id | DISPLAY | string | TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | simulation Id加载失败时显示字段级占位或隐藏 |
| FLD-04996 | 主要内容 | ruleVersionId | rule Version Id | DISPLAY | string | TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | rule Version Id加载失败时显示字段级占位或隐藏 |
| FLD-04997 | 主要内容 | matched | 是否命中 | DISPLAY | boolean | TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 是否命中加载失败时显示字段级占位或隐藏 |
| FLD-04998 | 主要内容 | score | 风险分 | DISPLAY | number | NUMBER_TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 风险分加载失败时显示字段级占位或隐藏 |
| FLD-04999 | 主要内容 | recommendedAction | 建议动作 | DISPLAY | string | TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 建议动作加载失败时显示字段级占位或隐藏 |
| FLD-05000 | 主要内容 | ruleHits | 规则命中 | DISPLAY | array<RuleHitResource> | LIST | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 规则命中加载失败时显示字段级占位或隐藏 |
| FLD-05001 | 主要内容 | sideEffectFree | side Effect Free | DISPLAY | boolean | TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | side Effect Free加载失败时显示字段级占位或隐藏 |
| FLD-05002 | 主要内容 | executedAt | executed At | DISPLAY | string | DATETIME_TEXT | adminRiskRulesPostSimulation.response.data.RiskSimulationResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | executed At加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| PARTIAL_ERROR | 非首屏请求或单模块失败 | 保留现有内容并在对应区域提示 | 局部重试 | 与当前状态、权限或服务端version冲突的所有写操作 | 成功后回到CONTENT | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-046 | 模拟风控规则 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 risk.rule.simulate，且资源状态允许“模拟风控规则”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,id,sampleMode,subjectType,samplePayload,datasetId,explain；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/risk-rule-versions/{id}/simulate；请求模型 RiskSimulationRequest；响应模型 AdminRiskRulesPostSimulationResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“模拟风控规则成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：首屏加载+显式刷新；筛选/排序变化重置分页；翻页去重追加；领域事件仅刷新受影响行
- 分页策略：后台页码或cursor二选一；默认20，最大100；筛选写入URL；默认排序由页面运营规格冻结
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-RISK-RULE-001;REQ-SEC-001`
- API：`POST /admin-api/v1/risk-rule-versions/{id}/simulate`
- operationId：`adminRiskRulesPostSimulation`
- 配置组：`system;auth;red_packet;withdrawal`
- 关键配置：`system.maintenance.enabled;system.maintenance.message;system.registration.enabled;system.publish.enabled;system.red_packet.enabled;system.withdrawal.enabled;auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required;red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount;withdrawal.min_amount_cent.normal;withdrawal.fee.fixed_cent.normal;withdrawal.fee.bps.normal;withdrawal.daily_count.normal;withdrawal.min_amount_cent.month;withdrawal.fee.fixed_cent.month;withdrawal.fee.bps.month;withdrawal.daily_count.month`
- 测试：`TST-V122-045`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 关键词;状态;创建时间范围 | updatedAt:desc | 名称/标识;状态;更新时间;版本;操作 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 模拟风控规则 | 概览;业务数据;状态历史;关联对象;操作审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
