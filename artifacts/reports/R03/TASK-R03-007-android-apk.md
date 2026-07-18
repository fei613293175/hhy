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
| 源码 Commit | `0958f1f96ff12b769647c293082e5a4fadd7cb78` |
| APK 文件 | `hhy-r03-0958f1f-debug.apk` |
| 包名 / 应用名称 | `cc.orbexa.hhy.debug` / `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10203` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,940,205` bytes |
| APK SHA-256 | `64f1b9df021091ad3866998babdba073308489d7e1480548339a2b04a686750d` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r03-0958f1f-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r03-artifacts/hhy-r03-0958f1f-debug.apk` |

## 构建、签名与联网门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 源码归档 SHA-256：`110fdd123253bd4c70444000b5a6e693f58f964d45b054de4a626eab8eb53617`。
- Gradle：`testDebugUnitTest lintDebug assembleDebug`，`BUILD SUCCESSFUL in 3m2s`，289 个 actionable task。
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

项目所有者需安装桌面 `hhy-r03-0958f1f-debug.apk`，至少确认安装、启动、四个认证页、点击业务按钮自动出现真实验证码、无短信注册和全局无技术提示。收到明确 PASS 前，`owner_physical_test` 保持 `PENDING`，TASK-R03-007 不得标记 DONE，R03 不得关闭。
