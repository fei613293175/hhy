---
cr_id: CR-0492
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: codex-independent-candidate-reviewer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T22:02:20Z
updated_at: 2026-07-29T22:02:51Z
---
# CR-0492 — 授权R14会话复进Compose语义路径唯一Attempt12

## 用户需求摘要

持续完成R14至R32；GitHub候选失败必须产生新修复或新证据后继续，不得原样反复浪费时间

## 原规则

CR-0490只授权R14 Attempt11、request011、修复Commit c79a7967与单次候选运行；Attempt11及其同Commit唯一基础设施失败作业重跑已经消费，禁止复用该请求、授权或原样重跑。

## 新规则

在不改变全局max_ai_attempts=3的前提下，只增加精确例外release=R14、attempt=12、request=R14-CANDIDATE-20260730-012、exception=CR-0492、required_fix_commit=c1b637e299288279d96e0ef85fde21b03e889251、max_candidate_runs=1。必须保留全部R14业务断言、四张截图、JUnit和目标进程日志，并新增发送后composer为空证明。

## 修改原因

Attempt11及唯一基础设施重跑均已消费；CR-0491提交c1b637e2建立会话行Compose语义操作和发送后输入清空的新验证路径，需要以新请求、精确Commit和单次运行重新授权。

## 影响摘要

只登记CR-0491新Compose语义验证路径的一次性候选授权；错误Release、Attempt、Request、CR、Commit或多次运行均必须非零拒绝，不修改产品、后端、数据库、全局三轮上限或截图范围。

## 影响文件

- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `tests/test_android_ci_gate.py`
- `CHANGELOG.md`

## 页面

- `无产品页面改动，仅候选自动化授权`

## API

- `无API合同变更`

## 数据库与迁移

- `无数据库变更`

## 配置

- `R14 Attempt12精确候选例外与request012`

## 资金/账本与历史数据

- `无资金或账本影响`

## 测试

- `python -m unittest tests.test_android_candidate_request tests.test_android_ci_gate tests.test_android_candidate_route`
- `python scripts/android_candidate_request.py --request config/android-candidate-request.yaml`

## 版本

- `R14`

## 迁移与兼容策略

Attempt11和CR-0490保持已消费历史；Attempt12必须绑定c1b637e2并运行一次，其他Release、历史例外和全局三轮规则不变。

## 用户确认

项目所有者明确授权AI持续推进并自行审核GitHub候选，同时要求相同问题不得反复浪费时间。

## 审批

- 审批人：`codex-independent-candidate-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-29T22:02:51Z`
- 说明：独立候选复审通过：Attempt12只绑定CR-0491的新Compose语义Commit并运行一次；旧跨框架路径不得重跑，全局上限与全部业务断言均未放宽。

## 状态记录 · 2026-07-29T22:03:05Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：写入Attempt12精确例外、request012及错误Release、CR、Commit、Request和轮次拒绝回归。

## 状态记录 · 2026-07-29T22:04:33Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Commit f4c7589f7950e978b013fae0cbcd9ee40773de0f 已写入Attempt12新Compose语义路径唯一授权、request012与精确错误绑定回归；102项治理测试和候选请求验证PASS。
