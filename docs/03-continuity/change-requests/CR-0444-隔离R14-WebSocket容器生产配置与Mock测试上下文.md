---
cr_id: CR-0444
status: APPROVED
requester_actor_id: codex-r16-implementation-20260728
approver_actor_id: codex-r14-websocket-container-independent-review-20260728
task_id: TASK-R16-003
session_id: SES-20260728T102501Z-3189682B
created_at: 2026-07-28T11:42:13Z
updated_at: 2026-07-28T11:46:50Z
---
# CR-0444 — 隔离R14 WebSocket容器生产配置与Mock测试上下文

## 用户需求摘要

持续推进R16开发并完成真实回归，不因既有配置故障停止

## 原规则

R14WebSocketConfiguration无条件创建ServletServerContainerFactoryBean，Mock ServletContext缺少jakarta.websocket.server.ServerContainer属性时上下文启动失败。

## 新规则

WebSocket处理器注册继续默认启用；仅Servlet容器缓冲与空闲超时工厂Bean受hhy.websocket.container.enabled控制，生产默认true，test profile固定false；真实容器部署行为不变，Mock上下文不得伪造生产ServerContainer。

## 修改原因

完整后端回归确认R14 ServletServerContainerFactoryBean在Mock ServletContext中无ServerContainer属性，导致47个Spring上下文用例级联失败；需要保留生产容器限制并在test profile禁用该容器工厂Bean

## 影响摘要

为容器工厂Bean增加显式配置条件，并在统一测试配置关闭该Bean；新增配置边界合同测试，修复47个由同一上下文根因引发的级联错误。

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/realtime/R14WebSocketConfiguration.java`
- `services/backend/boot/src/test/resources/application-test.yml`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/realtime/R14WebSocketConfigurationTest.java`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `hhy.websocket.container.enabled`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R14WebSocketConfigurationTest必须直接断言无配置时容器工厂存在且文本/二进制缓冲均为10485760字节、空闲超时75000毫秒；WebApplicationContextRunner在配置false时必须断言仅缺失ServletServerContainerFactoryBean，同时WebSocketHandlerMapping仍含/ws，WebSocketHttpRequestHandler持有原处理器和握手拦截器；完整mvn -pl boot -am test须489项0失败0错误。`

## 版本

- `R16`

## 迁移与兼容策略

无数据库或API变更。生产缺省值true保持10MiB缓冲和75秒空闲超时；仅test profile覆盖false。现有WebSocket协议、握手、路由及Android客户端不变。

## 用户确认

项目所有者已明确要求持续推进R16并完成真实回归，本修正仅隔离Mock测试容器且不改变生产WebSocket行为。

## 审批

- 审批人：`codex-r14-websocket-container-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T11:46:50Z`
- 说明：独立审查确认生产缺省仍创建ServletServerContainerFactoryBean并保持10MiB文本/二进制缓冲和75000毫秒空闲超时；test profile仅关闭该Bean，专项测试精确验证/ws映射、原处理器与握手拦截器仍注册，影响范围准确。

## 状态记录 · 2026-07-28T11:47:15Z

- Actor：`codex-r16-implementation-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T102501Z-3189682B`
- Note：独立审批通过后开始实施测试上下文隔离。
