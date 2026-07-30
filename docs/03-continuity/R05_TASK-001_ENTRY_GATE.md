# TASK-R05-001 开发就绪核验

- Release：`R05` — 单一实名认证
- Session：`SES-20260719T161434Z-47C1FAA4`
- 当前 Story：`STORY-R05-008`（契约、数据、配置、测试与交接）
- 终态前置版本：R04 已关闭，Release Commit `04cda65d4a0749727a482526b616fb56cb475f34`、Tag `R04-04cda65`、versionCode `10207` APK 和项目所有者真机 PASS 均已归档

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | 2 项：`REQ-ID-001`、`REQ-ID-002`，另含 APK 通用门禁 |
| 页面/交互面 | 7 个：Android 4 个、H5 1 个、后台 2 个 |
| 新增 API | 9 个冻结 operationId：客户端 4 个、后台 5 个；另复用 `userGetMe` |
| Story | 8 个，均为 `READY_FOR_IMPLEMENTATION` |
| 权威测试 | 10 项：身份主路径、幂等、拒绝、安全及两项页面契约测试 |
| 相关表 | 10 张，身份核心表为 `identity_profiles`、`identity_verification_sessions`、`identity_provider_requests`、`identity_review_records`、`sensitive_data_access_logs` |
| DoR | `PASS_DOCUMENTATION_READY`，全部适用门禁均有 PASS 结论 |
| 文档门禁 | `check_v122_documentation.py --release R05` 为 PASS，0 errors / 0 warnings / 0 open gaps |
| Release 产物结构 | `check_release_artifacts.py --release R05` 为 PASS |

## 依赖状态与不降级规则

- R04 已完整关闭，是 R05 私有媒体、Scope 隔离和审计能力的 GREEN 前置版本。
- R02 的 `TASK-R02-001` 至 `TASK-R02-006` 已完成实现、25 项集成测试和机器 APK 交付；其旧 R02 APK 的项目所有者真机状态仍为 `PENDING`，`TASK-R02-007` 保持 `BLOCKED`，不得伪造或改写为完成。
- CR-0089 仅允许 R05 使用已经通过测试的认证与用户会话能力开展本地实现和自动化验证。R02 真机待验继续阻断 R05 最终 Release 关闭和任何生产激活，直至获得真实项目所有者证据或另行批准的累积版本验收规则。
- 本规则不改变页面、接口、数据库或业务语义，也不降低每版 APK 的机器追溯与项目所有者验收要求。

## 数据、安全与外部能力边界

- 身份证号、姓名、活体照片、人脸比对材料属于高敏数据；客户端、后台页面、普通日志和错误提示不得展示内部对象路径、供应商原始响应、请求编号或调试字段。
- 私有照片必须复用 R04 的 `private_kyc` Scope 与短期签名读取能力，禁止公开桶、永久 URL 或跨 Scope 访问。
- 身份状态、供应商请求、人工复核和敏感媒体访问必须使用追加式历史与审计；不得以更新覆盖历史事实。
- 真实身份供应商凭据与活体 SDK 属于外部激活门禁。缺少真实凭据时先实现冻结端口、合同测试、超时/失败安全和审核闭环，不得用生产 Mock 冒充供应商成功。

## 实施顺序

1. `TASK-R05-002`：先完成身份数据迁移、唯一约束、状态历史、幂等键、敏感访问审计和回滚验证。
2. `TASK-R05-003`：在数据与领域不变量稳定后实现 9 个冻结 operationId、权限、幂等、错误码和审计。
3. `TASK-R05-004`：按冻结页面规格实现 Android、H5 和后台 7 个交互面，禁止手写重复 DTO 或临时技术入口。
4. `TASK-R05-005`：汇合页面和接口，执行 8 项专项测试、并发、超时、重复消息和供应商故障注入。
5. 后续按任务依赖完成可观测性、Staging、固定签名 APK、项目所有者真机验收和版本关闭。

R05 的数据迁移、后端应用服务和客户端页面存在严格前后依赖；当前 Task 由主控串行固定唯一事实源、Flyway 编号和状态机。进入页面任务后可按 Android、H5、后台互斥目录再评估并行，不提前修改未来 Task。

## APK 基线

- 继续使用固定 Staging 测试签名，API 必须为 `https://api.orbexa.cc`。
- R04 已交付 versionCode `10207`，R05 测试 APK 必须使用大于 `10207` 的单调版本号。
- 桌面、仓库忽略副本、服务器、公网四方 SHA 与项目所有者真机验收继续保持独立门禁。

## 结论

R05 页面、字段、状态、动作、API、配置、数据和测试无 TBD；R02 外部真机待验已透明登记且不会被误标完成。允许关闭 TASK-R05-001 后进入 TASK-R05-002 的数据迁移与领域不变量开发，但 R05 最终版本关闭仍受 CR-0089 所列外部门禁约束。
