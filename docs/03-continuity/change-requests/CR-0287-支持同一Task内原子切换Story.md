---
cr_id: CR-0287
status: APPROVED
requester_actor_id: codex-root-r11-client
approver_actor_id: codex-independent-continuity-reviewer
task_id: TASK-R11-004
session_id: SES-20260723T183130Z-454A6E0D
created_at: 2026-07-23T19:15:26Z
updated_at: 2026-07-23T19:15:49Z
---
# CR-0287 — 支持同一Task内原子切换Story

## 用户需求摘要

项目所有者要求按R01-R32依赖持续开发，换电脑换AI也必须从仓库无缝接续。

## 原规则

ACTIVE Session的story_id仅在start时写入，协议没有同一Task内切换Story的合法操作；close总是关闭整个Task。

## 新规则

新增story-switch命令：仅当前Actor、ACTIVE会话、同一Task、干净工作树、最新检查点已提交且目标Story为READY_FOR_IMPLEMENTATION时，原子更新Session/Active pointer/Claim/索引/CURRENT_STATUS/事件链/Context Pack；Task保持IN_PROGRESS且不得写DONE或关闭Claim。

## 修改原因

TASK-R11-004包含四个独立Story，现有协议仅能在start时固定story_id；close会误把整个Task标为DONE，无法在不伪造任务状态的前提下继续详情与编辑Story。

## 影响摘要

修复多Story Task无法逐Story审计推进的连续性缺口，并为R11详情与编辑提供合法切换路径。

## 影响文件

- `scripts/continuity.py`
- `scripts/continuity_lib.py`
- `scripts/test_continuity_protocol.py`
- `.continuity/CONTINUITY_POLICY.yaml`
- `docs/09-development/统一开发与交付效率规范.md`
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

- `continuity story-switch unit and isolated lifecycle tests`
- `strict continuity gate`

## 版本

- `R11`

## 迁移与兼容策略

不改变现有start/close语义；仅新增可选命令。旧会话可在满足前置条件时切换，旧仓库记录无需迁移。

## 用户确认

项目所有者要求持续按依赖开发且任何AI可从仓库事实无缝接续。

## 审批

- 审批人：`codex-independent-continuity-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-23T19:15:49Z`
- 说明：独立复核：原子切换不关闭Task/Claim，要求干净已提交检查点并同步全部索引和事件，避免重复事实源。

## 状态记录 · 2026-07-23T19:49:30Z

- Actor：`codex-root-r11-client`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T183130Z-454A6E0D`
- Note：story-switch、Session级单调Checkpoint序号和历史Commit身份校验已实现；完整生命周期pre-push/CI均PASS，准备形成实现检查点

## 状态记录 · 2026-07-23T19:59:25Z

- Actor：`codex-root-r11-client`
- Status：`IMPLEMENTED`
- Session：`SES-20260723T183130Z-454A6E0D`
- Note：原子Story切换、Session级单调Checkpoint、历史Commit身份校验完成；15项生命周期和12项重建集成均PASS
