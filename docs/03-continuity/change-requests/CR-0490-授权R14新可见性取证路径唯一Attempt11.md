---
cr_id: CR-0490
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: codex-independent-candidate-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T20:52:38Z
updated_at: 2026-07-29T20:53:18Z
---
# CR-0490 — 授权R14新可见性取证路径唯一Attempt11

## 用户需求摘要

持续完成R14至R32；同一GitHub失败不得原样反复，但改变验证路径后由AI继续完成大版本候选

## 原规则

CR-0488只授权release=R14、attempt=10、request010、required_fix_commit=a4b88424和单次运行；Attempt10已真实执行并消费，且旧UiAutomator resource-id路径达到三轮上限，禁止复用请求、授权或原样Attempt11。

## 新规则

不改变全局max_ai_attempts=3，也不把Attempt11算作旧路径第四轮；只增加一个绑定新验证方案的精确例外：release=R14、attempt=11、request=R14-CANDIDATE-20260730-011、exception=CR-0490、required_fix_commit=c79a796721ba5357315d3cd7a45417be653a160e、max_candidate_runs=1。候选必须保留CR-0489三重证据、解除拉黑、长按删除、四张截图、JUnit和日志审核。

## 修改原因

Attempt10已作为旧UiAutomator resource-id路径第三且最终轮入库；CR-0489提交c79a7967建立Compose显示、用户文本和composer消失的新验证路径。候选请求、例外、精确修复Commit与单次运行必须重新唯一绑定，避免复用已消费的CR-0488。

## 影响摘要

只登记新验证路径的一次性候选授权与请求，拒绝旧路径重复、错误Commit、错误CR、错误Request或多次运行；不修改产品、后端、数据库、三轮全局上限或截图范围。

## 影响文件

- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `tests/test_android_ci_gate.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R14 Attempt11新路径唯一例外与request011`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_android_candidate_request tests.test_android_ci_gate tests.test_android_candidate_route`
- `candidate request validator exact binding`

## 版本

- `R14`

## 迁移与兼容策略

Attempt10及CR-0488保持已消费历史；Attempt11必须包含c79a7967且以新验证路径执行。其他Release和历史例外完全不变。

## 用户确认

项目所有者明确授权AI持续推进并自行审核大版本GitHub候选，同时要求相同问题不得反复浪费时间；新方案已经产生代码与证据变化。

## 审批

- 审批人：`codex-independent-candidate-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T20:53:18Z`
- 说明：独立复审通过：Attempt11只允许绑定CR-0489的新跨框架验证Commit并运行一次；旧resource-id路径三轮上限、全局上限和全部业务断言均未放宽。

## 状态记录 · 2026-07-29T20:53:24Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt11精确例外、request011及错误CR/Commit/Request/运行次数拒绝回归。
