---
cr_id: CR-0437
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-version-identity-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T08:11:38Z
updated_at: 2026-07-28T08:14:02Z
---
# CR-0437 — 补齐R14 TEST_APK版本身份双事实源

## 用户需求摘要

持续开发R14并交付桌面TEST_APK，不因可修复确定性门禁停止

## 原规则

CR-0434已批准R14 TEST_APK versionCode从10222递增为10223，但实施范围只更新apps/android/app/build.gradle.kts；ReleasePolicy.VERSION_CODE和VersionMetadataTest仍冻结R13的10222及R13说明。

## 新规则

不新增版本策略，只把CR-0434已批准的R14 versionCode 10223精确投影到ReleasePolicy.VERSION_CODE和VersionMetadataTest；测试提示同步改为R14，BuildConfig、ReleasePolicy和单测三方必须相等。versionName、合同版本、渠道、环境、API、WSS、签名和所有业务行为保持不变。

## 修改原因

CR-0434已把Android versionCode冻结为10223并更新build.gradle，但遗漏ReleasePolicy及VersionMetadataTest两处R13旧值，固定工具链全量单测因此正确失败；需仅投影同一已批准版本身份，不建立第二套规则或扩大业务范围。

## 影响摘要

修复固定工具链发现的确定性版本身份不一致，仅改两处既有版本事实消费者，使10223单调升级门禁真实生效。

## 影响文件

- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`

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

- `VersionMetadataTest锁定R14 versionCode=10223且BuildConfig.VERSION_CODE=ReleasePolicy.VERSION_CODE；python scripts/check_android_ui_foundation.py与固定Android工具链verifyApiBaseUrl、testDebugUnitTest、lintDebug、assembleDebug必须全部PASS`

## 版本

- `R14`

## 迁移与兼容策略

无数据库、API或业务迁移；R13及更早APK不改写。R14继续复用hhy-staging-test-v2并以10223覆盖10222；改动后必须先通过python scripts/check_android_ui_foundation.py，再在固定工具链重跑verifyApiBaseUrl、testDebugUnitTest、lintDebug和assembleDebug。

## 用户确认

项目所有者已明确要求持续开发R14、自动解决确定性门禁并交付桌面TEST_APK，不因可修复问题停止。

## 审批

- 审批人：`codex-r14-version-identity-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T08:14:02Z`
- 说明：独立复审确认仅同步CR-0434已批准的10223到两个既有消费者；Android UI基础门禁与固定工具链四项门禁齐全，未改变版本名、合同、渠道、环境、API、WSS、签名或业务。

## 状态记录 · 2026-07-28T08:14:26Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：独立审批通过，开始同步R14 versionCode双事实源并重跑Android UI基础与固定工具链完整门禁。

## 状态记录 · 2026-07-28T08:45:01Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：10223已同步到ReleasePolicy与VersionMetadataTest，Android UI基础门禁和固定工具链完整构建于同一Commit PASS。
