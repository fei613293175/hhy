---
cr_id: CR-0500
status: APPROVED
requester_actor_id: codex-r14-attempt16-requester
approver_actor_id: codex-r14-attempt16-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T00:18:59Z
updated_at: 2026-07-30T00:19:29Z
---
# CR-0500 — 授权R14解除恢复Compose验收唯一Attempt16候选

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

CR-0498只允许Attempt15验证独立UI线程指纹且已消费；不能用其身份重跑或触发Attempt16。

## 新规则

保持全局max_ai_attempts=3和所有已消费指纹边界；仅为Attempt15新暴露的解除后UiAutomator资源观察指纹增加精确例外：release=R14、attempt=16、request=R14-CANDIDATE-20260730-016、exception=CR-0500、required_fix_commit=9817630e、max_candidate_runs=1。错误身份或重复运行必须拒绝。

## 修改原因

Attempt15已通过前三图、拉黑禁发、重进禁发与解除HTTP 200，唯一失败是分支重建composer的UiAutomator资源导出不可见；CR-0499及9817630e切换到Compose唯一可见语义并补全真实拉黑再解除回归，33项治理回归和obx-test固定工具链231任务通过。已消费CR-0498不得复用，必须以唯一Request和单次上限登记独立观察指纹Attempt16。

## 影响摘要

只登记CR-0499观察面修复的一次候选身份；不修改App产品代码、API、数据库、旧指纹轮次或四图/JUnit/日志标准。

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

- `R14 Attempt16精确例外`

## 资金/账本与历史数据

- `CR-0500解除观察指纹单次候选授权`

## 测试

- `精确匹配R14/16/Request016/CR-0500/9817630e且全局上限仍为3；错误身份拒绝；治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；Attempt15和CR-0498保持已消费，Attempt16必须包含9817630e且只运行一次。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-attempt16-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T00:19:29Z`
- 说明：Attempt15服务端解除200、三图和无崩溃证明这是独立观察面问题；新授权绑定已验证Commit并只运行一次，不复用旧授权或放宽任何业务验收。

## 状态记录 · 2026-07-30T00:19:35Z

- Actor：`codex-r14-attempt16-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt16精确例外、候选请求和错误身份拒绝断言。

## 状态记录 · 2026-07-30T00:34:10Z

- Actor：`codex-r14-attempt16-requester`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt16精确授权、请求、错误身份拒绝测试和Changelog已提交；33项治理回归及请求验证通过。
