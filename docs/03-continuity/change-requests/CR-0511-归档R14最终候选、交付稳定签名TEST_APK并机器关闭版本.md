---
cr_id: CR-0511
status: APPROVED
requester_actor_id: codex-r14-release-closer
approver_actor_id: codex-r14-release-authorizer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T03:51:23Z
updated_at: 2026-07-30T03:51:36Z
---
# CR-0511 — 归档R14最终候选、交付稳定签名TEST_APK并机器关闭版本

## 用户需求摘要

R14大版本完成后由自动化测试审核，交付桌面APK和测试文档；无需等待逐版本真机反馈，关闭后按相邻版本继续推进

## 原规则

R14当前交付仍绑定旧Commit 2eb8ac74、versionCode 10224和未覆盖最终消息体验修复的APK；候选PASS证据尚未归档到版本关闭事实源。

## 新规则

R14最终交付唯一绑定产品Commit 89ccaf45、versionCode 10225、Run 30510422149及晋升Run 30511539828；TEST_APK必须由obx-test固定工具链重建并复用hhy-staging-test-v2，四方SHA一致后才允许machine-close。

## 修改原因

Run 30510422149业务候选和Run 30511539828视觉晋升均PASS，需把同一89ccaf45产品Commit重建为固定v2测试签名、完成四方交付并机器关闭R14

## 影响摘要

仅更新R14最终候选、构建、交付、测试说明、问题闭环和版本关闭证据；不修改已通过候选的产品代码、API、数据库或视觉基线，不等待所有者真机反馈。

## 影响文件

- `artifacts/validation/r14-final-candidate/candidate-report.json`
- `artifacts/validation/r14-final-candidate/build-evidence.json`
- `artifacts/validation/r14-task007-android/build-evidence.json`
- `artifacts/apk/R14/APK_MANIFEST.yaml`
- `artifacts/reports/R14/R14-version-test-guide.md`
- `artifacts/validation/r14-apk-delivery/delivery-evidence.json`
- `releases/R14/RELEASE_MANIFEST.yaml`
- `releases/R14/ACCEPTANCE_MATRIX.csv`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-CHAT-001,SCR-CHAT-002,SHEET-CHAT-001,SHEET-CHAT-002`

## API

- `无运行时变更；复用已通过的R14候选API`

## 数据库与迁移

- `无迁移；复用已通过的R14候选数据库`

## 配置

- `R14交付清单与最终候选证据绑定`

## 资金/账本与历史数据

- `无资金或账本影响`

## 测试

- `Run 30510422149 candidate PASS; Run 30511539828 promotion PASS; fixed toolchain unit/lint/assemble; apksigner v2/v3; four-way delivery; machine-close`

## 版本

- `R14`

## 迁移与兼容策略

10225沿用固定v2签名，可覆盖10224；旧APK按交付脚本原子归档；owner_physical_test保持PENDING并异步反馈，不阻断R15。

## 用户确认

项目所有者已要求每个大版本由AI自动审核、桌面交付APK和测试文档，并长期授权无需逐版本等待真机反馈即可继续开发。

## 审批

- 审批人：`codex-r14-release-authorizer`
- 决定：`APPROVED`
- 时间：`2026-07-30T03:51:36Z`
- 说明：独立复核自动候选、视觉晋升、固定证书指纹和交付边界；批准只归档真实证据并重建同一产品Commit的稳定签名TEST_APK，不允许改动产品行为或伪造真机结论。

## 状态记录 · 2026-07-30T03:51:43Z

- Actor：`codex-r14-release-closer`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始归档最终候选PASS、生成稳定签名10225 APK、执行四方交付与R14机器关闭。
