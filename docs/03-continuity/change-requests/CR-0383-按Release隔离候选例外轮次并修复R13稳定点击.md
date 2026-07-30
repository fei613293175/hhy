---
cr_id: CR-0383
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-independent-r13-cr0383-review-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-26T21:49:38Z
updated_at: 2026-07-26T21:51:33Z
---
# CR-0383 — 按Release隔离候选例外轮次并修复R13稳定点击

## 用户需求摘要

项目所有者已长期授权AI持续开发、自动修复候选并自主审核截图，不再逐次请求终端、GitHub或候选批准。

## 原规则

Android候选例外列表按全局索引要求attempt从4连续递增，导致R12历史attempt4至7存在时，R13首个合规例外attempt4被错误要求为attempt8；R13项目详情旅程同时按Compose TextButton的可见文字子节点点击，可能点击不可点击节点。

## 新规则

全局max_ai_attempts继续固定为3；approved_attempt_exceptions必须按每个Release独立从attempt4连续编号，同时CR、request_id、required_fix_commit仍全局唯一且max_candidate_runs固定为1，任一Release自身跳号、重复或错误绑定均拒绝。项目详情分享与反馈TextButton必须暴露稳定资源标识，文字定位辅助也必须向上选择第一个enabled且clickable的真实祖先。仅允许release=R13、attempt=4、request_id=R13-CANDIDATE-20260727-004、exception_id=CR-0383、required_fix_commit=本CR首个修复Commit完整SHA、max_candidate_runs=1精确运行一次；R13 attempt5不得预授权。

## 修改原因

CR-0382独立审查确认R13 attempt3为新的Compose文字子节点点击根因，同时发现候选例外连续性错误地按全局列表计数；本CR补齐按Release独立从attempt4连续编号的门禁实现、专项回归、稳定资源点击和唯一R13 attempt4精确例外。

## 影响摘要

修复跨版本候选例外错误串号和Compose语义点击根因，使R13首个attempt4在不放宽全局三轮上限的前提下可被精确验证；不改变R12历史、产品功能、API、数据库、生产权限、秘密或异步真机反馈规则。

## 影响文件

- `scripts/android_ci_gate.py`
- `tests/test_android_candidate_request.py`
- `tests/test_android_ci_gate.py`
- `apps/android/feature/project/src/main/java/cc/orbexa/hhy/project/R08ProjectScreens.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_r13_candidate.py`
- `config/android-automation.yaml`
- `config/android-candidate-request.yaml`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `config/android-automation.yaml:remediation.approved_attempt_exceptions`
- `config/android-candidate-request.yaml:attempt_exception_id`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_r13_candidate`
- `python -m unittest tests.test_android_candidate_request`
- `python -m unittest tests.test_android_ci_gate`
- `python scripts/check_android_ui_foundation.py`
- `python scripts/android_ci_gate.py policy-check`

## 版本

- `R13`

## 迁移与兼容策略

既有R12 attempt4至7按Release分组后仍连续有效；无例外的普通attempt1至3行为不变。R13仅新增首个attempt4，错误Release、轮次、请求号、CR、Commit或多次运行均在模拟器前拒绝；稳定测试标识不改变用户可见UI。

## 用户确认

项目所有者已长期授权AI持续开发和候选修复；该授权不绕过仓库门禁，本次仅批准CR-0383登记的按Release编号和R13精确attempt4。

## 审批

- 审批人：`codex-independent-r13-cr0383-review-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-26T21:51:33Z`
- 说明：独立复核确认CR-0383完整吸收CR-0382拒绝意见：新增android_ci_gate按Release独立连续编号及candidate request回归，保留全局max_ai_attempts=3、全局身份唯一、单次运行和R13 attempt5硬拒绝；稳定资源点击范围与Run 30220806413证据一致。
