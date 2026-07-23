# TASK-R09-007 Android 测试 APK 与产物追溯

## 当前结论

- R09 Android 候选以产品 Commit `97dc163e1cbed8d19a054326806453c5fa0aee9b` 完成 GitHub 编译、Lint、单测、打包、模拟器安装、OIDC 一次性认证、真实 App 推广旅程、图片加载硬断言、目标进程日志和安全门禁。
- 源运行 `29969376611` 一次采集 App 列表、App 详情和 App 编辑三张目标截图；AI 审核合格后，轻量晋升运行 `29970240620` 在 17 秒内复用同一 APK、报告和截图生成 `PASS` 候选，没有重复 Gradle 编译或模拟器启动。
- 三页均达到绑定效果图或批准视觉规格的丰富度和精致度；列表与详情均明确显示发布者上传的两张真实应用截图，不再使用占位图，也未虚构业务功能、媒体或数据。
- 固定测试签名、`versionCode=10218`、`https://api.orbexa.cc`、APK 大小和 SHA-256 均通过。仓库副本、桌面副本、服务器文件和 HTTPS 完整下载四方一致。
- 机器交付为 `PASS`，项目所有者真机反馈保持异步 `PENDING`；不据此伪造正式发布或生产激活。

## APK 身份

| 项目 | 值 |
| --- | --- |
| 产品 Commit | `97dc163e1cbed8d19a054326806453c5fa0aee9b` |
| 源 GitHub Run | `29969376611`，总耗时 9 分 25 秒 |
| 轻量晋升 Run | `29970240620`，17 秒 |
| APK | `hhy-r09-97dc163-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10218` |
| 大小 | `20,752,173` bytes |
| SHA-256 | `78dda7171867f9005917b9768d5c79510ba0c9ddf86448c1497d8b62e65e31f9` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r09-97dc163-debug.apk` |
| 下载 | `https://download.orbexa.cc/r09-artifacts/hhy-r09-97dc163-debug.apk` |

## 自动化与视觉证据

- 完整构建、Lint、单测、APK 与模拟器真实认证旅程：`PASS`。
- 目标页面：`SCR-LIST-002`、`SCR-DETAIL-002`、`SCR-PUB-003`。
- 四张真实媒体加载标识在截图前全部出现：列表 media 1/2、详情 media 1/2；任一图片 error 或 20 秒超时都会让 instrumentation 失败并禁止截图。
- 三张截图 SHA-256：
  - `01-app-list.png`：`564a30da48820509ed73e604918509a3ec469a4fab8a4e1ab3eb25abe48c3ac6`
  - `02-app-detail.png`：`7e15198b77b0eeb93913635e0b06ecbd48fc8243b0982635c5b3bea2731ea02f`
  - `03-app-editor.png`：`17d49803d398e6b7f95a99c7bc7b07cdd8d58a7b9a9fb40e7aa8efb19bf6c5e4`
- 截图未出现登录/注册页重复、联系方式明文、内部错误码、请求编号、TraceId、原始业务枚举或虚构 APK 上传入口；字号、间距、颜色、圆角、卡片密度和固定操作区符合冻结视觉合同。
- 首版视觉基线采用“单次权威采集 + AI 审核 + 同源轻量晋升”，避免为同一基线重复运行完整候选。

## 固定签名与交付门禁

- 晋升 ZIP SHA-256：`bd24942389ef89cc75f5ed6ec00c21ef41b6e9881c54bb50280a91beeea81ff2`。
- 晋升内源 APK SHA-256：`4c4b05a1249802072168d3d4e43e5824998487bc4c9757b98dee90449b9e8224`。
- 固定容器：`hhy-android-toolchain:r01-46fb273`，Image ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- `zipalign` 通过；APK Signature Scheme v2、v3 通过；证书 SHA-256 与固定测试证书一致。
- APK 包名为 `cc.orbexa.hhy.debug`，versionName/versionCode 为 `1.2.2-debug` / `10218`；已验证正式 API 基址为 `https://api.orbexa.cc`，不存在 `.invalid` 形式的实际网络端点。
- 下载站增加 `/r09-artifacts/hhy-r09-97dc163-debug.apk` 精确路由；Nginx 配置测试和热加载通过，原配置保留精确备份。
- 公网验证：HTTPS `200`、Range `206`、MIME `application/vnd.android.package-archive`、大小及服务器/HTTPS/仓库/桌面 SHA-256 一致。
- 交付状态：`delivery_status=PASS`、`owner_physical_test=PENDING`、`formal_release_acceptance=PENDING_OWNER_PHYSICAL_TEST`、`production_activation=BLOCKED_OWNER_PHYSICAL_TEST`。

## 证据

- GitHub 候选、审批和构建证据：`artifacts/validation/r09-task007-android/`
- 视觉基线与原图：`tests/android/visual-baselines/R09/`
- 四方交付：`artifacts/validation/r09-apk-delivery/delivery-evidence.json`
- Manifest：`artifacts/apk/R09/APK_MANIFEST.yaml`
- 真机说明：`artifacts/reports/R09/R09-version-test-guide.md`

`TASK-R09-007` 的机器候选、视觉、安全、产物追溯和桌面交付已完成。异步真机反馈不阻断 `TASK-R09-008` 机器交接和后续版本开发。
