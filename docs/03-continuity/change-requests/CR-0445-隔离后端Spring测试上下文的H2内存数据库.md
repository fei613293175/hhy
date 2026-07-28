---
cr_id: CR-0445
status: APPROVED
requester_actor_id: codex-r16-backend-20260728
approver_actor_id: codex-r16-h2-test-isolation-independent-review-20260728
task_id: TASK-R16-003
session_id: SES-20260728T102501Z-3189682B
created_at: 2026-07-28T11:57:56Z
updated_at: 2026-07-28T11:59:01Z
---
# CR-0445 — 隔离后端Spring测试上下文的H2内存数据库

## 用户需求摘要

持续推进R16开发并完成真实回归，不因既有测试基础设施污染停止

## 原规则

application-test.yml固定使用jdbc:h2:mem:hhy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1，不同Spring上下文共享同一JVM内存库并重复执行schema.sql。

## 新规则

默认test profile的H2数据库名必须包含Spring RandomValuePropertySource生成的UUID，使每个独立ApplicationContext拥有隔离数据库；同一缓存上下文保持同一URL；测试显式声明的spring.datasource.url继续优先且不变。

## 修改原因

完整回归确认多个不同Spring测试上下文复用jdbc:h2:mem:hhy且DB_CLOSE_DELAY=-1，schema.sql在后续上下文重复建表，造成PublicEndpointsTest等8项级联错误；生产PostgreSQL与显式测试URL不受影响

## 影响摘要

仅修改统一测试配置中的默认H2 URL，从固定hhy改为hhy-${random.uuid}，消除上下文间重复建表和状态串扰。

## 影响文件

- `services/backend/boot/src/test/resources/application-test.yml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `spring.datasource.url`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `完整mvn -pl boot -am test须491项0失败0错误；PublicEndpointsTest及其他默认test profile上下文不得再出现APP_BUILD_ARTIFACTS already exists。`

## 版本

- `R16`

## 迁移与兼容策略

无生产配置、数据库迁移、API或运行时代码变化。所有显式声明测试数据库URL的测试保持原行为；默认测试上下文仍使用H2 PostgreSQL兼容模式和DB_CLOSE_DELAY=-1，但隔离到各自UUID数据库。

## 用户确认

项目所有者已明确要求持续推进R16并完成真实回归，本变更仅修复测试上下文数据库污染。

## 审批

- 审批人：`codex-r16-h2-test-isolation-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T11:59:01Z`
- 说明：独立审查确认变更仅作用于src/test/resources默认test profile：UUID内存库隔离不同ApplicationContext，同时保留MODE=PostgreSQL与DB_CLOSE_DELAY=-1；Spring高优先级显式spring.datasource.url继续覆盖默认值，生产配置与运行时代码不受影响，完整后端回归范围足够。

## 状态记录 · 2026-07-28T11:59:21Z

- Actor：`codex-r16-backend-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T102501Z-3189682B`
- Note：独立审批通过后实施默认测试库按ApplicationContext隔离。

## 状态记录 · 2026-07-28T12:55:04Z

- Actor：`codex-r16-backend-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260728T102501Z-3189682B`
- Note：测试H2使用每上下文唯一内存库，生产PostgreSQL和显式测试URL不变，491项Java回归通过。

## 状态记录 · 2026-07-28T12:55:10Z

- Actor：`codex-r16-backend-20260728`
- Status：`CLOSED`
- Session：`SES-20260728T102501Z-3189682B`
- Note：Spring测试上下文数据库污染已消除并形成回归保护。
