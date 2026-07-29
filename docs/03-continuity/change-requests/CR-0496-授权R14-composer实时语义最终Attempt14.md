---
cr_id: CR-0496
status: APPROVED
requester_actor_id: codex-implementation
approver_actor_id: codex-independent-test-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T23:10:48Z
updated_at: 2026-07-29T23:11:24Z
---
# CR-0496 — 授权R14 composer实时语义最终Attempt14

## 用户需求摘要

持续开发和自动候选审核，不向项目所有者索要逐轮批准

## 原规则

Attempt13、CR-0494和request013已消费；同一composer空值错误指纹已运行两轮。

## 新规则

保持全局max_ai_attempts=3，只增加同指纹第三且最终精确例外release=R14、attempt=14、request=R14-CANDIDATE-20260730-014、exception=CR-0496、required_fix_commit=7eeb1051bce7fe0f96bf04e1bfe75d7234a26ac0、max_candidate_runs=1。若仍为同指纹，禁止Attempt15；全部R14业务、四图、JUnit和日志审核不变。

## 修改原因

Attempt12与13已形成同一composer空值指纹前两轮；CR-0495提交`7eeb1051bce7fe0f96bf04e1bfe75d7234a26ac0`切换为Compose实时空值验收并通过治理回归及App AndroidTest编译，按三轮上限只允许一次最终候选。

## 影响摘要

仅授权CR-0495新Compose观察面的一次最终候选，不改变产品、接口、数据库或全局规则。

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

- `R14 Attempt14最终精确候选身份`

## 资金/账本与历史数据

- `CR-0496同指纹最终轮登记`

## 测试

- `正确身份通过；五类绑定漂移拒绝；单次运行；全局上限保持3`

## 版本

- `R14`

## 迁移与兼容策略

Attempt12和13保持已消费历史；Attempt14必须绑定7eeb1051且仅运行一次，其他Release与例外不变。

## 用户确认

项目所有者已站立授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-independent-test-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T23:11:24Z`
- 说明：CR-0495改变了验证观察面且不弱化业务标准，允许同指纹第三且最终轮；必须显式禁止后续同指纹Attempt15。

## 状态记录 · 2026-07-29T23:11:31Z

- Actor：`codex-implementation`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：写入Attempt14最终精确身份和正负向治理回归。
