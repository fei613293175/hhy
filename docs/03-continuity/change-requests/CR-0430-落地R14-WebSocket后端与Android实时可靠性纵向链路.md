---
cr_id: CR-0430
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-runtime-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T04:14:26Z
updated_at: 2026-07-28T04:34:32Z
---
# CR-0430 — 落地R14 WebSocket后端与Android实时可靠性纵向链路

## 用户需求摘要

继续R14开发并按已冻结WebSocket合同实现真实可测试页面链路，不依赖聊天中的隐含方案

## 原规则

CR-0429仅冻结12事件WebSocket合同；当前后端没有升级端点、每用户连续序列、持久投递、ACK重投与72小时续传，Android也没有WebSocket客户端，聊天页面仍只在进入或手动刷新时调用REST，ws.orbexa.cc代理未登记可审计配置；全局数据库基线和PostgreSQL迁移烟测仍以V043的200表为目标。

## 新规则

R14必须落地可测试的WebSocket纵向链路：V044以用户级原子连续序列持久化S2C投递、ACK状态、投递尝试、72小时到期与CHAT或NOTIFICATIONS缺口水位；REST发送与已读须在同一事务创建发送者及对端的实时投递，事务回滚必须共同回滚，只有提交后才可推送且推送失败必须保留delivery供重试。/ws握手严格验证hhy.v1与唯一hhy.access.compact-JWT、活动会话和lastServerSequence后才升级，只回选hhy.v1；支持重放、system.resume、有效ACK幂等、错配拒绝、10秒超时最多5次重投、ping/pong和非持久typing。Android使用稳定OkHttp单例连接，进程内维护连续水位，收到聊天事件后以REST刷新权威数据；CHAT缺口必须穷尽会话与消息分页后才按服务端水位重连，R15通知补洞未实现前不得把NOTIFICATIONS缺口伪标完成。数据库目录、实库基线与全局迁移烟测统一从200表升级为203表并保留旧基线兼容，V044/U044及R14真实不变量runner必须进入PostgreSQL 17全链。ws.orbexa.cc代理配置只登记Upgrade透传与协议头脱敏，本CR不得宣称已激活生产域名。

## 修改原因

CR-0429已消除协议歧义，但仓库仍无WebSocket握手端点、每用户投递序列与ACK保留事实、代理升级配置或Android客户端；当前聊天页仅REST轮询，无法满足R14范围中的WebSocket可靠性和未读实时闭环

## 影响摘要

新增R14后端与Android实时可靠性运行时，不改变九个既有REST接口和冻结12事件形状；修正REST ChatMessage.serverSequence不再用消息ID冒充用户级WebSocket序列。新增三张V044表、Spring原生WebSocket端点、OkHttp客户端、页面实时刷新和代理模板；同步数据库基线200到203、真实PostgreSQL 17迁移与回退链及事务提交边界负测。

## 影响文件

- `services/backend/boot/pom.xml`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityConfiguration.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14WebSocketConfiguration.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14WebSocketHandshakeInterceptor.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14WebSocketHandler.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14WebSocketSessionRegistry.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14RealtimeDeliveryListener.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14Contracts.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14Service.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14RealtimeContracts.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14RealtimeStore.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14RealtimePostgresStore.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/R14RealtimeService.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R14ServiceTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R14RealtimeServiceTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R14RealtimePostgresStoreTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/realtime/R14WebSocketHandshakeInterceptorTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/realtime/R14WebSocketHandlerTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/realtime/R14RealtimeTransactionBoundaryTest.java`
- `database/migrations/V044__r14_websocket_reliability.sql`
- `services/backend/boot/src/main/resources/db/migration/V044__r14_websocket_reliability.sql`
- `database/rollback/U044__r14_websocket_reliability_DEV_ONLY.sql`
- `database/tests/r14_websocket_reliability.sql`
- `database/schema_dictionary.csv`
- `database/schema_traceability.csv`
- `database/verification/verify_baseline.sql`
- `PROJECT_BASELINE.yaml`
- `PROJECT_BASELINE.json`
- `scripts/check_db_schema.py`
- `scripts/run_r14_database_invariants.sh`
- `scripts/run_postgres_migration_smoke.sh`
- `tests/test_r14_realtime_migration.py`
- `tests/test_r14_realtime_proxy.py`
- `apps/android/gradle/libs.versions.toml`
- `apps/android/app/build.gradle.kts`
- `apps/android/core/network/build.gradle.kts`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR14WebSocket.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ContractR14WebSocketTest.kt`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ConversationListScreen.kt`
- `apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt`
- `apps/android/feature/chat/src/test/java/cc/orbexa/hhy/chat/R14RealtimeRefreshTest.kt`
- `infra/nginx/ws.orbexa.cc.conf`
- `scripts/check_r14_entry_contract.py`
- `releases/R14/RELEASE_MANIFEST.yaml`
- `releases/R14/STORIES.yaml`
- `catalogs/release_story_backlog.csv`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-CHAT-001`
- `SCR-CHAT-002`

