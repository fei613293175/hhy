# 合伙云 Pro R16 测试清单

## 已完成的自动验证

| 范围 | 结果 | 证据 |
|---|---|---|
| R16 文档、页面、字段、状态、动作和 10 个接口 | PASS | `check_v122_documentation.py --release R16`，0 错误/0 警告 |
| V045/V046/V047 迁移、脏数据拒绝、回滚重放和 8 路并发幂等 | PASS | `TASK-R16-002-data-invariants.md` |
| 后端合同、权限、幂等、审计、Outbox、owner 隔离 | PASS | `TASK-R16-003-backend.md` |
| 后台 Vue 类型检查和测试 | PASS | 27 个测试文件、121 项测试 |
| Android 单测、Lint、编译 | PASS | obx-test 固定工具链，862 个 Gradle 任务 |
| Android 订单交互与视觉截图 | PASS | GitHub Actions `30829520789` |
| Web、后端、Android 全量远程回归 | PASS | GitHub Actions `30831992663` |
| 公网真实登录态订单列表 | PASS | HTTP 200，请求号 `e7caa5f6-5998-41df-8d41-2b651d17569b` |
| 公网跨用户/不存在订单隐藏 | PASS | HTTP 404 `COMMON-404-NOT_FOUND` |
| 健康、Prometheus、结构化日志和 TraceId | PASS | `TASK-R16-006-runtime-acceptance.md` |
| 固定签名与四方 APK SHA | PASS | `artifacts/validation/r16-apk-delivery/delivery-evidence.json` |

## 真机需要检查

- 覆盖安装后原账号和资料仍存在。
- “我的订单”入口、列表、状态筛选、刷新、分页和空状态正常。
- 订单详情金额、不退款凭证和时间字段显示正确。
- 断网、恢复、重试、快速切换和返回不会崩溃或卡死。
- 不出现支付、退款、物流、发票或再次购买等 R16 未登记按钮。

项目所有者真机测试当前为 `PENDING`，详细步骤见 `hhy-r16-4debca1-test-guide.md`。
