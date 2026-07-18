# TASK-R02-007 Android 测试 APK 构建与追溯

## 当前结论

- 固定 Android 工具链中的单元测试、Lint 与 debug APK 打包全部通过。
- APK 使用固定测试签名 Profile `hhy-staging-test-v1` 重签，v2/v3 签名、单一签名者和证书指纹校验通过。
- APK 内嵌真实 API `https://api.orbexa.cc`，不包含 `https://api.example.invalid` 占位端点；公开平台状态接口返回成功。
- 桌面、仓库忽略副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；Range 206、MIME 和下载文件名检查通过。
- 项目所有者真机安装、启动与 R02 核心认证路径仍为 `PENDING`；机器证据不能替代真机结论。

## 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `bbad603ed7136e1fc1d9fbcf03ca414c6862d16e` |
| APK 文件 | `hhy-r02-bbad603-debug.apk` |
| 包名 | `cc.orbexa.hhy.debug` |
| 应用名称 | `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10202` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,907,437` bytes |
| APK SHA-256 | `0e9f0b2c004fd839f4811bcce5b103111d435167c774812c0b97b90d7b345cf6` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r02-bbad603-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r02-artifacts/hhy-r02-bbad603-debug.apk` |

## 工具链与构建

固定镜像为 `hhy-android-toolchain:r01-46fb273`，镜像摘要为 `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。源码归档 SHA-256 为 `078f1d8eb324970b96c618117e9a5a3eb636ae89d26274be1f73e61ca9e7667a`。

执行 `testDebugUnitTest lintDebug assembleDebug`，结果为 `BUILD SUCCESSFUL in 2m 21s`，共 `295 actionable tasks`。完整日志见 `artifacts/validation/r02-task007-android/gradle-build.log`。

首次正式构建由 `VersionMetadataTest` 阻止了 Gradle `10202` 与应用内发布策略 `10201` 的漂移；修正、登记问题并加入显式回归断言后才重新构建。失败 APK 未交付。

## 签名、联网与下载复核

- `apksigner verify --verbose --print-certs`：v2/v3 PASS，单一 3072 位 RSA 测试签名者。
- `aapt dump badging`：包名、`versionCode 10202`、`versionName 1.2.2-debug` 与 SDK 范围正确。
- `zipalign -c -v 4`：PASS。
- DEX 扫描：真实 API 精确出现 1 次，占位端点出现 0 次；`.invalid` 后缀字面量仅用于发布地址拒绝策略。
- `https://api.orbexa.cc/public-api/v1/platform/status`：成功返回运行状态。
- 交付脚本首次公网校验发现公开目录错误继承 `umask 077` 为 700，已修复为公开目录 755、私有上传暂存目录 700并增加回归测试；失败上传被自动回撤。
- 修复后 `prepare` 与独立 `verify` 均 PASS，HTTPS 完整下载为 200，Range 验证为 206。

## 待完成门禁

当前环境没有 ADB 真机或模拟器。桌面 APK 已可供项目所有者安装；收到真机安装、启动、登录/注册入口与重试结果后，才执行 `accept`、关闭 TASK-R02-007 并进入 TASK-R02-008。

