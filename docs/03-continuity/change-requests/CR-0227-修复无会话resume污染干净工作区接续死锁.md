---
cr_id: CR-0227
status: APPROVED
requester_actor_id: codex-root-r08-007
approver_actor_id: codex-reviewer-continuity-r08
task_id: TASK-R08-007
session_id: SES-20260722T064621Z-68304DE1
created_at: 2026-07-22T06:48:43Z
updated_at: 2026-07-22T06:49:06Z
---
# CR-0227 — 修复无会话resume污染干净工作区接续死锁

## 用户需求摘要

项目所有者要求换电脑换AI后只说继续开发即可，所有规则自动生效且不得创建并列事实源。

## 原规则

现有唯一冷启动规则要求先运行resume并在干净工作区start；resume无条件调用build_context_pack并写入STATE、Context Pack，关闭元数据Commit改变仓库树后会产生未提交刷新。

## 新规则

不新增平行冷启动规则；直接修复既有resume：无ACTIVE Session时只读验证事件链、全部规则来源哈希、NEXT_TASK、Git与现有Context文件完整性，禁止写工作区；start成功后再由活动Session生成新Context Pack。ACTIVE/HANDED_OFF恢复行为保持原有机制。

## 修改原因

TASK-R08-006关闭提交推送后，resume重建Context Pack并写入STATE和三份Context文件；start随后因工作区不干净拒绝，且无ACTIVE Session导致刷新内容无法通过提交同步记录门禁。

## 影响摘要

修复一句继续开发在任务边界的确定性死锁；登记PROB-0079并增加无会话resume不调用写入式Context构建、连续两次resume工作区仍干净、随后start成功的回归。

## 影响文件

- `scripts/continuity.py`
- `tests/test_continuity_resume_read_only.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
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

- `python -m unittest tests.test_continuity_resume_read_only`
- `python -m unittest tests.test_context_pack_parallel_policy`
- `python scripts/continuity_gate.py --mode doctor --strict`

## 版本

- `R08`

## 迁移与兼容策略

仅连续性CLI行为修复，无产品API、数据库、APK或生产配置变化；旧Context Pack仍校验文件与规则来源哈希，忽略仅由关闭Commit造成的仓库树指纹滞后，start后在活动会话中刷新。

## 用户确认

项目所有者明确要求换电脑换AI后只需说继续开发，所有规则必须生效。

## 审批

- 审批人：`codex-reviewer-continuity-r08`
- 决定：`APPROVED`
- 时间：`2026-07-22T06:49:06Z`
- 说明：批准修复既有唯一resume入口，不新建规则文件；无会话只读路径仍必须验证事件链、规则来源和Context文件，且回归证明resume后可直接start。

## 状态记录 · 2026-07-22T06:49:10Z

- Actor：`codex-root-r08-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T064621Z-68304DE1`
- Note：开始修复无ACTIVE Session的resume只读接续路径并增加PROB-0079回归。

## 状态记录 · 2026-07-22T06:53:33Z

- Actor：`codex-root-r08-007`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T064621Z-68304DE1`
- Note：无会话resume只读实现与3项专用、13项既有规则测试通过；在827c8b7b真实关闭态叠加修复后连续两次resume均保持四个目标文件和git status不变，返回repository_mutated=false及唯一TASK-R08-007 start命令。

## 状态记录 · 2026-07-22T06:54:06Z

- Actor：`codex-root-r08-007`
- Status：`CLOSED`
- Session：`SES-20260722T064621Z-68304DE1`
- Note：源实现已推送，真实关闭态连续resume零写入复验和全部回归通过；PROB-0079已形成唯一根因、修复和防复发记录，无遗留项。
