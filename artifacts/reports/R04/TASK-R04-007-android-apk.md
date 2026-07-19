# TASK-R04-007 Android 测试 APK 与产物追溯

## 当前结论

- R04 Android 测试 APK 已基于不可变源码提交完成单元测试、Lint、构建、固定签名、对齐、版本身份与真实 API 检查。
- 桌面、仓库忽略副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；公网完整下载 200、Range 206、MIME 与下载文件名检查通过。
- 公开注册配置与安全验证接口回归通过，挑战图片为 PNG 160×56，正确和错误安全验证结果均符合合同。
- 机器交付状态为 `PASS`；项目所有者已于 2026-07-19 明确确认 versionCode `10207` 真机安装、启动和基础回归通过，真机验收状态为 `PASS`。

## APK 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `04cda65d4a0749727a482526b616fb56cb475f34` |
| APK 文件 | `hhy-r04-04cda65-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10207` |
| APK 大小 | `11,956,589` bytes |
| APK SHA-256 | `381d91e047c225009f38b152b790fb6f0fac14320d983be463f9ff368327bfce` |
| 签名配置 | `hhy-staging-test-v1` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r04-04cda65-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r04-artifacts/hhy-r04-04cda65-debug.apk` |

该 APK 是固定 Staging 签名的项目所有者真机测试产物，不是生产商店正式发布包。

## 构建、签名与联网门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 源码归档 SHA-256：`bc3587241865c5ac4f041d51bfe39daab28b6b7a42e7e1e6de439499e013fb55`。
- Gradle：执行 `clean testDebugUnitTest lintDebug assembleDebug`，338 项任务全部成功，构建用时 1 分 57 秒。
- `apksigner` 和 `zipalign` 均通过；签名证书与既有 Staging 测试签名一致。
- DEX 中真实 API `https://api.orbexa.cc` 精确出现 1 次，占位端点 `.invalid` 出现 0 次。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均通过；公网完整下载 200、Range 206、Android APK MIME 正确。
- 机器证据见 `artifacts/validation/r04-task007-android/`、`artifacts/validation/r04-apk-delivery/` 和 `artifacts/apk/R04/APK_MANIFEST.yaml`。

## 真机验收边界

冻结页面 `SHEET-MEDIA-001` 是由拥有上传动作的受信任业务页面携带不可变目标快照打开的复用组件，冻结合同明确禁止外部深链直接打开。因此，本 APK 不增加调试菜单、独立媒体测试页或面向用户的技术性入口。选择、并发上传、失败分片重试、取消、完成回传、私有预览和删除确认等组件路径已经由 R04 自动化测试与专项证据覆盖；待后续冻结业务页面接入时再由该页面触发真实交互。

项目所有者已验证 APK 能安装、正常启动并完成基础回归，于 2026-07-19 明确回复测试通过并要求立即继续推进。`artifacts/apk/R04/APK_MANIFEST.yaml` 与 `artifacts/validation/r04-apk-delivery/delivery-evidence.json` 已分别记录 `owner_physical_test: PASS`；TASK-R04-007 的机器交付和项目所有者真机门禁均已满足，可以进入 TASK-R04-008 版本关闭。
