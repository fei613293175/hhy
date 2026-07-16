# 本地基础设施

```bash
cp .env.example .env
make infra-up
docker compose -f infra/docker-compose.yml ps
```

本地 Profile 使用已锁定镜像：PostgreSQL 17.10、Redis 7.4.9、RabbitMQ 4.1.8 和 MinIO `RELEASE.2025-09-07T16-13-09Z`。禁止使用 `latest`，镜像升级必须走 Change Request 并重新执行迁移、Outbox/Inbox、缓存恢复和对象存储适配测试。

- PostgreSQL 是订单、库存、资金、状态和审计最终事实源；
- Redis 只作缓存、限流、短租约及实时路由；
- RabbitMQ 只承载异步投递，可靠事实保存在 Outbox/Inbox；
- MinIO 仅用于本地 S3 兼容测试，生产通过 `ObjectStorageProvider` 接入 R2/OSS，不绑定本地实现。

本地默认口令只允许出现在 `.env.example` 和个人 `.env`；预发布/生产必须由 Secret Manager 注入。生产部署清单必须进一步按平台锁定镜像 digest、资源限制、备份、PDB、NetworkPolicy 和告警。
