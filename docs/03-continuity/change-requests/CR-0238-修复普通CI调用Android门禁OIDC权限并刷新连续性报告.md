---
cr_id: CR-0238
status: APPROVED
requester_actor_id: codex-root-r08-008
approver_actor_id: codex-reviewer-user-self-r08
task_id: TASK-R08-008
session_id: SES-20260722T093645Z-C7DB8EF0
created_at: 2026-07-22T12:15:22Z
updated_at: 2026-07-22T12:15:48Z
---
# CR-0238 — 修复普通CI调用Android门禁OIDC权限并刷新连续性报告

## 用户需求摘要

项目所有者要求持续开发且GitHub不得反复浪费；R08关闭前修复已确认的普通CI确定性权限错误及连续性报告过期。

## 原规则

普通提交仅运行受影响FAST/MODULE且不得启动模拟器；可复用Android质量门禁，但调用方必须显式授予被调用工作流所需最小OIDC权限。连续性权威报告必须与其来源脚本哈希一致。

## 新规则

不新增平行规则；修正ci.yml复用工作流的id-token:write最小权限，并用现有唯一自测入口重新生成生命周期和无对话重建报告。普通CI仍保持candidate=false且不启动模拟器。

## 修改原因

ci.yml复用android-quality-gate时未授予id-token:write，而被调用工作流需要OIDC启动认证；continuity.py修订后两份权威自测报告的来源哈希已过期。

## 影响摘要

修复GitHub复用工作流权限合同和两份已过期连续性报告；不改变产品功能、API、数据库、Android候选次数或生产配置。

## 影响文件

- `.github/workflows/ci.yml`
- `artifacts/validation/continuity-integration-v1.2.3.json`
- `artifacts/validation/continuity-integration-v1.2.3.log`
- `artifacts/validation/continuity-lifecycle-integration-v1.2.3.json`
- `artifacts/validation/continuity-lifecycle-integration-v1.2.3.log`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `github-actions-permissions`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python scripts/run_continuity_self_test.py`
- `python scripts/check_v123_continuity.py --strict`
- `静态断言ci.yml的android调用作业显式id-token:write且candidate=false`

## 版本

- `R08`

## 迁移与兼容策略

GitHub权限只增加质量门禁现有OIDC步骤所需id-token写权限，contents保持只读；报告原路径覆盖更新，格式与严格校验器兼容。

## 用户确认

CONFIRMED_BY_OWNER_CONTINUOUS_DEVELOPMENT_AND_GITHUB_EFFICIENCY_RULE

## 审批

- 审批人：`codex-reviewer-user-self-r08`
- 决定：`APPROVED`
- 时间：`2026-07-22T12:15:48Z`
- 说明：批准确定性CI权限合同修复和权威报告刷新；禁止借此触发候选模拟器或扩展产品功能。

## 状态记录 · 2026-07-22T12:15:56Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：开始修复普通CI最小OIDC权限并刷新连续性权威自测报告。

## 状态记录 · 2026-07-22T12:23:56Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：ci.yml最小OIDC权限已修复；连续性生命周期与无对话重建报告均由唯一入口重新生成PASS。
