# 合伙云 Pro Android V1.2.2

这是可直接导入 Android Studio 的 Compose 工程基线，不再是空目录。

## 固定工具链

- Android Gradle Plugin: `9.2.1`
- Gradle Wrapper: `9.4.1`
- Built-in Kotlin / Compose Compiler: `2.3.21`
- Compose BOM: `2026.06.01`
- `compileSdk = 37`，`targetSdk = 36`，`minSdk = 26`
- Java bytecode target: 17

AGP 9 已启用 Built-in Kotlin，因此禁止重新应用 `org.jetbrains.kotlin.android`。Compose 模块仅应用 `org.jetbrains.kotlin.plugin.compose`。

## 模块

- `app`：应用入口、环境构建字段、壳层装配。
- `core:designsystem`：V1.2.2 设计 Token 和 Material 3 主题。
- `core:network`：公共接口模型与网络边界；后续由冻结 OpenAPI 生成客户端实现。
- `feature:shell`：启动后的五主导航壳层和开发基线状态页。

## 固定云端构建环境

除非项目所有者明确报告未连接，否则默认 Codex 客户端已经连接项目云服务器 `obx-test`，且项目既有 Android 环境可用。每次开发先执行：

```bash
python3 scripts/verify_cloud_environment.py --check-android
```

预检只验证现有资源，不创建、下载或重建任何基础设施：

- Docker image: `hhy-android-toolchain:r01-46fb273`
- Image ID: `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`
- Gradle cache volume: `hhy-r01-android-gradle-cache`

失败时必须停止开发并报告；不得切换到无服务器状态，也不得安装或重建本地 Android SDK 作为回退。

## 构建

```bash
export HHY_API_BASE_URL=https://api.dev.example.com
ssh -o BatchMode=yes obx-test '<在既有 hhy-android-toolchain:r01-46fb273 容器内运行 ./gradlew testDebugUnitTest lintDebug assembleDebug>'
```

本地 Android SDK 不是项目开发前提，禁止执行本地 SDK bootstrap/rebuild。签名材料不得提交到仓库；Release 构建由 CI 注入 keystore 和密码。

## 硬约束

1. 页面代码禁止直接新增颜色、间距和字号常量，必须通过 `core:designsystem`。
2. API DTO 不得由页面手写；契约冻结后从根目录 `contracts/openapi.yaml` 生成。
3. 任何 Token、手机号、身份证号和支付字段不得写日志。
4. `api.example.invalid` 仅为安全占位，未设置 `HHY_API_BASE_URL` 时不得发布。`assemble*`/`package*`/`bundle*` 现会在产生 APK/AAB 前强制执行 `verifyApiBaseUrl`，占位域名、非 HTTPS 或带凭据地址必须构建失败。
5. 102 个页面的机器可读目录位于 `app/src/main/assets/android-screens.v1.2.2.json`。
