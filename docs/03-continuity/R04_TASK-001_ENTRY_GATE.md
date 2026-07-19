# TASK-R04-001 开发就绪核验

- Release：`R04` — 媒体与多对象存储
- Session：`SES-20260719T083704Z-6E4CE28F`
- 当前 Story：`STORY-R04-002`（契约、数据、配置、测试与交接）
- 前置版本：R03 已关闭，`R03-660d148`、10206 APK 和项目所有者真机 PASS 均已归档

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | 2 项：`REQ-MEDIA-001`、`REQ-STORAGE-001`，另含 APK 通用门禁 |
| Android 交互面 | 1 个：`SHEET-MEDIA-001`，模板 `MOB-SHEET` |
| 客户端 API | 3 个冻结 operationId：创建上传会话、完成上传、删除未绑定媒体 |
| Story | 2 个，均为 `READY_FOR_IMPLEMENTATION` |
| 权威测试 | 6 项 |
| 相关表 | 12 张，核心媒体表为 `media_objects`、`upload_sessions`、`media_access_tokens` |
| DoR | `PASS_DOCUMENTATION_READY`，12 项适用/不适用门禁均有结论 |
| 文档门禁 | `check_v122_documentation.py --release R04` 为 PASS，0 errors / 0 warnings / 0 open gaps |

## 存储、安全与所有权边界

- `public_media`、`private_kyc`、`private_chat`、`audit_evidence`、`apk_release`、`backup` 六类 Scope 必须隔离，业务代码不得另设默认供应商。
- 私有预览由媒体应用服务签发短期 `readUrl`，有效期读取 `storage.private_preview.ttl_seconds`；URL、Secret 和私有对象内容不得进入日志。
- 存储迁移由内部迁移服务拥有，必须支持游标恢复、幂等和逐对象审计，不新增未批准外部 API。
- 访问审计由媒体访问服务写入追加式审计记录，高敏访问必须绑定用户、Scope、对象和请求链路。
- R2/OSS 测试桶和最小权限 SecretRef 仍登记在 `PROB-0030`；它阻止真实供应商激活，不阻止先完成端口、适配器合同、数据不变量和故障安全实现。禁止用生产 Mock 冒充连接成功。

## 实施顺序

1. `TASK-R04-002`：先冻结存储端口、Scope 隔离、V020 迁移、R2/OSS 适配器合同、迁移恢复和访问审计。
2. `TASK-R04-003`：在存储端口稳定后完成三个媒体 API 闭环。
3. `TASK-R04-004`：基于冻结生成类型完成 Android 上传管理器。
4. `TASK-R04-005`：汇合三分区并执行 6 项权威测试、故障注入和生成物漂移门禁。

当前任务采用主控串行写唯一事实源，避免契约、Flyway 编号和连续性元数据竞态。

## APK 基线

- 继续使用固定 Staging 测试签名，API 必须为 `https://api.orbexa.cc`。
- R03 已使用 versionCode `10206`，因此 R04 实际交付必须使用至少 `10207`，不能回退到计划文件中的历史最低值。
- 桌面、仓库副本、服务器、公网四方 SHA 与项目所有者真机验收仍是独立门禁。

## 结论

R04 页面、字段、状态、动作、API、配置、数据和测试无 TBD；存储外部凭据阻断已明确登记且不妨碍本地可验证实现。允许关闭 TASK-R04-001 后进入 TASK-R04-002 的存储与数据基线开发。
