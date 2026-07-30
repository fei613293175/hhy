# ADM-CONTENT-002 · 内容详情

> 本文件由 V1.2.2 权威目录生成，是该页面的施工规格。实现不得仅依据效果图、页面摘要或个人经验增加字段、按钮、状态和接口。

## 1. 页面合同

| 平台 | 模块 | 路由 | 页面形态 | 模板 | 版本 | 访问规则 | 数据分级 | DoR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN | 内容 | /contents/:id | PAGE | ADM-DETAIL | R06 | content.read | NORMAL | READY |

**业务目标：** 版本、媒体、链接、数据

**主要角色：** 具备 content.read 的后台员工；写操作另需 content.manage

**入口：** 从对应列表行、待办、审计记录或直接授权链接进入

**退出/返回：** 返回来源列表并保持筛选、排序、页码和滚动位置

## 2. 页面模板与区域

| 模板ID | 模板名称 | 页面结构 | 必备区域 | 交互规则 | 无障碍 | 响应式 | 证据要求 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-DETAIL | 标准详情与时间线 | 摘要头\|状态/主操作\|Tab详情\|时间线\|关联对象\|审计 | 版本、敏感字段访问、按钮条件、状态历史 | 所有写操作使用最新version；高敏原文临时授权并审计 | Tab和时间线语义化 | 双栏转单栏 | 状态按钮矩阵、敏感访问、冲突、审计测试 |

## 3. 字段施工表

