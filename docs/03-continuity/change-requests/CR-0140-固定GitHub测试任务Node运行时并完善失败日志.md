---
cr_id: CR-0140
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T11:50:25Z
updated_at: 2026-07-20T11:50:29Z
---
# CR-0140 — 固定GitHub测试任务Node运行时并完善失败日志

## 用户需求摘要

自动测试失败必须自行分析、修改、重新打包和重新测试

## 原规则

tooling和contracts任务依赖ubuntu-latest隐式Node环境，失败时缺少持久测试日志

## 新规则

GitHub所有调用Node的质量任务显式安装Node 24，并持久上传失败或成功日志

## 修改原因

GitHub tooling与contracts并行失败，而含Node的干净Linux镜像187项测试通过，需显式安装Node并输出可追踪日志

## 影响摘要

消除Runner镜像差异并缩短后续自动诊断时间

## 影响文件

- `.github/workflows/ci.yml`
- `tests/test_android_ci_gate.py`

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

- `python -m unittest tests.test_android_ci_gate`

## 版本

- `R05`

## 迁移与兼容策略

不改变产品逻辑，仅固定CI工具链；Node 24与现有前端任务保持一致

## 用户确认

用户明确要求自动测试失败后自行分析修改重新打包重新测试

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T11:50:29Z`
- 说明：依据用户自动修复重测与长期可复用体系授权执行确定性CI环境修复

## 状态记录 · 2026-07-20T11:50:32Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：开始补齐Node 24和诊断日志门禁

## 状态记录 · 2026-07-20T13:19:58Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：GitHub CI #244完整通过，相关实现与回归证据已验证

## 状态记录 · 2026-07-20T13:20:15Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：CI #244通过编译、Lint、单测、APK、模拟器旅程、四张截图、日志与候选资格门禁
