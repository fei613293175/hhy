---
cr_id: CR-0212
status: APPROVED
requester_actor_id: codex-root-r08-003
approver_actor_id: codex-reviewer-r08-schema
task_id: TASK-R08-003
session_id: SES-20260722T012224Z-E70EA3B7
created_at: 2026-07-22T01:41:57Z
updated_at: 2026-07-22T01:42:24Z
---
# CR-0212 — 对齐R08冻结发布字段与PostgreSQL列容量

## 用户需求摘要

项目所有者要求严格按照开发文档参数持续推进R08

## 原规则

content_posts.title、project_details.cooperation/conditions/region/website仍为varchar(255)，但冻结OpenAPI和SCR-PUB-002允许对应发布字段最多2000字符。

## 新规则

V034在种配置前将content_posts.title及project_details的cooperation、conditions、region、website扩容为varchar(2000)；不截断、不改写既有数据，不改变字段可空性、约束、索引或业务语义。U034仅在不存在长度超过255的新增数据时恢复旧容量，否则原子阻断。

## 修改原因

冻结合同允许标题、说明、分类、地区和项目扩展字段最多2000字符，旧数据库部分列仍为varchar(255)，必须在首个写接口前做只扩容兼容修正

## 影响摘要

消除R08首个真实发布接口的冻结合同与数据库容量冲突，完整接受最多2000字符且保持既有数据兼容。

## 影响文件

- `database/migrations/V034__r08_backend_configuration.sql`
- `services/backend/boot/src/main/resources/db/migration/V034__r08_backend_configuration.sql`
- `database/rollback/U034__r08_backend_configuration.sql`
- `database/tests/r08_backend_configuration.sql`
- `tests/test_r08_backend_configuration.py`

## 页面

- `SCR-PUB-002`

## API

- `POST /api/v1/contents`
- `PATCH /api/v1/contents/{id}`

## 数据库与迁移

- `content_posts.title and project_details textual columns varchar(2000)`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- `CR-0212 TASK-R08-003 evidence`

## 测试

- `V034 capacity expansion and guarded U034 downgrade tests`

## 版本

- `R08`

## 迁移与兼容策略

PostgreSQL varchar扩容为元数据兼容变更且不重写业务值；旧客户端不受影响。回滚前逐列检查长度，任何超过255的合法R08数据都会阻止降级，禁止截断。

## 用户确认

项目所有者已明确要求严格遵循开发文档硬性参数并持续推进，不等待逐项确认

## 审批

- 审批人：`codex-reviewer-r08-schema`
- 决定：`APPROVED`
- 时间：`2026-07-22T01:42:24Z`
- 说明：批准只扩容兼容修正；必须保持既有值不变，回滚遇到超过255字符数据时原子拒绝，禁止任何截断。

## 状态记录 · 2026-07-22T01:42:29Z

- Actor：`codex-root-r08-003`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T012224Z-E70EA3B7`
- Note：随V034实施冻结字段容量对齐
