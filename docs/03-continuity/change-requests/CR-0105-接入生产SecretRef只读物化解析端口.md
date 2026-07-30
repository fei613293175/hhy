---
cr_id: CR-0105
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner
task_id: TASK-R05-004
session_id: SES-20260719T183335Z-535311E4
created_at: 2026-07-19T21:58:51Z
updated_at: 2026-07-19T22:00:04Z
---
# CR-0105 — 接入生产SecretRef只读物化解析端口

## 用户需求摘要

用户要求持续推进R05到正式商业系统落地且不因非紧急问题暂停

## 原规则

AdminAccessConfiguration只注册抛出不可用异常的SecretResolver，占位实现无法读取Vault/KMS SecretRef对应的生产秘密材料

## 新规则

Vault/KMS代理或CSI必须把每个SecretRef短时物化为只读目录内SHA-256(reference).secret文件；后端仅接受规范vault://或kms://引用，NOFOLLOW读取8KiB以内UTF-8材料，拒绝软链接、越界路径和Linux宽权限，读取缓冲立即清零且不缓存；prod配置缺失必须启动失败，非prod保持安全不可用

## 修改原因

CR-0104真实R2端口依赖SecretResolver，但仓库当前仅有统一不可用占位，生产凭据无法从Vault或KMS物化结果安全进入供应商客户端

## 影响摘要

增加供应商秘密只读物化配置、生产启动门禁、文件解析器和安全测试，使全部供应商连接测试、实名认证与R2端口共享真实SecretResolver

## 影响文件

- `services/backend/boot/src/main/resources/application.yml`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/admin/AdminAccessConfiguration.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/admin/ProviderSecretMaterialProperties.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/admin/MountedProviderSecretResolver.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/admin/ProviderSecretStartupGuard.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/admin/MountedProviderSecretResolverTest.java`
- `docs/04-vendors/供应商配置中心详细规格_V1.2.2.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `HHY_PROVIDER_SECRET_DIR只读Vault/KMS物化目录`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `合法引用读取、引用到文件名映射、软链接和宽权限拒绝、大小/UTF-8边界、缓冲清零、轮换不缓存、prod启动失败门禁`

## 版本

- `R05`

## 迁移与兼容策略

不改变业务API和数据库；未配置目录的开发测试环境继续安全不可用，生产部署必须挂载只读tmpfs/CSI目录并设置HHY_PROVIDER_SECRET_DIR后方可启动

## 用户确认

用户要求无需逐项确认且没有让暂停时持续推进版本

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-19T22:00:04Z`
- 说明：按项目所有者持续推进到正式商业系统落地授权，补齐R2与实名认证共同依赖的生产秘密解析链路

## 状态记录 · 2026-07-19T22:00:05Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T183335Z-535311E4`
- Note：开始实现只读物化SecretRef解析、生产启动门禁与安全回归

## 状态记录 · 2026-07-19T22:10:30Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T183335Z-535311E4`
- Note：生产SecretRef只读物化解析已完成：vault/kms引用哈希映射、8KiB UTF-8边界、NOFOLLOW与只读POSIX权限、无缓存轮换、缓冲清零及production启动门禁；后端274项和专项5项通过
