---
cr_id: CR-0494
status: APPROVED
requester_actor_id: codex-implementation
approver_actor_id: codex-independent-test-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T22:40:26Z
updated_at: 2026-07-29T22:40:56Z
---
# CR-0494 — 授权R14输入框成功回调修复唯一Attempt13

## 用户需求摘要

持续开发，由GitHub Actions完成每个大版本最终功能审核，不向项目所有者索要逐轮确认

## 原规则

Attempt12、CR-0492和request012已消费，不能在无新身份或无新修复提交时重复运行。

## 新规则

保持全局max_ai_attempts=3，只增加精确例外release=R14、attempt=13、request=R14-CANDIDATE-20260730-013、exception=CR-0494、required_fix_commit=c5695b9242fa751b83166f50e85c47109a0b0d62、max_candidate_runs=1；完整业务断言、四张截图、JUnit和目标进程日志审核不变。

## 修改原因

Attempt12已消费且首次揭示真实客户端回调绑定错误；CR-0493提交c5695b9242fa751b83166f50e85c47109a0b0d62已修复并通过受影响MODULE门禁，需要新身份执行一次完整候选旅程。

## 影响摘要

仅为CR-0493已验证的成功回调修复创建单次候选身份，不改变产品功能、接口、数据库或全局三轮上限。

## 影响文件

- `config/android-candidate-request.yaml`
- `config/android-automation.yaml`
- `tests/test_android_candidate_request.py`
- `tests/test_android_ci_gate.py`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R14 Attempt13精确候选身份`

## 资金/账本与历史数据

- `CR-0494候选例外登记`

## 测试

- `候选请求正确身份通过；错误release/request/CR/commit/attempt拒绝；单次运行上限`

## 版本

- `R14`

## 迁移与兼容策略

Attempt12和CR-0492保持已消费历史；Attempt13必须绑定c5695b92且只能运行一次，其他Release与历史例外不变。

## 用户确认

项目所有者已站立授权AI持续开发并自行完成大版本候选审核

## 审批

- 审批人：`codex-independent-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T22:40:56Z`
- 说明：CR-0493提供独立产品根因、代码变化及双向回归，允许一次精确Attempt13；不得复用Attempt12授权。

## 状态记录 · 2026-07-29T22:41:03Z

- Actor：`codex-implementation`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt13精确请求、例外白名单和负向治理回归。

## 状态记录 · 2026-07-29T22:43:23Z

- Actor：`codex-implementation`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt13精确候选请求、例外白名单及正负向治理回归均已实现并通过；授权提交fdcea6c2。
