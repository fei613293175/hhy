# R19 Remote CI Evidence

- 最终 GitHub Actions：[Run 30946843363](https://github.com/fei613293175/hhy/actions/runs/30946843363)
- 源码 Commit：`706a6f29d2469ad601bfe207348764315d713271`。
- Web products：成功；Admin/H5 测试和生产构建通过。
- Backend：成功；PostgreSQL、Maven 测试和 R19 可观测性门禁通过。
- Android build and unit tests：成功；单元测试和 Debug APK 构建通过。
- R19 Android emulator interactions：成功；Pixel 7 / API 35 的商城、库存、使用、入口、截图、崩溃和 ANR 门禁通过。
- 管理端补充验证：32 个测试文件、139 项测试通过；`vue-tsc --noEmit` 和 Vite production build 通过。
- 管理端视觉：`ADM-PROP-001` 已在 1440x900 真实运行视口复验，无页面横向溢出，创建/修改弹窗无重叠。
- `obx-test`：固定镜像执行 `verifyApiBaseUrl testDebugUnitTest lintDebug assembleDebug` 成功，并完成稳定签名和 APK 身份核验。

设备安装后的真实账号、支付渠道和运营商品数据仍按 `R19-DEVICE-TEST-GUIDE.md` 人工验收。
