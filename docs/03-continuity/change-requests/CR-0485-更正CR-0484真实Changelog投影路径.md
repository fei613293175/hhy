---
cr_id: CR-0485
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: project-owner-continuity-directive-20260730
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T18:45:32Z
updated_at: 2026-07-29T18:46:00Z
---
# CR-0485 — 更正CR-0484真实Changelog投影路径

## 用户需求摘要

项目所有者要求持续修复R14候选并将全部有效事实写入仓库，禁止停下或依赖聊天。

## 原规则

CR-0484的impact.files误登记不存在的docs/10-governance/CHANGELOG.md，导致真实用户可见修复没有合法根Changelog投影。

## 新规则

CR-0484的拉黑状态发布修复必须更新仓库根CHANGELOG.md；禁止创建不存在的并列Changelog路径，CR-0485仅更正本次事实投影。

## 修改原因

CR-0484误把变更日志路径登记为不存在的docs/10-governance/CHANGELOG.md；pre-commit正确要求用户可见修复更新仓库根CHANGELOG.md，必须以补充CR更正而非伪造文件或绕过Hook。

## 影响摘要

只增加根CHANGELOG.md中的R14 Attempt7根因、apply修复和Attempt8前置验证记录，不改变产品行为、API、数据库、门禁或候选断言。

## 影响文件

- `CHANGELOG.md`

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

- `pre-commit strict gate requires and validates root CHANGELOG for user-visible change`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；保留CR-0484原记录作为审计事实，以CR-0485补充正确投影路径。

## 用户确认

项目所有者明确要求持续开发、无需逐次批准，并要求换AI后仅凭仓库继续。

## 审批

- 审批人：`project-owner-continuity-directive-20260730`
- 决定：`APPROVED`
- 时间：`2026-07-29T18:46:00Z`
- 说明：项目所有者已授权持续开发且要求规则与修复写入仓库；本CR只纠正真实Changelog路径，不扩大产品或降低门禁。
