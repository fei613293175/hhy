# TASK-R11-007 Android 测试 APK 与产物追溯

## 结论

- 产品候选 Commit `a3c32668ae1d6502d859efd3e8c18947f650e150` 的 GitHub Run `30069588243` 完成构建、Lint、单测、模拟器安装、OIDC认证、首页与团队长列表/详情/编辑四页真实旅程、日志和截图。
- 四张截图由AI逐图审核并批准；轻量晋升Run `30074128265`只复用源APK、报告和截图，没有重复运行Gradle或模拟器。
- 固定签名APK通过zipalign、v2/v3、单一签名者、正式API、包名和版本身份校验，并完成仓库、桌面、服务器和HTTPS四方一致交付。
- 机器交付 `PASS`；项目所有者真机反馈异步 `PENDING`，不伪造正式验收或生产激活，也不阻断后续开发。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r11-a3c3266-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10220` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式API | `https://api.orbexa.cc` |
| 大小 | `21,079,912` bytes |
| SHA-256 | `01e2c435752fcf969c641057a11855a12dd1991dda6f1af8ba2d982d4b07185a` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r11-a3c3266-debug.apk` |
| 下载 | `https://download.orbexa.cc/r11-artifacts/hhy-r11-a3c3266-debug.apk` |

## 自动化和视觉

- 源Run：`30069588243`；源Artifact：`8587614599`；源APK SHA：`0ad93ced14d5a47c1300c0dc340fde836613ec19243b6eedec2f4c42cf94bd5f`。
- 晋升Run：`30074128265`；晋升Artifact：`8589169043`；Artifact digest：`28f0bb663ff0fec6fcecd1bee00d2e0ca642253c9054fec59253a46c87aeb3ca`。
- 截图：`01-home.png`、`02-team-leader-list.png`、`03-team-leader-detail.png`、`04-team-leader-editor.png`；页面身份、信息层级、真实数据、团队专用媒体、敏感信息掩码和禁现技术字段全部合格。
- 首轮错误复用App截图的问题已由专用团队Logo修复，`PROB-0105`关闭；不合格截图没有被晋升或交付。

## 四方交付

- 仓库、桌面、服务器 `/www/wwwroot/download.orbexa.cc/r11-artifacts/` 和HTTPS完整下载SHA完全一致；HTTPS `200`、Range `206`、APK MIME和Content-Disposition通过。
- R11精确Nginx下载路由已通过配置语法检查、reload和运行时回读；未改变其他版本路由。

## 证据

- `artifacts/validation/r11-task007-android/`
- `tests/android/visual-baselines/R11/`
- `artifacts/validation/r11-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R11/APK_MANIFEST.yaml`
- `artifacts/reports/R11/R11-version-test-guide.md`
