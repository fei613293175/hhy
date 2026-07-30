---
cr_id: CR-0340
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: codex-independent-r12-requestid-reviewer
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-25T20:52:32Z
updated_at: 2026-07-25T20:54:31Z
---
# CR-0340 — 修复R12候选OIDC异常日志重复requestId遮蔽

## 用户需求摘要

持续推进R12最终候选，终端静默执行，不得反复浪费GitHub模拟器时间

## 原规则

GlobalExceptionHandler.unexpected同时从RequestIdFilter的MDC继承requestId并再次以结构化keyValue写入同名字段，Spring Boot结构化日志序列化拒绝重复键，导致原始异常类型和失败阶段不可见

## 新规则

GlobalExceptionHandler在MDC已有requestId时只记录安全errorType并复用MDC，不重复写入同名结构化字段；MDC缺失时才以keyValue回退；用双路径回归锁定不重复、不泄密且保留关联ID，并在唯一Problem Registry登记根因

## 修改原因

Run 30173649942的bootstrap请求返回500，后端结构化日志因MDC与keyValue重复requestId再次抛错，原始OIDC异常被遮蔽，必须先恢复安全可观测性再重跑候选

## 影响摘要

只修复共享后端异常日志的安全可观测性并补回归与问题登记，不改变API响应、OIDC验证策略、生产业务契约、数据库、Android页面或候选截图范围

## 影响文件

- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandler.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandlerTest.java`
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

- `GlobalExceptionHandlerTest;backend MODULE;Problem Registry YAML parse`

## 版本

- `R12`

## 迁移与兼容策略

无数据迁移；日志中的requestId字段和值保持兼容，仅消除同一事件重复键；候选后端重建后再请求bootstrap以安全识别原始异常类型

## 用户确认

项目所有者已明确授权持续开发、静默执行终端命令，并要求避免在GitHub候选调试中反复浪费时间。

## 审批

- 审批人：`codex-independent-r12-requestid-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T20:54:31Z`
- 说明：独立复核确认范围精确：仅消除GlobalExceptionHandler结构化日志中MDC与keyValue重复requestId，双路径回归覆盖MDC存在时复用及MDC缺失时安全回退，同时锁定errorType、关联ID和异常敏感信息不泄露；根因登记到既有唯一Problem Registry。API错误响应、OIDC令牌验证与bootstrap策略、数据库、Android候选合同均不改变。

## 状态记录 · 2026-07-25T20:55:15Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：独立审批完成，开始修复重复requestId并补双路径回归与唯一问题登记。

## 状态记录 · 2026-07-25T21:00:45Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：GlobalExceptionHandler已消除MDC与keyValue重复requestId并保留缺失MDC回退；obx-test Java21固定Maven镜像运行GlobalExceptionHandlerTest共2项，0失败0错误；Problem Registry YAML共113项唯一且PROB-0113已登记。
