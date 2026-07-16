# SHEET-MEDIA-001 · 媒体上传管理器

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ANDROID | 通用组件 | sheet://media-upload | SHEET | MOB-SHEET | R04 | 登录后 | NORMAL | READY |

**业务目标：** 创建上传会话、分片/直传进度、失败重试、完成确认、删除未绑定媒体；被发布、资料、工单等页面复用

**主要角色：** 已登录用户；公开能力仅限文档明确的H5页面

**入口：** 由拥有该动作的页面显式打开，携带不可变目标快照

**退出/返回：** 成功后向调用页回传结果并关闭；取消恢复触发控件焦点

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOB-SHEET | 标准底部面板/确认弹层 | 拖拽柄/标题\|说明\|字段/选项\|主次操作 | 影响说明、取消、错误就地反馈 | 不可逆动作不能点击遮罩直接确认；成功后回传结果 | 焦点限制在弹层；关闭后返回触发控件 | 大屏切换居中对话框 | 焦点、返回键、重复提交、取消测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-02146 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-02147 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | mediaPostMediaUploadSessions.header | 必填 | 执行“创建上传会话”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-02148 | 表单输入 | purpose | 用途 | INPUT | string | TEXT_INPUT | mediaPostMediaUploadSessions.request | 必填 | 执行“创建上传会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用途不符合要求 |
| FLD-02149 | 表单输入 | fileName | 文件名 | INPUT | string | TEXT_INPUT | mediaPostMediaUploadSessions.request | 必填 | 执行“创建上传会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 文件名不符合要求 |
| FLD-02150 | 表单输入 | contentType | 内容类型 | INPUT | string | TEXT_INPUT | mediaPostMediaUploadSessions.request | 必填 | 执行“创建上传会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型不符合要求 |
| FLD-02151 | 表单输入 | sizeBytes | 文件大小 | INPUT | integer | NUMBER_INPUT | mediaPostMediaUploadSessions.request | 必填 | 执行“创建上传会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 文件大小不符合要求 |
| FLD-02152 | 表单输入 | sha256 | SHA-256 | INPUT | string | TEXT_INPUT | mediaPostMediaUploadSessions.request | 必填 | 执行“创建上传会话”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | SHA-256不符合要求 |
| FLD-02153 | 主要内容 | id | ID | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-02154 | 主要内容 | purpose | 用途 | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 用途加载失败时显示字段级占位或隐藏 |
| FLD-02155 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-02156 | 主要内容 | sizeBytes | 文件大小 | DISPLAY | integer | NUMBER_TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 文件大小加载失败时显示字段级占位或隐藏 |
| FLD-02157 | 主要内容 | sha256 | SHA-256 | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符；正则:^[A-Fa-f0-9]{40,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | SHA-256加载失败时显示字段级占位或隐藏 |
| FLD-02158 | 主要内容 | uploadUrl | upload Url | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | upload Url加载失败时显示字段级占位或隐藏 |
| FLD-02159 | 主要内容 | readUrl | read Url | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | read Url加载失败时显示字段级占位或隐藏 |
| FLD-02160 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-02161 | 主要内容 | expiresAt | 过期时间 | DISPLAY | string | DATETIME_TEXT | mediaPostMediaUploadSessions.response.data.MediaResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 过期时间加载失败时显示字段级占位或隐藏 |
| FLD-02162 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | mediaPostMediaUploadSessionsByIdComplete.path | 必填 | 执行“完成上传”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-02163 | 表单输入 | etag | ETag | INPUT | string | TEXT_INPUT | mediaPostMediaUploadSessionsByIdComplete.request | 必填 | 执行“完成上传”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ETag不符合要求 |
| FLD-02164 | 表单输入 | parts | 分片信息 | INPUT | array<object> | MULTI_SELECT_OR_LIST | mediaPostMediaUploadSessionsByIdComplete.request | 可选；按业务条件或页面状态决定 | 执行“完成上传”且字段适用时显示 | 具备 登录 且资源状态允许 | 最多10000项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分片信息不符合要求 |
| FLD-02165 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | mediaDeleteMediaById.request | 必填 | 执行“删除未绑定媒体”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-02166 | 表单输入 | xIdempotencyKey | x Idempotency Key | INPUT | string | TEXT_INPUT | mediaDeleteMediaById.request | 必填 | 执行“删除未绑定媒体”且字段适用时显示 | 具备 登录 且资源状态允许 | 必填；最少16字符；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | x Idempotency Key不符合要求 |
| FLD-02167 | 主要内容 | resourceId | resource Id | DISPLAY | string | TEXT | mediaDeleteMediaById.response.data.CommandResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | resource Id加载失败时显示字段级占位或隐藏 |
| FLD-02168 | 主要内容 | businessNo | business No | DISPLAY | string | TEXT | mediaDeleteMediaById.response.data.CommandResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | business No加载失败时显示字段级占位或隐藏 |
| FLD-02169 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | mediaDeleteMediaById.response.data.CommandResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-02170 | 主要内容 | acceptedAt | accepted At | DISPLAY | string | DATETIME_TEXT | mediaDeleteMediaById.response.data.CommandResultResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | accepted At加载失败时显示字段级占位或隐藏 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| OPEN | 由页面业务状态机进入 | 按“OPEN”状态展示标准状态区 | 按页面动作矩阵 | 与当前状态、权限或服务端version冲突的所有写操作 | 重新查询服务端事实 | 否 |
| EDITING | 用户正在编辑表单 | 显示字段、帮助、脏状态和保存/提交入口 | 编辑/保存/提交/取消 | 与当前状态、权限或服务端version冲突的所有写操作 | 离开时执行脏表单保护 | 否 |
| SUBMITTING | 写请求已提交，结果未确定 | 锁定同资源写操作，保留输入，展示进度 | 查询结果/取消（业务允许时） | 与当前状态、权限或服务端version冲突的所有写操作 | 不得重复生成新业务意图 | 否 |
| SUCCESS | 操作成功且服务端已返回事实 | 展示明确结果并刷新关联数据 | 继续/返回/查看详情 | 与当前状态、权限或服务端version冲突的所有写操作 | 一次性敏感输入立即清除 | 是 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-V122-002 | 创建上传会话 | UPLOAD_OR_FILE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户选择合规文件并通过类型、大小、数量、分辨率或证书格式本地预检后触发 | 页面允许上传且用户具备对应写权限 | 文件未超限、网络可用、并发上传数未超过配置、剩余配额充足 | 校验 X-Idempotency-Key,purpose,fileName,contentType,sizeBytes,sha256；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 删除已选但未绑定媒体需要确认；上传证书/签名材料必须显示敏感风险和审批要求 | POST /api/v1/media/upload-sessions；请求模型 MediaPostMediaUploadSessionsRequest；响应模型 MediaPostMediaUploadSessionsResponse | X-Idempotency-Key + 服务端结果查询 | 逐文件展示排队、上传、校验、完成和失败状态；支持取消；页面退出前提示未完成任务 | 保存 mediaId/证书引用而非本地路径；更新缩略图、校验结果和表单脏状态；完成接口成功后才视为可提交 | 单文件失败不影响其他文件；展示稳定错误码和重试；校验失败立即删除临时引用；过期上传会话重新创建 | 仅重试失败分片或失败文件；完成接口幂等；删除操作不可自动重试 | 留在调用页面或关闭复用弹层并回传已完成媒体集合 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-V122-003 | 完成上传 | UPLOAD_OR_FILE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户选择合规文件并通过类型、大小、数量、分辨率或证书格式本地预检后触发 | 页面允许上传且用户具备对应写权限 | 文件未超限、网络可用、并发上传数未超过配置、剩余配额充足 | 校验 id,X-Idempotency-Key,etag,parts；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 删除已选但未绑定媒体需要确认；上传证书/签名材料必须显示敏感风险和审批要求 | POST /api/v1/media/upload-sessions/{id}/complete；请求模型 MediaPostMediaUploadSessionsByIdCompleteRequest；响应模型 MediaPostMediaUploadSessionsByIdCompleteResponse | X-Idempotency-Key + 服务端结果查询 | 逐文件展示排队、上传、校验、完成和失败状态；支持取消；页面退出前提示未完成任务 | 保存 mediaId/证书引用而非本地路径；更新缩略图、校验结果和表单脏状态；完成接口成功后才视为可提交 | 单文件失败不影响其他文件；展示稳定错误码和重试；校验失败立即删除临时引用；过期上传会话重新创建 | 仅重试失败分片或失败文件；完成接口幂等；删除操作不可自动重试 | 留在调用页面或关闭复用弹层并回传已完成媒体集合 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-V122-004 | 删除未绑定媒体 | UPLOAD_OR_FILE | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户选择合规文件并通过类型、大小、数量、分辨率或证书格式本地预检后触发 | 页面允许上传且用户具备对应写权限 | 文件未超限、网络可用、并发上传数未超过配置、剩余配额充足 | 校验 id,X-Idempotency-Key,xIdempotencyKey；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 删除已选但未绑定媒体需要确认；上传证书/签名材料必须显示敏感风险和审批要求 | DELETE /api/v1/media/{id}；请求模型 MediaDeleteMediaByIdParameters；响应模型 MediaDeleteMediaByIdResponse | X-Idempotency-Key + 服务端结果查询 | 逐文件展示排队、上传、校验、完成和失败状态；支持取消；页面退出前提示未完成任务 | 保存 mediaId/证书引用而非本地路径；更新缩略图、校验结果和表单脏状态；完成接口成功后才视为可提交 | 单文件失败不影响其他文件；展示稳定错误码和重试；校验失败立即删除临时引用；过期上传会话重新创建 | 仅重试失败分片或失败文件；完成接口幂等；删除操作不可自动重试 | 留在调用页面或关闭复用弹层并回传已完成媒体集合 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 由拥有该动作的页面显式打开，携带不可变目标快照 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 成功后向调用页回传结果并关闭；取消恢复触发控件焦点 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 禁止外部深链直接打开；必须由受信任调用页创建上下文 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：初始化按需加载；写操作后仅刷新受影响数据；禁止无差别全局缓存清空
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-MEDIA-001;REQ-PAGE-SPEC-001`
- API：`POST /api/v1/media/upload-sessions;POST /api/v1/media/upload-sessions/{id}/complete;DELETE /api/v1/media/{id}`
- operationId：`mediaPostMediaUploadSessions;mediaPostMediaUploadSessionsByIdComplete;mediaDeleteMediaById`
- 配置组：`storage;content`
- 关键配置：`storage.default_provider;storage.scope.public_media.provider;storage.scope.private_kyc.provider;storage.scope.private_chat.provider;storage.scope.audit_evidence.provider;storage.scope.apk_release.provider;storage.scope.backup.provider;storage.r2.account_id;content.limit.normal.online;content.limit.month.online;content.limit.quarter.online;content.limit.year.online;content.team_leader_per_account;content.limit.normal.pending;content.limit.normal.drafts;content.limit.normal.daily_submissions`
- 测试：`TST-V122-001;TST-V122-002;TST-V122-003`
- UI参考：`TOKENS_AND_TEMPLATE`

## 8. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图仅用于视觉参考。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
