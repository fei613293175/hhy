# TASK-R02-007 Android 测试 APK 构建与追溯

## 当前结论

- 固定 Android 工具链中的单元测试、Lint 与 debug APK 打包全部通过。
- APK 使用固定测试签名 Profile `hhy-staging-test-v1` 重签，v2/v3 签名、单一签名者和证书指纹校验通过。
- APK 内嵌真实 API `https://api.orbexa.cc`，不包含 `https://api.example.invalid` 占位端点；公开平台状态、注册配置和安全验证匿名接口全部返回成功。
- 桌面、仓库忽略副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；Range 206、MIME 和下载文件名检查通过。
- 项目所有者第二轮真机已确认安装、启动及新版页面布局正常，但图形验证码没有显示且交互暴露技术按钮；`2bbfa9b` APK 判定为 `FAILED`，不得执行 `accept`。

## 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `7b425c4e4ca7aa53eef142fd00172bbdc761afd5` |
| APK 文件 | `hhy-r02-7b425c4-debug.apk` |
| 包名 | `cc.orbexa.hhy.debug` |
| 应用名称 | `合伙云 Pro 测试` |
| versionName / versionCode | `1.2.2-debug` / `10202` |
| minSdk / targetSdk / compileSdk | `26` / `36` / `37` |
| APK 大小 | `11,923,821` bytes |
| APK SHA-256 | `3104c3de17efc175512a7e4466844450362285ad79b03d7c3e845e72dcbb9768` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r02-7b425c4-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r02-artifacts/hhy-r02-7b425c4-debug.apk` |

## 工具链与构建

固定镜像为 `hhy-android-toolchain:r01-46fb273`，镜像摘要为 `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。源码归档 SHA-256 为 `6cba3726ee0da3900cdf7cdab2c4aa3a9dd393226897b1a513056dbeaee153ec`。

从精确 Commit 执行 `testDebugUnitTest lintDebug assembleDebug`，结果为 `BUILD SUCCESSFUL in 39s`，共 `199 actionable tasks`。完整日志见 `artifacts/validation/r02-task007-android/gradle-build.log`。

首次正式构建由 `VersionMetadataTest` 阻止了 Gradle `10202` 与应用内发布策略 `10201` 的漂移；修正、登记问题并加入显式回归断言后才重新构建。失败 APK 未交付。

## 签名、联网与下载复核

- `apksigner verify --verbose --print-certs`：v2/v3 PASS，单一 3072 位 RSA 测试签名者。
- `aapt dump badging`：包名、`versionCode 10202`、`versionName 1.2.2-debug` 与 SDK 范围正确。
- `zipalign -c -v 4`：PASS。
- DEX 扫描：真实 API 精确出现 1 次，占位端点出现 0 次；`.invalid` 后缀字面量仅用于发布地址拒绝策略。
- `https://api.orbexa.cc/public-api/v1/platform/status`、`registration-config` 与 `security-challenges`：全部返回 HTTP 200；后端已从旧P00旁路升级到R02并完成Flyway V018。
- 交付脚本首次公网校验发现公开目录错误继承 `umask 077` 为 700，已修复为公开目录 755、私有上传暂存目录 700并增加回归测试；失败上传被自动回撤。
- 修复后 `prepare` 与独立 `verify` 均 PASS，HTTPS 完整下载为 200，Range 验证为 206。
- 新后端镜像 `hhy-backend-r02:7b425c4` readiness 为 `UP`；公开安全挑战门禁已解码响应并确认 `PNG 160x56`，不再只判断 Base64 字段非空。

## 真机首轮反馈与修复

项目所有者在 2026-07-18 19:35 上传的首轮真机截图判定旧 `bbad603` APK 为 `FAILED`，该结果没有被记录为验收通过。截图暴露两项真实问题：认证页没有按 B01 参考图与冻结字号/间距/组件尺寸施工，四个路由被压入同一行导致“注册账号”纵向折行；同时公网仍运行旧 P00 后端，使无需登录的安全验证和注册配置接口返回 401，并被客户端显示为“认证已失效”。

修复包 `2bbfa9b` 已按 B01 重构认证页、接入设计 Typography/Shapes/尺寸令牌，并将注册与忘记密码恢复为辅助入口；公网 R02 候选服务在数据库备份后完成 V012—V018 迁移，三个公网门禁通过后切换。首次下载验证还发现 Cloudflare 负缓存旧 404，现已增加强制回源验证并完成四方哈希交付。

项目所有者在 2026-07-18 20:35—20:37 上传第二轮真机截图。页面视觉结构、四个认证路由以及公开接口访问已符合预期，但创建挑战后只出现提示和结果输入框，没有显示实际验证码；同时“创建安全验证”属于内部技术步骤，普通用户无法理解。排查确认后端把 SVG 文本放入 `imageBase64`，而 Android 使用 `BitmapFactory` 只解码栅格图片。当前修复将响应改为真实 160x56 PNG，并在所有认证及账号注销页面移除该技术按钮，改为点击登录或发送验证码时自动触发。该轮因此记录为 `FAILED`，旧 APK 不得继续验收。

## 待完成门禁

包含 PNG 与自动触发修复的 `7b425c4` 已重新构建、固定签名并完成桌面/仓库/服务器/HTTPS 四方哈希交付，机器状态为 `PASS`、真机状态为 `PENDING`。项目所有者看到真实四位验证码并完成登录/注册入口复测后，才执行 `accept`、关闭 TASK-R02-007 并进入 TASK-R02-008。