| 字段ID | 区域 | 字段键 | 显示名称 | 字段角色 | 语义类型 | 控件类型 | 数据来源 | 必填条件 | 显示条件 | 可编辑条件 | 校验规则 | 敏感级别 | 脱敏规则 | 错误提示 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FLD-02630 | 页面头部 | pageTitle | 页面标题 | UI_META | ui | TEXT | LOCAL_UI | 必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页面标题不符合页面规格 |
| FLD-02631 | 状态与审计 | resourceVersion | 资源版本 | UI_META | ui | VERSION_TEXT | LOCAL_UI | 写操作前必需 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 必须来自最近一次服务端成功响应 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 资源版本不符合页面规格 |
| FLD-02632 | 状态与审计 | statusTimeline | 状态时间线 | UI_META | ui | TIMELINE | LOCAL_UI | 存在状态变更时显示 | 按页面状态显示 | 本地UI字段不可写入业务事实 | 按页面模板 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态时间线不符合页面规格 |
| FLD-02633 | 路由与筛选 | page | 页码 | FILTER | integer | NUMBER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码格式或范围不正确 |
| FLD-02634 | 路由与筛选 | pageSize | 每页数量 | FILTER | integer | NUMBER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量格式或范围不正确 |
| FLD-02635 | 路由与筛选 | cursor | 游标 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标格式或范围不正确 |
| FLD-02636 | 路由与筛选 | status | 状态 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态格式或范围不正确 |
| FLD-02637 | 路由与筛选 | keyword | 关键词 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词格式或范围不正确 |
| FLD-02638 | 路由与筛选 | sort | 排序 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序格式或范围不正确 |
| FLD-02639 | 路由与筛选 | contentType | 内容类型 | FILTER | string | SELECT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型格式或范围不正确 |
| FLD-02640 | 路由与筛选 | categoryCode | 分类 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类格式或范围不正确 |
| FLD-02641 | 路由与筛选 | regionCode | 地区 | FILTER | string | FILTER_INPUT | adminContentGetContents.query | 可选；仅在对应操作/筛选时提交 | 执行“内容列表”时显示 | 用户具备权限且页面状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区格式或范围不正确 |
| FLD-02642 | 表单输入 | page | 页码 | INPUT | integer | NUMBER_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | ≥1 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 页码不符合要求 |
| FLD-02643 | 表单输入 | pageSize | 每页数量 | INPUT | integer | NUMBER_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | ≥1；≤100 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 每页数量不符合要求 |
| FLD-02644 | 表单输入 | cursor | 游标 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多256字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 游标不符合要求 |
| FLD-02645 | 表单输入 | status | 状态 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态不符合要求 |
| FLD-02646 | 表单输入 | keyword | 关键词 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多100字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 关键词不符合要求 |
| FLD-02647 | 表单输入 | sort | 排序 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 排序不符合要求 |
| FLD-02648 | 表单输入 | contentType | 内容类型 | INPUT | string | SELECT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 枚举:PROJECT/APP/GROUP_CHAT/TEAM_LEADER | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型不符合要求 |
| FLD-02649 | 表单输入 | categoryCode | 分类 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类不符合要求 |
| FLD-02650 | 表单输入 | regionCode | 地区 | INPUT | string | TEXT_INPUT | adminContentGetContents.request | 可选；按业务条件或页面状态决定 | 执行“内容列表”且字段适用时显示 | 具备 content.read 且资源状态允许 | 最多32字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区不符合要求 |
| FLD-02651 | 主要内容 | id | ID | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多64字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID加载失败时显示字段级占位或隐藏 |
| FLD-02652 | 主要内容 | contentType | 内容类型 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 内容类型加载失败时显示字段级占位或隐藏 |
| FLD-02653 | 主要内容 | title | 标题 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标题加载失败时显示字段级占位或隐藏 |
| FLD-02654 | 主要内容 | summary | 摘要 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 摘要加载失败时显示字段级占位或隐藏 |
| FLD-02655 | 主要内容 | description | 详细说明 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 详细说明加载失败时显示字段级占位或隐藏 |
| FLD-02656 | 主要内容 | categoryCode | 分类 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 分类加载失败时显示字段级占位或隐藏 |
| FLD-02657 | 主要内容 | regionCode | 地区 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 地区加载失败时显示字段级占位或隐藏 |
| FLD-02658 | 主要内容 | media | 媒体 | DISPLAY | array<MediaItemResource> | MEDIA_GALLERY | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多50项 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 媒体加载失败时显示字段级占位或隐藏 |
| FLD-02659 | 主要内容 | publisher | 发布者 | DISPLAY | PublisherSummaryResource | STRUCTURED_SECTION | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 发布者加载失败时显示字段级占位或隐藏 |
| FLD-02660 | 主要内容 | contactsMasked | 脱敏联系方式 | DISPLAY | array<ContactChannelSummaryResource> | LIST | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多20项 | HIGH | 按数据分级展示部分掩码；导出执行更严格脱敏 | 脱敏联系方式加载失败时显示字段级占位或隐藏 |
| FLD-02661 | 主要内容 | status | 状态 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 状态加载失败时显示字段级占位或隐藏 |
| FLD-02662 | 主要内容 | reviewStatus | 审核状态 | DISPLAY | string | TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 审核状态加载失败时显示字段级占位或隐藏 |
| FLD-02663 | 主要内容 | statistics | 统计 | DISPLAY | ContentStatisticsResource | STRUCTURED_SECTION | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 统计加载失败时显示字段级占位或隐藏 |
| FLD-02664 | 主要内容 | createdAt | 创建时间 | DISPLAY | string | DATETIME_TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 创建时间加载失败时显示字段级占位或隐藏 |
| FLD-02665 | 主要内容 | updatedAt | 更新时间 | DISPLAY | string | DATETIME_TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 更新时间加载失败时显示字段级占位或隐藏 |
| FLD-02666 | 主要内容 | version | 版本 | DISPLAY | integer | NUMBER_TEXT | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 版本加载失败时显示字段级占位或隐藏 |
| FLD-02667 | 主要内容 | attributes | 扩展属性 | DISPLAY | JsonObject | STRUCTURED_SECTION | adminContentGetContents.response.data.ContentResource | 服务端返回时展示 | 有值且当前角色拥有字段访问权限 | 只读；通过明确写操作更新 | 按服务端Schema校验；前端不得放宽 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 扩展属性加载失败时显示字段级占位或隐藏 |
| FLD-02668 | 路由与筛选 | id | ID | PATH_PARAM | string | TEXT_INPUT | adminContentGetContentsById.path | 必填 | 执行“内容详情与版本”时显示 | 用户具备权限且页面状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID格式或范围不正确 |
| FLD-02669 | 表单输入 | id | ID | INPUT | string | TEXT_INPUT | adminContentGetContentsById.request | 必填 | 执行“内容详情与版本”且字段适用时显示 | 具备 content.read 且资源状态允许 | 必填；正则:^[A-Za-z0-9_-]{1,64}$ | NORMAL | 无需特殊掩码；仍遵守最小展示 | ID不符合要求 |
| FLD-02670 | 请求头 | X-Idempotency-Key | X-Idempotency-Key | REQUEST_HEADER | string | TEXT_INPUT | adminContentPostContentsByIdOnline.header | 必填 | 执行“平台上架”时显示 | 用户具备权限且页面状态允许 | 必填；最多128字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | X-Idempotency-Key格式或范围不正确 |
| FLD-02671 | 表单输入 | expectedVersion | 数据版本 | INPUT | integer | NUMBER_INPUT | adminContentPostContentsByIdOnline.request | 必填 | 执行“平台上架”且字段适用时显示 | 具备 content.manage 且资源状态允许 | 必填；格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 数据版本不符合要求 |
| FLD-02672 | 表单输入 | reason | 原因 | INPUT | string | TEXT_INPUT | adminContentPostContentsByIdOnline.request | 可选；按业务条件或页面状态决定 | 执行“平台上架”且字段适用时显示 | 具备 content.manage 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 原因不符合要求 |
| FLD-02673 | 表单输入 | enabled | 是否启用 | INPUT | boolean | SWITCH | adminContentPostContentsByIdRecommend.request | 必填 | 执行“运营推荐”且字段适用时显示 | 具备 content.recommend 且资源状态允许 | 必填 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 是否启用不符合要求 |
| FLD-02674 | 表单输入 | weight | 权重 | INPUT | integer | NUMBER_INPUT | adminContentPostContentsByIdRecommend.request | 可选；按业务条件或页面状态决定 | 执行“运营推荐”且字段适用时显示 | 具备 content.recommend 且资源状态允许 | 格式:int64 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 权重不符合要求 |
| FLD-02675 | 表单输入 | startAt | 开始时间 | INPUT | string | DATETIME_PICKER | adminContentPostContentsByIdRecommend.request | 可选；按业务条件或页面状态决定 | 执行“运营推荐”且字段适用时显示 | 具备 content.recommend 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 开始时间不符合要求 |
| FLD-02676 | 表单输入 | endAt | 结束时间 | INPUT | string | DATETIME_PICKER | adminContentPostContentsByIdRecommend.request | 可选；按业务条件或页面状态决定 | 执行“运营推荐”且字段适用时显示 | 具备 content.recommend 且资源状态允许 | 格式:date-time | NORMAL | 无需特殊掩码；仍遵守最小展示 | 结束时间不符合要求 |
| FLD-02677 | 表单输入 | label | 标签 | INPUT | string | TEXT_INPUT | adminContentPostContentsByIdOfficialMark.request | 可选；按业务条件或页面状态决定 | 执行“官方标识”且字段适用时显示 | 具备 content.official 且资源状态允许 | 最多2000字符 | NORMAL | 无需特殊掩码；仍遵守最小展示 | 标签不符合要求 |

