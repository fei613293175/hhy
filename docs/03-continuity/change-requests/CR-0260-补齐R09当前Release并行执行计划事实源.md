---
cr_id: CR-0260
status: APPROVED
requester_actor_id: codex-root-r09-candidate
approver_actor_id: codex-r09-continuity-review
task_id: TASK-R09-007
session_id: SES-20260722T205410Z-782F22B9
created_at: 2026-07-22T21:36:52Z
updated_at: 2026-07-22T21:37:12Z
---
# CR-0260 — 补齐R09当前Release并行执行计划事实源

## 用户需求摘要

项目所有者要求换电脑或换AI只需继续开发即可完整复用全部规则，并要求优化版本关闭反复失败。

## 原规则

Active Session的Context Pack必须包含当前Release并行执行计划，未明确授权子代理时保持单主控串行。

## 新规则

不新增第二套规则；补齐R09的PARALLEL_EXECUTION_PLAN，沿用R08单主控串行模式和大版本最终候选策略，并在既有Problem Registry登记缺失根因。

## 修改原因

R09启动时未生成强制PARALLEL_EXECUTION_PLAN，Active Session后Context Pack无法包含该来源，严格Continuity在GitHub确定性失败。

## 影响摘要

只补齐R09已有治理合同的版本级实例和复发记录，不改变产品、候选请求、GitHub模拟器次数、API、数据库或APK身份。

## 影响文件

- `releases/R09/PARALLEL_EXECUTION_PLAN.yaml`
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

- `python scripts/check_v123_continuity.py --strict`
- `R09并行计划YAML结构与任务映射校验`

## 版本

- `R09`

## 迁移与兼容策略

纯治理事实源；checkpoint自动把计划哈希纳入Context Pack，后续电脑和AI直接读取。

## 用户确认

项目所有者已明确要求规则跨电脑跨AI生效、相似规则不得重复、版本关闭优化且持续推进。

## 审批

- 审批人：`codex-r09-continuity-review`
- 决定：`APPROVED`
- 时间：`2026-07-22T21:37:12Z`
- 说明：复用既有并行与候选规则，只补当前Release缺失实例和问题记录。
