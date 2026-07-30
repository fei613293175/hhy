---
cr_id: CR-0167
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T06:30:15Z
updated_at: 2026-07-21T06:30:51Z
---
# CR-0167 — 修复Release关闭门禁的操作数候选提交与Windows Git解析

## 用户需求摘要

项目所有者要求解决阻断并持续开发，同时要求规则可跨电脑跨AI复用。

## 原规则

关闭门禁固定要求3个operationId、把APK候选Commit与Release关闭Commit直接比较，并调用PATH中的git。

## 新规则

关闭门禁要求至少一个且全部有效的operationId；APK与自动候选均绑定android_delivery.source_commit并彼此一致，Release关闭Commit独立追溯；Git依次解析HHY_GIT_BIN、PATH和Codex受控运行时。owner_physical_test仍按正式验收规则保持硬门禁，不得放宽。

## 修改原因

R06机器关闭检查证明关闭脚本硬编码3个operationId、错误要求APK候选Commit等于后续Release关闭Commit，并在Windows忽略受控Git路径；这些会制造确定性假失败。

## 影响摘要

修复跨Release、跨电脑确定性假失败，增加多operationId和候选Commit早于关闭Commit的隔离回归；不改变所有者真机验收门禁。

## 影响文件

- `scripts/check_release_artifacts.py`
- `tests/test_release_close_gate.py`
- `docs/09-development/统一开发与交付效率规范.md`

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

- `python -m unittest tests.test_release_close_gate`
- `python scripts/check_release_artifacts.py --release R06`

## 版本

- `R06`

## 迁移与兼容策略

既有仅3操作版本继续通过；R06及后续任意正数operationId均按OpenAPI校验；旧Manifest没有android_delivery.source_commit时回退APK Manifest commit但仍要求候选证据一致。

## 用户确认

项目所有者要求解决当前阻断、跨电脑跨AI复用并持续进入后续版本。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T06:30:51Z`
- 说明：修复仅去除错误假设并保留owner真机正式验收硬门禁；隔离测试覆盖多操作与候选/关闭提交分离。

## 状态记录 · 2026-07-21T07:13:38Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：已按批准范围实施并完成本地回归。

## 状态记录 · 2026-07-21T07:13:40Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：实现与证据已进入提交609c2ae6。

## 状态记录 · 2026-07-21T07:13:43Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：提交609c2ae6已推送，pre-push严格门禁通过。
