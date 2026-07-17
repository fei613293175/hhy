---
cr_id: CR-0007
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-P00-005
session_id: SES-20260717T024407Z-B03C9375
created_at: 2026-07-17T03:05:39Z
updated_at: 2026-07-17T03:11:05Z
---
# CR-0007 — 修复Spring Boot 4 staging Flyway自动迁移未启用

## 用户需求摘要

完成P00后暂停推进

## 原规则

Spring Boot 4.1.0应用启动时由现有flyway-core依赖自动执行classpath迁移

## 新规则

使用Spring Boot 4.1.0官方spring-boot-starter-flyway触发自动配置，保留PostgreSQL专用Flyway模块，启动失败即阻断发布

## 修改原因

真实staging空库启动后未创建Flyway历史表与198张业务表，运行手册承诺与实际依赖不一致

## 影响摘要

仅修复后端启动迁移链；不改变API、数据库DDL、业务语义或正式版本号

## 影响文件

- `services/backend/boot/pom.xml`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/P00FlywayAutoConfigurationTest.java`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `spring-boot-starter-flyway`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `services/backend/boot/src/test/java/cc/orbexa/hhy/P00FlywayAutoConfigurationTest.java`

## 版本

- `P00`

## 迁移与兼容策略

现有V001-V011文件不变；空库自动升级到V011，已升级库由Flyway校验后保持不变

## 用户确认

用户已授权开始项目开发，并要求P00完成后暂停推进

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T03:11:05Z`
- 说明：真实staging空库复现且Spring Boot 4.1官方要求starter，范围和回归测试充分

## 状态记录 · 2026-07-17T03:11:40Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T024407Z-B03C9375`
- Note：按批准范围修复Boot4 Flyway自动配置并复验空库staging
