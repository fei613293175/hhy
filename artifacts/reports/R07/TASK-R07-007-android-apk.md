# TASK-R07-007 Android 测试 APK 与产物追溯

## 当前结论

- R07 Android 候选以产品 Commit `69406d918b4c2aa6f1eea78baed48464e4f94277` 完成 GitHub 编译、Lint、单测、打包、模拟器安装、一次性认证、真实业务旅程、目标进程日志和安全门禁。
- 源运行 `29875839322` 一次采集四张目标页面截图；AI 审核合格后，轻量晋升运行 `29877272491` 在 23 秒内复用同一 APK、报告和截图生成 `PASS` 候选，没有重复 Gradle 编译或模拟器启动。
- 搜索落地页、搜索结果、发布者主页和清空历史确认框均为稳定且互不重复的真实业务页面；高敏联系方式面板使用 `FLAG_SECURE`，因此不截图，只验证标题、资源、操作按钮和安全窗口标记。
- 固定测试签名、`versionCode=10207`、`https://api.orbexa.cc`、APK 大小和 SHA-256 均通过。仓库副本、桌面副本、服务器文件和 HTTPS 完整下载四方一致。
- 机器交付为 `PASS`，项目所有者真机反馈保持异步 `PENDING`；不据此伪造正式发布或生产激活。

## APK 身份

| 项目 | 值 |
| --- | --- |
| 产品 Commit | `69406d918b4c2aa6f1eea78baed48464e4f94277` |
| 源 GitHub Run | `29875839322`，完整候选约 8 分 34 秒 |
| 轻量晋升 Run | `29877272491`，23 秒 |
| APK | `hhy-r07-69406d9-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10207` |
| 大小 | `19,427,925` bytes |
| SHA-256 | `48d1486878c562246759432bce2b4b9645df80c24a9ccc0017a20f3e7799813f` |
| 签名 | `hhy-staging-test-v1` / `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| 桌面 | `C:\Users\小白\Desktop\hhy-r07-69406d9-debug.apk` |
| 下载 | `https://download.orbexa.cc/r07-artifacts/hhy-r07-69406d9-debug.apk` |

## 自动化与视觉证据

- 完整构建、Lint、单测、APK 与模拟器真实登录旅程：`PASS`。
- 目标页面：`SCR-SEARCH-001`、`SCR-SEARCH-002`、`SCR-PUBLISHER-001`、`DIALOG-SEARCH-001`；`SHEET-CONTACT-001` 由安全语义门禁覆盖。
- 四张截图 SHA-256：
  - `01-search-landing.png`：`01bf67243c6744f681d3369f42056dc6fe1d17bef3f0418f23dd44d76d97f5d9`
  - `02-search-results.png`：`032c7872436602292028b69f37542dfef28aec8b0c456f6a0b91aae860109bf9`
  - `03-publisher.png`：`367d8082e86fb1588c4435a98915a1c44829af750657e15f2fd2f1727fc99cf4`
  - `04-clear-history-dialog.png`：`0db0736589c022fcf6eb0b637f8ffabfdb9432fca72a0f049d0ea98db389f825`
- 截图未出现登录/注册页重复、联系方式明文、内部错误码、请求编号或 TraceId；字号、间距、颜色、圆角与层级遵守冻结 Design Token。
- 视觉基线首次建立采用“单次权威采集 + AI 审核 + 同源轻量晋升”，避免大版本为同一基线重复完整运行。

## 交付门禁

- 下载站只增加 `/r07-artifacts/hhy-r07-69406d9-debug.apk` 精确路由；Nginx 配置测试和热加载通过，原配置保留精确备份。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均通过：HTTPS 200、Range 206、APK MIME、大小和四方 SHA-256 一致。
- 交付状态：`delivery_status=PASS`、`owner_physical_test=PENDING`、`formal_release_acceptance=PENDING_OWNER_PHYSICAL_TEST`、`production_activation=BLOCKED_OWNER_PHYSICAL_TEST`。

## 证据

- GitHub 候选、审批和截图：`artifacts/validation/r07-task007-android/`
- 四方交付：`artifacts/validation/r07-apk-delivery/delivery-evidence.json`
- Manifest：`artifacts/apk/R07/APK_MANIFEST.yaml`
- 真机说明：`artifacts/reports/R07/R07-version-test-guide.md`

`TASK-R07-007` 的机器候选、视觉、安全、产物追溯和桌面交付已完成。异步真机反馈不阻断 `TASK-R07-008` 机器交接和后续版本开发。
