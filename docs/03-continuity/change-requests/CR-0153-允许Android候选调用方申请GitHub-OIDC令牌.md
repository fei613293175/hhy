---
cr_id: CR-0153
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R06-004
session_id: SES-20260720T173038Z-35648A77
created_at: 2026-07-20T21:41:03Z
updated_at: 2026-07-20T21:41:07Z
---
# CR-0153 — 允许Android候选调用方申请GitHub OIDC令牌

## 用户需求摘要

项目所有者批准使用GitHub OIDC短时会话实现不中断的实际页面自动测试

## 原规则

Android Candidate Request质量作业仅授予contents read和issues write，不能签发OIDC身份

## 新规则

仅Android候选质量调用作业增加id-token write，用于向固定Staging端点申请仓库、工作流、Commit和Run强绑定的一次性引导码；不授予其他写权限

## 修改原因

可复用质量工作流不能获得高于调用作业的id-token权限，候选调用方必须显式授予最小id-token write

## 影响摘要

单文件最小权限补齐，不改变触发条件、构建内容或仓库写权限

## 影响文件

- `.github/workflows/android-candidate-request.yml`

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

- `workflow static permission contract;GitHub OIDC bootstrap integration`

## 版本

- `R06-R32`

## 迁移与兼容策略

无迁移；普通CI和非候选作业不获得OIDC权限；后端关闭自动认证时请求失败关闭

## 用户确认

项目所有者批准使用GitHub OIDC自动登录并要求持续开发不等待人工核实

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T21:41:07Z`
- 说明：依据本轮明确授权批准候选调用作业最小OIDC权限，不扩大仓库或生产权限

## 状态记录 · 2026-07-20T21:41:10Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T173038Z-35648A77`
- Note：补齐候选调用方id-token write权限