## 4. 页面状态与恢复

| 状态码 | 进入条件 | 展示行为 | 允许操作 | 禁用操作 | 恢复策略 | 是否终态 |
| --- | --- | --- | --- | --- | --- | --- |
| LOADING | 首屏数据请求进行中 | 显示与最终布局一致的骨架屏 | 返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 失败后显示重试 | 否 |
| CONTENT | 核心数据完整且可交互 | 展示内容并按权限/状态机计算操作入口 | 页面定义的读写动作 | 与当前状态、权限或服务端version冲突的所有写操作 | 刷新或执行操作 | 否 |
| STALE | 后台详情版本已过期 | 保留只读快照并提示刷新 | 重新加载 | 与当前状态、权限或服务端version冲突的所有写操作 | 不得覆盖服务端新版本 | 否 |
| NOT_FOUND | 资源不存在、已删除或不再可访问 | 显示资源状态，移除无效入口 | 返回来源列表 | 与当前状态、权限或服务端version冲突的所有写操作 | 来源列表同步更新 | 是 |
| NO_PERMISSION | 后台角色或数据范围不足 | 隐藏敏感内容并记录访问拒绝 | 返回/申请权限 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止仅靠前端绕过 | 是 |
| CONFLICT | expectedVersion或状态机冲突 | 展示数据已变化和差异摘要 | 重新加载/放弃本地变更 | 与当前状态、权限或服务端version冲突的所有写操作 | 禁止盲目覆盖 | 否 |
| ERROR | 请求失败且无法展示可靠内容 | 展示稳定错误码、requestId、重试和返回 | 重试/返回 | 与当前状态、权限或服务端version冲突的所有写操作 | 按错误码恢复 | 否 |
| OFFLINE | 网络不可用 | 只读页面可展示标记为缓存的数据；写操作禁用 | 重试网络 | 与当前状态、权限或服务端version冲突的所有写操作 | 恢复网络后主动刷新 | 否 |

