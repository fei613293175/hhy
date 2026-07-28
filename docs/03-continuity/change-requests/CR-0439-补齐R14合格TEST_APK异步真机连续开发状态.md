---
cr_id: CR-0439
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-async-continuation-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T09:02:31Z
updated_at: 2026-07-28T09:04:52Z
---
# CR-0439 — 补齐R14合格TEST_APK异步真机连续开发状态

## 用户需求摘要

项目所有者已要求真机测试异步反馈且AI持续推进，不因未反馈或外部门禁停止

## 原规则

R14 Manifest已记录machine_delivery=PASS、owner_physical_test=PENDING和ON_DEMAND_NON_BLOCKING_SPECIALTY，但遗漏android_delivery.next_release_development、android_automation.policy_id与android_automation.next_release_development；既有continuity规则只有完整TEST_APK证据链且这些字段精确匹配时才允许挂起外部门禁继续独立Release。

## 新规则

不新增第二套策略，仅把全局HHY-ANDROID-AUTOMATION-V1既有异步真机规则精确投影到R14：android_delivery.next_release_development=ALLOWED；android_automation.policy_id=HHY-ANDROID-AUTOMATION-V1且next_release_development=ALLOWED。owner_physical_test继续PENDING，自动专项继续NOT_RUN_NOT_REQUIRED_FOR_TEST_APK，公网WebSocket与举报目录继续BLOCKED，R14不得标DONE或生产PASS。continuity必须在既有APK Manifest、固定工具链构建证据、稳定签名、正式API、四方SHA和测试说明全部一致时才允许切换到依赖已全绿的独立Release。

## 修改原因

R14 TEST_APK构建、单测、Lint、稳定签名、身份、四方SHA、桌面和HTTPS交付均已PASS，但RELEASE_MANIFEST遗漏既有HHY-ANDROID-AUTOMATION-V1的policy_id及next_release_development=ALLOWED投影，continuity因此不能识别合格TEST_APK的异步真机连续开发状态并产生确定性假阻断

## 影响摘要

补齐R14 Manifest对既有异步真机连续开发策略的遗漏投影，使已验证TEST_APK可被continuity识别；不修改APK、代码、接口、数据、UI、自动测试结果、生产状态或外部门禁。

## 影响文件

- `releases/R14/RELEASE_MANIFEST.yaml`

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

- `check_release_artifacts.py --release R14必须PASS；release_has_continuable_test_apk(R14)必须为true；continuity close只能转入依赖R02/R06已全绿的R16首任务，R15因依赖R14不得旁路；R14状态必须BLOCKED_EXTERNAL_GATE且owner/public_websocket/report_catalog原状态不变`

## 版本

- `R14`

## 迁移与兼容策略

纯治理元数据增量，无运行时、数据库、API或客户端迁移；旧APK及证据字节不变。若任一APK身份、签名、SHA、正式API或证据路径不一致，continuity仍必须拒绝跨Release。

## 用户确认

项目所有者已明确要求真机反馈异步且持续推进所有可开发版本，不因未反馈或外部门禁停止

## 审批

- 审批人：`codex-r14-async-continuation-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T09:04:52Z`
- 说明：独立复核确认仅补入既有策略的三个Manifest字段；完整TEST_APK证据链内存投影验证为true，R14/owner/自动专项/公网WS/举报目录状态不变；R16只依赖已绿R02/R06，R15仍不可旁路。

## 状态记录 · 2026-07-28T09:04:57Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：独立审批通过，开始仅向R14 Manifest投影既有异步真机连续开发三个机器字段

## 状态记录 · 2026-07-28T09:06:10Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：R14 Manifest已仅补入既有策略三个机器字段；release artifacts门禁PASS且release_has_continuable_test_apk返回TRUE，owner、自动专项、公网WS、举报目录及R14完成状态均未改变
