---
cr_id: CR-0078
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-19T05:05:33Z
updated_at: 2026-07-19T05:05:35Z
---
# CR-0078 — 修复历史检查点对Git重命名折叠的误判

## 用户需求摘要

用户要求持续推进且不得因自动门禁错误停止开发

## 原规则

历史项目指纹使用默认Git diff重命名检测，重命名只返回新路径

## 新规则

历史项目指纹使用--no-renames，删除路径和新增路径分别参与指纹，与检查点工作区语义一致

## 修改原因

历史指纹复算启用Git重命名检测时会漏掉原删除路径，导致合法APK清单归档提交被误报CHECKPOINT_STALE

## 影响摘要

修复预推送门禁的假失败，并增加针对重命名归档场景的单元测试

## 影响文件

- `scripts/continuity_gate.py`
- `tests/test_continuity_historical_fingerprint.py`

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

- `历史指纹重命名回归测试、严格pre-commit/pre-push`

## 版本

- `R03`

## 迁移与兼容策略

仅连续性验证工具变化；不改业务代码、历史提交或远端分支；真实内容变化仍会触发过期检查点

## 用户确认

用户明确要求解决自动停止问题并持续推进开发

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T05:05:35Z`
- 说明：门禁应保留删除与新增两侧路径，禁止重命名折叠造成假停止；修复不放宽实际哈希一致性

## 状态记录 · 2026-07-19T05:05:36Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：为历史指纹禁用rename折叠并补回归测试
