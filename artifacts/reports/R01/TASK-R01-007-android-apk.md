# TASK-R01-007 Android 测试 APK 构建与追溯

## 当前结论

- 固定 Docker 工具链中的单元测试、Lint 与 debug APK 打包全部通过。
- APK 已放在项目所有者桌面，且桌面副本 SHA-256 与服务器构建产物一致。
- 项目所有者于 2026-07-18 提供真机截图，确认 APK 已安装、正常启动并进入首页，底部五个导航入口可见；`TASK-R01-007` 与 `AC-R01-006` 真机验收为 PASS。

## 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `db3cfddcc4cda84265ec18f24de972804d62a08f` |
| APK 文件 | `hhy-r01-db3cfdd-debug.apk` |
| 包名 | `cc.orbexa.hhy.debug` |
| 应用名称 | `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10201` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,708,202` bytes |
| APK SHA-256 | `7dcd2e86bba02ad45a9d9187d28ed9cab260aefa39d7ae40ed0bfd487fa83b9f` |
| 签名 | Android Debug，v2 PASS，单一签名者 |
| 签名证书 SHA-256 | `68529af253b7254248c1213e27dc113febdcdddfdd0be1501e2804e266dd35ee` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r01-db3cfdd-debug.apk` |

## 工具链与构建

工具链定义在 `infra/toolchains/android/Dockerfile`，使用 Java 21、Android SDK Platform 37.0 和 Build Tools 36.0.0。构建镜像为 `hhy-android-toolchain:r01-46fb273`，镜像摘要为 `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。

以源码 Commit `db3cfdd` 的归档校验值 `770107187d1646a116849581f421d17e9eca5f3e2345a42629a5c7888cbc0cd4` 构建，执行：

```text
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

结果为 `BUILD SUCCESSFUL in 1m 11s`，`193 actionable tasks: 117 executed, 76 from cache`。完整日志见 [gradle-build.log](../../validation/r01-task007-android/gradle-build.log)。

## 安全与可安装性静态复核

- `apksigner verify --verbose --print-certs`：APK v2 签名验证通过；完整输出见 [apk-signing.txt](../../validation/r01-task007-android/apk-signing.txt)。
- `aapt dump badging`：确认独立调试包名、版本、SDK 范围、启动入口以及 `合伙云 Pro 测试` 应用名称；完整输出见 [apk-badging.txt](../../validation/r01-task007-android/apk-badging.txt)。
- APK 内容扫描确认构建注入的是 `https://api.orbexa.cc`，未使用 `api.example.invalid` 安全占位地址。
- 本地和桌面副本均已按 SHA-256 与服务器产物复核。

当前环境没有可用 ADB 真机或模拟器，因此上述静态复核不能替代真机安装、启动与基本导航检查。项目所有者已于 2026-07-18 提供真机截图，确认安装成功、应用正常启动进入首页，且首页/红包/发布/消息/我的五个导航入口可见。该截图作为本任务的真机验收证据；[APK_MANIFEST.yaml](../../apk/R01/APK_MANIFEST.yaml) 已同步回填 PASS。