## 5. 页面动作

| 动作ID | 动作 | 动作类型 | 入口组件 | 触发条件 | 显示条件 | 可用条件 | 前置校验 | 二次确认 | 请求映射 | 并发控制 | 加载表现 | 成功状态 | 失败状态 | 重试策略 | 成功后导航 | 审计要求 | 敏感处理 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-ADM-CONTENT-002-01 | 内容列表 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort,contentType,categoryCode,regionCode | 不需要 | GET /admin-api/v1/contents；请求模型 AdminContentGetContentsParameters；响应模型 AdminContentGetContentsResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-02 | 内容详情与版本 | DETAIL_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面进入、显式刷新、从后台恢复且缓存过期，或收到相关领域事件后触发 | 页面具备读取权限且路由参数完整 | 网络可用且不存在同资源进行中的请求 | 校验 id | 不需要 | GET /admin-api/v1/contents/{id}；请求模型 AdminContentGetContentsByIdParameters；响应模型 AdminContentGetContentsByIdResponse | 同资源单写请求锁；状态机服务端复核 | 首次进入使用结构骨架；刷新保留当前内容并显示轻量刷新态 | 以服务端返回的 version 和状态覆盖详情；重算按钮显示/可用条件；记录页面曝光和请求 requestId | 404显示资源不存在或已下架并提供返回；403显示无权限；409触发重新拉取；网络失败保留缓存并标记可能过期 | GET 可人工重试；缓存可作为只读降级但禁止基于过期数据执行写操作 | 留在当前页；资源不存在时返回上一级并从来源列表移除或标记 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-03 | 平台上架 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 content.manage，且资源状态允许“平台上架”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,expectedVersion,reason；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/contents/{id}/online；请求模型 AdminContentPostContentsByIdOnlineRequest；响应模型 AdminContentPostContentsByIdOnlineResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“平台上架成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-04 | 平台下架 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 content.manage，且资源状态允许“平台下架”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,expectedVersion,reason；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/contents/{id}/offline；请求模型 AdminContentPostContentsByIdOfflineRequest；响应模型 AdminContentPostContentsByIdOfflineResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“平台下架成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-05 | 违规封禁 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 content.ban，且资源状态允许“违规封禁”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,reason,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 必须二次确认；确认框展示目标、影响、不可逆性、原因、审批要求和将触发的通知 | POST /admin-api/v1/contents/{id}/ban；请求模型 AdminContentPostContentsByIdBanRequest；响应模型 AdminContentPostContentsByIdBanResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“违规封禁成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-06 | 运营推荐 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 content.recommend，且资源状态允许“运营推荐”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,enabled,weight,startAt,endAt,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/contents/{id}/recommend；请求模型 AdminContentPostContentsByIdRecommendRequest；响应模型 AdminContentPostContentsByIdRecommendResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“运营推荐成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-07 | 官方标识 | CREATE_OR_SUBMIT | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 用户显式点击操作入口，且本地字段校验、权限、状态机前置条件全部满足 | 具备权限 content.official，且资源状态允许“官方标识”；按钮显示条件由页面状态表和状态机共同决定 | 不存在同资源写请求；必填字段有效；服务端 version 未被本地标记过期；需要审批时已选择审批单 | 校验 id,X-Idempotency-Key,enabled,label,expectedVersion；expectedVersion 必须等于页面最后一次成功读取的服务端版本；同一业务意图生成稳定 X-Idempotency-Key；请求体变化必须换键 | 金额、权益或发布范围变化时二次确认；普通可撤销保存不需要 | POST /admin-api/v1/contents/{id}/official-mark；请求模型 AdminContentPostContentsByIdOfficialMarkRequest；响应模型 AdminContentPostContentsByIdOfficialMarkResponse | expectedVersion 乐观锁 + X-Idempotency-Key + 服务端结果查询 | 提交期间锁定同资源写操作，保留输入并显示明确进度；禁止全页无反馈等待 | 以响应数据更新页面和关联缓存；展示“官方标识成功”；仅刷新受影响区域 | 400逐字段映射；401要求重新认证；403隐藏后续写入口并记录越权拒绝；409提示数据已变化并重新加载；422展示业务原因且保留输入；429显示可重试时间；500保留上下文并提供 requestId | 使用同一幂等键仅重试同一请求体；若服务端已受理则先查询结果；409版本冲突不得盲重试 | 默认留在当前页并刷新；创建成功进入详情，支付/认证/发布结果按对应流程导航 | 记录操作人、角色、目标、前后状态/版本、原因、审批单、requestId、IP/设备和结果 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |
| ACT-ADM-CONTENT-002-08 | 内容字典 | LIST_QUERY | 页面主按钮/行操作/更多菜单/标准弹层，具体位置由模板和页面操作规格确定 | 页面首次进入、下拉刷新、筛选/排序变化或滚动触底时触发；相同查询条件避免并发重复请求 | 页面具备读取权限；筛选项按数据范围和角色显示 | 网络可用；非正在刷新；翻页时 nextCursor/hasNext 为真 | 校验 page,pageSize,cursor,status,keyword,sort | 不需要 | GET /admin-api/v1/content-dictionaries；请求模型 AdminContentGetContentDictionariesParameters；响应模型 AdminContentGetContentDictionariesResponse | 同资源单写请求锁；状态机服务端复核 | 首屏使用骨架屏；刷新保留现有数据；翻页使用列表尾部加载器 | 首屏替换数据，翻页仅追加去重；更新分页、筛选回显、总数/游标和最后刷新时间；空结果进入业务空状态 | 首屏失败显示全页错误与重试；刷新失败保留旧数据并提示；翻页失败保留已加载数据并提供行内重试；401跳登录或会话升级，403显示无权限，404不适用 | GET 可人工重试；网络瞬断最多指数退避自动重试 2 次；筛选变化取消旧请求 | 留在当前页；点击行/卡片按页面导航规格进入详情 | 记录必要业务埋点；读取高敏字段时额外记录访问审计 | 遵循最小采集；仅记录业务标识和状态，不记录自由文本中的个人信息 |

