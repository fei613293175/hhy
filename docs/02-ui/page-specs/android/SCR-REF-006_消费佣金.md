# SCR-REF-006 · 消费佣金

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 推广 | /me/referral/commissions | PAGE | MOB-WALLET | R25 | 是+实名 | HIGH | READY |

**业务目标：** 一级二级明细

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-WALLET | 资产与流水 | 余额/指标摘要\|状态说明\|筛选\|流水列表\|规则入口 | 金额单位、待结算/可用/冻结拆分、账期、风险提示 | 金额全部以分传输并格式化；余额与流水口径一致 | 金额和正负方向完整播报 | 摘要卡片可换行 | 金额格式、账本一致、状态筛选测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-01792 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-01793 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-01794 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-01795 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-01796 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-01797 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-01798 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | referralGetMeReferralCommissions.query | 可选；仅在对应操作/筛选时提交 | 执行“一级二级佣金”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-01799 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-01800 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-01801 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-01802 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-01803 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-01804 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | referralGetMeReferralCommissions.request | 可选；按业务条件或页面状态决定 | 执行“一级二级佣金”且字段适用时显示 | 具备 登录+实名 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-01805 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-01806 | 主要内容 | inviteCode | 邀请码 | DISPLAY | string | TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 邀请码加载失败时显示字段级占位或隐藏 |
| FLD-01807 | 主要内容 | directCount | 直推人数 | DISPLAY | integer | NUMBER_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 直推人数加载失败时显示字段级占位或隐藏 |
| FLD-01808 | 主要内容 | level2Count | 二级人数 | DISPLAY | integer | NUMBER_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 二级人数加载失败时显示字段级占位或隐藏 |
| FLD-01809 | 主要内容 | qualifiedDirectCount | 合格直推人数 | DISPLAY | integer | NUMBER_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 合格直推人数加载失败时显示字段级占位或隐藏 |
| FLD-01810 | 主要内容 | pendingCommissionCent | 待结算佣金 | DISPLAY | integer | MONEY_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 待结算佣金加载失败时显示字段级占位或隐藏 |
| FLD-01811 | 主要内容 | settledCommissionCent | 已结算佣金 | DISPLAY | integer | MONEY_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 已结算佣金加载失败时显示字段级占位或隐藏 |
| FLD-01812 | 主要内容 | milestones | 奖励档位 | DISPLAY | array<object> | LIST | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 奖励档位加载失败时显示字段级占位或隐藏 |
| FLD-01813 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | referralGetMeReferralCommissions.response.data.ReferralResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| EMPTY | 查询成功但无符合条件的数据 | 展示业务化空状态、当前筛选和明确下一步 | 清除筛选/创建/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 修改筛选或执行主行动 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| RISK_FROZEN | 收益或资金因风险冻结 | 拆分展示冻结金额和原因 | 申诉/联系客服 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止提现和调整 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-REF-006-01 | 一级二级佣金 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/me/referral/commissions；请求模型 ReferralGetMeReferralCommissionsParameters；响应模型 ReferralGetMeReferralCommissionsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-REF-001;REQ-REF-002;REQ-REF-ACT-001;REQ-REF-ACT-002`
- API：`GET /api/v1/me/referral/commissions`
- operationId：`referralGetMeReferralCommissions`
- 配置组：`referral`
- 关键配置：`commission.settlement_delay_hours;referral.max_commission_level;referral.binding_mode;referral.relation_change_allowed;referral.attribution_days;referral.block_self_invite;referral.block_same_device;referral.red_packet_principal_commissionable`
- 测试：`TST-REF_001-HAPPY;TST-REF_002-HAPPY;TST-REF_ACT_001-HAPPY;TST-REF_ACT_002-HAPPY`
- UI参考：`B10/B08/P06-P07/P01`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
