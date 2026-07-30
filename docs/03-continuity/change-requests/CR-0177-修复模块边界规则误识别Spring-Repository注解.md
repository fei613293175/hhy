---
cr_id: CR-0177
status: APPROVED
requester_actor_id: codex-root-r07
approver_actor_id: codex-reviewer
task_id: TASK-R07-002
session_id: SES-20260721T130339Z-785E85BE
created_at: 2026-07-21T16:41:11Z
updated_at: 2026-07-21T16:41:54Z
---
# CR-0177 — 修复模块边界规则误识别Spring Repository注解

## 用户需求摘要

项目所有者要求持续解决开发阻断并继续R07至R32，不因测试工具误报停止。

## 原规则

模块边界规则仅按依赖目标简单类名Repository筛选，并错误用andShould把共享包条件施加到源类；Spring的Repository注解因此被当作项目持久化仓库。

## 新规则

模块边界规则把Repository目标限定为cc.orbexa.hhy项目命名空间且排除shared包；Spring及第三方Repository注解不触发，项目内非shared仓库依赖仍受阻断；回归显式验证框架注解排除和项目仓库命中。

## 修改原因

基线提交f0e45bae在obx-test隔离复现证明ModuleBoundaryTest将org.springframework.stereotype.Repository注解误识别为项目跨模块Repository依赖，导致与R07迁移无关的确定性假失败。

## 影响摘要

只修复ArchUnit测试谓词和登记复发规则，不修改生产后端、数据库、API或R07业务行为。

## 影响文件

- `services/backend/boot/src/test/java/cc/orbexa/hhy/ModuleBoundaryTest.java`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/reports/R07/TASK-R07-002-database.md`

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

- `obx-test baseline f0e45bae ModuleBoundaryTest reproduces 1 failure`
- `obx-test mvn -B -pl access,boot -am test returns 0 failures`

## 版本

- `R07`

## 迁移与兼容策略

测试规则兼容修复，无数据迁移；既有合法Spring注解通过，真实项目跨模块Repository依赖继续失败。

## 用户确认

项目所有者已授权自行解决确定性测试问题并持续推进R07至R32。

## 审批

- 审批人：`codex-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-21T16:41:54Z`
- 说明：基线复现证明误报来自测试谓词；限定项目命名空间并保留非shared仓库阻断符合原架构意图，回归同时覆盖排除与命中。

## 状态记录 · 2026-07-21T16:51:21Z

- Actor：`codex-root-r07`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T130339Z-785E85BE`
- Note：基线误报已隔离复现，ArchUnit项目命名空间修复和311项后端MODULE回归已通过，进入提交绑定阶段。

## 状态记录 · 2026-07-21T17:01:10Z

- Actor：`codex-root-r07`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T130339Z-785E85BE`
- Note：ArchUnit目标限定、防回归断言和后端311项零失败已由实现提交b60a7a2d证明。

## 状态记录 · 2026-07-21T17:02:21Z

- Actor：`codex-root-r07`
- Status：`CLOSED`
- Session：`SES-20260721T130339Z-785E85BE`
- Note：基线复现、规则修复、正反分类回归、311项后端MODULE和严格推送门禁均完成。
