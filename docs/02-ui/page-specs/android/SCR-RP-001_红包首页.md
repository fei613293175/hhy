# SCR-RP-001 · 红包首页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 红包 | /red-packet | PAGE | MOB-HOME | R22 | 是 | NORMAL | READY |

**业务目标：** 四类红包聚合

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 登录后由底部导航、业务通知或深链进入

**退出/返回：** 切换底部导航；返回键按应用根导航策略处理

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-HOME | 聚合首页 | 顶栏\|状态/资产摘要\|模块化内容区\|底部导航 | 骨架、空模块降级、模块追踪上下文 | 模块独立失败不得拖垮整页；顺序由服务端模块合同控制 | 模块标题语义化；轮播可暂停 | 卡片宽度按Token缩放 | 模块容错、埋点上下文、CMS兼容测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00638 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00639 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-00640 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-00641 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-00642 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-00643 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-00644 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | redPacketGetRedPacketCampaigns.query | 可选；仅在对应操作/筛选时提交 | 执行“红包聚合列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-00645 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-00646 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-00647 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-00648 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-00649 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-00650 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | redPacketGetRedPacketCampaigns.request | 可选；按业务条件或页面状态决定 | 执行“红包聚合列表”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-00651 | 主要内容 | id | ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-00652 | 主要内容 | contentId | 内容ID | DISPLAY | string | TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容ID加载失败时显示字段级占位或隐藏 |
| FLD-00653 | 主要内容 | ownerUserId | owner User Id | DISPLAY | string | TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | owner User Id加载失败时显示字段级占位或隐藏 |
| FLD-00654 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-00655 | 主要内容 | totalCount | 红包总数量 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 红包总数量加载失败时显示字段级占位或隐藏 |
| FLD-00656 | 主要内容 | remainingCount | remaining Count | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | remaining Count加载失败时显示字段级占位或隐藏 |
| FLD-00657 | 主要内容 | amountPerClaimCent | 单个红包金额 | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 单个红包金额加载失败时显示字段级占位或隐藏 |
| FLD-00658 | 主要内容 | principalCent | principal Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | principal Cent加载失败时显示字段级占位或隐藏 |
| FLD-00659 | 主要内容 | serviceFeeCent | service Fee Cent | DISPLAY | integer | MONEY_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | ≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | service Fee Cent加载失败时显示字段级占位或隐藏 |
| FLD-00660 | 主要内容 | startAt | 开始时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间加载失败时显示字段级占位或隐藏 |
| FLD-00661 | 主要内容 | endAt | 结束时间 | DISPLAY | string | DATETIME_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间加载失败时显示字段级占位或隐藏 |
| FLD-00662 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | redPacketGetRedPacketCampaigns.response.data.RedPacketCampaignResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| PARTIAL_CONTENT | 部分模块成功、部分模块失败 | 保留成功模块，失败模块显示局部重试 | 重试失败模块 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得将局部失败升级为全页失败 | 否 |
| REFRESHING | 已有内容上执行刷新 | 保留内容并显示轻量刷新指示 | 取消刷新 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败保留旧数据 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| INELIGIBLE | 不满足任务或红包资格 | 展示具体原因和可改善条件 | 返回/查看规则 | 与当前状态、权限或服务端version冲突的所有写操作 | 不创建浏览会话 | 是 |
| READY | 资格检查通过，可开始任务 | 展示任务规则和开始确认 | 开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 开始后由服务端创建会话 | 否 |
| RUNNING | 任务有效进行中 | 展示服务端进度、心跳和可见性 | 继续/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 本地计时不决定奖励 | 否 |
| RESERVED | 奖励名额已短期预留 | 显示剩余预留时间并完成领取条件 | 领取/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 超时进入EXPIRED | 否 |
| COMPLETED | 任务条件满足或奖励已领取 | 展示结果和账本入口 | 查看记录/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 重复进入查询已有结果 | 是 |
| EXPIRED | 资格、报价、验证码或预留过期 | 说明过期原因 | 重新开始/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 生成新的会话/报价/挑战 | 是 |
| RISK_BLOCKED | 风控拒绝继续任务 | 展示通用原因和申诉入口，不泄露规则细节 | 申诉/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 记录风险事件 | 是 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-RP-001-01 | 红包聚合列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /api/v1/red-packet-campaigns；请求模型 RedPacketGetRedPacketCampaignsParameters；响应模型 RedPacketGetRedPacketCampaignsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 登录后由底部导航、业务通知或深链进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 切换底部导航；返回键按应用根导航策略处理 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：模块独立加载和刷新；局部失败不清空成功模块；展示数据更新时间
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：允许展示明确标记的只读缓存；所有写操作离线禁用；恢复网络后主动校验version

## 7. 配置、数据和测试

- 需求：`REQ-RP-003;REQ-RP-006`
- API：`GET /api/v1/red-packet-campaigns`
- operationId：`redPacketGetRedPacketCampaigns`
- 配置组：`red_packet`
- 关键配置：`red_packet.default_view_seconds;red_packet.reservation_min_seconds;red_packet.unit_amount_min_cent;red_packet.unit_amount_max_cent;red_packet.min_count;red_packet.service_fee_bps;red_packet.one_active_per_content;red_packet.allow_raise_unit_amount`
- 测试：`TST-RP_003-HAPPY;TST-RP_006-HAPPY`
- UI参考：`B03/B09/P01/P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
