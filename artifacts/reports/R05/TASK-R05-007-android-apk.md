# TASK-R05-007 Android 测试 APK 与产物追溯

## 当前结论

- R05 Android 测试 APK 已基于不可变源码提交完成单元测试、Lint、构建、固定签名、对齐、版本身份和真实 API 地址检查。
- 桌面、仓库追溯副本、服务器文件和 HTTPS 完整下载四方 SHA-256 一致；公网完整下载 200、Range 206、MIME 检查通过。
- 机器交付状态为 `PASS`，项目所有者真机验收保持 `PENDING`；本报告不把桌面交付冒充为真机通过，也不把测试 APK 标记为生产商店正式发布包。

## APK 产物身份

| 项目 | 值 |
| --- | --- |
| 源码 Commit | `bc52fe68ae169a79586002af4f8b2af9b21690a6` |
| APK 文件 | `hhy-r05-bc52fe6-debug.apk` |
| versionName / versionCode | `1.2.2-debug` / `10211` |
| APK 大小 | `19,268,698` bytes |
| APK SHA-256 | `c02b20d8e2198dc2938d7be8245ffadcf4269cc6187ea8d79e3464bb631d84d4` |
| 签名配置 | `hhy-staging-test-v1` |
| 签名证书 SHA-256 | `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873` |
| API Base URL | `https://api.orbexa.cc` |
| 桌面副本 | `C:\Users\小白\Desktop\hhy-r05-bc52fe6-debug.apk` |
| 下载地址 | `https://download.orbexa.cc/r05-artifacts/hhy-r05-bc52fe6-debug.apk` |

## 构建、签名与联网门禁

- 固定工具链：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 源码归档 SHA-256：`828d43c4487f7a52e4bb22d27404c6188463a42aa7a2cbe0ade8c10085f262c3`。
- Gradle 执行 `clean testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug`，430 项任务全部成功，构建用时 5 分 2 秒。
- `apksigner`、`zipalign` 均通过；APK Signature Scheme v2、v3 为 `true`，签名证书与既有 Staging 测试签名一致。
- DEX 中真实 API `https://api.orbexa.cc` 精确出现 1 次，占位端点 `.invalid` 出现 0 次。
- `deliver_android_test_apk.py prepare` 与独立 `verify` 均通过；公网完整下载 200、Range 206、Android APK MIME 正确。
- 真机发现授权说明加载失败后确认公网仍运行R03后端；已在数据库可恢复备份、候选就绪和旧认证回归通过后滚动切换至R05。真实测试注册取得Bearer后，`GET /api/v1/identity/consent`返回HTTP 200、标题正确且正文长度239；证据见`artifacts/validation/r05-public-rollforward/evidence.json`。
- 公网Staging已滚动切换至生产硬隔离实名沙箱候选；注册、协议、创建会话、HTTPS活体页、服务端`VERIFIED`终态和一次性state重放422黑盒全部通过，旧容器与Nginx备份保留。

## 本版 Android 可见闭环

- 全部既有 Android 页面已迁移到稳定版 Navigation Compose 真实返回栈，不再用手写页面枚举模拟导航；包括登录、验证码登录、注册、忘记密码、账号安全、五个主导航页和实名认证完整流程。
- 页面左上角返回、Android 系统返回键和返回手势共用同一返回栈；从“我的”进入账号安全和实名认证后，返回会回到真实来源“我的”，不会错误跳首页或直接退出应用。
- 建立统一 `HhyIcons` 语义图标注册表并采用官方 Material 矢量图标，已清除相机、盾牌、底部导航等位置的汉字、Unicode 和文本占位图标。
- 建立统一 `HhyMotion` 页面动效规范：进入、退出及反向返回均使用集中管理的 120/200/300ms 动效参数，并尊重系统减少动态效果设置。
- 新增静态门禁和导航测试，禁止后续重新引入手写页面栈、文本假图标、页面私有动效参数和非稳定导航依赖。
- 全局规则已强制采用大型商业 App 验证过的官方稳定主流方案；禁止小众、预发布或自行构思的导航、返回栈、图标和动效基础设施，例外必须经 ADR 与变更审批。
- 四个实名认证页面已按 `design/R05-UI-FROZEN` 冻结逐状态效果图、字号、间距、圆角和组件尺寸重构；效果图中的虚拟业务内容未照搬。
- 实名首页使用标准居中顶栏、状态渐变卡和结构化准备卡；资料页、240dp活体容器及结果页均覆盖冻结状态布局。
- 登录成功后，从底部“我的”进入“账号与安全”，可打开“实名认证”。
- 实名首页展示认证准备事项并进入身份信息表单。
- 表单支持真实姓名、身份证号、动态加载的实名认证授权说明、同意确认和字段级错误提示。
- 提交后进入活体检测容器，按需申请相机权限，限制非 HTTPS 和异常回跳，并每 3 秒轮询结果。
- 结果页覆盖核验中、人工审核、成功、拒绝、失败、过期和未知状态；失败状态可确认后重新认证。
- 用户可见页面不展示请求编号、TraceId、接口名称、内部状态码或调试入口。
- 密码登录与验证码登录属于同级模式，切换仅使用无方向淡入淡出；从任一登录模式进入注册或忘记密码时使用前进转场，页内、系统键和手势返回使用反向转场并恢复真实来源登录模式。

## 当前外部边界

R05 的代码、隔离 Staging、数据库、告警和 APK 机器门禁已经通过。当前仅Staging显式启用生产硬隔离的实名沙箱，用于完整真机覆盖相机、活体容器、服务端状态和结果页；沙箱成功不代表第三方正式实名认证。正式生产仍必须在受控 SecretRef、真实供应商配置与审批齐备后启用，production误开沙箱会拒绝启动。

## 证据

- 构建与签名：`artifacts/validation/r05-task007-android/`
- APK 交付：`artifacts/validation/r05-apk-delivery/delivery-evidence.json`
- 产物 Manifest：`artifacts/apk/R05/APK_MANIFEST.yaml`
- 真机测试说明：`artifacts/reports/R05/R05-version-test-guide.md`

## 验收状态

机器构建、签名、下载和追溯门禁为 `PASS`；项目所有者真机验收为 `PENDING`。收到明确真机通过反馈后，使用交付工具记录验收，再关闭 TASK-R05-007 并进入 TASK-R05-008。
