---
cr_id: CR-0161
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R06-006
session_id: SES-20260721T020225Z-397AF410
created_at: 2026-07-21T03:36:31Z
updated_at: 2026-07-21T03:37:01Z
---
# CR-0161 — 固化R06 Staging证据与回滚自动演练脚本

## 用户需求摘要

项目所有者要求持续开发并将可复用方案写入仓库，避免换电脑或换AI后重复浪费时间

## 原规则

R06 Staging现场步骤只能由聊天或一次性SSH命令编排，跨Windows与Linux时长命令容易被Shell转义破坏

## 新规则

R06 Staging基线采集、TraceId和日志脱敏、两组告警firing/resolved、同库卷应用回切与SHA证据必须由仓库内可执行Bash脚本完成；脚本仅操作显式R06 Compose project和精确测试事件

## 修改原因

Windows到Linux的长SSH内联命令发生确定性引号转义破坏，需要仓库脚本作为跨电脑事实源

## 影响摘要

新增单一R06现场验收脚本，不修改业务代码、API、数据库结构或既有环境；脚本默认只操作hhy-r06项目并精确清理自身测试事件

## 影响文件

- `scripts/run_r06_staging_acceptance.sh`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `仅查询现有表并插入后精确删除带r06-stage-alert前缀的隔离Outbox测试事件`

## 配置

- `R06隔离Staging现场验收参数`

## 资金/账本与历史数据

- `不访问资金或账本表`

## 测试

- `shell语法检查、精确Commit现场基线、告警和回滚演练`

## 版本

- `R06`

## 迁移与兼容策略

脚本为纯运维自动化；不执行迁移或降级DDL，停止脚本不影响正在运行的R06栈

## 用户确认

项目所有者要求可行方案写入全局硬性规则和仓库事实源，换电脑或换AI可直接复用并持续推进

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-21T03:37:01Z`
- 说明：该脚本将已批准的R06现场验收步骤固化为可审计、可重复执行入口，范围仅限隔离项目和精确测试事件

## 状态记录 · 2026-07-21T03:39:39Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T020225Z-397AF410`
- Note：跨Shell可复用R06现场验收脚本已实现，远端bash -n通过，准备提交精确Commit后执行

## 状态记录 · 2026-07-21T04:01:09Z

- Actor：`codex-root`
- Status：`SUPERSEDED`
- Session：`SES-20260721T020225Z-397AF410`
- Note：现场验证证明Outbox触发器禁止删除事件；原清理表述与不可变事实冲突，改由CR-0162以合法PENDING到PUBLISHING到PUBLISHED状态推进替代
