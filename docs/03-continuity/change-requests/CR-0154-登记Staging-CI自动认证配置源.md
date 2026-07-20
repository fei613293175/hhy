---
cr_id: CR-0154
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R06-004
session_id: SES-20260720T173038Z-35648A77
created_at: 2026-07-20T21:53:43Z
updated_at: 2026-07-20T21:53:47Z
---
# CR-0154 — 登记Staging CI自动认证配置源

## 用户需求摘要

项目所有者批准OIDC自动登录并要求跨电脑持续开发

## 原规则

配置注册表没有CI自动认证开关、测试身份和GitHub OIDC绑定项

## 新规则

五项CI自动认证配置进入机器注册表和CSV事实源；默认关闭、测试手机号按秘密处理、其余OIDC身份字段固定Staging启动配置，生产不得启用

## 修改原因

CR-0152新增五项启动配置，CONFIG_REGISTRY声明catalogs/config_registry.csv为事实源，必须同步登记

## 影响摘要

仅补齐配置元数据和事实源，不启用运行时功能或写入秘密值

## 影响文件

- `config/CONFIG_REGISTRY.yaml`
- `catalogs/config_registry.csv`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `ci.automation.enabled;ci.automation.user_phone;ci.automation.github_repository;ci.automation.github_workflow;ci.automation.oidc_audience`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `check_config_registry.py;documentation registry checks`

## 版本

- `R06-R32`

## 迁移与兼容策略

默认关闭且秘密默认值为空；部署通过受控环境变量注入，生产配置保持禁用

## 用户确认

项目所有者批准持续自动登录测试且不等待人工核实

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T21:53:47Z`
- 说明：依据OIDC自动登录授权批准最小配置登记，秘密值仍不得进入仓库

## 状态记录 · 2026-07-20T21:53:51Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T173038Z-35648A77`
- Note：同步机器注册表与CSV事实源
