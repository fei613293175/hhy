---
cr_id: CR-0504
status: APPROVED
requester_actor_id: codex-r14-attempt18-requester
approver_actor_id: codex-r14-attempt18-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T01:47:55Z
updated_at: 2026-07-30T01:48:22Z
---
# CR-0504 — 授权R14列表文字Compose验收唯一Attempt18候选

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

CR-0502只允许Attempt17验证删除后空状态Compose观察修复且已经消费；不得复用其身份重跑或触发Attempt18。

## 新规则

保持全局max_ai_attempts=3和所有已消费指纹边界；仅为Attempt17新暴露的peer-name UiAutomator资源导出指纹增加精确例外：release=R14、attempt=18、request=R14-CANDIDATE-20260730-018、exception=CR-0504、required_fix_commit=d2d803831ae0def0902b2aff6362c39f68c8286f、max_candidate_runs=1。错误身份或重复运行必须拒绝。

## 修改原因

Attempt17已通过请求、构建、OIDC、会话兑换、用户、首页和两次会话GET，唯一失败是UiAutomator无法找到peer-name资源。CR-0503及d2d80383改为Compose唯一节点精确文字验收，35项治理和obx-test固定工具链231任务通过；已消费CR-0502不得复用。

## 影响摘要

只登记CR-0503观察面修复的一次候选身份；不修改App业务、API、数据库、旧指纹轮次或四图/JUnit/日志标准。

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

- `R14 Attempt18精确例外`

## 资金/账本与历史数据

- `CR-0504列表文字Compose观察指纹单次候选授权`

## 测试

- `精确匹配R14/18/Request018/CR-0504/d2d80383且全局上限仍为3；错误身份拒绝；治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；Attempt17和CR-0502保持已消费，Attempt18必须包含d2d80383且只运行一次。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-attempt18-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T01:48:22Z`
- 说明：Attempt17所有后端前置请求均200且页面根可见，失败仅在跨框架resource-id观察；新授权绑定已编译验证的Compose精确文字修复并只运行一次，不复用旧授权。

## 状态记录 · 2026-07-30T01:48:29Z

- Actor：`codex-r14-attempt18-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始写入Attempt18精确例外、候选请求和错误身份拒绝断言。
