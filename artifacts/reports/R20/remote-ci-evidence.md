# R20 Remote CI Evidence

- 最终 GitHub Actions：[Run 30996447732](https://github.com/fei613293175/hhy/actions/runs/30996447732)
- 源码 Commit：`8c9c1da034242e0b27d14bd6b1964eb2249d55e0`。
- Web products：成功；Admin/H5 测试和生产构建通过。
- Backend：成功；PostgreSQL、Maven 测试、数据库迁移/不变量及 R19/R20 可观测性门禁通过。
- Android build and unit tests：成功；单元测试和 Debug APK 构建通过。
- R20 Android emulator interactions：成功；Pixel 7 / API 35 的活动列表、创建、预审核和报价交互与截图门禁通过。
- `obx-test`：固定镜像执行 `verifyApiBaseUrl testDebugUnitTest lintDebug assembleDebug`，复核输出为 `BUILD SUCCESSFUL`；3 组测试 XML 为 0 failures / 0 errors。
- 正式 API / WSS：APK 内嵌 `https://api.orbexa.cc` 与 `wss://ws.orbexa.cc`，均由构建门禁和产物字符串复核确认。

项目所有者真机上的账号资格、预审核业务数据和订单支付仍按 `hhy-r20-8c9c1da-test-guide.md` 验收。
