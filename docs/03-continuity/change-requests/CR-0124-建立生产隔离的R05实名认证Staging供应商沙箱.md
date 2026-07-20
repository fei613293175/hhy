---
cr_id: CR-0124
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-007
session_id: SES-20260719T234639Z-1D7D7A00
created_at: 2026-07-20T05:31:37Z
updated_at: 2026-07-20T05:32:41Z
---
# CR-0124 — 建立生产隔离的R05实名认证Staging供应商沙箱

## 用户需求摘要

项目所有者真机确认实名资料页通过，但提交后因认证服务配置缺失无法测试活体与结果页，并要求持续推进开发。

## 原规则

R05实名认证仅从ACTIVE供应商配置读取策略，测试环境无活动配置时在创建会话阶段返回500，无法测试活体和结果页面。

## 新规则

实名认证测试闭环采用大型App通行的生产与沙箱双通道：沙箱默认关闭且只能在staging显式启用；正式环境继续只接受后台激活的真实供应商和密钥。沙箱使用一次性state、HTTPS页面、真实相机权限、数据库事务终态和真实Android轮询结果，不展示技术字段。

## 修改原因

线上活动identity策略缺失使创建会话在第三方调用前失败；需要生产默认关闭、仅Staging显式启用的供应商沙箱完成真机闭环。

## 影响摘要

新增严格环境隔离的Staging实名供应商沙箱与真机闭环，修复无真实供应商配置时无法验证R05余下页面的问题，不改变生产实名认证逻辑。

## 影响文件

- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/IdentitySandboxProperties.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/IdentitySandboxStartupGuard.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/R05IdentitySandboxClient.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/R05IdentityProviderRouter.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/R05IdentityRuntimePolicy.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/R05IdentityAccessConfiguration.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/R05IdentityPostgresStore.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/IdentitySandboxService.java`
- `services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/PublicIdentitySandboxController.java`
- `services/backend/boot/src/main/resources/application.yml`
- `infra/staging/r05-smoke/docker-compose.yml`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/identity/IdentitySandboxIsolationTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/boot/user/PublicIdentitySandboxControllerTest.java`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`
- `artifacts/reports/R05/R05-version-test-guide.md`

## 页面

- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`

## API

- `GET /public-api/v1/identity/sandbox/liveness`
- `POST /public-api/v1/identity/sandbox/complete`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `HHY_IDENTITY_SANDBOX_ENABLED`
- `HHY_IDENTITY_SANDBOX_PUBLIC_BASE_URL`
- `HHY_IDENTITY_SANDBOX_ALLOWED_RETURN_HOST`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `沙箱只能在staging启用，production启动拒绝`
- `沙箱供应商路由、一次性state完成及生产真实供应商回退测试`
- `公开沙箱页面相机权限、无技术字段及安全回跳测试`

## 版本

- `R05`

## 迁移与兼容策略

无数据库结构变更；默认关闭保持现有生产行为；仅R05 Staging部署显式启用，关闭后自动回到后台活动供应商配置。

## 用户确认

2026-07-20项目所有者确认实名资料页通过但后续活体无法测试，并持续授权自行决定后推进。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-20T05:32:41Z`
- 说明：依据项目所有者明确要求持续开发、后续问题自行决定，以及本轮真机阻断反馈，批准仅Staging显式启用且生产默认关闭的供应商沙箱。

## 状态记录 · 2026-07-20T05:32:44Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T234639Z-1D7D7A00`
- Note：开始实现生产隔离的Staging实名认证供应商沙箱与测试闭环。
