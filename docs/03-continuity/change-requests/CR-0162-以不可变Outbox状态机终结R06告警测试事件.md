---
cr_id: CR-0162
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R06-006
session_id: SES-20260721T020225Z-397AF410
created_at: 2026-07-21T04:01:26Z
updated_at: 2026-07-21T04:02:01Z
---
# CR-0162 — 以不可变Outbox状态机终结R06告警测试事件

## 用户需求摘要

项目所有者要求持续开发、自动判断测试结果并把可复用方案固化到仓库

## 原规则

CR-0161计划在告警恢复时删除带r06-stage-alert前缀的Outbox测试事件

## 新规则

R06告警测试事件按冻结Commit唯一命名；插入后只能依次从PENDING推进到PUBLISHING并递增attempts，再推进到PUBLISHED并设置published_at，永久保留不可变事实，禁止DELETE；失败恢复执行同一幂等终结流程

## 修改原因

CR-0161原计划删除测试Outbox事件，现场触发器按设计拒绝；必须保留事实并使用合法投递状态转换恢复Gauge

## 影响摘要

修正R06现场脚本和部署手册，不修改Outbox触发器、业务API或数据库结构；既有四条隔离测试事件也按同一合法状态机终结

## 影响文件

- `scripts/run_r06_staging_acceptance.sh`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `仅对精确测试event_id执行PENDING到PUBLISHING到PUBLISHED合法转换；禁止删除或修改事件身份和payload`

## 配置

- `R06告警演练事件唯一命名、阶段复用和失败恢复`

## 资金/账本与历史数据

- `不访问资金或账本表`

## 测试

- `Outbox不可变拒绝、合法双阶段终结、Gauge恢复、告警resolved和阶段复用`

## 版本

- `R06`

## 迁移与兼容策略

无迁移；兼容现有V030与V010不可变触发器，测试事件终态PUBLISHED不再计入积压Gauge且保留审计

## 用户确认

项目所有者要求AI自行判断自动测试、持续推进并将可复用经验写入仓库事实源

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-21T04:02:01Z`
- 说明：方案严格遵守V010不可变Outbox状态机，保留测试审计事实并通过合法终态恢复积压指标；不扩大数据库或生产权限

## 状态记录 · 2026-07-21T04:03:47Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T020225Z-397AF410`
- Note：脚本已改为按冻结Commit唯一事件前缀和合法PENDING到PUBLISHING到PUBLISHED终结；既有四条测试事件已验证达到PUBLISHED且attempts=1、published_at非空
