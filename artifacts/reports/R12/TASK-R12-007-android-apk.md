# TASK-R12-007 Android 测试 APK 与产物追溯

## 结论

- 产品候选 Commit `8090001f064c304db08ca9886fe1b1d4d231ef00` 的 GitHub Run `30194396225` 完成编译、Lint、单测、打包、模拟器安装、真实 OIDC 自动认证、十页功能旅程、目标进程日志和截图。
- 十张截图由 AI 逐页对照 R12 冻结规格及 B04、B05、B06、B08 精确效果图审核通过；轻量晋升 Run `30195682023` 只复用源 APK、报告和截图，没有重新构建或启动模拟器。
- 固定签名 APK 通过 zipalign、v2/v3、单一签名者、正式 API、包名和版本身份校验，并完成仓库、桌面、服务器和 HTTPS 四方一致交付。
- 机器交付 `PASS`；项目所有者真机反馈异步 `PENDING`，不伪造正式验收或生产激活，也不阻断后续开发。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r12-8090001-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10221` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式 API | `https://api.orbexa.cc` |
| 大小 | `21,522,280` bytes |
| SHA-256 | `b97bf8fd1a0306678f297da72c6b5b2e0b224aff2965a962a7db41167bc3e4f8` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r12-8090001-debug.apk` |
| 下载 | `https://download.orbexa.cc/r12-artifacts/hhy-r12-8090001-debug.apk` |

## 自动化和视觉

- 源 Run：`30194396225`；源 Artifact：`8629749064`；源 Artifact digest：`ff9ef0e221a0a99a0bd3054b54598b18171e5ceeefebfe2aaa99d4153c13ad63`；源 APK SHA：`c5fb2eb7546bc1c68d227d3c4b4397060886315435a520890c4b7b1fade0c503`。
- 晋升 Run：`30195682023`；晋升 Artifact：`8630028491`；Artifact digest：`423f76d0247ef331a988538ece00a424988d9f3caa75ea15e89efbe9f139446c`。
- 截图覆盖发布中心、我的首页、个人资料、草稿箱、内容管理详情、发布预览、提交结果、我的发布、审核记录和内容数据；页面丰富度、层级、安全区、真实数据映射、按钮对比度和合同空状态全部合格。
- 未出现转场残层、崩溃、ANR、JUnit 失败、ISO 技术时间、requestId、接口名、异常栈、虚构业务字段或未经合同支持的趋势数据。

## 四方交付

- 仓库、桌面、服务器 `/www/wwwroot/download.orbexa.cc/r12-artifacts/` 和 HTTPS 完整下载 SHA-256 完全一致。
- HTTPS `200`、Range `206`、APK MIME 和 Content-Disposition 均通过。
- R12 精确 Nginx 下载路由已通过配置语法检查、reload 和运行时回读；未改变其他版本下载路由。

## 证据

- `artifacts/validation/r12-task007-android/`
- `tests/android/visual-baselines/R12/`
- `artifacts/validation/r12-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R12/APK_MANIFEST.yaml`
- `artifacts/reports/R12/R12-version-test-guide.md`
