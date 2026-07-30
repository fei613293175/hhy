---
cr_id: CR-0169
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-reviewer
task_id: TASK-R06-008
session_id: SES-20260721T055708Z-741C3D49
created_at: 2026-07-21T06:47:01Z
updated_at: 2026-07-21T06:47:36Z
---
# CR-0169 — 修复连续性严格门禁忽略受控Git路径

## 用户需求摘要

项目所有者要求解决阻断并持续开发，规则必须跨电脑跨AI复用。

## 原规则

连续性严格门禁使用PATH中的git ls-files，忽略仓库统一运行时和HHY_GIT_BIN。

## 新规则

连续性严格门禁依次解析HHY_GIT_BIN、系统PATH和Codex捆绑Git；配置的覆盖路径不存在时明确失败，禁止静默回退。

## 修改原因

check_v123_continuity.py在Windows硬编码git ls-files并忽略HHY_GIT_BIN，导致严格门禁和Doctor在断言前确定性失败。

## 影响摘要

只修复连续性严格检查的Git可执行文件解析并新增隔离单测；门禁断言和跟踪文件规则不变。

## 影响文件

- `scripts/check_v123_continuity.py`
- `tests/test_continuity_git_resolution.py`
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

- `python -m unittest tests.test_continuity_git_resolution`
- `python scripts/check_v123_continuity.py --strict`

## 版本

- `R06`

## 迁移与兼容策略

跨平台兼容增量；Linux/macOS继续使用PATH，Windows可使用HHY_GIT_BIN或捆绑Git。

## 用户确认

项目所有者要求解决当前阻断并形成跨电脑跨AI可复用规则。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T06:47:36Z`
- 说明：修复与统一运行时策略一致，保留全部连续性断言并新增覆盖路径测试。

## 状态记录 · 2026-07-21T07:13:54Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：已按批准范围实施并完成本地回归。

## 状态记录 · 2026-07-21T07:13:56Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：实现与证据已进入提交609c2ae6。

## 状态记录 · 2026-07-21T07:13:59Z

- Actor：`codex-root`
- Status：`CLOSED`
- Session：`SES-20260721T055708Z-741C3D49`
- Note：提交609c2ae6已推送，pre-push严格门禁通过。
