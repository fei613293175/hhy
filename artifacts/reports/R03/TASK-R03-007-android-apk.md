# TASK-R03-007 外部激活证据与 Android 回归 APK

## 当前结论

- R03 Android 回归 APK 已在固定云端工具链完成单元测试、Lint、构建、固定签名、对齐和版本身份检查。
- APK 内嵌真实 API `https://api.orbexa.cc` 1 次，占位端点 `https://api.example.invalid` 0 次；公开注册配置与安全验证接口均为 HTTP 200，挑战图片为 PNG 160x56。
- 桌面、仓库忽略副本、服务器和 HTTPS 完整下载四方 SHA-256 一致；公网完整下载 200、Range 206、MIME 与下载文件名检查通过。
- 外部供应商、SecretRef、证书和域名均已逐项登记为 VERIFIED 或明确的负责人、截止版本和阻断编号，没有伪造连接成功或临时生产凭据。
- 机器交付状态为 `PASS`；项目所有者真机安装、启动和核心路径状态严格保持 `PENDING`。

## APK 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `660d148d0743e796a9b77558bed68f046197519e` |
| APK 文件 | `hhy-r03-660d148-debug.apk` |
| 包名 / 应用名称 | `cc.orbexa.hhy.debug` / `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10206` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,940,205` bytes |
| APK SHA-256 | `a5f2c39a4a9bdd499e99e47be660944625bd71748851c314f728b52d4a211994` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r03-660d148-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r03-artifacts/hhy-r03-660d148-debug.apk` |

## 构建、签名与联网门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 源码归档 SHA-256：`7b22d484fbe12af98e04f46dd633ed63e6dabf852d1133bfa64c88825312cc57`。
- Gradle：基于不可变提交执行 `clean testDebugUnitTest lintDebug assembleDebug` 并通过；后端完整测试与注册错误场景回归测试均通过。
- `apksigner`：v2/v3 PASS、单一签名者、RSA 3072；`zipalign -c -v 4` PASS。
- DEX：真实 API 精确出现 1 次，占位端点 0 次。
- 公开认证：注册配置与安全挑战均为 200，挑战图片解码为 PNG 160x56。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均 PASS；公开下载 200、Range 206、Android APK MIME 正确。

## 外部激活清单

| 项目 | 当前状态 | 负责人 | 截止版本 | 阻断 |
| --- | --- | --- | --- | --- |
| SMS | BLOCKED | PROJECT_OWNER | R03 | PROB-0012 |
| STORAGE | BLOCKED | PROJECT_OWNER | R04 | PROB-0030 |
| IDENTITY | BLOCKED | PROJECT_OWNER | R05 | PROB-0030 |
| PAYMENT | BLOCKED | PROJECT_OWNER | R16 | PROB-0030 |
| PAYOUT | BLOCKED | PROJECT_OWNER | R22 | PROB-0030 |
| `api.orbexa.cc` | VERIFIED | — | — | — |
| `download.orbexa.cc` 精确 APK 路径 | VERIFIED | — | — | — |
| 其余 10 个 R03 域名 | BLOCKED | PROJECT_OWNER | R03 | PROB-0031 |

详细 SecretRef、证书指纹要求和 DNS/TLS/服务健康三层证据见 `artifacts/validation/r03-task007-external/`。连接测试未通过的配置版本不得激活。

## 待完成门禁

项目所有者需覆盖安装桌面 `hhy-r03-660d148-debug.apk`，重点确认：已注册手机号明确提示“该手机号已注册，请直接登录或找回密码”；不存在或失效邀请码明确提示“邀请码无效或已失效，请检查后重试”，不得再出现笼统注册失败或服务暂不可用。收到明确 PASS 前，`owner_physical_test` 保持 `PENDING`，TASK-R03-007 不得标记 DONE，R03 不得关闭。
