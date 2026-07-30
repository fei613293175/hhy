# ADM-BUILD-001 · 构建中心首页

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | App构建 | /delivery/builds | PAGE | ADM-DASHBOARD | R29 | app.build.manage | HIGH | READY |

**业务目标：** 官方App构建概览、队列、最近结果、失败告警和快捷创建

**主要角色：** 具备 app.build.manage 的后台员工；写操作另需 app.build.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-DASHBOARD | 数据驾驶舱 | 全局时间筛选\|指标卡\|趋势\|分布\|待办\|异常 | 指标口径版本、数据更新时间、跳转目标 | 模块独立失败；口径和时间范围显式展示 | 图表提供表格替代 | ≥1280px多列，窄屏单列 | 指标口径、模块容错、权限裁剪测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04450 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04451 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04452 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04453 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-04454 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04455 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04456 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04457 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04458 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04459 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-04460 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04461 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04462 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04463 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04464 | 主要内容 | name | 名称 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称加载失败时显示字段级占位或隐藏 |
| FLD-04465 | 主要内容 | environment | 环境 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境加载失败时显示字段级占位或隐藏 |
| FLD-04466 | 主要内容 | applicationId | application Id | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | application Id加载失败时显示字段级占位或隐藏 |
| FLD-04467 | 主要内容 | apiBaseUrl | api Base Url | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | api Base Url加载失败时显示字段级占位或隐藏 |
| FLD-04468 | 主要内容 | webSocketUrl | web Socket Url | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | web Socket Url加载失败时显示字段级占位或隐藏 |
| FLD-04469 | 主要内容 | signingProfileId | signing Profile Id | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | signing Profile Id加载失败时显示字段级占位或隐藏 |
| FLD-04470 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04471 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04472 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.header | 必填 | 执行“创建构建Profile”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-04473 | 表单输入 | name | 名称 | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 必填 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称不符合要求 |
| FLD-04474 | 表单输入 | environment | 环境 | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 必填 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境不符合要求 |
| FLD-04475 | 表单输入 | applicationIdSuffix | application Id Suffix | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | application Id Suffix不符合要求 |
| FLD-04476 | 表单输入 | apiBaseUrl | api Base Url | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 必填 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | api Base Url不符合要求 |
| FLD-04477 | 表单输入 | webSocketUrl | web Socket Url | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 必填 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | web Socket Url不符合要求 |
| FLD-04478 | 表单输入 | featureFlags | 功能开关 | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 功能开关不符合要求 |
| FLD-04479 | 表单输入 | signingProfileId | signing Profile Id | INPUT | string | TEXT_INPUT | adminAppBuildProfilePostAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“创建构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | signing Profile Id不符合要求 |
| FLD-04480 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminAppBuildProfilePutAppBuildProfilesById.path | 必填 | 执行“修改构建Profile”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04481 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminAppBuildProfilePutAppBuildProfilesById.request | 必填 | 执行“修改构建Profile”且字段适用时显示 | 具备 app.build.write 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-04482 | 主要内容 | jobNo | 任务编号 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 任务编号加载失败时显示字段级占位或隐藏 |
| FLD-04483 | 主要内容 | profileId | 构建配置 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 构建配置加载失败时显示字段级占位或隐藏 |
| FLD-04484 | 主要内容 | gitRef | Git引用 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | Git引用加载失败时显示字段级占位或隐藏 |
| FLD-04485 | 主要内容 | commitSha | 提交SHA | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | 提交SHA加载失败时显示字段级占位或隐藏 |
| FLD-04486 | 主要内容 | steps | 执行步骤 | DISPLAY | array<BuildStepResource> | LIST | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 执行步骤加载失败时显示字段级占位或隐藏 |
| FLD-04487 | 主要内容 | artifactId | 产物ID | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 产物ID加载失败时显示字段级占位或隐藏 |
| FLD-04488 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04489 | 主要内容 | finishedAt | 完成时间 | DISPLAY | string | DATETIME_TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 完成时间加载失败时显示字段级占位或隐藏 |
| FLD-04490 | 表单输入 | profileId | 构建配置 | INPUT | string | TEXT_INPUT | adminAppBuildJobPostAppBuildJobs.request | 必填 | 执行“基于批准Git Ref创建官方App构建任务”且字段适用时显示 | 具备 app.build.run 且资源状态允许 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 构建配置不符合要求 |
| FLD-04491 | 表单输入 | gitRef | Git引用 | INPUT | string | TEXT_INPUT | adminAppBuildJobPostAppBuildJobs.request | 必填 | 执行“基于批准Git Ref创建官方App构建任务”且字段适用时显示 | 具备 app.build.run 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | Git引用不符合要求 |
| FLD-04492 | 表单输入 | commitSha | 提交SHA | INPUT | string | TEXT_INPUT | adminAppBuildJobPostAppBuildJobs.request | 必填 | 执行“基于批准Git Ref创建官方App构建任务”且字段适用时显示 | 具备 app.build.run 且资源状态允许 | 必填；最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | 提交SHA不符合要求 |
| FLD-04493 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminAppBuildJobPostAppBuildJobs.request | 可选；按业务条件或页面状态决定 | 执行“基于批准Git Ref创建官方App构建任务”且字段适用时显示 | 具备 app.build.run 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-04494 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | adminAppBuildJobGetAppBuildJobsById.request | 必填 | 执行“构建任务详情”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| PARTIAL_CONTENT | 部分模块成功、部分模块失败 | 保留成功模块，失败模块显示局部重试 | 重试失败模块 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得将局部失败升级为全页失败 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-BUILD-001-01 | App构建Profile列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/profiles；请求模型 AdminAppBuildProfileGetAppBuildProfilesParameters；响应模型 AdminAppBuildProfileGetAppBuildProfilesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-02 | 创建构建Profile | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 app.build.write，且资源状态允许“创建构建Profile”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,name,environment,applicationIdSuffix,apiBaseUrl,webSocketUrl,featureFlags,signingProfileId；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/app-build/profiles；请求模型 AdminAppBuildProfilePostAppBuildProfilesRequest；响应模型 AdminAppBuildProfilePostAppBuildProfilesResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“创建构建Profile成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-03 | 修改构建Profile | UPDATE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 app.build.write，且资源状态允许“修改构建Profile”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,name,apiBaseUrl,webSocketUrl,featureFlags,signingProfileId,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | PUT /admin-api/v1/app-build/profiles/{id}；请求模型 AdminAppBuildProfilePutAppBuildProfilesByIdRequest；响应模型 AdminAppBuildProfilePutAppBuildProfilesByIdResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“修改构建Profile成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-04 | 构建任务列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/jobs；请求模型 AdminAppBuildJobGetAppBuildJobsParameters；响应模型 AdminAppBuildJobGetAppBuildJobsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-05 | 基于批准Git Ref创建官方App构建任务 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 app.build.run，且资源状态允许“基于批准Git Ref创建官方App构建任务”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 X-Idempotency-Key,profileId,gitRef,commitSha,reason；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/app-build/jobs；请求模型 AdminAppBuildJobPostAppBuildJobsRequest；响应模型 AdminAppBuildJobPostAppBuildJobsResponse | X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 使用响应中的资源和 version 更新页面；清除已提交脏状态；展示“基于批准Git Ref创建官方App构建任务成功”；刷新受影响列表、详情、余额或配置版本 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-06 | 构建任务详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /admin-api/v1/app-build/jobs/{id}；请求模型 AdminAppBuildJobGetAppBuildJobsByIdParameters；响应模型 AdminAppBuildJobGetAppBuildJobsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-07 | 构建步骤日志 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 id,page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/jobs/{id}/logs；请求模型 AdminAppBuildJobGetAppBuildJobsByIdLogsParameters；响应模型 AdminAppBuildJobGetAppBuildJobsByIdLogsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-001-08 | 取消可取消的构建任务 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 app.build.run，且资源状态允许“取消可取消的构建任务”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,reason,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/app-build/jobs/{id}/cancel；请求模型 AdminAppBuildJobPostAppBuildJobsByIdCancelRequest；响应模型 AdminAppBuildJobPostAppBuildJobsByIdCancelResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 按状态机更新资源并移除或禁用不再可用的操作；展示“取消可取消的构建任务成功”；保留操作记录入口 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：模块独立加载和刷新；局部失败不清空成功模块；展示数据更新时间
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-BUILD-001;REQ-APK-001`
- API：`GET /admin-api/v1/app-build/profiles;POST /admin-api/v1/app-build/profiles;PUT /admin-api/v1/app-build/profiles/{id};GET /admin-api/v1/app-build/jobs;POST /admin-api/v1/app-build/jobs;GET /admin-api/v1/app-build/jobs/{id};GET /admin-api/v1/app-build/jobs/{id}/logs;POST /admin-api/v1/app-build/jobs/{id}/cancel`
- operationId：`adminAppBuildProfileGetAppBuildProfiles;adminAppBuildProfilePostAppBuildProfiles;adminAppBuildProfilePutAppBuildProfilesById;adminAppBuildJobGetAppBuildJobs;adminAppBuildJobPostAppBuildJobs;adminAppBuildJobGetAppBuildJobsById;adminAppBuildJobGetAppBuildJobsByIdLogs;adminAppBuildJobPostAppBuildJobsByIdCancel`
- 配置组：`platform`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-BUILD_001-HAPPY;TST-APK_001-HAPPY;TST-BUILD_001-IDEMPOTENT;TST-APK_001-IDEMPOTENT`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序 | createdAt:desc | ID;名称;环境;application Id;api Base Url;web Socket Url;signing Profile Id;状态;版本;任务编号;构建配置;Git引用;提交SHA;执行步骤 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 创建构建Profile;修改构建Profile;基于批准Git Ref创建官方App构建任务;取消可取消的构建任务 | 配置;任务;日志;产物;签名;发布;审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
