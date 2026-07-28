# TASK-R14-007 Android 测试 APK 与产物追溯

## 结论

- 冻结产品 Commit `ca5766600937e1bed056210b8579ee7f8269561b` 在 `obx-test` 固定镜像完成 Android UI 基础、正式 API/WSS、单测、Lint 和 APK 打包，结果为 `BUILD SUCCESSFUL in 6m 57s`，772 个任务中 194 个执行、578 个保持最新。
- APK 使用长期固定测试签名 `hhy-staging-test-v2`；zipalign、APK Signature Scheme v2/v3、单一 RSA 3072 位签名者、包名、版本号以及内嵌 API/WSS 全部通过。
- 仓库忽略副本、项目所有者桌面、服务器文件与 HTTPS 完整下载的大小和 SHA-256 一致；精确下载配置由 Commit `05d738171911d31d382d8cf5b40dfaab9dacf5b4` 追溯。
- GitHub 模拟器、功能旅程和截图属于按需专项，本次未运行；项目所有者真机反馈保持异步 `PENDING`。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r14-ca57666-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10223` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式 API | `https://api.orbexa.cc` |
| WebSocket | `wss://ws.orbexa.cc` |
| 大小 | `21,903,068` bytes |
| SHA-256 | `46f050310143d5968864c5aacd9ad38b1fb7290931bc113c76429ebc7ecf126d` |
| 签名 | `hhy-staging-test-v2` / `e32a9d7ff8a209d2903db6383b461b259f0018a698d1f3646e6ed7f1112671be` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r14-ca57666-debug.apk` |
| 下载 | `https://download.orbexa.cc/r14-artifacts/hhy-r14-ca57666-debug.apk` |

## 机器验证

- 首轮构建在 3 分 31 秒由 `VersionMetadataTest` 正确拒绝 10223 与旧 10222 不一致；`CR-0437`只同步两个既有版本消费者，未改变业务功能，修正后完整门禁重跑 PASS。
- `verifyApiBaseUrl`、`testDebugUnitTest`、`lintDebug`、`assembleDebug` 和 `check_android_ui_foundation.py` 全部 PASS。
- APK 内含 `https://api.orbexa.cc` 与 `wss://ws.orbexa.cc`，不含占位 API；公网状态接口 HTTP 200。
- HTTPS 完整请求 200、Range 请求 206、MIME 为 `application/vnd.android.package-archive`；未授权的其他 R14 路径继续 404。

## 边界

- `ws.orbexa.cc` 仍为 `BLOCKED_EXTERNAL_DNS`，因此本报告不声明公网实时链路 PASS；客户端继续保留断线退避和 REST 权威刷新。
- `PROB-0135` 仍为 `OPEN`，举报原因目录为空时举报提交继续禁用。
- 本报告证明机器构建、签名、身份和交付，签署 `AC-R14-006 PASS`；由于前置任务仍阻断，不把 `TASK-R14-007` 或完整 R14 伪标 DONE。

## 证据

- `artifacts/validation/r14-task007-android/`
- `artifacts/validation/r14-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R14/APK_MANIFEST.yaml`
- `artifacts/reports/R14/R14-version-test-guide.md`
