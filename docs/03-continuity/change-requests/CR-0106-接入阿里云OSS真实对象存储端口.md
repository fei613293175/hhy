---
cr_id: CR-0106
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T22:13:06Z
updated_at: 2026-07-19T22:13:24Z
---
# CR-0106 — 接入阿里云OSS真实对象存储端口

## 用户需求摘要

持续按开发文档推进R05并完成真实供应商落地

## 原规则

ALIYUN_OSS绑定只允许登记，运行时解析器拒绝激活且无真实SDK端口

## 新规则

ALIYUN_OSS绑定必须通过精确激活且连接测试成功的storage配置版本，使用SecretRef解析凭据并支持签名PUT、HEAD完整性校验、私有签名GET、删除、扫描和同配置复制

## 修改原因

ALIYUN_OSS路由当前仍失败关闭，需完成签名上传、完整性校验、私有读取及迁移操作

## 影响摘要

新增阿里云OSS SDK传输实现并扩展配置解析与供应商路由，保持现有R2行为兼容

## 影响文件

- `services/backend/pom.xml`
- `services/backend/access/pom.xml`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04StorageProviderSettings.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04OssStorageTransport.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04StoragePortResolver.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04MediaAccessConfiguration.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/R04OssStorageTransportTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/R04StorageProviderSettingsTest.java`
- `docs/04-vendors/多对象存储适配规范_V1.2.2.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `storage.scope.<scope>.provider与storage.aliyun_oss.*激活值和SecretRef`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `OSS签名URL、请求头、密钥清零、HEAD三要素、路由及失败关闭测试；后端全量测试`

## 版本

- `R05正式关闭前纳入门禁，不单独发布APK`

## 迁移与兼容策略

不变更数据库结构；现有R2继续工作；OSS仅在绑定精确指向ACTIVE且connection_successful配置时启用，否则失败关闭

## 用户确认

2026-07-20用户持续开发指令

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T22:13:24Z`
- 说明：用户已明确要求不暂停并持续推进开发文档版本，授权正常实现步骤自主决策

## 状态记录 · 2026-07-19T22:24:57Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T183335Z-535311E4`
- Note：OSS V4真实端口、作用域精确配置、双供应商路由和专项测试已实现，进入提交与关闭门禁

## 状态记录 · 2026-07-19T22:25:32Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T183335Z-535311E4`
- Note：阿里云OSS V4端口、作用域精确激活配置、SecretRef凭据、HEAD完整性及双端口路由已实现；专项13项与全量279项测试通过
