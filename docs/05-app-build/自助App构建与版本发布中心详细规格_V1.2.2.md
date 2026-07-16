# 合伙云 Pro 官方 App 自助构建与版本发布中心 V1.2

## 1. 边界

该中心只构建**合伙云 Pro 官方 Android App**，不是白标工厂，也不允许上传第三方源码、执行任意 Shell 或构建任意 APK。

## 2. 能力

- 构建 Profile：环境、包名、显示名、API/H5/WS地址、版本策略、允许Git Ref、Gradle参数。
- 签名 Profile：测试签名自动生成；生产签名由受保护 CI Secret 或密钥文件提供，只展示证书指纹。
- 构建任务：选择批准的 Commit/Tag，执行代码拉取、依赖锁定、Lint、单测、UI测试、后端/契约检查、APK构建、签名、SHA256、上传。
- 实时日志：每一步单独状态和日志对象，不允许输出秘密。
- 产物：APK、mapping、测试报告、SBOM、SHA256、签名指纹、Commit、版本号和下载二维码。
- 发布：测试渠道直接发布；生产渠道双人复核，支持可选/推荐/强制更新、灰度、回滚。
- 每个涉及Android的开发版本必须自动创建测试构建并提供下载。

## 3. 安全

- Runner隔离、无生产数据库权限、网络白名单、任务超时、并发限制。
- 仅允许 `main/release/*/v*` 等批准Ref；生产构建必须来自Tag和绿灯Commit。
- 后台参数不能注入任意命令；Gradle参数采用白名单字段。
- 正式签名不得放入Git或普通Codex工作区。

## 4. 页面/API/表

页面 `ADM-BUILD-001` 至 `ADM-BUILD-007`；API `/admin-api/v1/app-build/*`；表 `app_build_*`、`app_signing_profiles`、`app_release_*`。

## 5. P00包名策略

Codex在P00运行 `scripts/generate_android_identity.py`，生成稳定随机包名，例如 `cc.orbexa.hhypro.a7k3m9`，测试包追加 `.staging`。结果写入版本化配置后冻结；后续修改必须CR并考虑升级兼容。正式签名方案也在P00生成，但生产密钥由用户/受保护CI持有。
