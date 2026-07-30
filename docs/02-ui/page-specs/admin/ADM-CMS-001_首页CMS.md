# ADM-CMS-001 · 首页CMS

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 运营 | /operations/cms/home | PAGE | ADM-CMS | R28 | cms.manage | NORMAL | READY |

**业务目标：** 模块、轮播、专题

**主要角色：** 具备 cms.manage 的后台员工；写操作另需 cms.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-CMS | CMS编辑与预览 | 内容树/列表\|编辑器\|多端预览\|版本\|发布计划 | XSS净化、版本差异、SEO、预览、审批 | 保存草稿不等于发布；富文本白名单；版本不可覆盖 | 编辑器键盘可用、预览替代文本 | 编辑与预览切换 | XSS、版本、预览、定时发布测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-03745 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-03746 | 表单状态 | formDirty | 未保存变更 | UI_META | ui | DIRTY_INDICATOR | LOCAL_UI | 字段发生变化时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 未保存变更不符合页面规格 |
| FLD-03747 | 表单状态 | fieldErrorSummary | 字段错误摘要 | UI_META | ui | ERROR_SUMMARY | LOCAL_UI | 存在校验错误时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 字段错误摘要不符合页面规格 |
| FLD-03748 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-03749 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-03750 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-03751 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-03752 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-03753 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminCMSGetCmsHomeModules.query | 可选；仅在对应操作/筛选时提交 | 执行“首页模块”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-03754 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-03755 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-03756 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-03757 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-03758 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-03759 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminCMSGetCmsHomeModules.request | 可选；按业务条件或页面状态决定 | 执行“首页模块”且字段适用时显示 | 具备 cms.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-03760 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-03761 | 主要内容 | code | 代码 | DISPLAY | string | TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最少4字符；最多10字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码加载失败时显示字段级占位或隐藏 |
| FLD-03762 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-03763 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-03764 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-03765 | 主要内容 | content | 正文 | DISPLAY | array<PublicPageBlockResource> | LIST | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文加载失败时显示字段级占位或隐藏 |
| FLD-03766 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-03767 | 主要内容 | effectiveAt | 生效时间 | DISPLAY | string | DATETIME_TEXT | adminCMSGetCmsHomeModules.response.data.CmsResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 生效时间加载失败时显示字段级占位或隐藏 |
| FLD-03768 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminCMSPutCmsHomeModules.header | 必填 | 执行“排序和配置”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-03769 | 表单输入 | items | 数据项 | INPUT | object | STRUCTURED_EDITOR | adminCMSPutCmsHomeModules.request | 必填 | 执行“排序和配置”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据项不符合要求 |
| FLD-03770 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminCMSPutCmsHomeModules.request | 必填 | 执行“排序和配置”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-03771 | 表单输入 | title | 标题 | INPUT | string | TEXT_INPUT | adminCMSPostCmsBanners.request | 必填 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题不符合要求 |
| FLD-03772 | 表单输入 | imageMediaId | image Media Id | INPUT | string | TEXT_INPUT | adminCMSPostCmsBanners.request | 必填 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | image Media Id不符合要求 |
| FLD-03773 | 表单输入 | targetUrl | target Url | INPUT | string | TEXT_INPUT | adminCMSPostCmsBanners.request | 可选；按业务条件或页面状态决定 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | target Url不符合要求 |
| FLD-03774 | 表单输入 | startAt | 开始时间 | INPUT | string | DATETIME_PICKER | adminCMSPostCmsBanners.request | 可选；按业务条件或页面状态决定 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间不符合要求 |
| FLD-03775 | 表单输入 | endAt | 结束时间 | INPUT | string | DATETIME_PICKER | adminCMSPostCmsBanners.request | 可选；按业务条件或页面状态决定 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间不符合要求 |
| FLD-03776 | 表单输入 | weight | 权重 | INPUT | integer | NUMBER_INPUT | adminCMSPostCmsBanners.request | 可选；按业务条件或页面状态决定 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 权重不符合要求 |
| FLD-03777 | 表单输入 | enabled | 是否启用 | INPUT | boolean | SWITCH | adminCMSPostCmsBanners.request | 可选；按业务条件或页面状态决定 | 执行“创建轮播”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 是否启用不符合要求 |
| FLD-03778 | 表单输入 | articleType | article Type | INPUT | string | TEXT_INPUT | adminCMSPostCmsArticles.request | 必填 | 执行“创建文章”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | article Type不符合要求 |
| FLD-03779 | 表单输入 | summary | 摘要 | INPUT | string | TEXT_INPUT | adminCMSPostCmsArticles.request | 可选；按业务条件或页面状态决定 | 执行“创建文章”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 摘要不符合要求 |
| FLD-03780 | 表单输入 | content | 正文 | INPUT | string | RICH_TEXT_OR_TEXTAREA | adminCMSPostCmsArticles.request | 必填 | 执行“创建文章”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文不符合要求 |
| FLD-03781 | 表单输入 | publishAt | publish At | INPUT | string | DATETIME_PICKER | adminCMSPostCmsArticles.request | 可选；按业务条件或页面状态决定 | 执行“创建文章”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | publish At不符合要求 |
| FLD-03782 | 路由与筛选 | code | 代码 | PATH_PARAM | string | OTP_INPUT | adminCMSPutCmsH5PagesByCode.path | 必填 | 执行“更新H5配置新版本”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码格式或范围不正确 |
| FLD-03783 | 表单输入 | seoMetadata | seo Metadata | INPUT | string | TEXT_INPUT | adminCMSPutCmsH5PagesByCode.request | 可选；按业务条件或页面状态决定 | 执行“更新H5配置新版本”且字段适用时显示 | 具备 cms.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | seo Metadata不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| DIRTY | 存在未保存变更 | 显示未保存标识和离开确认 | 保存/放弃 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止静默丢失 | 否 |
| PREVIEWING | CMS或模板预览中 | 显示目标端、版本和示例变量 | 返回编辑/提交审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 预览不得写入生产 | 否 |
| PENDING_APPROVAL | 敏感变更等待双人复核 | 锁定关键字段，展示审批进度 | 撤回（允许时）/查看审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 申请人不得自审 | 否 |
| PUBLISHED | 内容/版本已发布 | 显示渠道、范围、时间和产物哈希 | 查看/回滚申请 | 与当前状态、权限或服务端version冲突的所有写操作 | 不可修改已发布快照 | 是 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-CMS-001-01 | 首页模块 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/cms/home-modules；请求模型 AdminCMSGetCmsHomeModulesParameters；响应模型 AdminCMSGetCmsHomeModulesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-02 | 排序和配置 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 cms.write，且资源状态允许“排序和配置”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,items,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /admin-api/v1/cms/home-modules；请求模型 AdminCMSPutCmsHomeModulesRequest；响应模型 AdminCMSPutCmsHomeModulesResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“排序和配置成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-03 | 轮播 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/cms/banners；请求模型 AdminCMSGetCmsBannersParameters；响应模型 AdminCMSGetCmsBannersResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-04 | 创建轮播 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 cms.write，且资源状态允许“创建轮播”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,title,imageMediaId,targetUrl,startAt,endAt,weight,enabled；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/cms/banners；请求模型 AdminCMSPostCmsBannersRequest；响应模型 AdminCMSPostCmsBannersResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建轮播成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-05 | 公告帮助规则 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/cms/articles；请求模型 AdminCMSGetCmsArticlesParameters；响应模型 AdminCMSGetCmsArticlesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-06 | 创建文章 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 cms.write，且资源状态允许“创建文章”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,articleType,title,summary,content,status,publishAt；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/cms/articles；请求模型 AdminCMSPostCmsArticlesRequest；响应模型 AdminCMSPostCmsArticlesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建文章成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-07 | H5配置 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/cms/h5-pages；请求模型 AdminCMSGetCmsH5PagesParameters；响应模型 AdminCMSGetCmsH5PagesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CMS-001-08 | 更新H5配置新版本 | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 cms.write，且资源状态允许“更新H5配置新版本”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 code,X-Idempotency-Key,title,content,seoMetadata,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /admin-api/v1/cms/h5-pages/{code}；请求模型 AdminCMSPutCmsH5PagesByCodeRequest；响应模型 AdminCMSPutCmsH5PagesByCodeResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“更新H5配置新版本成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-CMS-001`
- API：`GET /admin-api/v1/cms/home-modules;PUT /admin-api/v1/cms/home-modules;GET /admin-api/v1/cms/banners;POST /admin-api/v1/cms/banners;GET /admin-api/v1/cms/articles;POST /admin-api/v1/cms/articles;GET /admin-api/v1/cms/h5-pages;PUT /admin-api/v1/cms/h5-pages/{code}`
- operationId：`adminCMSGetCmsHomeModules;adminCMSPutCmsHomeModules;adminCMSGetCmsBanners;adminCMSPostCmsBanners;adminCMSGetCmsArticles;adminCMSPostCmsArticles;adminCMSGetCmsH5Pages;adminCMSPutCmsH5PagesByCode`
- 配置组：`platform`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-CMS_001-HAPPY;TST-CMS_001-IDEMPOTENT`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | createdAt:desc | ID;代码;内容类型;标题;状态;正文;版本;生效时间 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 排序和配置;创建轮播;创建文章;更新H5配置新版本 | 概览;业务数据;状态历史;关联对象;操作审计 | 具备页面读取和 export.create 权限可异步导出当前筛选；禁止导出未展示或无权限字段 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