## API

- `chat.message.send`
- `chat.message.ack`
- `chat.message.new`
- `chat.message.read`
- `chat.read.updated`
- `chat.typing`
- `system.ping`
- `system.pong`
- `system.delivery.ack`
- `system.resume`

## 数据库与迁移

- `hhy.websocket_user_sequences`
- `hhy.websocket_deliveries`
- `hhy.websocket_gap_watermarks`

## 配置

- `HHY_WS_BASE_URL and BuildConfig.WS_BASE_URL must resolve to safe wss; ws.orbexa.cc /ws upgrades without logging Sec-WebSocket-Protocol`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Backend unit tests cover handshake rejection, active-session authentication, resume replay and gap, ACK idempotency and mismatch, retry limits, REST send/read delivery creation, ping/pong and typing authorization`
- `Transaction tests prove message or read rollback also rolls back realtime deliveries, listeners never push before commit, and post-commit push failure leaves the persisted delivery eligible for retry`
- `PostgreSQL 17 migration and store tests cover the full V001 to V044 chain, 200 to 203 table baseline, per-user concurrent contiguous sequence, unique event and sequence, 72-hour expiry watermark, ACK ownership, U044 rollback and V044 replay`
- `Android unit tests cover safe wss validation, exact subprotocols, typed event parsing, contiguous watermark, duplicate ACK, sequence gap reconnect, CHAT gap completion and NOTIFICATIONS non-completion`
- `Proxy and R14 entry gates prove Upgrade forwarding, Sec-WebSocket-Protocol redaction, runtime wiring, unchanged REST contract and no production activation claim`

## 版本

- `R14`

## 迁移与兼容策略

V044仅增表和约束，不改写既有聊天历史；目录和实库目标升级为203表，同时基线校验保留V043及更早迁移链的兼容判断。旧客户端继续使用REST且不受影响，新客户端连接失败时保留REST页面可用。已有消息不生成伪造历史序列，首次连接从0只重放V044后真实投递；过期序列通过CHAT或NOTIFICATIONS缺口水位要求权威REST补洞。部署顺序为数据库迁移、后端、代理激活、Android，代理文件在候选阶段前仅作为模板。回退时先停止WebSocket流量和客户端激活，再由PostgreSQL 17 runner验证U044只删除三张新增表并可重放V044恢复203表；不得删除既有聊天数据或回滚九个REST接口。

## 用户确认

用户已明确要求持续开发R14并授权按仓库规则自主推进，不等待逐步确认

## 审批

- 审批人：`codex-r14-runtime-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T04:34:32Z`
- 说明：独立计划复审通过：修订已闭环R14 WebSocket纵向链路及上一轮全部阻断。V044以websocket_user_sequences、websocket_deliveries和websocket_gap_watermarks三表分别承载用户级原子连续序列、持久投递/ACK/尝试/72小时到期及CHAT或NOTIFICATIONS缺口水位，并以用户并发连续分配、事件与序列唯一、ACK归属和过期水位的PostgreSQL 17实测约束其语义。REST消息发送与已读在同一事务创建发送者及对端投递，业务回滚与投递共同回滚；监听器提交前禁止推送，提交后推送失败仍保留delivery供重试。握手、唯一双子协议、升级前JWT和活动会话验证、lastServerSequence、重放/system.resume、ACK二元组幂等与错配拒绝、10秒最多5次重投、72小时缺口、ping/pong和非持久typing均沿用CR-0429冻结合同。Android使用稳定OkHttp和进程内连续水位，聊天事件以REST权威刷新；CHAT缺口穷尽分页后重连，NOTIFICATIONS未实现时不得伪造成功。代理文件仅作为Upgrade透传和协议头脱敏模板，不代表生产域名已激活。数据库范围覆盖200到203表基线、真实全链、U044回退重放和事务负测。批准按当前范围实施。

## 状态记录 · 2026-07-28T05:58:02Z

- Actor：`codex-root-r14-runtime-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：V044、后端WebSocket、真实事务回滚、PostgreSQL 17全链和脱敏代理模板已实现并通过模块验证；Android独立工作包正在固定镜像复测

## 状态记录 · 2026-07-28T06:38:46Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：V044、后端WebSocket可靠投递与握手、Android OkHttp客户端和CHAT权威补洞、代理模板及分层测试全部完成；生产域名激活仍按候选阶段执行。

## 状态记录 · 2026-07-28T06:43:35Z

- Actor：`codex-root-r14-client-20260728`
- Status：`CLOSED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：批准范围已由实现提交 7ce3c2169925fef717c051e16e09f556ce619aa6 完整交付并通过门禁；CR关闭。ws生产激活不在CR-0430范围内，仍留待R14候选阶段。
