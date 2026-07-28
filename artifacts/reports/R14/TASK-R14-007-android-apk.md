# TASK-R14-007 Android 测试 APK 与产物追溯

## 结论

- 冻结产品 Commit `2eb8ac74b55c35833609c6eb6f330268a289bf3a` 在 `obx-test` 固定镜像完成 Android UI 基础、正式 API/WSS、单测、Lint 和 APK 打包，结果为 `BUILD SUCCESSFUL in 3m 44s`，772 个任务中 470 个执行、302 个命中缓存。
- APK 使用长期固定测试签名 `hhy-staging-test-v2`；zipalign、APK Signature Scheme v2/v3、单一 RSA 3072 位签名者、包名、版本号以及内嵌 API/WSS 全部通过。
- 仓库忽略副本、项目所有者桌面、服务器文件与 HTTPS 完整下载的大小和 SHA-256 一致；精确下载配置由 Commit `32522a9da99366d320bc6bb65f7ca0b86867512d` 追溯，旧 `ca57666` Manifest 与 Evidence 已归档。
- GitHub 模拟器、功能旅程和截图属于按需专项，本次未运行；项目所有者真机反馈保持异步 `PENDING`。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r14-2eb8ac7-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10224` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式 API | `https://api.orbexa.cc` |
| WebSocket | `wss://ws.orbexa.cc` |
| 大小 | `21,915,555` bytes |
| SHA-256 | `6ce8494e46d3a831c3ca9629d78bad97c445e9d93d084d8c0cd1c7292a885481` |
| 签名 | `hhy-staging-test-v2` / `e32a9d7ff8a209d2903db6383b461b259f0018a698d1f3646e6ed7f1112671be` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r14-2eb8ac7-debug.apk` |
| 下载 | `https://download.orbexa.cc/r14-artifacts/hhy-r14-2eb8ac7-debug.apk` |

## 机器验证

- `CR-0471`按替换交付规则将 Gradle、`ReleasePolicy` 与 `VersionMetadataTest` 三处身份统一递增到 10224；Android UI 基础门禁与固定工具链完整门禁一次通过。
- `verifyApiBaseUrl`、`testDebugUnitTest`、`lintDebug`、`assembleDebug` 和 `check_android_ui_foundation.py` 全部 PASS。
- APK 内含 `https://api.orbexa.cc` 与 `wss://ws.orbexa.cc`，不含占位 API；公网状态接口 HTTP 200。
- HTTPS 完整请求 200、Range 请求 206、MIME 为 `application/vnd.android.package-archive`；未授权的其他 R14 路径继续 404。
- APK 已包含 `CR-0464` 的正式举报原因目录，OpenAPI 单源、Java/Kotlin消费者及未知、停用、顺序和大小写拒绝保持一致。

## 边界

- `ws.orbexa.cc` 仍为 `BLOCKED_EXTERNAL_DNS`，因此本报告不声明公网实时链路 PASS；客户端继续保留断线退避和 REST 权威刷新。
- `owner_physical_test=PENDING` 保持异步，不要求项目所有者逐版本反馈，也不阻断后续机器任务。
- 本报告证明机器构建、签名、身份、下载和桌面成对交付，签署 `AC-R14-006 PASS`；`TASK-R14-007` 可按连续性协议关闭并仅进入 `TASK-R14-008`。

## 证据

- `artifacts/validation/r14-task007-android/`
- `artifacts/validation/r14-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R14/APK_MANIFEST.yaml`
- `artifacts/reports/R14/R14-version-test-guide.md`
