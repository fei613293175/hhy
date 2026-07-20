# TASK-R05-007 Android 测试 APK 与产物追溯

## 当前结论

- R05 Android 测试 APK 已基于不可变源码提交完成单元测试、Lint、构建、固定签名、对齐、版本身份和真实 API 地址检查。
- 桌面、仓库追溯副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；公网完整下载 200、Range 206、MIME 检查通过。
- 机器交付状态为 `PASS`，项目所有者真机验收保持 `PENDING`；本报告不把桌面交付冒充为真机通过，也不把测试 APK 标记为生产商店正式发布包。

## APK 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `3454ff232a66c83c8fd7f6f00dc95d6d078f63ef` |
| APK 文件 | `hhy-r05-3454ff2-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10208` |
| APK 大小 | `12,038,568` bytes |
| APK SHA-256 | `9ab8c942ddc90de5c69898f610d4c96dc1aa798681ef32932542bca26af961ca` |
| 签名配置 | `hhy-staging-test-v1` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r05-3454ff2-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r05-artifacts/hhy-r05-3454ff2-debug.apk` |

## 构建、签名与联网门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 源码归档 SHA-256：`8b259fe5980b822933d8c749b479a9e96a58c3e07cf73db884b5ee5995297076`。
- Gradle 执行 `clean testDebugUnitTest lintDebug assembleDebug`，387 项任务全部成功，构建用时 2 分 46 秒。
- `apksigner`、`zipalign` 均通过；APK Signature Scheme v2、v3 为 `true`，签名证书与既有 Staging 测试签名一致。
- DEX 中真实 API `https://api.orbexa.cc` 精确出现 1 次，占位端点 `.invalid` 出现 0 次。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均通过；公网完整下载 200、Range 206、Android APK MIME 正确。
- 真机发现授权说明加载失败后确认公网仍运行R03后端；已在数据库可恢复备份、候选就绪和旧认证回归通过后滚动切换至R05。真实测试注册取得Bearer后，`GET /api/v1/identity/consent`返回HTTP 200、标题正确且正文长度239；证据见`artifacts/validation/r05-public-rollforward/evidence.json`。

## 本版 Android 可见闭环

- 登录成功后，从底部“我的”进入“账号与安全”，可打开“实名认证”。
- 实名首页展示认证准备事项并进入身份信息表单。
- 表单支持真实姓名、身份证号、动态加载的实名认证授权说明、同意确认和字段级错误提示。
- 提交后进入活体检测容器，按需申请相机权限，限制非 HTTPS 和异常回跳，并每 3 秒轮询结果。
- 结果页覆盖核验中、人工审核、成功、拒绝、失败、过期和未知状态；失败状态可确认后重新认证。
- 用户可见页面不展示请求编号、TraceId、接口名称、内部状态码或调试入口。

## 当前外部边界

R05 的代码、隔离 Staging、数据库、告警和 APK 机器门禁已经通过。根据 `CR-0089` 和 R05 入口门禁，R02 真机验收事实未在仓库中关闭前，不执行 R05 生产激活；真实活体供应商也只允许在受控 SecretRef 与正式测试配置齐备后启用。因此项目所有者本轮先验证安装、登录后入口、页面布局、表单交互和可达到的测试环境响应，不能把未获得真实供应商结果视为实名认证成功。

## 证据

- 构建与签名：`artifacts/validation/r05-task007-android/`
- APK 交付：`artifacts/validation/r05-apk-delivery/delivery-evidence.json`
- 产物 Manifest：`artifacts/apk/R05/APK_MANIFEST.yaml`
- 真机测试说明：`artifacts/reports/R05/R05-version-test-guide.md`

## 验收状态

机器构建、签名、下载和追溯门禁为 `PASS`；项目所有者真机验收为 `PENDING`。收到明确真机通过反馈后，使用交付工具记录验收，再关闭 TASK-R05-007 并进入 TASK-R05-008。
