---
cr_id: CR-0233
status: APPROVED
requester_actor_id: codex-root-r08-008
approver_actor_id: codex-reviewer-user-self-r08
task_id: TASK-R08-008
session_id: SES-20260722T093645Z-C7DB8EF0
created_at: 2026-07-22T09:42:16Z
updated_at: 2026-07-22T09:42:49Z
---
# CR-0233 — 补齐R08关闭前历史Android页面统一模拟器视觉复核

## 用户需求摘要

既有未达效果图级标准页面与后续版本必须按新肉眼标准开发，截图由AI独立判断且持续推进。

## 原规则

复用CR-0213强化后的唯一效果图级视觉规则与既有Android最终候选门禁；历史页面在R08关闭前必须具有真实模拟器或安全语义证据，禁止另建平行规则。

## 新规则

不改变规则；修复R08最终候选遗漏的历史Android覆盖：R02、R04、R05使用仅debug/androidTest可达的生产组件视觉夹具，R06、R07和R04媒体面板使用真实已认证旅程；单次受控模拟器批次采集后仍由AI逐图判定。

## 修改原因

R08三页候选已PASS，但完整视觉关闭门禁发现R02、R04至R07共23个Android历史页面仍为IN_REVIEW；最终候选遗漏跨版本统一复核，必须补充同一受控模拟器批次而不得伪造PASS。

## 影响摘要

增加历史视觉审计Instrumentation与debug专用组合入口，把22个可截图页面和1个FLAG_SECURE联系方式面板纳入R08视觉清单；R08隔离夹具补齐R07真实搜索事实，不进入生产构建与业务接口。

## 影响文件

- `apps/android/feature/auth/src/debug/java/cc/orbexa/hhy/auth/AuthVisualAuditScreen.kt`
- `apps/android/feature/identity/src/main/java/cc/orbexa/hhy/identity/IdentityFlowScreen.kt`
- `apps/android/feature/identity/src/debug/java/cc/orbexa/hhy/identity/IdentityVisualAuditScreen.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/HistoricalVisualAuditTest.kt`
- `apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt`
- `scripts/prepare_r08_ci_fixture.sh`
- `tests/android/visual-manifests/R08.yaml`
- `tests/test_android_ci_gate.py`
- `tests/test_r08_ci_fixture.py`
- `config/android-candidate-request.yaml`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-APP-001`
- `SCR-APP-002`
- `SCR-APP-003`
- `SCR-AUTH-001`
- `SCR-AUTH-002`
- `SCR-AUTH-003`
- `SCR-AUTH-004`
- `SCR-AUTH-005`
- `SCR-AUTH-006`
- `SCR-AUTH-007`
- `SCR-AUTH-008`
- `SHEET-MEDIA-001`
- `SCR-ID-001`
- `SCR-ID-002`
- `SCR-ID-003`
- `SCR-ID-004`
- `SCR-HOME-001`
- `SCR-ABOUT-001`
- `SCR-SEARCH-001`
- `SCR-SEARCH-002`
- `SCR-PUBLISHER-001`
- `SHEET-CONTACT-001`
- `DIALOG-SEARCH-001`

## API

- `无协议变化`

## 数据库与迁移

- `无结构变化；仅R08隔离Staging候选夹具`

## 配置

- `现有Android候选视觉清单与一次独立历史补审请求`

## 资金/账本与历史数据

- `无`

## 测试

- `HistoricalVisualAuditTest；ReleaseCandidateSmokeTest；test_android_ci_gate；test_r08_ci_fixture；受影响Android MODULE；一次GitHub模拟器历史补审`

## 版本

- `R08`

## 迁移与兼容策略

仅debug/androidTest及隔离Staging夹具变化；Release构建不含视觉夹具，生产API、数据库结构、业务数据、固定签名APK和已批准R08三页基线不变。

## 用户确认

用户明确要求历史未达页面按新肉眼标准补齐、AI独立判断且不中断开发。

## 审批

- 审批人：`codex-reviewer-user-self-r08`
- 决定：`APPROVED`
- 时间：`2026-07-22T09:42:49Z`
- 说明：已确认复用唯一视觉与候选规则；只补齐遗漏的历史页面覆盖，debug夹具不进入Release，真实旅程继续绑定隔离Staging。

## 状态记录 · 2026-07-22T09:42:55Z

- Actor：`codex-root-r08-008`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- Note：开始补齐历史Android统一模拟器视觉审计和R07隔离搜索夹具。
