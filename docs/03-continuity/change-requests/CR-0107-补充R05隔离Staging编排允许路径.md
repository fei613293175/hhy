---
cr_id: CR-0107
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R05-006
session_id: SES-20260719T230526Z-55ABC07F
created_at: 2026-07-19T23:14:23Z
updated_at: 2026-07-19T23:14:44Z
---
# CR-0107 — 补充R05隔离Staging编排允许路径

## 用户需求摘要

用户要求持续推进R05并完成正式版本开发与预发布验收，无需就常规开发事项反复确认

## 原规则

会话路径允许services/backend、docs、scripts等，但未包含infra目录

## 新规则

仅增加infra/staging/r05-smoke/**用于TASK-R05-006隔离预发布编排与验收

## 修改原因

TASK-R05-006明确要求Staging部署和告警回滚演练，但启动会话漏列infra/staging/r05-smoke路径

## 影响摘要

新增R05专用Compose、Prometheus、Alertmanager、Nginx与告警配置，不修改既有环境

## 影响文件

- `infra/staging/r05-smoke/**`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R05隔离Staging编排与可观测性规则`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `docker compose config; promtool config/rules; amtool check-config; isolated staging smoke`

## 版本

- `R05`

## 迁移与兼容策略

新增隔离目录和独立网段端口，无现有配置迁移；删除该目录即可回退

## 用户确认

用户已明确要求持续推进版本开发、常规开发问题自行决定且无需反复确认

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T23:14:44Z`
- 说明：任务定义要求Staging部署，范围扩展仅补齐遗漏且不影响现有环境

## 状态记录 · 2026-07-19T23:14:58Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T230526Z-55ABC07F`
- Note：应用已批准的R05隔离Staging路径

## 状态记录 · 2026-07-19T23:15:13Z

- Actor：`codex-root`
- Status：`SUPERSEDED`
- Session：`SES-20260719T230526Z-55ABC07F`
- Note：范围工具要求精确文件路径，改由CR-0108逐文件列出
