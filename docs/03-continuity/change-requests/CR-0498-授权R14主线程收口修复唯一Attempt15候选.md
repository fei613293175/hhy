---
cr_id: CR-0498
status: APPROVED
requester_actor_id: codex-r14-candidate-requester
approver_actor_id: codex-r14-candidate-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T23:44:22Z
updated_at: 2026-07-29T23:44:49Z
---
# CR-0498 — 授权R14主线程收口修复唯一Attempt15候选

## 用户需求摘要

持续开发并由AI完成每个大版本GitHub候选审核，不要求项目所有者逐项核实

## 原规则

CR-0496只允许composer同指纹第三且最终Attempt14；该授权已消费且不得用于任何Attempt15。

## 新规则

保持composer旧指纹禁止继续的规则和全局max_ai_attempts=3；仅为Attempt14之后首次出现的CalledFromWrongThreadException独立指纹增加精确例外：release=R14、attempt=15、request=R14-CANDIDATE-20260730-015、exception=CR-0498、required_fix_commit=2ad71a06、max_candidate_runs=1。若身份不匹配或重复运行必须拒绝。

## 修改原因

Attempt14已经通过composer严格空值并关闭旧指纹，随后首次暴露CalledFromWrongThreadException；CR-0497及2ad71a06以主线程统一收口修复该独立新指纹，并通过35项治理回归与obx-test固定工具链244任务。已消费CR-0496不能复用，必须用全局唯一Request和单次上限登记Attempt15。

## 影响摘要

只登记CR-0497新线程修复的一次候选身份；不修改产品功能、API、数据库、旧指纹轮次或四图/JUnit/日志验收。

## 影响文件

- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_ci_gate.py`
- `CHANGELOG.md`

## 页面

- `R14 GitHub候选请求入口`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `R14 Attempt15精确例外`

## 资金/账本与历史数据

- `CR-0498新线程指纹单次候选授权`

## 测试

- `候选解析必须精确匹配R14/15/Request015/CR-0498/2ad71a06且全局上限仍为3；错误身份拒绝；治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；Attempt14和CR-0496保持已消费历史，Attempt15必须包含2ad71a06且仅运行一次。

## 用户确认

项目所有者已授权AI持续开发并自行审核大版本GitHub候选

## 审批

- 审批人：`codex-r14-candidate-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T23:44:49Z`
- 说明：Attempt14截图和运行日志证明composer旧指纹已经通过，随后才出现独立CalledFromWrongThreadException；新授权绑定已验证修复Commit且只运行一次，不放宽旧指纹或全局三轮。

## 状态记录 · 2026-07-29T23:44:56Z

- Actor：`codex-r14-candidate-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt15精确候选例外、请求文件与治理断言。
