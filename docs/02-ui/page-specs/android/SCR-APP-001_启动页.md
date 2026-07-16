# SCR-APP-001 · 启动页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 启动 | /splash | PAGE | MOB-GATE | R02 | 否 | NORMAL | READY |

**业务目标：** 品牌展示、版本/维护/Token并行检查

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-GATE | 启动与全局门禁 | 品牌区\|状态说明\|主操作\|次操作\|诊断信息 | 安全区、主状态、恢复入口、版本/requestId | 并行检查结果必须按优先级合并；强制更新和维护高于登录导航 | 状态变化需读屏播报；主操作触控区≥48dp | 适配小屏和横屏，主操作保持可见 | 状态优先级单测、错误态截图、导航测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-00001 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-00002 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-00003 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-00004 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-00005 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-00006 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-00007 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | publicGetPlatformStatus.query | 可选；仅在对应操作/筛选时提交 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-00008 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-00009 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-00010 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-00011 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-00012 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-00013 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | publicGetPlatformStatus.request | 可选；按业务条件或页面状态决定 | 执行“启动维护状态、注册/发布/红包/提现总开关和服务端时间”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-00014 | 主要内容 | code | 代码 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最少4字符；最多10字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码加载失败时显示字段级占位或隐藏 |
| FLD-00015 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-00016 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-00017 | 主要内容 | content | 正文 | DISPLAY | array<PublicPageBlockResource> | LIST | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文加载失败时显示字段级占位或隐藏 |
| FLD-00018 | 主要内容 | seoMetadata | seo Metadata | DISPLAY | SeoMetadataResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | seo Metadata加载失败时显示字段级占位或隐藏 |
| FLD-00019 | 主要内容 | download | download | DISPLAY | DownloadInfoResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download加载失败时显示字段级占位或隐藏 |
| FLD-00020 | 主要内容 | trackingContext | 追踪上下文 | DISPLAY | TrackingContextResource | STRUCTURED_SECTION | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 追踪上下文加载失败时显示字段级占位或隐藏 |
| FLD-00021 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | publicGetPlatformStatus.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-00022 | 表单输入 | platform | platform | INPUT | string | SELECT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符；枚举:ANDROID | NORMAL | 无需特殊掩码；仍遵守最小展示 | platform不符合要求 |
| FLD-00023 | 表单输入 | versionCode | version Code | INPUT | integer | NUMBER_INPUT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Code不符合要求 |
| FLD-00024 | 表单输入 | versionName | version Name | INPUT | string | TEXT_INPUT | appReleasePostAppVersionCheck.request | 可选；按业务条件或页面状态决定 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Name不符合要求 |
| FLD-00025 | 表单输入 | channel | 渠道 | INPUT | string | TEXT_INPUT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道不符合要求 |
| FLD-00026 | 表单输入 | environment | 环境 | INPUT | string | SELECT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符；枚举:DEV/TEST/STAGING/PROD | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境不符合要求 |
| FLD-00027 | 主要内容 | platform | platform | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | platform加载失败时显示字段级占位或隐藏 |
| FLD-00028 | 主要内容 | latestVersionCode | latest Version Code | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | latest Version Code加载失败时显示字段级占位或隐藏 |
| FLD-00029 | 主要内容 | latestVersionName | latest Version Name | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | latest Version Name加载失败时显示字段级占位或隐藏 |
| FLD-00030 | 主要内容 | updateType | update Type | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | update Type加载失败时显示字段级占位或隐藏 |
| FLD-00031 | 主要内容 | downloadUrl | download Url | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download Url加载失败时显示字段级占位或隐藏 |
| FLD-00032 | 主要内容 | sha256 | SHA-256 | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | SHA-256加载失败时显示字段级占位或隐藏 |
| FLD-00033 | 主要内容 | releaseNotes | release Notes | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | release Notes加载失败时显示字段级占位或隐藏 |
| FLD-00034 | 主要内容 | minSupportedVersionCode | min Supported Version Code | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | min Supported Version Code加载失败时显示字段级占位或隐藏 |
| FLD-00035 | 主要内容 | serverTime | 服务端时间 | DISPLAY | string | DATETIME_TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 服务端时间加载失败时显示字段级占位或隐藏 |
| FLD-00036 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | authPostAuthRefresh.header | 必填 | 执行“刷新Token”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-00037 | 表单输入 | refreshToken | refresh Token | INPUT | string | TEXT_INPUT | authPostAuthRefresh.request | 必填 | 执行“刷新Token”且字段适用时显示 | 具备 RefreshToken 且资源状态允许 | 必填；最多2000字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | refresh Token不符合要求 |
| FLD-00038 | 表单输入 | deviceId | 设备ID | INPUT | string | TEXT_INPUT | authPostAuthRefresh.request | 必填 | 执行“刷新Token”且字段适用时显示 | 具备 RefreshToken 且资源状态允许 | 必填；最多64字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 设备ID不符合要求 |
| FLD-00039 | 主要内容 | accessToken | access Token | DISPLAY | string | TEXT | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | access Token加载失败时显示字段级占位或隐藏 |
| FLD-00040 | 主要内容 | refreshToken | refresh Token | DISPLAY | string | TEXT | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | CRITICAL | 禁止回显；仅瞬时输入；日志和埋点完全移除 | refresh Token加载失败时显示字段级占位或隐藏 |
| FLD-00041 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-00042 | 主要内容 | userId | 用户ID | DISPLAY | string | TEXT | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用户ID加载失败时显示字段级占位或隐藏 |
| FLD-00043 | 主要内容 | sessionId | session Id | DISPLAY | string | TEXT | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | session Id加载失败时显示字段级占位或隐藏 |
| FLD-00044 | 主要内容 | device | 设备信息 | DISPLAY | DeviceSummaryResource | STRUCTURED_SECTION | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 设备信息加载失败时显示字段级占位或隐藏 |
| FLD-00045 | 主要内容 | capabilities | capabilities | DISPLAY | array<string> | LIST | authPostAuthRefresh.response.data.AuthSessionResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | capabilities加载失败时显示字段级占位或隐藏 |
| FLD-00046 | 路由与筛选 | platform | platform | FILTER | string | SELECT | appReleaseGetAppVersionCheck.query | 必填 | 执行“登录后版本检查兼容接口；新客户端优先使用公开版本检查”时显示 | 用户具备权限且页面状态允许 | 必填；枚举:ANDROID | NORMAL | 无需特殊掩码；仍遵守最小展示 | platform格式或范围不正确 |
| FLD-00047 | 路由与筛选 | versionCode | version Code | FILTER | integer | NUMBER_INPUT | appReleaseGetAppVersionCheck.query | 必填 | 执行“登录后版本检查兼容接口；新客户端优先使用公开版本检查”时显示 | 用户具备权限且页面状态允许 | 必填；≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Code格式或范围不正确 |
| FLD-00048 | 路由与筛选 | channel | 渠道 | FILTER | string | FILTER_INPUT | appReleaseGetAppVersionCheck.query | 必填 | 执行“登录后版本检查兼容接口；新客户端优先使用公开版本检查”时显示 | 用户具备权限且页面状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道格式或范围不正确 |
| FLD-00049 | 路由与筛选 | environment | 环境 | FILTER | string | SELECT | appReleaseGetAppVersionCheck.query | 必填 | 执行“登录后版本检查兼容接口；新客户端优先使用公开版本检查”时显示 | 用户具备权限且页面状态允许 | 必填；枚举:DEV/TEST/STAGING/PROD | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境格式或范围不正确 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| INITIAL | 页面首次创建，尚未发起请求 | 显示稳定布局占位，禁止空白闪烁 | 发起初始化 | 与当前状态、权限或服务端version冲突的所有写操作 | 自动进入下一状态 | 否 |
| CHECKING | 正在执行版本、维护、资格或安全检查 | 显示检查进度和当前步骤，禁止提前导航 | 取消/重试（允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 超时后进入可恢复错误 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| BLOCKED | 系统维护、强制更新或账号限制阻断 | 展示阻断原因和唯一允许动作 | 更新/重试/联系客服 | 与当前状态、权限或服务端version冲突的所有写操作 | 阻断优先级高于普通导航 | 是 |
| RETRYABLE_ERROR | 网络、限流或临时依赖故障 | 说明可恢复原因和最早重试时间 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得丢失安全可保留输入 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-APP-001-01 | 启动维护状态、注册/发布/红包/提现总开关和服务端时间 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /public-api/v1/platform/status；请求模型 PublicGetPlatformStatusParameters；响应模型 PublicGetPlatformStatusResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-APP-001-02 | 按平台、版本号、渠道和环境返回可选/强制更新策略 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 公开，且资源状态允许“按平台、版本号、渠道和环境返回可选/强制更新策略”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 platform,versionCode,versionName,channel,environment | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /public-api/v1/app/version-check；请求模型 AppReleasePostAppVersionCheckRequest；响应模型 AppReleasePostAppVersionCheckResponse | 同资源单写请求锁；状态机服务端复核 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“按平台、版本号、渠道和环境返回可选/强制更新策略成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 写操作不自动重试；用户重新确认后使用最新 version 发起新请求 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-APP-001-03 | 刷新Token | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 X-Idempotency-Key,refreshToken,deviceId；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | POST /api/v1/auth/refresh；请求模型 AuthPostAuthRefreshRequest；响应模型 AuthPostAuthRefreshResponse | X-Idempotency-Key + 服务端结果查询 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-V122-012 | 登录后版本检查兼容接口；新客户端优先使用公开版本检查 | AUTH_SECURITY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式提交且本地校验、安全挑战或会话升级条件已满足 | 仅在当前认证状态允许时显示；已完成步骤不可重复展示 | 必填字段有效、验证码/挑战未过期、未触发倒计时或锁定 | 校验 page,pageSize,cursor,status,keyword,sort,platform,versionCode,channel,environment | 修改密码、解绑MFA、退出全部会话等高风险操作需要二次确认；登录和普通验证不需要 | GET /api/v1/app/version-check；请求模型 AppReleaseGetAppVersionCheckParameters；响应模型 AppReleaseGetAppVersionCheckResponse | 同资源单写请求锁；状态机服务端复核 | 锁定提交按钮，保留非敏感输入；密码、验证码和密钥不得写入日志、埋点或持久化 | 更新认证会话或安全状态；清除一次性挑战和敏感输入；按来源跳转；记录安全审计但不记录秘密 | 字段错误就地提示；挑战过期要求重建；限流显示剩余时间；账号受限跳转账号受限页；服务端错误不清空可安全保留的非敏感字段 | 仅在获得新挑战/验证码或用户再次确认后重试；幂等键不得跨不同提交内容复用 | 按认证流程图跳转到首页、MFA、账号受限页或原始目标页 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-AUTH-001;REQ-APP-RELEASE-001;REQ-APK-001;REQ-PAGE-SPEC-001`
- API：`GET /public-api/v1/platform/status;POST /public-api/v1/app/version-check;POST /api/v1/auth/refresh;GET /api/v1/app/version-check`
- operationId：`publicGetPlatformStatus;appReleasePostAppVersionCheck;authPostAuthRefresh;appReleaseGetAppVersionCheck`
- 配置组：`platform;app_build;auth`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact;app.android.application_id;app.android.staging_application_id;app.android.display_name;app.android.signing.staging_profile;app.android.signing.production_profile;app.build.runner;app.build.allowed_ref_patterns;app.build.artifact_storage_scope;auth.default_login_method;auth.security_challenge.provider;auth.security_challenge.mode;auth.password.min_length;auth.password.max_length;auth.password.max_failures;auth.password.lock_seconds;auth.invite.app_required`
- 测试：`TST-AUTH_001-HAPPY;TST-APP_RELEASE_001-HAPPY;TST-APK_001-HAPPY;TST-AUTH_001-IDEMPOTENT;TST-APP_RELEASE_001-IDEMPOTENT;TST-APK_001-IDEMPOTENT;TST-V122-011`
- UI参考：`B01/P01/P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
