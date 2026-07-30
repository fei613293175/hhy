---
cr_id: CR-0200
status: APPROVED
requester_actor_id: codex-r07-candidate
approver_actor_id: codex-release-audit
task_id: TASK-R07-007
session_id: SES-20260721T210252Z-D631F6E4
created_at: 2026-07-21T23:26:39Z
updated_at: 2026-07-21T23:26:44Z
---
# CR-0200 — 修复Linux基线晋升中文路径转义误判

## 用户需求摘要

GitHub调试必须有界且经验可复用，不得反复浪费完整编译和模拟器时间

## 原规则

晋升内联Git diff沿用运行器core.quotepath默认值，含中文的合法连续性路径可被转义后误拒绝

## 新规则

所有用于机器路径判定的diff-tree和diff命令显式使用git -c core.quotepath=false，按UTF-8真实路径匹配；第二次只重跑轻量晋升，不重新编译或启动模拟器

## 修改原因

轻量晋升Run 29876741055在Resolve步骤14秒内失败；本地同逻辑通过，差异为Linux Git默认core.quotepath=true会把中文CR文档路径输出为带引号八进制，导致连续性白名单误判

## 影响摘要

修复跨平台路径编码误报并保留完整范围白名单；原Run 29875839322的APK、四图和报告保持不变

## 影响文件

- `.github/workflows/android-baseline-promotion.yml`
- `tests/test_android_ci_gate.py`
- `tests/android/visual-baselines/R07/APPROVAL.yaml`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

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

- `Android CI Python回归；本地模拟Linux core.quotepath=true时仍以显式false输出真实中文路径；轻量晋升Actions PASS`

## 版本

- `R07`

## 迁移与兼容策略

纯CI路径解析修复；已批准基线增加promotion_attempt记录触发同源轻量重试，不改变任何图片字节或产品源码

## 用户确认

项目所有者已要求GitHub问题有界修复并写入可复用经验

## 审批

- 审批人：`codex-release-audit`
- 决定：`APPROVED`
- 时间：`2026-07-21T23:26:44Z`
- 说明：根因是确定性的跨平台Git显示配置；修复限定路径解析且重跑只下载原工件，不消耗完整Android门禁

## 状态记录 · 2026-07-21T23:28:45Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：diff-tree与diff均显式core.quotepath=false；Windows模拟Linux默认true已复现八进制转义，显式false输出真实中文路径；18项回归通过

## 状态记录 · 2026-07-21T23:51:55Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：批准范围已实现并由对应提交及R07候选/交付门禁验证。

## 状态记录 · 2026-07-21T23:52:00Z

- Actor：`codex-root-r07-007`
- Status：`CLOSED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：实现提交已推送，相关受影响门禁均通过。
