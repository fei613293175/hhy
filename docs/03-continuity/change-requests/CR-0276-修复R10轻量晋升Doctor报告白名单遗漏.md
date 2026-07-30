---
cr_id: CR-0276
status: APPROVED
requester_actor_id: codex-root-r10-candidate
approver_actor_id: codex-independent-r10-promotion-reviewer
task_id: TASK-R10-007
session_id: SES-20260723T122953Z-52EBA51E
created_at: 2026-07-23T14:21:52Z
updated_at: 2026-07-23T14:22:25Z
---
# CR-0276 — 修复R10轻量晋升Doctor报告白名单遗漏

## 用户需求摘要

项目所有者要求持续推进并避免在GitHub模拟器上反复浪费时间。

## 原规则

轻量晋升允许连续性目录、Context Pack、会话/CR索引和Problem Registry等治理变化，但未允许严格Doctor每次自动刷新的artifacts/validation/project-doctor-v1.2.3.json。

## 新规则

轻量晋升治理白名单必须允许严格Doctor唯一机器报告artifacts/validation/project-doctor-v1.2.3.json，同时继续禁止任何产品代码；失败重试递增promotion_attempt并复用同一源Run、Commit、APK和四图SHA。

## 修改原因

Run 30015180600在下载Artifact前仅因严格Doctor自动报告未列入治理白名单而失败；需最小修复唯一晋升工作流并复用Run 30013677033。

## 影响摘要

修复Run 30015180600在Artifact下载前的确定性白名单误拒绝，新增静态回归和PROB记录；不改变产品功能、APK、截图、API或数据库。

## 影响文件

- `.github/workflows/android-baseline-promotion.yml`
- `tests/test_android_ci_gate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `tests/android/visual-baselines/R10/APPROVAL.yaml`

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

- `tests.test_android_ci_gate`

## 版本

- `R10`

## 迁移与兼容策略

无生产迁移；仅重跑轻量晋升，Run 30013677033及APK SHA保持不变。

## 用户确认

项目所有者要求GitHub确定性问题由AI自行修复且不得反复运行模拟器。

## 审批

- 审批人：`codex-independent-r10-promotion-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-23T14:22:25Z`
- 说明：独立复核确认失败发生在Artifact下载前，Doctor报告属于受控治理派生物；精确加入单文件白名单并保留产品路径拒绝，复用源候选是最小安全修复。
