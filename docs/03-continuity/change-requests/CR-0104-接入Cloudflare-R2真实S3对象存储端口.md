---
cr_id: CR-0104
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T21:37:59Z
updated_at: 2026-07-19T21:38:22Z
---
# CR-0104 — 接入Cloudflare R2真实S3对象存储端口

## 用户需求摘要

用户要求持续推进R05正式商业实名认证闭环并向项目落地方向开发

## 原规则

R04 MediaAccessConfiguration在不存在PortResolver时注册统一不可用占位，StorageProviderAdapter只有抽象Transport且Binding不携带配置版本

## 新规则

Cloudflare R2必须按storage_scope_bindings绑定的ACTIVE且已连接测试通过的精确配置版本解析公开参数和SecretRef，短时解析凭据后使用AWS SDK v2 S3兼容端口生成受Content-Type与SHA元数据约束的PUT签名URL，并以HEAD校验对象键、大小、SHA和ETag；阿里云OSS在下一独立端口完成前继续安全不可用

## 修改原因

CR-0101已完成private_kyc桥接，但R04 PortResolver仍为安全占位，无法生成真实R2签名上传或校验对象

## 影响摘要

增加AWS SDK v2固定依赖、绑定配置版本身份、R2设置加载、真实S3 Transport和Provider resolver，覆盖签名头、对象完整性、秘密清零和失败安全测试

## 影响文件

- `services/backend/pom.xml`
- `services/backend/access/pom.xml`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/StorageObjectPort.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04StoragePostgresStore.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04MediaAccessConfiguration.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04StorageProviderSettings.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04S3StorageTransport.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/storage/R04StoragePortResolver.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/R04StorageProviderSettingsTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/storage/R04S3StorageTransportTest.java`
- `docs/04-vendors/多对象存储适配规范_V1.2.2.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `storage.r2.* SecretRef和激活配置`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R2精确配置版本、签名PUT必需头、HEAD完整性、私有GET、对象键幂等、秘密清零和OSS安全不可用`

## 版本

- `R05`

## 迁移与兼容策略

不修改数据库结构或公开API；Binding新增configVersionId并保留旧构造器兼容现有测试，R2仅接受与绑定精确一致的激活配置，OSS继续显式不可用

## 用户确认

用户持续授权向项目落地方向推进并要求无需逐项确认

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T21:38:22Z`
- 说明：先完成R05默认Cloudflare R2真实端口，OSS保持明确安全占位并后续独立实现

## 状态记录 · 2026-07-19T21:38:23Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T183335Z-535311E4`
- Note：开始实现精确激活配置绑定的R2 S3真实端口

## 状态记录 · 2026-07-19T21:57:16Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T183335Z-535311E4`
- Note：Cloudflare R2真实S3兼容端口完成：精确ACTIVE且连接测试成功配置版本、AWS SDK预签名PUT/GET、Content-Type和SHA元数据绑定、HEAD大小/SHA/ETag校验、幂等对象键、凭据清零；PostgreSQL17及后端268项通过，OSS继续失败关闭