## 6. 导航、深链与缓存

| 允许入口 | 成功出口 | 取消/返回 | 未登录处理 | 无权限处理 | 资源不存在处理 | 深链规则 | 返回栈规则 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 从对应列表行、待办、审计记录或直接授权链接进入 | 按动作成功后导航列执行；未指定时留在当前页并刷新 | 返回来源列表并保持筛选、排序、页码和滚动位置 | 保存原目标并进入登录；公开H5除外 | 展示无权限，不泄露资源存在性；返回安全上级 | 展示明确空/失效状态并从来源列表移除无效入口 | 仅允许后台同源授权链接；未认证先登录并保存原目标；无权限不泄露资源存在性 | 单一来源栈；回跳页清理敏感参数；弹层关闭恢复焦点 |

- 刷新策略：页面进入加载；缓存过期/前台恢复/领域事件触发轻量刷新；写操作成功按资源局部刷新
- 分页策略：不适用；若子区块为列表，遵循所属平台标准分页模板
- 离线策略：不支持离线写入；网络不可用时保留安全可保留输入并提供重试

## 7. 配置、数据和测试

- 需求：`REQ-CONTENT-001;REQ-PUBLISH-001`
- API：`GET /admin-api/v1/contents;GET /admin-api/v1/contents/{id};POST /admin-api/v1/contents/{id}/online;POST /admin-api/v1/contents/{id}/offline;POST /admin-api/v1/contents/{id}/ban;POST /admin-api/v1/contents/{id}/recommend;POST /admin-api/v1/contents/{id}/official-mark;GET /admin-api/v1/content-dictionaries`
- operationId：`adminContentGetContents;adminContentGetContentsById;adminContentPostContentsByIdOnline;adminContentPostContentsByIdOffline;adminContentPostContentsByIdBan;adminContentPostContentsByIdRecommend;adminContentPostContentsByIdOfficialMark;adminContentGetContentDictionaries`
- 配置组：`content`
- 关键配置：`content.limit.normal.online;content.limit.month.online;content.limit.quarter.online;content.limit.year.online;content.team_leader_per_account;content.limit.normal.pending;content.limit.normal.drafts;content.limit.normal.daily_submissions`
- 测试：`TST-CONTENT_001-HAPPY;TST-PUBLISH_001-HAPPY;TST-CONTENT_001-IDEMPOTENT;TST-PUBLISH_001-IDEMPOTENT`
- UI参考：`TOKENS_ONLY`

