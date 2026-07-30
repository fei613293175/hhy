# TASK-R08-007 Android 测试 APK 与产物追溯

## 当前结论

- R08 Android 候选以产品 Commit `49f40f2dfeb71b5f5210d2e03a500307bf42c31c` 完成 GitHub 编译、Lint、单测、打包、模拟器安装、OIDC 一次性认证、真实项目旅程、目标进程日志和安全门禁。
- 源运行 `29905158793` 一次采集项目列表、项目详情和项目编辑三张目标截图；AI 审核合格后，轻量晋升运行 `29906167593` 在 15 秒内复用同一 APK、报告和截图生成 `PASS` 候选，没有重复 Gradle 编译或模拟器启动。
- 三页均达到绑定效果图或批准补充规格的丰富度和精致度；只呈现真实项目夹具和已实现功能，技术编码、虚构媒体、金额收益及联系方式明文均未进入截图。
- 固定测试签名、`versionCode=10216`、`https://api.orbexa.cc`、APK 大小和 SHA-256 均通过。仓库副本、桌面副本、服务器文件和 HTTPS 完整下载四方一致。
- 机器交付为 `PASS`，项目所有者真机反馈保持异步 `PENDING`；不据此伪造正式发布或生产激活。

## APK 身份

| 项目 | 值 |
| --- | --- |
| 产品 Commit | `49f40f2dfeb71b5f5210d2e03a500307bf42c31c` |
| 源 GitHub Run | `29905158793` |
| 轻量晋升 Run | `29906167593`，15 秒 |
| APK | `hhy-r08-49f40f2-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10216` |
| 大小 | `19,678,357` bytes |
| SHA-256 | `d424c880ccf45cb1f092bef5a88a6459ec17747c95f3b9ed1a833de09d63557a` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r08-49f40f2-debug.apk` |
| 下载 | `https://download.orbexa.cc/r08-artifacts/hhy-r08-49f40f2-debug.apk` |

## 自动化与视觉证据

- 完整构建、Lint、单测、APK 与模拟器真实认证旅程：`PASS`。
- 目标页面：`SCR-LIST-001`、`SCR-DETAIL-001`、`SCR-PUB-002`。
- 三张截图 SHA-256：
  - `01-project-list.png`：`2626afaeeac612601b268fcb305411b4abecb61eff8cd4c76b2e35e2f49d6c66`
  - `02-project-detail.png`：`32bb238774bfda8f03bd7e1d6049177d16ea0e176a7ee9eecba90719ef00e409`
  - `03-project-editor.png`：`b9bc8fb2337b458551385a35fe440595f192717007830b0e1e7602e732d39cf4`
- 截图未出现登录/注册页重复、联系方式明文、内部错误码、请求编号、TraceId 或原始业务枚举；字号、间距、颜色、圆角、卡片密度和固定操作区符合冻结视觉合同。
- 首版视觉基线采用“单次权威采集 + AI 审核 + 同源轻量晋升”，避免为同一基线重复运行完整候选。

## 交付门禁

- 下载站只增加 `/r08-artifacts/hhy-r08-49f40f2-debug.apk` 精确路由；Nginx 配置测试和热加载通过，原配置保留精确备份。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均通过：HTTPS 200、Range 206、APK MIME、大小和四方 SHA-256 一致。
- 交付状态：`delivery_status=PASS`、`owner_physical_test=PENDING`、`formal_release_acceptance=PENDING_OWNER_PHYSICAL_TEST`、`production_activation=BLOCKED_OWNER_PHYSICAL_TEST`。

## 证据

- GitHub 候选、审批和截图：`artifacts/validation/r08-task007-android/`
- 四方交付：`artifacts/validation/r08-apk-delivery/delivery-evidence.json`
- Manifest：`artifacts/apk/R08/APK_MANIFEST.yaml`
- 真机说明：`artifacts/reports/R08/R08-version-test-guide.md`

`TASK-R08-007` 的机器候选、视觉、安全、产物追溯和桌面交付已完成。异步真机反馈不阻断 `TASK-R08-008` 机器交接和后续版本开发。
