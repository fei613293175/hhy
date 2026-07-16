# SCR-ABOUT-001 · 关于与检查更新

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 设置 | /me/about | PAGE | MOB-SETTINGS | R06 | 是 | NORMAL | READY |

**业务目标：** 版本、协议、下载页

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 从上级页面、导航入口、通知或业务流程进入

**退出/返回：** 按导航栈返回；成功流程按动作规格进入结果或详情

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-SETTINGS | 设置与安全中心 | 账号摘要\|设置分组\|状态/风险\|操作入口 | 当前状态、影响说明、敏感操作二次验证 | 危险操作独立分组；会话和MFA变更实时更新 | 开关状态与风险说明可读 | 分组列表适配 | 权限、会话失效、MFA、注销测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-02121 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-02122 | 表单输入 | platform | platform | INPUT | string | SELECT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符；枚举:ANDROID | NORMAL | 无需特殊掩码；仍遵守最小展示 | platform不符合要求 |
| FLD-02123 | 表单输入 | versionCode | version Code | INPUT | integer | NUMBER_INPUT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Code不符合要求 |
| FLD-02124 | 表单输入 | versionName | version Name | INPUT | string | TEXT_INPUT | appReleasePostAppVersionCheck.request | 可选；按业务条件或页面状态决定 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | version Name不符合要求 |
| FLD-02125 | 表单输入 | channel | 渠道 | INPUT | string | TEXT_INPUT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 渠道不符合要求 |
| FLD-02126 | 表单输入 | environment | 环境 | INPUT | string | SELECT | appReleasePostAppVersionCheck.request | 必填 | 执行“按平台、版本号、渠道和环境返回可选/强制更新策略”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最多2000字符；枚举:DEV/TEST/STAGING/PROD | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境不符合要求 |
| FLD-02127 | 主要内容 | platform | platform | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | platform加载失败时显示字段级占位或隐藏 |
| FLD-02128 | 主要内容 | latestVersionCode | latest Version Code | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | latest Version Code加载失败时显示字段级占位或隐藏 |
| FLD-02129 | 主要内容 | latestVersionName | latest Version Name | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | latest Version Name加载失败时显示字段级占位或隐藏 |
| FLD-02130 | 主要内容 | updateType | update Type | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | update Type加载失败时显示字段级占位或隐藏 |
| FLD-02131 | 主要内容 | downloadUrl | download Url | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download Url加载失败时显示字段级占位或隐藏 |
| FLD-02132 | 主要内容 | sha256 | SHA-256 | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | SHA-256加载失败时显示字段级占位或隐藏 |
| FLD-02133 | 主要内容 | releaseNotes | release Notes | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | release Notes加载失败时显示字段级占位或隐藏 |
| FLD-02134 | 主要内容 | minSupportedVersionCode | min Supported Version Code | DISPLAY | string | TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | min Supported Version Code加载失败时显示字段级占位或隐藏 |
| FLD-02135 | 主要内容 | serverTime | 服务端时间 | DISPLAY | string | DATETIME_TEXT | appReleasePostAppVersionCheck.response.data.AppVersionPolicyResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 服务端时间加载失败时显示字段级占位或隐藏 |
| FLD-02136 | 路由与筛选 | code | 代码 | PATH_PARAM | string | OTP_INPUT | publicGetAgreementsByCode.path | 必填 | 执行“协议内容”时显示 | 用户具备权限且页面状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码格式或范围不正确 |
| FLD-02137 | 表单输入 | code | 代码 | INPUT | string | OTP_INPUT | publicGetAgreementsByCode.request | 必填 | 执行“协议内容”且字段适用时显示 | 具备 公开 且资源状态允许 | 必填；最少1字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码不符合要求 |
| FLD-02138 | 主要内容 | code | 代码 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最少4字符；最多10字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 代码加载失败时显示字段级占位或隐藏 |
| FLD-02139 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-02140 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-02141 | 主要内容 | content | 正文 | DISPLAY | array<PublicPageBlockResource> | LIST | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 正文加载失败时显示字段级占位或隐藏 |
| FLD-02142 | 主要内容 | seoMetadata | seo Metadata | DISPLAY | SeoMetadataResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | seo Metadata加载失败时显示字段级占位或隐藏 |
| FLD-02143 | 主要内容 | download | download | DISPLAY | DownloadInfoResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download加载失败时显示字段级占位或隐藏 |
| FLD-02144 | 主要内容 | trackingContext | 追踪上下文 | DISPLAY | TrackingContextResource | STRUCTURED_SECTION | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 追踪上下文加载失败时显示字段级占位或隐藏 |
| FLD-02145 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | publicGetAgreementsByCode.response.data.PublicPageResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ABOUT-001-01 | 按平台、版本号、渠道和环境返回可选/强制更新策略 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 公开，且资源状态允许“按平台、版本号、渠道和环境返回可选/强制更新策略”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 platform,versionCode,versionName,channel,environment | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /public-api/v1/app/version-check；请求模型 AppReleasePostAppVersionCheckRequest；响应模型 AppReleasePostAppVersionCheckResponse | 同资源单写请求锁；状态机服务端复核 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“按平台、版本号、渠道和环境返回可选/强制更新策略成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 写操作不自动重试；用户重新确认后使用最新 version 发起新请求 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ABOUT-001-02 | 协议内容 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 code；敏感值仅在内存中使用，不写日志、URL、埋点或本地明文缓存 | 不需要 | GET /public-api/v1/agreements/{code}；请求模型 PublicGetAgreementsByCodeParameters；响应模型 PublicGetAgreementsByCodeResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从上级页面、导航入口、通知或业务流程进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按导航栈返回；成功流程按动作规格进入结果或详情 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许已登记App Link/内部路由；登录前保存原目标；路由参数校验失败返回模块首页 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-APP-RELEASE-001`
- API：`POST /public-api/v1/app/version-check;GET /public-api/v1/agreements/{code}`
- operationId：`appReleasePostAppVersionCheck;publicGetAgreementsByCode`
- 配置组：`app_build;platform`
- 关键配置：`app.android.application_id;app.android.staging_application_id;app.android.display_name;app.android.signing.staging_profile;app.android.signing.production_profile;app.build.runner;app.build.allowed_ref_patterns;app.build.artifact_storage_scope;platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-APP_RELEASE_001-HAPPY`
- UI参考：`B12/P01-P08`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
