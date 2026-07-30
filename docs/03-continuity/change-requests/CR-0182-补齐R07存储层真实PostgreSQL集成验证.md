---
cr_id: CR-0182
status: APPROVED
requester_actor_id: codex-root-r07-003
approver_actor_id: codex-reviewer-r07-store
task_id: TASK-R07-003
session_id: SES-20260721T171025Z-A3718A1C
created_at: 2026-07-21T18:12:12Z
updated_at: 2026-07-21T18:12:54Z
---
# CR-0182 — 补齐R07存储层真实PostgreSQL集成验证

## 用户需求摘要

项目所有者要求R07功能真实落地并由AI判断测试合格。

## 原规则

R07服务与控制器使用Mock存储验证业务规则；数据库门禁只执行迁移和约束SQL，不直接执行R07PostgresStore查询与写入。

## 新规则

新增R07PostgresStoreTest，在显式确认的一次性PostgreSQL数据库运行完整Flyway后，实际验证ONLINE搜索与筛选、热词排期、用户历史去重、发布者公开摘要、联系方式密文读取、幂等领取/完成/重放、不可变访问审计和不含明文联系方式的Outbox。

## 修改原因

现有服务单测和迁移门禁未直接执行R07PostgresStore的搜索、历史、发布者、联系方式、幂等、审计与Outbox SQL，仍可能遗漏参数或行映射错误。

## 影响摘要

补充一项环境显式启用的真实存储集成测试，不改变生产代码、合同、页面或数据库结构；本地无数据库时明确skip，R07 MODULE/候选门禁必须在固定PostgreSQL环境运行。

## 影响文件

- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/R07PostgresStoreTest.java`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `GET /api/v1/search`
- `GET /api/v1/search/hot`
- `GET /api/v1/search/history`
- `GET /api/v1/publishers/{id}`
- `POST /api/v1/contents/{id}/contacts/{channel}/access`

## 数据库与迁移

- `content_posts;content_versions;search_histories;hot_search_terms;content_contacts;content_contact_access_logs;idempotency_records;outbox_events`

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R07PostgresStore PostgreSQL 17 integration`

## 版本

- `R07`

## 迁移与兼容策略

无迁移；测试使用唯一随机数据并在finally按外键顺序清理，不接触共享或生产数据库。

## 用户确认

项目所有者已授权持续开发并要求AI独立完成、判断测试，不等待真机反馈。

## 审批

- 审批人：`codex-reviewer-r07-store`
- 决定：`APPROVED`
- 时间：`2026-07-21T18:12:54Z`
- 说明：该测试直接覆盖R07PostgresStore的真实SQL与行映射，使用显式一次性数据库和随机隔离数据，不扩大生产范围，能够消除Mock测试无法覆盖的主要风险。

## 状态记录 · 2026-07-21T18:44:48Z

- Actor：`codex-root-r07-003`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T171025Z-A3718A1C`
- Note：R07PostgresStore真实PostgreSQL 17集成测试已通过，覆盖搜索、热词、历史、发布者、联系方式、幂等、审计及Outbox。

## 状态记录 · 2026-07-21T18:52:50Z

- Actor：`codex-root-r07-003`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T171025Z-A3718A1C`
- Note：实现提交83d58c6a绑定真实R07PostgresStore PostgreSQL 17.10集成测试。

## 状态记录 · 2026-07-21T18:53:28Z

- Actor：`codex-root-r07-003`
- Status：`CLOSED`
- Session：`SES-20260721T171025Z-A3718A1C`
- Note：真实PostgreSQL 17.10存储层集成覆盖通过，测试库一次性容器已自动清理。
