# TASK-R13-007 Android 测试 APK 与产物追溯

## 结论

- 冻结源码 Commit `ccdb5d39b22498a5d0f5082f8210f9fb59b4a2ae` 在 `obx-test` 固定镜像完成正式 API 校验、单测、Lint 和 APK 打包，结果为 `BUILD SUCCESSFUL in 5m 19s`，710 个任务中 426 个执行、284 个命中缓存。
- APK 使用 R13 起长期固定测试签名 `hhy-staging-test-v2`；zipalign、APK Signature Scheme v2/v3、单一 RSA 3072 位签名者、包名、版本号和内嵌正式 API 全部通过。
- 仓库忽略副本、项目所有者桌面、服务器和 HTTPS 完整下载的大小与 SHA-256 一致；独立 `verify` 结果为 `PASS`。
- 本次交付类型为 `TEST_APK`。GitHub 模拟器、功能旅程、截图和视觉报告属于按需专项，本次未运行 Attempt 21，也未把历史失败候选或普通 CI 产物冒充合格自动候选。
- 机器交付为 `PASS`；项目所有者真机反馈保持异步 `PENDING`，不伪造正式验收或生产激活，也不阻断 R14 开发。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r13-ccdb5d3-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10222` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式 API | `https://api.orbexa.cc` |
| 大小 | `21,637,027` bytes |
| SHA-256 | `ccffcd89e116ee2bab0fb55961126a5bcc0c01bd34375ebf542b78cbcd9daba0` |
| 签名 | `hhy-staging-test-v2` / `e32a9d7ff8a209d2903db6383b461b259f0018a698d1f3646e6ed7f1112671be` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r13-ccdb5d3-debug.apk` |
| 下载 | `https://download.orbexa.cc/r13-artifacts/hhy-r13-ccdb5d3-debug.apk` |

## 签名轮换

- R06 至 R12 的旧测试签名私钥未持久化，只有证书指纹，无法恢复或继续签名；历史 APK 和证据保持不改写。
- R13 起签名秘密只在 `obx-test` root-only SecretRef `secretref://obx-test/hhy/android/test-signing/v2` 解析，仓库和日志不含 keystore、口令或私钥。
- 项目所有者首次安装 R13 前需卸载 R12 及更早测试 APK 一次，因此本地登录会话和缓存会清除；服务端账号与业务数据不受影响。R13 及后续包持续复用 v2 后可通过递增 versionCode 覆盖安装。

## 四方交付

- Nginx 精确路由为 `/r13-artifacts/hhy-r13-ccdb5d3-debug.apk`；修改前配置备份为 `/www/server/panel/vhost/nginx/r13-apk-location.inc.before-CR-0413-20260727T154523Z`，`nginx -t`、reload 和仓库路由预检通过。
- HTTPS 完整请求为 200，Range 请求为 206，Content-Type 为 `application/vnd.android.package-archive`。
- 公网 `https://api.orbexa.cc/public-api/v1/platform/status` 返回 HTTP 200；APK 内含 `https://api.orbexa.cc`，未含 `.invalid` API URL。

## 自动候选边界

- R13 历史 Attempt 1 至 20 的运行、失败截图缺失与媒体加载诊断保持原证据；Attempt 21 未授权、未触发。
- 本报告只证明固定环境构建、签名、身份和交付，不声称 GitHub 模拟器截图或自动候选视觉已经 PASS。
- 项目所有者后续真机反馈进入当前适用版本或热修队列；未反馈不停止后续开发。

## 证据

- `artifacts/validation/r13-task007-android/`
- `artifacts/validation/r13-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R13/APK_MANIFEST.yaml`
- `artifacts/reports/R13/R13-version-test-guide.md`
