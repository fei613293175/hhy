---
cr_id: CR-0183
status: APPROVED
requester_actor_id: codex-root-r07-004
approver_actor_id: codex-reviewer-r07
task_id: TASK-R07-004
session_id: SES-20260721T190306Z-32CB66BF
created_at: 2026-07-21T19:05:20Z
updated_at: 2026-07-21T19:07:11Z
---
# CR-0183 — 实现R07 Android搜索发布者与联系方式闭环

## 用户需求摘要

项目所有者要求持续完成R07至R32，由AI判断截图与门禁结果，真机反馈异步且不得阻断开发。

## 原规则

R07五个Android页面或弹层已冻结字段状态与动作，七项服务端operationId已完成，但当前Android仅有R06首页壳层，没有R07合同模型、传输绑定、搜索入口、发布者主页、联系方式面板或清空历史确认，用户无法使用已落地后端能力。

## 新规则

Android以core/network唯一合同模型和ContractR07Api绑定七项operationId；新增feature/discovery实现五个冻结UI的加载内容空错误离线权限恢复与幂等交互；搜索结果进入发布者主页，公开内容打开联系方式面板；联系方式明文仅存在当前Composable内存，显式复制，关闭立即清除且不进入日志埋点持久化或保存状态。

## 修改原因

R07后端七项冻结接口已经完成，但Android尚无对应网络绑定、页面状态、导航与敏感联系方式内存展示，TASK-R07-004必须补齐五个冻结页面/弹层并接入现有已认证壳层。

## 影响摘要

不改变冻结OpenAPI数据库和服务端行为，补齐R07 Android端到端闭环、已认证导航、稳定幂等键、最小披露及自动化测试；H5和后台没有R07登记页面，明确N/A且不发明界面。

## 影响文件

- `apps/android/settings.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractR07Api.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/R07ApiModelsSerializationTest.kt`
- `apps/android/feature/discovery/build.gradle.kts`
- `apps/android/feature/discovery/src/main/java/cc/orbexa/hhy/discovery/R07DiscoveryScreens.kt`
- `apps/android/feature/discovery/src/main/java/cc/orbexa/hhy/discovery/R07DiscoveryState.kt`
- `apps/android/feature/discovery/src/test/java/cc/orbexa/hhy/discovery/R07DiscoveryStateTest.kt`
- `CHANGELOG.md`

## 页面

- `SCR-SEARCH-001`
- `SCR-SEARCH-002`
- `SCR-PUBLISHER-001`
- `SHEET-CONTACT-001`
- `DIALOG-SEARCH-001`

## API

- `GET /api/v1/search`
- `GET /api/v1/search/hot`
- `GET /api/v1/search/history`
- `DELETE /api/v1/search/history`
- `GET /api/v1/publishers/{id}`
- `GET /api/v1/contents`
- `POST /api/v1/contents/{id}/contacts/{channel}/access`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `HHY_API_BASE_URL`

## 资金/账本与历史数据

- `R07 Android页面与冻结operationId一对一绑定`

## 测试

- `core/network R07合同序列化与高敏字段边界`
- `feature/discovery状态幂等键复用与敏感值清除测试`
- `Android受影响MODULE单测lint与编译`

## 版本

- `R07`

## 迁移与兼容策略

仅新增Android模块和路由，复用API_BASE_URL与Bearer会话；不迁移本地数据、不持久化联系方式明文、不改变R02至R06路由；H5和后台因无R07登记页面明确N/A。

## 用户确认

项目所有者已明确授权持续推进R07至R32并由AI自行判断门禁结果，真机反馈异步且不得阻断。

## 审批

- 审批人：`codex-reviewer-r07`
- 决定：`APPROVED`
- 时间：`2026-07-21T19:07:11Z`
- 说明：范围严格对应五个冻结Android页面和七项已实现接口；H5及后台无登记页面明确N/A；敏感联系方式仅内存展示并关闭清除，幂等写保持同意图稳定键，未扩大合同。

## 状态记录 · 2026-07-21T19:25:31Z

- Actor：`codex-root-r07-004`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T190306Z-32CB66BF`
- Note：五个R07 Android交互面、七项合同绑定、导航、稳定幂等键和高敏明文生命周期已完成并通过MODULE门禁，进入提交绑定。

## 状态记录 · 2026-07-21T19:25:34Z

- Actor：`codex-root-r07-004`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T190306Z-32CB66BF`
- Note：实现提交fea04949完成R07 Android搜索到联系方式闭环；合同、全部Android单测和lint通过。

## 状态记录 · 2026-07-21T19:25:53Z

- Actor：`codex-root-r07-004`
- Status：`CLOSED`
- Session：`SES-20260721T190306Z-32CB66BF`
- Note：实现提交fea04949、R07合同检查、全部Android单测与lint、严格连续性门禁均通过，CR生命周期关闭。
