---
cr_id: CR-0141
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-008
session_id: SES-20260720T095830Z-752E5121
created_at: 2026-07-20T12:14:53Z
updated_at: 2026-07-20T12:14:57Z
---
# CR-0141 — 修复模拟器Runner逐行Shell语义并公开失败摘要

## 用户需求摘要

自动测试失败必须自行分析、修改、重新打包和重新测试

## 原规则

模拟器动作内嵌多行状态脚本，工具与连续性失败只上传需鉴权日志

## 新规则

模拟器动作只调用仓库内单一可执行Bash脚本；关键失败同时输出脱敏GitHub annotation并保留完整Artifact

## 修改原因

reactivecircus runner逐行以sh -c执行script，原多行脚本的cd变量和续行不共享，模拟器门禁退出127；公开API又只能看到退出码

## 影响摘要

修复模拟器退出127并使换电脑AI可从公开检查摘要直接定位失败

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `.github/workflows/ci.yml`
- `.github/workflows/continuity-gate.yml`
- `scripts/run_android_emulator_gate.sh`
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
- `bash -n scripts/run_android_emulator_gate.sh`

## 版本

- `R05`

## 迁移与兼容策略

不改变业务功能和测试旅程；仅调整CI执行封装与诊断通道

## 用户确认

用户要求自动测试失败后自行分析修改重新打包重新测试

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T12:14:57Z`
- 说明：依据用户长期自动修复重测授权修复已确认的Runner脚本语义错误

## 状态记录 · 2026-07-20T12:15:00Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T095830Z-752E5121`
- Note：实现单脚本模拟器门禁与公开失败摘要

## 状态记录 · 2026-07-20T13:20:00Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：GitHub CI #244完整通过，相关实现与回归证据已验证

## 状态记录 · 2026-07-20T13:20:17Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260720T095830Z-752E5121`
- Note：CI #244通过编译、Lint、单测、APK、模拟器旅程、四张截图、日志与候选资格门禁
