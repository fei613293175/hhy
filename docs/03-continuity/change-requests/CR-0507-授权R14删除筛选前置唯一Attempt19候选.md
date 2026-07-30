---
cr_id: CR-0507
status: APPROVED
requester_actor_id: codex-r14-attempt19-requester
approver_actor_id: codex-r14-attempt19-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T02:27:28Z
updated_at: 2026-07-30T02:28:00Z
---
# CR-0507 — 授权R14删除筛选前置唯一Attempt19候选

## 用户需求摘要

持续开发并由AI完成R14候选审核，不要求项目所有者逐项确认

## 原规则

CR-0504只允许Attempt18且已经消费；CR-0506因错误哈希已在写配置和推送前废止，二者均不得用于Attempt19。

## 新规则

保持全局max_ai_attempts=3和所有旧指纹边界；仅为Attempt18新暴露的候选旅程搜索前置缺失增加精确例外：release=R14、attempt=19、request=R14-CANDIDATE-20260730-019、exception=CR-0507、required_fix_commit=cf0412194af17e3a5562ba28b6436b4bf3d9e678、max_candidate_runs=1。错误身份或重复运行必须拒绝。

## 修改原因

Attempt18已通过完整构建和全部R14业务操作并生成四图，唯一失败是返回列表后未重新输入搜索词却期待筛选空状态。CR-0505与Git实际Commit cf0412194af17e3a5562ba28b6436b4bf3d9e678修复候选旅程前置，固定Android工具链225任务通过；CR-0506因错误哈希已在写配置和推送前废止。

## 影响摘要

只登记CR-0505自动化旅程前置修复的一次候选身份；不修改App业务、API、数据库、旧指纹轮次或截图/JUnit/日志标准。

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

- `R14 Attempt19精确例外`

## 资金/账本与历史数据

- `CR-0507删除筛选搜索前置单次候选授权`

## 测试

- `精确匹配R14/19/Request019/CR-0507/cf041219且全局上限仍为3；错误身份拒绝；治理回归`

## 版本

- `R14`

## 迁移与兼容策略

无运行时迁移；Attempt18和CR-0504保持已消费，CR-0506保持SUPERSEDED，Attempt19必须包含Git实际Commit cf0412194af17e3a5562ba28b6436b4bf3d9e678且只运行一次。

## 用户确认

项目所有者已授权AI持续开发和候选自主审核

## 审批

- 审批人：`codex-r14-attempt19-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-30T02:28:00Z`
- 说明：Git实际Commit已由rev-parse复核；Attempt18删除证据完整，新授权只绑定搜索前置修复并运行一次，CR-0506保持废止。

## 状态记录 · 2026-07-30T02:28:06Z

- Actor：`codex-r14-attempt19-requester`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始以Git实际完整Commit写入Attempt19精确例外、候选请求和错误身份拒绝断言。

## 状态记录 · 2026-07-30T02:30:13Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Attempt19唯一候选授权由Commit d9b14517实现；request019严格绑定CR-0507与Git实际修复Commit cf0412194af17e3a5562ba28b6436b4bf3d9e678，35项治理回归及请求校验通过，CR-0506保持SUPERSEDED。
