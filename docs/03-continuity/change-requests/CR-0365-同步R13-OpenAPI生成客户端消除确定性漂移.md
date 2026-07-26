---
cr_id: CR-0365
status: APPROVED
requester_actor_id: codex-root-r13-20260726
approver_actor_id: project-owner
task_id: TASK-R13-001
session_id: SES-20260726T110209Z-C9DC8AD5
created_at: 2026-07-26T11:19:09Z
updated_at: 2026-07-26T11:19:40Z
---
# CR-0365 — 同步R13 OpenAPI生成客户端消除确定性漂移

## 用户需求摘要

批准，以后不要让我批准了 你自己持续开发就行了

## 原规则

contracts/openapi.yaml是客户端类型唯一事实源，packages/api-client/src/client.generated.ts必须与固定openapi-typescript版本及仓库后处理器的确定性输出一致。

## 新规则

不修改OpenAPI、业务语义或手写生成文件逻辑；使用仓库锁定的pnpm、openapi-typescript与postprocess-openapi-types.mjs机械重生成client.generated.ts，并保持admin.generated.ts仅在确定性输出确有变化时同步。

## 修改原因

R13入口文档变更未修改OpenAPI，但严格生成资产门禁发现packages/api-client/src/client.generated.ts与当前contracts/openapi.yaml的确定性输出漂移；必须先恢复单一生成事实源，才能关闭TASK-R13-001。

## 影响摘要

恢复生成客户端与当前冻结OpenAPI的一致性，消除R13入口严格生成资产门禁的唯一漂移。

## 影响文件

- `packages/api-client/src/client.generated.ts`

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

- `check_generated_assets`
- `api_client_typecheck`

## 版本

- `R13`

## 迁移与兼容策略

纯生成资产同步；不改变HTTP路径、operationId、Schema、数据库、配置、UI或运行时兼容性。

## 用户确认

批准，以后不要让我批准了 你自己持续开发就行了

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-26T11:19:40Z`
- 说明：项目所有者已明确批准并要求后续无需逐项批准；本CR仅执行冻结OpenAPI生成资产的机械同步，不扩展权限或生产范围。

## 状态记录 · 2026-07-26T11:20:08Z

- Actor：`codex-root-r13-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T110209Z-C9DC8AD5`
- Note：开始使用锁定工具链机械重生成客户端，并运行生成漂移与类型门禁。

## 状态记录 · 2026-07-26T11:30:10Z

- Actor：`codex-root-r13-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T110209Z-C9DC8AD5`
- Note：OpenAPI客户端已按锁定工具链机械同步423响应类型；生成资产、API客户端类型、后台与H5回归全部PASS。
