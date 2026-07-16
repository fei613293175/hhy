# ADM-NOTIFY-001 · 通知模板

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 运营 | /operations/notifications/templates | PAGE | ADM-CONFIG | R30 | notification.template.read | HIGH | READY |

**业务目标：** 站内信、推送、短信模板版本、变量白名单、预览、审批和灰度

**主要角色：** 具备 notification.template.read 的后台员工；写操作另需 notification.template.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-CONFIG | 配置与版本管理 | 环境/版本选择\|配置表单\|差异\|校验/测试\|审批/激活\|历史 | 单位范围、依赖、影响预览、SecretRef、回滚 | 编辑产生新版本；秘密不回显；激活和回滚需审批 | 字段帮助和错误关联 | 表单与差异并排/堆叠 | 跨字段校验、连接测试、审批、回滚测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-05003 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-05004 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-05005 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-05006 | 版本与差异 | activeVersionDiff | 当前与草稿差异 | UI_META | ui | DIFF_VIEW | LOCAL_UI | 存在草稿时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 当前与草稿差异不符合页面规格 |
| FLD-05007 | 版本与差异 | impactPreview | 生效影响预览 | UI_META | ui | IMPACT_PREVIEW | LOCAL_UI | 提交审批前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效影响预览不符合页面规格 |
| FLD-05008 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-05009 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-05010 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-05011 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-05012 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-05013 | 路由与筛选 | channel | 渠道 | FILTER | string | FILTER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道格式或范围不正确 |
| FLD-05014 | 路由与筛选 | templateCode | 模板代码 | FILTER | string | FILTER_INPUT | adminNotificationGetTemplates.query | 可选；仅在对应操作/筛选时提交 | 执行“通知模板列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 模板代码格式或范围不正确 |
| FLD-05015 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | ≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-05016 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | ≥1；≤100；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-05017 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-05018 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-05019 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-05020 | 表单输入 | channel | 渠道 | INPUT | string | TEXT_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道不符合要求 |
| FLD-05021 | 表单输入 | templateCode | 模板代码 | INPUT | string | TEXT_INPUT | adminNotificationGetTemplates.request | 可选；按业务条件或页面状态决定 | 执行“通知模板列表”且字段适用时显示 | 具备 notification.template.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 模板代码不符合要求 |
| FLD-05022 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-05023 | 主要内容 | templateCode | 模板代码 | DISPLAY | string | TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 模板代码加载失败时显示字段级占位或隐藏 |
| FLD-05024 | 主要内容 | channel | 渠道 | DISPLAY | string | STATUS_TAG | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:IN_APP/PUSH/SMS | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道加载失败时显示字段级占位或隐藏 |
| FLD-05025 | 主要内容 | locale | 语言 | DISPLAY | string | TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多16字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 语言加载失败时显示字段级占位或隐藏 |
| FLD-05026 | 主要内容 | versionNo | 版本号 | DISPLAY | integer | NUMBER_TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥1；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本号加载失败时显示字段级占位或隐藏 |
| FLD-05027 | 主要内容 | status | 状态 | DISPLAY | string | STATUS_TAG | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多32字符；枚举:DRAFT/PENDING_APPROVAL/ACTIVE/INACTIVE/REJECTED | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-05028 | 主要内容 | titleTemplate | 标题模板 | DISPLAY | string | TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多300字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题模板加载失败时显示字段级占位或隐藏 |
| FLD-05029 | 主要内容 | bodyTemplate | 正文模板 | DISPLAY | string | TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多5000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文模板加载失败时显示字段级占位或隐藏 |
| FLD-05030 | 主要内容 | allowedVariables | 允许变量 | DISPLAY | array<string> | LIST | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 允许变量加载失败时显示字段级占位或隐藏 |
| FLD-05031 | 主要内容 | sampleVariables | 示例变量 | DISPLAY | JsonObject | STRUCTURED_SECTION | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 示例变量加载失败时显示字段级占位或隐藏 |
| FLD-05032 | 主要内容 | effectiveAt | 生效时间 | DISPLAY | string | DATETIME_TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间加载失败时显示字段级占位或隐藏 |
| FLD-05033 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminNotificationGetTemplates.response.data.NotificationTemplateResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-05034 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminNotificationPostTemplate.header | 必填 | 执行“创建通知模板版本”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-05035 | 表单输入 | locale | 语言 | INPUT | string | TEXT_INPUT | adminNotificationPostTemplate.request | 必填 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 必填；最多16字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 语言不符合要求 |
| FLD-05036 | 表单输入 | titleTemplate | 标题模板 | INPUT | string | TEXT_INPUT | adminNotificationPostTemplate.request | 可选；按业务条件或页面状态决定 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 最多300字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题模板不符合要求 |
| FLD-05037 | 表单输入 | bodyTemplate | 正文模板 | INPUT | string | RICH_TEXT_OR_TEXTAREA | adminNotificationPostTemplate.request | 必填 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 必填；最多5000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文模板不符合要求 |
| FLD-05038 | 表单输入 | allowedVariables | 允许变量 | INPUT | array<string> | MULTI_SELECT_OR_LIST | adminNotificationPostTemplate.request | 必填 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 必填；最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 允许变量不符合要求 |
| FLD-05039 | 表单输入 | sampleVariables | 示例变量 | INPUT | JsonObject | STRUCTURED_SECTION | adminNotificationPostTemplate.request | 可选；按业务条件或页面状态决定 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 示例变量不符合要求 |
| FLD-05040 | 表单输入 | effectiveAt | 生效时间 | INPUT | string | DATETIME_PICKER | adminNotificationPostTemplate.request | 可选；按业务条件或页面状态决定 | 执行“创建通知模板版本”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间不符合要求 |
| FLD-05041 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminNotificationPatchTemplate.path | 必填 | 执行“修改通知模板草稿”时显示 | 用户具备权限且页面状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-05042 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminNotificationPatchTemplate.request | 必填 | 执行“修改通知模板草稿”且字段适用时显示 | 具备 notification.template.manage 且资源状态允许 | 必填；≥0；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |

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
| ACT-V122-048 | 通知模板列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,keyword,status,sort,channel,templateCode | 不需要 | GET /admin-api/v1/notification-templates；请求模型 AdminNotificationGetTemplatesParameters；响应模型 AdminNotificationGetTemplatesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-049 | 创建通知模板版本 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 notification.template.manage，且资源状态允许“创建通知模板版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,templateCode,channel,locale,titleTemplate,bodyTemplate,allowedVariables,sampleVariables,effectiveAt；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/notification-templates；请求模型 NotificationTemplateCreateRequest；响应模型 AdminNotificationPostTemplateResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建通知模板版本成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-V122-050 | 修改通知模板草稿 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 notification.template.manage，且资源状态允许“修改通知模板草稿”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,id,titleTemplate,bodyTemplate,allowedVariables,sampleVariables,status,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PATCH /admin-api/v1/notification-templates/{id}；请求模型 NotificationTemplatePatchRequest；响应模型 AdminNotificationPatchTemplateResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“修改通知模板草稿成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-NOTIFICATION-OPS-001;REQ-NOTIFY-001`
- API：`GET /admin-api/v1/notification-templates;POST /admin-api/v1/notification-templates;PATCH /admin-api/v1/notification-templates/{id}`
- operationId：`adminNotificationGetTemplates;adminNotificationPostTemplate;adminNotificationPatchTemplate`
- 配置组：`operations;notification;platform`
- 关键配置：`ops.backup.rpo_minutes;ops.recovery.rto_minutes;ops.audit_log.retention_days;ops.security_event.retention_days;ops.alert.error_rate_percent;ops.alert.api_p95_ms;notification.marketing.default_enabled;notification.quiet_hours.start;notification.quiet_hours.end;platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-V122-047;TST-V122-048;TST-V122-049`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;关键词;状态;排序;渠道;模板代码 | updatedAt:desc | ID;模板代码;渠道;语言;版本号;状态;标题模板;正文模板;允许变量;示例变量;生效时间;版本 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 创建通知模板版本;修改通知模板草稿 | 概览;业务数据;状态历史;关联对象;操作审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
