---
cr_id: CR-0151
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R06-004
session_id: SES-20260720T173038Z-35648A77
created_at: 2026-07-20T20:10:50Z
updated_at: 2026-07-20T20:11:22Z
---
# CR-0151 — 修正Android候选截图过早与页面错位

## 用户需求摘要

继续GitHub模拟器并通过已登录仓库读取Actions日志和artifacts，定位后修复确定性问题

## 原规则

候选SmokeTest只等待目标文本存在后立即截图，Compose过渡期间隐藏或底层语义节点可提前满足条件

## 新规则

每张截图必须同时等待目标页面唯一文案出现、前一页面或启动态唯一文案消失并等待UI空闲；截图回归静态锁定这些稳定条件

## 修改原因

R06候选仪器测试返回0且四张截图存在，但密码页截到启动态、忘记密码截图仍为短信登录；错误截图不得被批准为视觉基线

## 影响摘要

只修复测试截图时序，不改变产品业务、API、数据库、正式UI或候选视觉阈值；第2轮仅生成可供项目所有者审查的正确截图，仍因未批准基线而不得自动放行

## 影响文件

- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `tests/test_android_ci_gate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `config/android-candidate-request.yaml`

## 页面

- `SCR-APP-001;SCR-AUTH-001;SCR-AUTH-002;SCR-AUTH-003;SCR-AUTH-004`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `ReleaseCandidateSmokeTest instrumentation;test_android_ci_gate static contract;GitHub attempt 2 screenshot review`

## 版本

- `R06`

## 迁移与兼容策略

无数据迁移；兼容同一API35 Pixel7旅程和既有截图文件名；不自动创建或接受R06视觉基线

## 用户确认

我已经登录成功，继续读取GitHub Actions日志和artifacts；仅修复已定位的截图时序，不自动批准基线

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T20:11:22Z`
- 说明：项目所有者已登录GitHub并明确选择继续GitHub模拟器，允许依据真实Actions证据修复候选测试确定性缺陷；视觉基线仍需另行人工确认

## 状态记录 · 2026-07-20T20:11:25Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260720T173038Z-35648A77`
- Note：开始修正候选截图稳定条件并登记PROB-0059，完成模块回归后才请求第2轮截图证据