## 8. 后台运营操作规格

| 筛选项 | 默认排序 | 表格列 | 批量操作 | 行操作 | 详情Tab | 导出策略 | 脱敏规则 | 审批规则 | 确认规则 | 成功反馈 | 失败恢复 | 空状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 页码;每页数量;游标;状态;关键词;排序;内容类型;分类;地区 | createdAt:desc | ID;内容类型;标题;摘要;详细说明;分类;地区;媒体;发布者;脱敏联系方式;状态;审核状态;统计;创建时间 | 无默认批量业务写操作；除非页面规格明确，不得因表格组件能力自行增加 | 平台上架;平台下架;违规封禁;运营推荐;官方标识 | 正文与媒体;发布者;联系方式策略;审核版本;数据指标;举报申诉;状态历史 | 具备页面读取和 export.create 权限可异步导出当前筛选；禁止导出未展示或无权限字段 | 手机号/身份证/支付账号/IP/设备指纹/SecretRef默认脱敏；查看原文需字段权限、会话升级、目的说明和访问审计 | 普通保存按页面权限；涉及资金、秘密、生产发布、永久处罚或高风险导出时自动升级为双人审批 | 不可逆或高影响操作展示目标、影响范围、原因、审批单、服务端version；确认文案不得使用含糊的“确定” | 更新当前行/详情、状态计数和待办；显示操作结果、requestId和审计入口；禁止仅Toast不更新数据 | 字段错误就地提示；409重新加载差异；422保留输入；权限变化立即收回入口；首屏/翻页采用不同错误态 | 展示当前筛选、无数据原因和可执行下一步；不得使用无上下文的空白表格 |

## 9. 开发就绪检查

- [ ] 页面模板和区域按本文件实现；效果图对页面建模、区域顺序、信息层级、布局、组件形态和视觉样式具有强制约束，但不得作为功能、字段、动作或业务数据事实源。
- [ ] 字段、校验、显示/编辑条件与 OpenAPI/配置一致，无新增匿名字段。
- [ ] 所有状态均有可见表现、允许操作和恢复路径。
- [ ] 所有动作均使用登记 operationId、权限、幂等或 expectedVersion。
- [ ] 无权限、404、离线、局部失败、429、409 和 422 均按规格处理。
- [ ] 敏感字段、审计、埋点、返回栈和测试证据齐全。

**成熟度：** `FROZEN_CONSTRUCTION_V1.2.2`
