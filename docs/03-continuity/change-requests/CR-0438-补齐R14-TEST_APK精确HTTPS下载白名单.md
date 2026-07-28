---
cr_id: CR-0438
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-apk-download-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T08:30:47Z
updated_at: 2026-07-28T08:32:20Z
---
# CR-0438 — 补齐R14 TEST_APK精确HTTPS下载白名单

## 用户需求摘要

持续开发并把可真机安装的R14测试APK交付到桌面，按项目既有四方SHA门禁完成机器交付

## 原规则

CR-0434要求R14 TEST_APK完成仓库忽略副本、项目所有者桌面、服务器文件和HTTPS完整下载四方SHA一致，同时禁止对公网后端、DNS、TLS、通用Nginx或WebSocket进行范围外激活。当前下载站默认拒绝全部未登记路径，单纯复制APK仍返回404。

## 新规则

不放宽下载站默认404，不建立目录通配或通用静态路由；只登记精确GET/Range路径/r14-artifacts/hhy-r14-ca57666-debug.apk，文件固定为SHA256 46f050310143d5968864c5aacd9ad38b1fb7290931bc113c76429ebc7ecf126d，MIME固定application/vnd.android.package-archive并设置nosniff与attachment。禁止修改API、WebSocket、DNS、TLS、其他版本路由或任何业务反代；nginx -t失败不得reload。

## 修改原因

R14 APK已在固定工具链完成构建签名并复制到下载站文件系统，但download.orbexa.cc默认location返回404且只允许逐版本精确白名单；CR-0434要求四方SHA同时禁止泛化公网激活，需仅为单个不可变APK增加精确下载location。

## 影响摘要

为已签名不可变R14 TEST_APK补齐既有四方SHA交付所需的最小精确下载白名单，默认拒绝策略和其他公网能力保持不变。

## 影响文件

- `infra/nginx/r14-apk-location.inc`
- `artifacts/validation/r14-apk-delivery/delivery-evidence.json`
- `artifacts/apk/R14/APK_MANIFEST.yaml`
- `artifacts/reports/R14/TASK-R14-007-android-apk.md`
- `artifacts/reports/R14/R14-version-test-guide.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `仓库include与服务器include SHA256一致；活动配置只新增该精确include；nginx -t与reload PASS；HTTPS完整请求200、Range请求206、MIME正确，服务器/HTTPS/仓库/桌面四方大小和SHA256均一致；任意其他r14路径继续404`

## 版本

- `R14`

## 迁移与兼容策略

部署前备份download.orbexa.cc活动配置，安装与仓库字节一致的r14精确location include并仅追加一个include；nginx -t通过后reload。失败或回退时恢复备份并移除仅CR-0438创建的include，再次nginx -t与reload；APK文件可保留但未授权路由继续404。

## 用户确认

项目所有者已明确要求持续开发、桌面交付测试APK并按全局交付门禁自主执行，不因可恢复交付步骤停止。

## 审批

- 审批人：`codex-r14-apk-download-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T08:32:20Z`
- 说明：独立复审确认这是CR-0434四方SHA所需的单文件精确白名单，不属于泛化公网激活；默认404、禁止通配与其他公网边界、备份/nginx-t/reload/回退均完整。

## 状态记录 · 2026-07-28T08:32:29Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：独立审批通过，开始生成仓库精确location、备份并最小部署下载白名单，随后验证四方SHA和默认404。

## 状态记录 · 2026-07-28T08:45:07Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：仓库精确include与服务器字节一致，活动配置有可恢复备份，nginx-t/reload、HTTPS200、Range206、MIME、其他路径404与四方SHA全部PASS。
