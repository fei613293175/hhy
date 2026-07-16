# 环境矩阵

| 环境 | 用途 | 数据 | 支付/出款 | 域名 | 部署来源 |
|---|---|---|---|---|---|
| DEV | 本地开发 | 本地/脱敏 | Mock | localhost | task分支 |
| TEST | 自动测试 | 自动重建 | Mock | 内部 | PR Commit |
| STAGING | 用户验收和测试APK | 独立测试库 | 测试配置/Mock | stg-*.orbexa.cc | release分支绿灯Commit |
| PRODUCTION | 正式运营 | 生产 | 正式供应商 | *.orbexa.cc | 受保护Tag+双人复核 |

任何环境的数据库、缓存、Bucket、密钥、回调和日志必须隔离。
