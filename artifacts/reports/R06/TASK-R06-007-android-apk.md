# TASK-R06-007 Android 测试 APK 与产物追溯

## 当前结论

- R06 Android 候选以 Commit `f5cf1e4b315221d7e5998dca6ea5110421c1e62b` 完成 GitHub 编译、Lint、单测、打包、模拟器安装、登录后业务旅程、三张目标页面截图、视觉基线和目标进程日志门禁。
- AI 已审核首页、我的、关于与检查更新三张截图，页面身份、加载状态、五栏导航、返回入口、文本边界和布局均合格；未出现登录注册页重复截图。
- 最终 APK 使用稳定测试证书重新签名，v2/v3、zipalign、`versionCode=10214`、`https://api.orbexa.cc`、`official + STAGING` 与公网启动策略全部通过。
- 仓库副本、桌面副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致。机器交付为 `PASS`，项目所有者真机反馈保持异步 `PENDING`。

## APK 身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `f5cf1e4b315221d7e5998dca6ea5110421c1e62b` |
| GitHub Run | `29803223373`，总耗时 `12m 22s` |
| APK | `hhy-r06-f5cf1e4-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10214` |
| 大小 | `19,317,850` bytes |
| SHA-256 | `32e18bd454548c4a271c9199d5e2646615b0a2ab12aca271f039a737af43f3f7` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r06-f5cf1e4-debug.apk` |
| 下载 | `https://download.orbexa.cc/r06-artifacts/hhy-r06-f5cf1e4-debug.apk` |

## 自动化与视觉证据

- 编译、Lint、单测和打包：`PASS`，7 分 57 秒。
- 模拟器安装、GitHub OIDC 一次性登录、首页/我的/关于旅程、截图和日志：`PASS`，3 分 48 秒。
- 候选资格：`status=PASS`、`owner_test_allowed=true`、修复队列未触发。
- 三张截图对批准基线的像素变化率分别为 `0.000758`、`0.000758`、`0.000755`，没有重叠、裁切、错页或技术字段。
- 日志中的系统 Google 搜索进程 ANR 与目标 App 无关；`cc.orbexa.hhy` 无崩溃、ANR 或异常退出。

## 签名、联网与交付门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- APK Signature Scheme v2/v3 为 `true`，证书与 R05 已验收测试包一致，可覆盖安装。
- DEX 中 `https://api.orbexa.cc` 精确出现 1 次，`.invalid` 和 `owner-test` 均为 0；`official` 与 `STAGING` 已写入。
- `POST /public-api/v1/app/version-check` 使用 `ANDROID + official + STAGING + 10214` 返回 HTTP 200。
- 下载站只增加当前 APK 精确路由；Nginx 配置测试和热加载通过，原配置保留备份。
- `deliver_android_test_apk.py prepare` 和独立 `verify` 均通过：HTTPS 200、Range 206、APK MIME、大小和四方 SHA-256 一致。

## 证据

- GitHub 候选、截图与签名：`artifacts/validation/r06-task007-android/`
- 交付：`artifacts/validation/r06-apk-delivery/delivery-evidence.json`
- Manifest：`artifacts/apk/R06/APK_MANIFEST.yaml`
- 真机说明：`artifacts/reports/R06/R06-version-test-guide.md`

## 状态

`TASK-R06-007` 的机器候选、固定签名、安装旅程、视觉、日志、产物追溯和桌面交付已完成。`owner_physical_test=PENDING` 是异步真实状态，不伪造为通过，也不阻断依赖已满足的后续开发。
