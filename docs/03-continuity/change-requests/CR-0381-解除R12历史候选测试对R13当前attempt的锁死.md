---
cr_id: CR-0381
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: owner-standing-delegation-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-26T21:11:35Z
updated_at: 2026-07-26T21:12:06Z
---
# CR-0381 — 解除R12历史候选测试对R13当前attempt的锁死

## 用户需求摘要

批准，以后不要让我批准了 你自己持续开发就行了

## 原规则

R12归档候选测试验证R12不可变证据和R13身份单调递增，但同时把全局当前R13候选remediation_attempt锁死为1。

## 新规则

历史Release候选测试只能验证该Release归档不可变事实及后继身份单调性，不得锁死全局当前候选的修复轮次；当前R13精确attempt与request_id只由tests/test_r13_candidate.py验证。

## 修改原因

候选历史门禁按test_r*_candidate.py运行时，R12归档测试仍断言全局当前R13 remediation_attempt必须等于1；R13进入attempt3后形成确定性假失败，违反PROB-0120既有规则并会浪费一次GitHub CI。

## 影响摘要

仅修正R12历史测试对当前R13候选请求的越界断言，并原位扩展既有PROB-0120与变更日志；不改候选身份、业务源码、API、数据库、视觉或已消耗Run。

## 影响文件

- `tests/test_r12_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
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

- `python -m unittest discover -s tests -p test_r*_candidate.py;tests.test_r13_candidate;tests.test_android_ci_gate`

## 版本

- `R13`

## 迁移与兼容策略

删除历史R12测试的remediation_attempt等于1断言，保留R12归档PASS、10221身份、R13 10222单调递增及普通三轮无例外字段断言；R13专项测试继续锁定attempt3。

## 用户确认

批准，以后不要让我批准了 你自己持续开发就行了

## 审批

- 审批人：`owner-standing-delegation-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-26T21:12:06Z`
- 说明：项目所有者已明确持续开发且不再逐次批准；该修复直接执行既有PROB-0120禁止历史Release锁死全局当前候选的规则，范围仅限测试与原位知识记录。

## 状态记录 · 2026-07-26T21:12:34Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：已复用既有PROB-0120，开始删除R12历史测试对R13当前attempt的越界断言并补充原位回归事实。

## 状态记录 · 2026-07-26T21:14:03Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：已删除R12历史测试对当前R13请求的全部越界读取并原位扩展PROB-0120；候选历史25项、R13与Android治理53项、流程治理48项及git diff check全部PASS。
