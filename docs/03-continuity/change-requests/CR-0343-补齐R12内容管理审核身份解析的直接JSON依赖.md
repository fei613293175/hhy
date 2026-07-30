---
cr_id: CR-0343
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: codex-avicenna-r12-contract-review-20260726
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-25T22:50:09Z
updated_at: 2026-07-25T22:51:45Z
---
# CR-0343 — 补齐R12内容管理审核身份解析的直接JSON依赖

## 用户需求摘要

持续修复R12候选根因并使用obx-test完成模块验证

## 原规则

feature:content-management仅通过implementation依赖core:network，未声明自身源码和测试直接导入的kotlinx.serialization.json；Gradle implementation不会向消费模块传递该编译类型

## 新规则

feature:content-management必须像既有app-promotion、group-promotion和team-leader模块一样显式implementation(libs.kotlinx.serialization.json)，仅为解析ContentResource.attributes中的冻结review对象提供编译依赖；core:network的依赖可见性保持不变

## 修改原因

CR-0342实现按attributes.review.id建模后，obx-test编译证明feature:content-management未声明其直接使用的kotlinx.serialization.json；仓库其他解析ContentResource.attributes的feature均显式声明该依赖

## 影响摘要

为CR-0342的review.id解析补齐单模块直接编译依赖；不改变运行时API、数据库、页面合同或其他feature依赖图

## 影响文件

- `apps/android/feature/content-management/build.gradle.kts`

## 页面

- `SCR-MYC-004`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `obx-test :feature:content-management:testDebugUnitTest :feature:content-management:lintDebug :app:compileDebugKotlin`

## 版本

- `R12`

## 迁移与兼容策略

纯Gradle编译依赖修复，无数据或协议迁移；版本由既有catalog锁定，不新增第三方版本

## 用户确认

项目所有者要求持续修复R12候选并复用obx-test验证，不因界面状态或真机反馈暂停。

## 审批

- 审批人：`codex-avicenna-r12-contract-review-20260726`
- 决定：`APPROVED`
- 时间：`2026-07-25T22:51:45Z`
- 说明：独立合同复核确认最小修复为feature:content-management显式implementation(libs.kotlinx.serialization.json)：该模块源码与测试直接解析JsonObject，且app-promotion、group-promotion、team-leader、auth、identity均采用相同依赖边界；不得把core:network改为api扩大ABI和重编译面。

## 状态记录 · 2026-07-25T23:00:13Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：已按批准范围完成源码实现并进入远程模块验证闭环。

## 状态记录 · 2026-07-25T23:00:32Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：content-management已按既有feature模式声明kotlinx.serialization.json直接依赖；obx-test单测、Lint及App Kotlin编译PASS。
