# TASK-R10-007 Android 测试 APK 与产物追溯

## 结论

- 产品候选 Commit `1e35a97fb0541644d85a264a84229435ead378bf` 的 GitHub Run `30013677033` 完成构建、Lint、单测、模拟器安装、OIDC认证、首页与群聊列表/详情/编辑四页真实旅程、日志和截图。
- 四张截图由AI逐图审核并批准；轻量晋升Run `30015737539`总耗时27秒，验证作业16秒，只复用源APK、报告和截图，没有Gradle或模拟器。
- 固定签名APK通过zipalign、v2/v3、单一签名者、正式API、包名和版本身份校验，并完成仓库、桌面、服务器和HTTPS四方一致交付。
- 机器交付 `PASS`；项目所有者真机反馈异步 `PENDING`，不伪造正式验收或生产激活，也不阻断后续开发。

## APK 身份

| 项目 | 值 |
| --- | --- |
| APK | `hhy-r10-1e35a97-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10219` |
| 包名 | `cc.orbexa.hhy.debug` |
| 正式API | `https://api.orbexa.cc` |
| 大小 | `20,916,072` bytes |
| SHA-256 | `f2f0888917e5476b99e2ca63221dd66bae7a68a39e21c7020bb2d226d1fd66ec` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r10-1e35a97-debug.apk` |
| 下载 | `https://download.orbexa.cc/r10-artifacts/hhy-r10-1e35a97-debug.apk` |

## 自动化和视觉

- 源Run：`30013677033`；源Artifact：`8566390406`；源APK SHA：`30e1a54322c73a056751fb723eaa127aaf12bbffa827b9fc1ce52430bfc27e9a`。
- 晋升Run：`30015737539`；晋升Artifact：`8567031882`；Artifact digest：`6919c6ebd6e508db7ff84762af8cfcaaebe61ff4c220629397471440bbb5382e`。
- 截图：`01-home.png`、`02-group-list.png`、`03-group-detail.png`、`04-group-editor.png`；页面身份、信息层级、真实数据、敏感信息掩码和禁现技术字段全部合格。
- 首页显示搜索、平台精选、四大分类和最新发布；群聊闭环覆盖真实列表、详情、联系方式/口令掩码和所有者编辑表单。

## 四方交付与服务器修复

- 仓库、桌面、服务器 `/www/wwwroot/download.orbexa.cc/r10-artifacts/` 和HTTPS完整下载SHA完全一致；HTTPS `200`、Range `206`、APK MIME和Content-Disposition通过。
- 首次传输发现公网preauth扫描占用OpenSSH默认连接槽；`PROB-0103`已通过 `LoginGraceTime 30`、`MaxStartups 30:50:100` 修复，`sshd -t`、reload、新连接回读和12次连续连接通过。
- SSH修复没有改变端口、密钥、密码/root登录策略；服务器备份为 `/etc/ssh/sshd_config.bak-hhy-preauth-20260723`。

## 证据

- `artifacts/validation/r10-task007-android/`
- `tests/android/visual-baselines/R10/`
- `artifacts/validation/r10-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R10/APK_MANIFEST.yaml`
- `artifacts/reports/R10/R10-version-test-guide.md`
