# 可复用模式

## PATTERN-CONTRACT-001 契约先行
OpenAPI、WebSocket和数据库字典先冻结，前端生成/校验DTO，后端实现不得先于契约。

## PATTERN-CONFIG-001 供应商配置版本
Secret不可读回，连接测试后双人激活，历史版本可回滚，业务保存规则快照。

## PATTERN-STORAGE-001 Scope化对象存储
业务只使用Storage Scope，R2/OSS由绑定配置决定，支持SHA校验和迁移回滚。

## PATTERN-LEDGER-001 不可变账本
余额由流水汇总，任何修正产生冲正记录，不允许管理员直接修改金额字段。

## PATTERN-APK-001 可追溯APK
APK绑定Commit、versionName、versionCode、签名指纹、SHA256、测试报告和下载地址。

## PATTERN-CONTINUITY-001 无状态接续
CURRENT_STATUS + NEXT_TASK + Release Manifest + Context Pack + Session Log构成AI接手上下文。
