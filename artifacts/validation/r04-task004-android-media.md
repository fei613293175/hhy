# TASK-R04-004 Android 媒体上传管理器验证

## 实现范围

- 新增 `:feature:media`，实现 `SHEET-MEDIA-001` 标准底部面板、文件选择、逐文件进度、取消、失败重试、完成回传与删除确认。
- `core:network` 新增冻结 R04 Media DTO 与 `ContractMediaApi`，仅调用三个登记 operationId 对应接口及服务端签发的 HTTPS 上传地址。
- 创建、完成、删除分别持有稳定幂等键；重试复用原键，删除只在用户明确操作后执行，不进入自动重试队列。
- 上传 URL、私有读取 URL、Token、请求编号、错误码和幂等键均不进入可见 UI，也没有日志输出。

## 状态和恢复

| 场景 | 行为 |
| --- | --- |
| 离线/连接失败 | 保留所选文件，显示“网络连接不可用”，允许手动重试 |
| 401 | 提示重新登录并触发认证恢复回调 |
| 403 | 权限不足；直传凭证 403 单独视为上传凭证失效 |
| 404 | 上传任务失效；删除目标不存在使用完成语义提示 |
| 409 | 提示文件状态已变化，禁止盲目覆盖 |
| 422 | 优先展示服务端逐字段业务原因，否则提示重新选择 |
| 429 | 按 `Retry-After` 显示等待秒数 |
| 取消 | 协程取消并进入可恢复 `CANCELLED` 状态 |
| 删除失败 | 不自动重试；用户主动重试时复用原删除幂等键 |

## 权威环境

- 云端预检：`scripts/verify_cloud_environment.py --check-android --json` → PASS。
- 主机：`obx-test` / `ebs-178264`。
- 固定镜像：`hhy-android-toolchain:r01-46fb273`，镜像 ID `sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607`。
- 固定缓存：`hhy-r01-android-gradle-cache`。
- 隔离目录：`/tmp/hhy-r04-android-04FDBE70-a2`。
- 本机 Android SDK 未安装且未重建，符合远端固定工具链硬规则。

## 测试证据

- Android MODULE：`./gradlew --no-daemon testDebugUnitTest lintDebug` → `BUILD SUCCESSFUL`，297 个 Gradle task；日志 `/tmp/hhy-r04-android-04FDBE70-a2/android-module.log`，SHA-256 `193307a2a13614528466e4d1e66e44b2f0913645debb6b9631a2f96077d4fb41`。
- Media 专项：`MediaUploadManagerTest` 6 tests，0 skipped / 0 failures / 0 errors；包含上传闭环、创建/完成/删除稳定幂等键、取消和全部冻结错误分支。
- Network 契约：`ApiModelsSerializationTest` 14 tests、`RequestIdPolicyTest` 2 tests、`StartupGateTest` 7 tests，全部通过。
- Android Lint：`core:network`、`feature:media` 及全 Android 工程 `lintDebug` 均通过。
- 静态商业 UI 门禁：`MediaUploadSheet.kt` 不含 `requestId`、`X-Idempotency`、`uploadUrl`、`readUrl`、`traceId`、`errorCode`、`API`；Media 源码不含 `Log.*`、`println`、`printStackTrace`。

## 后续汇合边界

该任务交付可复用 Feature 与网络实现，不在当前纵向分区修改主路由。`TASK-R04-005` 负责将其注册到受信任调用页面、执行三分区集成与故障注入。
