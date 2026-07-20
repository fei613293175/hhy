---
cr_id: CR-0143
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T12:25:46Z
updated_at: 2026-07-20T12:25:50Z
---
# CR-0143 — 修复Linux临时Worktree禁止提交Hook执行位

## 用户需求摘要

自动测试失败必须自行分析、修改、重新打包和重新测试

## 原规则

临时Worktree只写入pre-commit和pre-push内容，未在POSIX系统赋可执行位

## 新规则

非Windows系统创建临时Hook后必须chmod 700并验证成功，任何失败关闭执行

## 修改原因

GitHub Tooling精确失败为parallel worktree回归；Linux上动态生成Hook默认为0644，导致委托工作树可绕过禁止提交门禁

## 影响摘要

恢复Linux委托工作树禁止提交和推送的安全边界

## 影响文件

- `scripts/prepare_parallel_worktrees.ps1`
- `tests/test_parallel_worktrees.py`

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

- `python -m unittest tests.test_parallel_worktrees`

## 版本

- `R05`

## 迁移与兼容策略

Windows行为不变；Linux和macOS补齐标准POSIX执行权限

## 用户确认

用户要求自动测试失败后自行修复且换电脑AI保持规则

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T12:25:50Z`
- 说明：依据长期跨电脑跨AI与自动修复授权处理Linux安全门禁缺陷

## 状态记录 · 2026-07-20T12:25:54Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：补齐POSIX临时Hook执行权限并回归
