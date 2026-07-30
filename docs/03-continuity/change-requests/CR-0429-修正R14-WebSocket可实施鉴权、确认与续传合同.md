---
cr_id: CR-0429
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T03:32:38Z
updated_at: 2026-07-28T03:40:58Z
---
# CR-0429 — 修正R14 WebSocket可实施鉴权、确认与续传合同

## 用户需求摘要

按R14冻结开发文档持续落地WebSocket实时可靠性，不虚构协议实现

## 原规则

当前合同只写Bearer access token in Sec-WebSocket-Protocol or wsTicket，未冻结合法子协议格式、握手拒绝与全链路脱敏；delivery.resume未定义lastServerSequence的0语义、连续处理口径、超前拒绝、服务端安全水位和聊天/通知补洞范围；S2C需确认事件没有C2S确认事件，C2S既有事件也未绑定对应确认；生成器与门禁将事件数写死为10且没有证明定向生成不破坏富化OpenAPI、非WS注册表行、既有事件和definitions。

## 新规则

R14/R15 WebSocket请求必须且只能各携带一个hhy.v1与hhy.access.<compact-JWT>子协议；compact JWT限定为无填充base64url三段格式，拒绝等号、空白、重复或缺失认证协议，并在验证JWT与会话有效后才升级，服务端只回选hhy.v1。完整Sec-WebSocket-Protocol头必须在边缘/反向代理、握手异常、服务端和应用日志中脱敏，令牌不得进入URL、响应或错误文本；wsTicket在签发API冻结前为RESERVED_UNAVAILABLE。lastServerSequence为非负查询参数，0表示从未处理，正数表示客户端已连续处理且对需确认S2C事件完成有效ACK的最高用户级序列，超出服务端高水位必须拒绝。新增C2S system.delivery.ack(eventId,serverSequence)，二元组必须匹配当前认证用户实际投递的同一事件，重复ACK幂等，未知或错配拒绝，只有有效ACK停止重投，事件本身ack_required=false；C2S chat.message.send由chat.message.ack按conversationId+clientMessageId确认，chat.message.read由chat.read.updated按conversationId+认证用户+lastReadMessageId确认。新增S2C system.resume，返回mode、requestedLastServerSequence、serverHighWatermark、resumeFromServerSequence与affectedScopes；REPLAY_COMPLETE在保留窗口内重放完成后返回空scope和握手快照高水位，REST_GAP_FILL返回CHAT/NOTIFICATIONS受影响域及安全水位。CHAT固定穷尽chatGetConversations和各会话chatGetConversationsByIdMessages分页，NOTIFICATIONS固定穷尽notificationGetNotifications分页；全部补洞成功后只能以resumeFromServerSequence重连，禁止从REST数据猜测序列。生成器增加--sync-websocket，仅重建WebSocket合同及12条WEBSOCKET_EVENT行，并证明两个OpenAPI字节SHA、非WS注册表行内容与顺序、既有10事件及definitions除本CR批准字段外均不变。

## 修改原因

审计确认当前WebSocket仅有生成合同无后端或Android实现，且合同未定义Bearer在Sec-WebSocket-Protocol的合法编码、lastServerSequence续传携带方式、S2C ackRequired的C2S确认事件以及超出保留期时的REST补洞指令，直接实现必然依赖猜测

## 影响摘要

修正冻结WebSocket协议与生成/校验事实源，将事件数由10增至12并同步runtime副本、R14 manifest/story及story backlog和项目基线；本CR只冻结无歧义协议及隔离生成边界，不实现后端端点、Android网络层或数据库迁移，运行时由后续独立CR完成。

## 影响文件

