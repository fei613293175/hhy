# TASK-R02-007 Android 测试 APK 构建与追溯

## 当前结论

- 固定 Android 工具链中的单元测试、Lint 与 debug APK 打包全部通过。
- APK 使用固定测试签名 Profile `hhy-staging-test-v1` 重签，v2/v3 签名、单一签名者和证书指纹校验通过。
- APK 内嵌真实 API `https://api.orbexa.cc`，不包含 `https://api.example.invalid` 占位端点；公开平台状态、注册配置和安全验证匿名接口全部返回成功。
- 桌面、仓库忽略副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；Range 206、MIME 和下载文件名检查通过。
- 项目所有者真机安装、启动与 R02 核心认证路径仍为 `PENDING`；机器证据不能替代真机结论。

## 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `2bbfa9b7c6cb52457841a8cdd1a0e12b467de494` |
| APK 文件 | `hhy-r02-2bbfa9b-debug.apk` |
| 包名 | `cc.orbexa.hhy.debug` |
| 应用名称 | `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10202` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,923,821` bytes |
| APK SHA-256 | `0ae7d292097d2b0bb5aaff1873229a1a766218500d1fe0f62494fb34109a66af` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r02-2bbfa9b-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r02-artifacts/hhy-r02-2bbfa9b-debug.apk` |

## 工具链与构建

固定镜像为 `hhy-android-toolchain:r01-46fb273`，镜像摘要为 `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。源码归档 SHA-256 为 `3f818101871a192d6cbb24c9630483fa08f76c535e2b00c0a45ed59239f1c308`。

执行 `testDebugUnitTest lintDebug assembleDebug`，结果为 `BUILD SUCCESSFUL in 1m 29s`，共 `289 actionable tasks`。完整日志见 `artifacts/validation/r02-task007-android/gradle-build.log`。

首次正式构建由 `VersionMetadataTest` 阻止了 Gradle `10202` 与应用内发布策略 `10201` 的漂移；修正、登记问题并加入显式回归断言后才重新构建。失败 APK 未交付。

## 签名、联网与下载复核

- `apksigner verify --verbose --print-certs`：v2/v3 PASS，单一 3072 位 RSA 测试签名者。
- `aapt dump badging`：包名、`versionCode 10202`、`versionName 1.2.2-debug` 与 SDK 范围正确。
- `zipalign -c -v 4`：PASS。
- DEX 扫描：真实 API 精确出现 1 次，占位端点出现 0 次；`.invalid` 后缀字面量仅用于发布地址拒绝策略。
- `https://api.orbexa.cc/public-api/v1/platform/status`、`registration-config` 与 `security-challenges`：全部返回 HTTP 200；后端已从旧P00旁路升级到R02并完成Flyway V018。
- 交付脚本首次公网校验发现公开目录错误继承 `umask 077` 为 700，已修复为公开目录 755、私有上传暂存目录 700并增加回归测试；失败上传被自动回撤。
- 修复后 `prepare` 与独立 `verify` 均 PASS，HTTPS 完整下载为 200，Range 验证为 206。

## 真机首轮反馈与修复

项目所有者在 2026-07-18 19:35 上传的首轮真机截图判定旧 `bbad603` APK 为 `FAILED`，该结果没有被记录为验收通过。截图暴露两项真实问题：认证页没有按 B01 参考图与冻结字号/间距/组件尺寸施工，四个路由被压入同一行导致“注册账号”纵向折行；同时公网仍运行旧 P00 后端，使无需登录的安全验证和注册配置接口返回 401，并被客户端显示为“认证已失效”。

修复包 `2bbfa9b` 已按 B01 重构认证页、接入设计 Typography/Shapes/尺寸令牌，并将注册与忘记密码恢复为辅助入口；公网 R02 候选服务在数据库备份后完成 V012—V018 迁移，三个公网门禁通过后切换。首次下载验证还发现 Cloudflare 负缓存旧 404，现已增加强制回源验证并完成四方哈希交付。

## 待完成门禁

当前环境没有 ADB 真机或模拟器。桌面修复 APK 已可供项目所有者安装；收到该新文件的真机安装、启动、登录/注册入口与公开接口重试结果后，才执行 `accept`、关闭 TASK-R02-007 并进入 TASK-R02-008。
