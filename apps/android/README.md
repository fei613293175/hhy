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

## 首次构建

```bash
export HHY_API_BASE_URL=https://api.dev.example.com
./gradlew --version
./gradlew testDebugUnitTest lintDebug assembleDebug
```

本地必须安装 Android SDK API 37 和 Build Tools 36.0.0。签名材料不得提交到仓库；Release 构建由 CI 注入 keystore 和密码。

## 硬约束

1. 页面代码禁止直接新增颜色、间距和字号常量，必须通过 `core:designsystem`。
2. API DTO 不得由页面手写；契约冻结后从根目录 `contracts/openapi.yaml` 生成。
3. 任何 Token、手机号、身份证号和支付字段不得写日志。
4. `api.example.invalid` 仅为安全占位，未设置 `HHY_API_BASE_URL` 时不得发布。
5. 102 个页面的机器可读目录位于 `app/src/main/assets/android-screens.v1.2.2.json`。