- `scripts/generate_contracts.py`
- `contracts/websocket-events.yaml`
- `contracts/contract_status.csv`
- `services/backend/boot/src/main/resources/contracts/websocket-events.yaml`
- `scripts/check_api_contract.py`
- `scripts/check_r14_entry_contract.py`
- `tests/test_r14_websocket_contract.py`
- `PROJECT_BASELINE.yaml`
- `PROJECT_BASELINE.json`
- `releases/R14/RELEASE_MANIFEST.yaml`
- `releases/R14/STORIES.yaml`
- `catalogs/release_story_backlog.csv`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-CHAT-001`
- `SCR-CHAT-002`

## API

- `system.delivery.ack`
- `system.resume`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `合同测试覆盖握手合法/等号/空白/重复/缺失认证协议、升级前JWT验证和全链路协议头脱敏；ACK二元组匹配、重复幂等、未知/错配拒绝、有效ACK停止重投及两个既有C2S确认映射；resume 0、合法连续值、超前拒绝、REPLAY_COMPLETE、CHAT与NOTIFICATIONS gap scope、固定REST分页穷尽和安全水位重连；--sync-websocket前后两个OpenAPI字节SHA不变、非WS registry行内容与顺序不变、既有10事件与definitions深度不变、仅生成12条WS行且runtime副本哈希一致；R14入口、全局API合同、生成资产和文档门禁通过。`

## 版本

- `R14`

## 迁移与兼容策略

新增system.delivery.ack和system.resume，不改变既有REST发送、已读和会话接口。当前仓库没有WebSocket生产消费者；既有10事件与definitions保持深度一致，只有本CR明确批准的合同元数据可扩展。后续服务端必须先验证唯一hhy.access.<compact-JWT>再升级且只回选hhy.v1；不得以URL token、私有ACK、未声明scope或从REST结果猜序列方式兼容。

## 用户确认

用户已明确要求持续开发并授权按独立复审通过方案推进

## 审批

- 审批人：`codex-r14-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T03:40:58Z`
- 说明：独立计划复审通过：修订已消除上一轮全部实施歧义。握手唯一允许各一个hhy.v1与hhy.access.<compact-JWT>，compact JWT限定无填充base64url三段并覆盖等号、空白、重复和缺失认证协议负例；JWT及会话有效性必须在升级前验证，服务端只回选hhy.v1，完整Sec-WebSocket-Protocol头在边缘、反向代理、握手异常、服务端和应用日志中统一脱敏，令牌不得进入URL、响应或错误文本，wsTicket保持RESERVED_UNAVAILABLE。system.delivery.ack以eventId+serverSequence匹配当前认证用户实际收到的同一S2C需确认事件，重复ACK幂等、未知或错配拒绝、仅有效ACK停止重投且ACK本身无需确认；现有chat.message.send与chat.message.read分别明确映射chat.message.ack和chat.read.updated。lastServerSequence已冻结为用户级最高连续已处理且完成必要ACK的序列，0表示从未处理、超前于服务端高水位拒绝；system.resume返回requestedLastServerSequence、serverHighWatermark、resumeFromServerSequence和affectedScopes，REPLAY_COMPLETE与REST_GAP_FILL语义分离，CHAT及NOTIFICATIONS均绑定固定REST分页补洞操作，完成后只能按服务端resumeFromServerSequence重连，禁止从REST数据猜测。--sync-websocket仅重建WebSocket合同和12条WS注册表行，并以回归证明两个富化OpenAPI字节SHA、非WS registry内容与顺序、既有10事件及definitions均不退化，同时同步runtime副本。R14 manifest、Stories、声明事实源catalogs/release_story_backlog.csv、项目基线、门禁、Problem Registry和Changelog均纳入范围；当前仓库无生产WebSocket消费者，因此本CR只冻结合同、后续另立运行时CR的边界合理。批准按修订计划实施。

## 状态记录 · 2026-07-28T03:52:05Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：合同、隔离生成器、runtime副本、R14清单与严格门禁已实现并通过首轮验证，进入提交前收口

## 状态记录 · 2026-07-28T04:07:05Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：12事件合同、定向生成器、runtime副本、R14事实源与严格门禁已在实现Commit中完成；运行时纵向链路按批准边界另立CR

## 状态记录 · 2026-07-28T08:59:25Z

- Actor：`codex-root-r14-client-20260728`
- Status：`CLOSED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：十二事件WebSocket合同、定向生成器、runtime副本与严格合同回归均已完成；运行时纵向链路由已关闭CR-0430独立实施，公网DNS阻断不影响本合同CR关闭
