---
cr_id: CR-0185
status: APPROVED
requester_actor_id: codex-root-r07-005
approver_actor_id: codex-reviewer-windows-runtime
task_id: TASK-R07-005
session_id: SES-20260721T193434Z-0C5F0BFA
created_at: 2026-07-21T19:54:05Z
updated_at: 2026-07-21T19:54:10Z
---
# CR-0185 — 固化Windows受控Git用户环境与跨机恢复规则

## 用户需求摘要

项目所有者要求彻底解决PowerShell频繁缺少Git PATH，并写入踩坑记录和经验复用记录。

## 原规则

Windows脚本可自行回退到受控Git，但用户环境未持久化，裸git和第三方子进程仍会频繁报PATH缺失；换电脑只能依赖聊天经验手工注入。

## 新规则

Windows接手项目时必须一次运行仓库Git运行时配置脚本：优先验证显式HHY_GIT_BIN，其次系统Git，最后Codex受控运行时；把可执行文件写入用户级HHY_GIT_BIN并把其cmd目录幂等加入用户PATH，支持check回读。项目命令仍优先使用HHY_GIT_BIN或hhy_workflow入口，不因当前宿主进程未刷新而重复诊断。换电脑按START_HERE执行同一脚本。

## 修改原因

项目脚本虽已能解析HHY_GIT_BIN和Codex捆绑Git，但Windows用户级PATH与HHY_GIT_BIN从未持久化，导致裸git及Python子进程在每个新执行环境重复失败和临时注入。

## 影响摘要

新增可复用Windows Git环境配置脚本和机器可读传输恢复入口，补全START_HERE、统一开发规范、踩坑与Problem Registry；当前电脑用户环境同步完成。

## 影响文件

- `scripts/configure_windows_git_runtime.ps1`
- `config/REPOSITORY_TRANSPORT.yaml`
- `START_HERE.md`
- `docs/09-development/统一开发与交付效率规范.md`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Windows User PATH`
- `HHY_GIT_BIN`

## 资金/账本与历史数据

- `PROB-0069 Windows用户环境未持久化受控Git`

## 测试

- `PowerShell配置脚本install与check幂等回读`
- `裸git与Python subprocess均解析git 2.53.0`

## 版本

- `R07`

## 迁移与兼容策略

仅影响Windows用户级开发环境与工程文档；不修改系统级PATH、不写凭据、不改变Git远端、业务代码、API、数据库或发布行为。已有Codex进程可继续用HHY_GIT_BIN，重启后裸git直接生效。

## 用户确认

项目所有者明确要求彻底修复并计入全局踩坑及经验复用规则。

## 审批

- 审批人：`codex-reviewer-windows-runtime`
- 决定：`APPROVED`
- 时间：`2026-07-21T19:54:10Z`
- 说明：只写Windows用户级非秘密运行时路径，具备幂等检查和跨机恢复说明；不触及系统PATH、凭据或远端配置。

## 状态记录 · 2026-07-21T19:54:14Z

- Actor：`codex-root-r07-005`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T193434Z-0C5F0BFA`
- Note：当前电脑用户级PATH与HHY_GIT_BIN已持久化并回读，开始补仓库自动配置脚本和跨机硬规则。
