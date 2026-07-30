---
cr_id: CR-0509
status: APPROVED
requester_actor_id: codex-r14-attempt20-requester
approver_actor_id: codex-r14-attempt20-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T02:57:29Z
updated_at: 2026-07-30T02:57:40Z
---
# CR-0509 — 授权R14 App模块范围修复唯一Attempt20候选

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

CR-0507只允许Attempt19且已经消费，不得复用于Attempt20；Attempt19在App业务旅程完成后因未限定模块的connectedDebugAndroidTest把App测试类过滤器传给feature:chat而失败。

## 新规则

保持全局max_ai_attempts=3和所有业务、视觉、日志断言；仅为CR-0508模块范围修复增加精确例外：release=R14、attempt=20、request=R14-CANDIDATE-20260730-020、exception=CR-0509、required_fix_commit=02201855、max_candidate_runs=1。错误身份或重复运行必须拒绝。

## 修改原因

Attempt19的App级ReleaseCandidateSmokeTest全部业务操作与四张截图已通过，唯一失败为根Gradle任务把App测试类过滤器继续派发到feature:chat而ClassNotFoundException。CR-0508已用Git实际提交限定为:app:connectedDebugAndroidTest并通过35项治理回归、bash语法和连续性门禁；需要一次精确身份候选验证修复。

## 影响摘要

只登记CR-0508测试任务范围修复的一次候选身份；不修改App产品代码、API、数据库、截图、JUnit、崩溃/ANR或视觉标准。

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

- `R14 Attempt20精确例外`

## 资金/账本与历史数据

- `CR-0509 App模块范围修复单次候选授权`

## 测试

- `精确匹配R14/20/Request020/CR-0509/02201855且全局上限仍为3；错误身份拒绝；35项治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；Attempt19保持已消费，Attempt20必须包含Git实际CR-0508实现Commit并仅运行一次；功能模块自身测试继续由常规模块门禁执行。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-attempt20-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T02:57:40Z`
- 说明：Attempt19的App业务旅程和四图已通过；把测试类过滤器限定到唯一拥有该类的App模块是最小且可回归的基础设施修复，新候选仅运行一次。

## 状态记录 · 2026-07-30T02:57:46Z

- Actor：`codex-r14-attempt20-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt20精确例外、候选请求及错误身份拒绝断言；实现提交必须从Git rev-parse取得并绑定CR-0508。

## 状态记录 · 2026-07-30T03:01:13Z

- Actor：`codex-primary`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt20唯一候选授权已由Commit e8ac4698a5219d2c9170848a54baa88f44109b0c实现；request020严格绑定CR-0509与CR-0508完整修复Commit 0220185599581170af24db6ca575592da940e9f4，35项治理回归及请求校验通过。
