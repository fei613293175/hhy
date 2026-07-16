# ADM-BUILD-004 · 构建任务列表

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | App构建 | /delivery/builds/jobs | PAGE | ADM-BUILD | R29 | app.build.manage | HIGH | READY |

**业务目标：** 任务状态、Commit、版本、环境、发起人、耗时和产物

**主要角色：** 具备 app.build.manage 的后台员工；写操作另需 app.build.manage

**入口：** 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入

**退出/返回：** 按面包屑返回；跨模块跳转必须保留来源上下文

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-BUILD | 构建与发布任务 | 环境/配置\|任务步骤\|日志\|产物校验\|发布/回滚\|审批 | commit SHA、签名指纹、SHA256、日志脱敏、环境隔离 | 不可从未验证产物晋级生产；生产签名仅引用密钥 | 步骤状态文本化 | 日志区可全屏 | 可复现构建、签名、晋级、回滚测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-04594 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-04595 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-04596 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-04597 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-04598 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-04599 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-04600 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminAppBuildProfileGetAppBuildProfiles.query | 可选；仅在对应操作/筛选时提交 | 执行“App构建Profile列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-04601 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-04602 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-04603 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-04604 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-04605 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-04606 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminAppBuildProfileGetAppBuildProfiles.request | 可选；按业务条件或页面状态决定 | 执行“App构建Profile列表”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-04607 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-04608 | 主要内容 | name | 名称 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 名称加载失败时显示字段级占位或隐藏 |
| FLD-04609 | 主要内容 | environment | 环境 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 环境加载失败时显示字段级占位或隐藏 |
| FLD-04610 | 主要内容 | applicationId | application Id | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | application Id加载失败时显示字段级占位或隐藏 |
| FLD-04611 | 主要内容 | apiBaseUrl | api Base Url | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | api Base Url加载失败时显示字段级占位或隐藏 |
| FLD-04612 | 主要内容 | webSocketUrl | web Socket Url | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | web Socket Url加载失败时显示字段级占位或隐藏 |
| FLD-04613 | 主要内容 | signingProfileId | signing Profile Id | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | signing Profile Id加载失败时显示字段级占位或隐藏 |
| FLD-04614 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-04615 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminAppBuildProfileGetAppBuildProfiles.response.data.BuildProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-04616 | 主要内容 | jobNo | 任务编号 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 任务编号加载失败时显示字段级占位或隐藏 |
| FLD-04617 | 主要内容 | profileId | 构建配置 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 构建配置加载失败时显示字段级占位或隐藏 |
| FLD-04618 | 主要内容 | gitRef | Git引用 | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | Git引用加载失败时显示字段级占位或隐藏 |
| FLD-04619 | 主要内容 | commitSha | 提交SHA | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | 提交SHA加载失败时显示字段级占位或隐藏 |
| FLD-04620 | 主要内容 | steps | 执行步骤 | DISPLAY | array<BuildStepResource> | LIST | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多100项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 执行步骤加载失败时显示字段级占位或隐藏 |
| FLD-04621 | 主要内容 | artifactId | 产物ID | DISPLAY | string | TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 产物ID加载失败时显示字段级占位或隐藏 |
| FLD-04622 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-04623 | 主要内容 | finishedAt | 完成时间 | DISPLAY | string | DATETIME_TEXT | adminAppBuildJobGetAppBuildJobs.response.data.BuildJobResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 完成时间加载失败时显示字段级占位或隐藏 |
| FLD-04624 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminAppBuildJobGetAppBuildJobsById.path | 必填 | 执行“构建任务详情”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-04625 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | adminAppBuildJobGetAppBuildJobsById.request | 必填 | 执行“构建任务详情”且字段适用时显示 | 具备 app.build.read 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-04626 | 主要内容 | jobId | job Id | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | job Id加载失败时显示字段级占位或隐藏 |
| FLD-04627 | 主要内容 | artifactType | artifact Type | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | artifact Type加载失败时显示字段级占位或隐藏 |
| FLD-04628 | 主要内容 | fileName | 文件名 | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 文件名加载失败时显示字段级占位或隐藏 |
| FLD-04629 | 主要内容 | sizeBytes | 文件大小 | DISPLAY | integer | NUMBER_TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 文件大小加载失败时显示字段级占位或隐藏 |
| FLD-04630 | 主要内容 | sha256 | SHA-256 | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | SHA-256加载失败时显示字段级占位或隐藏 |
| FLD-04631 | 主要内容 | signatureFingerprint | signature Fingerprint | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | signature Fingerprint加载失败时显示字段级占位或隐藏 |
| FLD-04632 | 主要内容 | downloadUrl | download Url | DISPLAY | string | TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | download Url加载失败时显示字段级占位或隐藏 |
| FLD-04633 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | adminAppBuildArtifactGetAppBuildArtifacts.response.data.BuildArtifactResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-04634 | 路由与筛选 | disposition | disposition | FILTER | string | SELECT | adminAppBuildArtifactGetAppBuildArtifactsByIdDownload.query | 可选；仅在对应操作/筛选时提交 | 执行“获取短期下载URL”时显示 | 用户具备权限且页面状态允许 | 枚举:INLINE/ATTACHMENT | NORMAL | 无需特殊掩码；仍遵守最小展示 | disposition格式或范围不正确 |
| FLD-04635 | 表单输入 | disposition | disposition | INPUT | string | SELECT | adminAppBuildArtifactGetAppBuildArtifactsByIdDownload.request | 可选；按业务条件或页面状态决定 | 执行“获取短期下载URL”且字段适用时显示 | 具备 app.build.download 且资源状态允许 | 枚举:INLINE/ATTACHMENT | NORMAL | 无需特殊掩码；仍遵守最小展示 | disposition不符合要求 |
| FLD-04636 | 主要内容 | keyAlias | key Alias | DISPLAY | string | TEXT | adminAppSigningGetAppBuildSigningProfiles.response.data.SigningProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | key Alias加载失败时显示字段级占位或隐藏 |
| FLD-04637 | 主要内容 | certificateFingerprint | certificate Fingerprint | DISPLAY | string | TEXT | adminAppSigningGetAppBuildSigningProfiles.response.data.SigningProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | certificate Fingerprint加载失败时显示字段级占位或隐藏 |
| FLD-04638 | 主要内容 | lastTestAt | 最后测试时间 | DISPLAY | string | DATETIME_TEXT | adminAppSigningGetAppBuildSigningProfiles.response.data.SigningProfileResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 最后测试时间加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| DRAFT | 存在未生效配置/规则/模板草稿 | 显示与当前版本差异 | 编辑/校验/提交审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得影响运行时 | 否 |
| QUEUED | 构建、导出或异步任务排队 | 显示队列位置和提交快照 | 取消（允许时）/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 同一快照避免重复任务 | 否 |
| RUNNING | 任务有效进行中 | 展示服务端进度、心跳和可见性 | 继续/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 本地计时不决定奖励 | 否 |
| SUCCEEDED | 异步任务成功 | 显示产物、校验和、过期时间 | 下载/发布/查看 | 与当前状态、权限或服务端version冲突的所有写操作 | 下载使用短时令牌 | 是 |
| FAILED | 业务操作明确失败 | 展示业务原因和可执行下一步 | 修改后重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 保留非敏感上下文 | 是 |
| PENDING_APPROVAL | 敏感变更等待双人复核 | 锁定关键字段，展示审批进度 | 撤回（允许时）/查看审批 | 与当前状态、权限或服务端version冲突的所有写操作 | 申请人不得自审 | 否 |
| PUBLISHED | 内容/版本已发布 | 显示渠道、范围、时间和产物哈希 | 查看/回滚申请 | 与当前状态、权限或服务端version冲突的所有写操作 | 不可修改已发布快照 | 是 |
| ROLLED_BACK | 版本已回滚 | 显示来源版本、目标版本和原因 | 查看历史 | 与当前状态、权限或服务端version冲突的所有写操作 | 回滚本身形成新记录 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-BUILD-004-01 | App构建Profile列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/profiles；请求模型 AdminAppBuildProfileGetAppBuildProfilesParameters；响应模型 AdminAppBuildProfileGetAppBuildProfilesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-02 | 构建任务列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/jobs；请求模型 AdminAppBuildJobGetAppBuildJobsParameters；响应模型 AdminAppBuildJobGetAppBuildJobsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-03 | 构建任务详情 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /admin-api/v1/app-build/jobs/{id}；请求模型 AdminAppBuildJobGetAppBuildJobsByIdParameters；响应模型 AdminAppBuildJobGetAppBuildJobsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-04 | 构建步骤日志 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 id,page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/jobs/{id}/logs；请求模型 AdminAppBuildJobGetAppBuildJobsByIdLogsParameters；响应模型 AdminAppBuildJobGetAppBuildJobsByIdLogsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-05 | 构建产物列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/app-build/artifacts；请求模型 AdminAppBuildArtifactGetAppBuildArtifactsParameters；响应模型 AdminAppBuildArtifactGetAppBuildArtifactsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-06 | 获取短期下载URL | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id,disposition | 不需要 | GET /admin-api/v1/app-build/artifacts/{id}/download；请求模型 AdminAppBuildArtifactGetAppBuildArtifactsByIdDownloadParameters；响应模型 AdminAppBuildArtifactGetAppBuildArtifactsByIdDownloadResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |
| ACT-ADM-BUILD-004-07 | 签名Profile元数据列表 | UPLOAD_OR_FILE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户选择合规文件并通过类型、大小、数量、分辨率或证书格式本地预检后触发 | 页面允许上传且用户具备对应写权限 | 文件未超限、网络可用、并发上传数未超过配置、剩余配额充足 | 校验 page,pageSize,cursor,status,keyword,sort | 删除已选但未绑定媒体需要确认；上传证书/签名材料必须显示敏感风险和审批要求 | GET /admin-api/v1/app-build/signing-profiles；请求模型 AdminAppSigningGetAppBuildSigningProfilesParameters；响应模型 AdminAppSigningGetAppBuildSigningProfilesResponse | 同资源单写请求锁；状态机服务端复核 | 逐文件展示排队、上传、校验、完成和失败状态；支持取消；页面退出前提示未完成任务 | 保存 mediaId/证书引用而非本地路径；更新缩略图、校验结果和表单脏状态；完成接口成功后才视为可提交 | 单文件失败不影响其他文件；展示稳定错误码和重试；校验失败立即删除临时引用；过期上传会话重新创建 | 仅重试失败分片或失败文件；完成接口幂等；删除操作不可自动重试 | 留在调用页面或关闭复用弹层并回传已完成媒体集合 | 记录敏感数据访问审计：操作人、目标、字段范围、目的、requestId 和结果 | 敏感字段按数据分级脱敏；密码、验证码、身份证、支付账号、秘密引用绝不进入埋点和普通日志 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从左侧菜单、数据驾驶舱待办、全局搜索或关联详情跳转进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 按面包屑返回；跨模块跳转必须保留来源上下文 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-BUILD-001;REQ-APK-001`
- API：`GET /admin-api/v1/app-build/profiles;GET /admin-api/v1/app-build/jobs;GET /admin-api/v1/app-build/jobs/{id};GET /admin-api/v1/app-build/jobs/{id}/logs;GET /admin-api/v1/app-build/artifacts;GET /admin-api/v1/app-build/artifacts/{id}/download;GET /admin-api/v1/app-build/signing-profiles`
- operationId：`adminAppBuildProfileGetAppBuildProfiles;adminAppBuildJobGetAppBuildJobs;adminAppBuildJobGetAppBuildJobsById;adminAppBuildJobGetAppBuildJobsByIdLogs;adminAppBuildArtifactGetAppBuildArtifacts;adminAppBuildArtifactGetAppBuildArtifactsByIdDownload;adminAppSigningGetAppBuildSigningProfiles`
- 配置组：`platform`
- 关键配置：`platform.brand.name;platform.brand.slogan;platform.brand.logo_media_id;platform.customer_service.name;platform.customer_service.contact`
- 测试：`TST-BUILD_001-HAPPY;TST-APK_001-HAPPY`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序;disposition | createdAt:desc | ID;名称;环境;application Id;api Base Url;web Socket Url;signing Profile Id;状态;版本;任务编号;构建配置;Git引用;提交SHA;执行步骤 | 无批量写操作 | 查看详情 | 配置;任务;日志;产物;签名;发布;审计 | 仅具备 export.create 权限可导出；异步任务、字段白名单、默认脱敏、用途必填、下载审计 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
