# API 契约与兼容规范 V1.2.2

- OpenAPI 3.1 文件是接口唯一事实源；CSV 只作为检索目录。
- 每个操作必须有唯一 `operationId`、显式权限、路径/查询参数、请求Schema、响应Schema、错误码和幂等规则。
- 写请求使用 `X-Idempotency-Key`；服务端保存请求哈希和首次结果，不得简单忽略重复请求。
- 并发更新使用 `expectedVersion` 或 `If-Match`；冲突返回 `COMMON-409-VERSION_CONFLICT`。
- 公共接口显式 `security: []`；支付/实名回调使用供应商签名方案，不继承 Bearer Token。
- 分页统一支持 `cursor`/`pageSize`，后台导出使用异步任务，禁止无边界全量响应。
- 兼容扩展允许新增可选字段和新枚举前提是客户端具备未知值策略；删除/重命名/类型改变必须升级主版本。
- 生成的 Kotlin/TypeScript/Java 类型在 CI 中进行 diff；手工 DTO 不得与生成模型重复定义同一事实。
