---
cr_id: CR-0168
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T06:42:24Z
updated_at: 2026-07-21T06:42:28Z
---
# CR-0168 — 登记R06浏览器视觉与Release关闭门禁复发规则

## 用户需求摘要

项目所有者要求解决阻断并把可复用经验写入全局硬性规则。

## 原规则

问题登记尚未覆盖R06管理端截图暴露的原始枚举/排版缺陷和Release关闭门禁的固定操作数、候选Commit、Windows Git错误假设。

## 新规则

把两类根因、修复证据、回归命令和禁止复发项登记到PROBLEM_REGISTRY，后续电脑和AI必须优先复用。

## 修改原因

本轮修复了管理端技术枚举泄漏和关闭门禁三项确定性假设，需要进入问题登记供换电脑换AI直接复用。

## 影响摘要

仅新增可审计问题登记，不改变业务、契约、数据库、生产配置或APK。

## 影响文件

- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

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

- `python scripts/check_v123_documentation.py --strict`

## 版本

- `R06`

## 迁移与兼容策略

文档治理增量，无迁移；关联CR-0166和CR-0167。

## 用户确认

项目所有者要求把解决经验写入全局硬性规则供跨电脑跨AI复用。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T06:42:28Z`
- 说明：登记内容来自本轮可复现证据，范围为问题知识库且无产品行为变化。

## 状态记录 · 2026-07-21T07:13:46Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：已按批准范围实施并完成本地回归。

## 状态记录 · 2026-07-21T07:13:48Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：实现与证据已进入提交609c2ae6。

## 状态记录 · 2026-07-21T07:13:51Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：提交609c2ae6已推送，pre-push严格门禁通过。
