---
cr_id: CR-0090
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T18:54:36Z
updated_at: 2026-07-19T18:54:57Z
---
# CR-0090 — 补齐TASK-R05-004冻结Android页面实施范围

## 用户需求摘要

用户要求持续推进R05及后续版本开发，不遗漏开发文档中的任何页面和功能

## 原规则

R05-004会话允许路径遗漏apps/android，无法实现任务目标中冻结的四个Android实名页面

## 新规则

允许TASK-R05-004修改apps/android下身份网络绑定、identity功能模块、App导航、Shell商业入口及Gradle模块声明

## 修改原因

TASK-R05-004目标明确包含SCR-ID-001至004，但启动时Story派生范围遗漏apps/android，导致合法实现无法记录检查点

## 影响摘要

仅补齐任务原定Android交付范围，不改变冻结接口字段、数据库或页面数量

## 影响文件

- `apps/android/**`

## 页面

- `SCR-ID-001`
- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`

## API

- `identityPostIdentitySessions`
- `identityPostIdentitySessionsByIdLivenessToken`
- `identityGetIdentitySessionsById`
- `identityPostIdentitySessionsByIdRetry`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `HHY_IDENTITY_CONSENT_VERSION`
- `HHY_H5_BASE_URL`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `:feature:identity:testDebugUnitTest,:feature:identity:lintDebug,:app:compileDebugKotlin`

## 版本

- `R05`

## 迁移与兼容策略

新增feature identity模块并由App显式依赖；现有认证与Shell导航保持兼容，敏感信息不持久化

## 用户确认

用户已明确要求立即持续推进整个项目开发，严格完成开发文档每个版本的全部功能，不允许遗漏

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T18:54:57Z`
- 说明：范围修正与TASK-R05-004冻结目标一致，只补齐遗漏的Android实施路径
