---
cr_id: CR-0198
status: APPROVED
requester_actor_id: codex-r07-candidate
approver_actor_id: codex-release-audit
task_id: TASK-R07-007
session_id: SES-20260721T210252Z-D631F6E4
created_at: 2026-07-21T22:56:21Z
updated_at: 2026-07-21T22:56:27Z
---
# CR-0198 — 补正Android基线复用规则的踩坑文档路径

## 用户需求摘要

把可复用方案写入全局硬性规则和踩坑记录

## 原规则

CR-0197只登记了不存在的根目录PITFALLS.md路径

## 新规则

在真实docs/03-continuity/PITFALLS.md登记首版基线单次采集与轻量晋升硬规则

## 修改原因

CR-0197影响清单误写根目录PITFALLS.md，事实文件位于docs/03-continuity/PITFALLS.md，必须用独立批准CR补齐真实路径而不篡改已批准记录

## 影响摘要

只补正治理文档路径，不改变CR-0197技术方案或候选行为

## 影响文件

- `docs/03-continuity/PITFALLS.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `rg核对陷阱条款与android-automation策略一致`

## 版本

- `R07`

## 迁移与兼容策略

纯文档路径补正，无运行时迁移

## 用户确认

项目所有者已明确要求写入全局硬性规则和踩坑复用记录

## 审批

- 审批人：`codex-release-audit`
- 决定：`APPROVED`
- 时间：`2026-07-21T22:56:27Z`
- 说明：真实文件路径已核对，独立补正保留CR审计不可变性

## 状态记录 · 2026-07-21T22:59:13Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：真实PITFALLS路径已登记第20条全局硬规则并与策略和Problem Registry互相绑定

## 状态记录 · 2026-07-21T23:51:44Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：批准范围已实现并由对应提交及R07候选/交付门禁验证。

## 状态记录 · 2026-07-21T23:51:47Z

- Actor：`codex-root-r07-007`
- Status：`CLOSED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：实现提交已推送，相关受影响门禁均通过。
