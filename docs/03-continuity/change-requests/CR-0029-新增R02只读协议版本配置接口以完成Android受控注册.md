---
cr_id: CR-0029
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: user-project-owner
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-18T05:03:14Z
updated_at: 2026-07-18T05:03:39Z
---
# CR-0029 — 新增R02只读协议版本配置接口以完成Android受控注册

## 用户需求摘要

项目所有者要求立即完成R02并进入下一版本，不允许无限做不完。

## 原规则

R02 Android注册页只能手工输入协议版本ID，无法从受控来源取得可提交版本。

## 新规则

R02新增只读兼容端点GET /api/v1/auth/registration-config，返回当前生效协议版本ID与公开协议代码；Android只提交该端点返回的ID。协议内容、版本创建和后台管理仍归R28。

## 修改原因

R02注册必须提交服务端生效协议版本ID；现有R28通用页面读取契约不承载该数据，需最小非破坏性R02接口，且不得伪造法律协议数据。

## 影响摘要

解除R02协议版本来源缺口并移除客户端手输版本ID；不生成或写入法律协议数据，数据为空时注册不可提交。

## 影响文件

- `contracts/openapi.yaml`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthStore.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserAuthController.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthPublicSuccessContractTest.java`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt`
- `apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt`
- `apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt`
- `tests/test_r02_auth_slice_contract.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- `SCR-AUTH-004`
- `SCR-AUTH-005`

## API

- `GET /api/v1/auth/registration-config`

## 数据库与迁移

- `hhy.agreements,hhy.agreement_versions (read only)`

## 配置

- `无；仍由受控协议版本数据发布决定生效内容`

## 资金/账本与历史数据

- `无资金或账本影响`

## 测试

- `OpenAPI contract;backend registration-config HTTP contract;Android auth form regression;cloud Maven and feature:auth unit tests`

## 版本

- `R02`

## 迁移与兼容策略

新增非破坏性R02公开读取接口和DTO；复用agreements/agreement_versions只读查询，无迁移。Android在读取失败或无生效版本时禁用注册并显示通用可恢复提示，绝不回退到客户端默认值。

## 用户确认

项目所有者在当前对话明确要求：现在立即开始开发，这次必须把这个版本收尾做完，不允许进入无限做不完的状态，做完后立即推进下一版本。

## 审批

- 审批人：`user-project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-18T05:03:39Z`
- 说明：项目所有者要求完成R02；批准最小只读R02兼容接口，以移除Android客户端手输协议版本。

## 状态记录 · 2026-07-18T05:03:40Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：开始实现R02只读协议版本配置接口及Android绑定。

## 状态记录 · 2026-07-18T05:23:08Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：Read-only current agreement-version source, Android controlled submission, and regression evidence are implemented; R28 agreement-management ownership remains unchanged.
