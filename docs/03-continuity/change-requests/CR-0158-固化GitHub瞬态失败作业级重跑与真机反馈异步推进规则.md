---
cr_id: CR-0158
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R06-005
session_id: SES-20260721T012656Z-E067730A
created_at: 2026-07-21T01:28:28Z
updated_at: 2026-07-21T01:29:26Z
---
# CR-0158 — 固化GitHub瞬态失败作业级重跑与真机反馈异步推进规则

## 用户需求摘要

现在立即按你说的计划开始方案后并立即推进开发；真机验证测试不能强制每个版本反馈，未反馈不能停止推进开发

## 原规则

原Commit仅基础设施瞬态故障允许重跑一次；项目所有者真机反馈异步且不阻断下一版本编码，但未精确定义作业级重跑范围、已通过阶段复用和重复瞬态停止条件

## 新规则

仅在业务测试开始前且日志明确证明GitHub、网络、镜像或Staging接口为瞬态故障时，允许同Commit只重跑失败作业及其依赖一次，禁止重跑已通过编译、Lint、单测和打包或修改业务代码；同一瞬态再次出现立即停止重跑并检查Staging健康与基础设施。任何单版owner_physical_test=PENDING只阻断该版正式验收和生产激活，不阻断后续依赖已满足版本的编码、机器候选和桌面APK累积交付

## 修改原因

R06最终候选证明Staging引导HTTP 500可通过同Commit仅失败作业有界重跑恢复；现行规则未明确保留已通过编译阶段及重复瞬态的停止条件

## 影响摘要

减少GitHub重复编译和盲目重跑，保证项目所有者可不定时真机测试而持续推进R06-R32，同时保留正式验收与生产激活真实性

## 影响文件

- `config/android-automation.yaml`
- `docs/00-baseline/正式商业系统全局硬性开发边界.md`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `docs/09-development/统一开发与交付效率规范.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `android_automation.remediation.retry_rule`
- `android_automation.enforcement.owner_feedback_non_blocking_for_next_release`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests/test_android_ci_gate.py`

## 版本

- `R06-R32`

## 迁移与兼容策略

兼容现有R06-R32候选工作流、最长三轮AI修复和既有延期外部门禁；不改变API、数据库、签名、候选PASS或生产授权要求

## 用户确认

现在立即按你说的计划开始方案后并立即推进开发；额外说明：真机验证测试不能强制我每个版本必须给你反馈，我会不定时测试，然后会在对话反应问题，不能因为我没有真机反馈结果就停止推进开发

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-21T01:29:26Z`
- 说明：项目所有者明确要求立即按方案执行、固化跨电脑跨AI可复用规则，并确认真机反馈不定时且不得阻断持续开发
