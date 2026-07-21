---
cr_id: CR-0197
status: APPROVED
requester_actor_id: codex-r07-candidate
approver_actor_id: codex-release-audit
task_id: TASK-R07-007
session_id: SES-20260721T210252Z-D631F6E4
created_at: 2026-07-21T22:51:33Z
updated_at: 2026-07-21T22:51:39Z
---
# CR-0197 — 建立Android首版视觉基线单次模拟器采集与轻量晋升门禁

## 用户需求摘要

大版本完成才执行一次最终模拟器测试，避免重复GitHub编译和模拟器浪费时间；截图由AI自主判断并持续推进

## 原规则

强制版本首次候选也必须先存在已批准基线；没有基线时完整编译和模拟器必然失败，批准截图后必须再次完整重跑

## 新规则

首版无基线且仪器、JUnit、日志、截图身份、稳定性和跨页差异全部通过时，门禁只产生BASELINE_REVIEW_REQUIRED采集证据；AI审核并提交原图与哈希后，由轻量GitHub晋升门禁下载原始运行工件、核对来源/哈希/唯一允许缺口并生成PASS候选，禁止再次启动模拟器或重新编译

## 修改原因

R07前两轮功能修复后尚无视觉基线，现有门禁要求同一模拟器旅程先与不存在的基线比较，必然额外失败和重跑；本机未启用虚拟化驱动，不能以不可靠本机AVD替代权威GitHub截图

## 影响摘要

每个大版本仍只有一次权威模拟器旅程；首版视觉基线通过可追溯AI审批与轻量Actions晋升闭环，不降低功能、日志、视觉、所有者测试或生产激活约束

## 影响文件

- `scripts/android_ci_gate.py`
- `scripts/run_android_emulator_gate.sh`
- `.github/workflows/android-quality-gate.yml`
- `.github/workflows/android-baseline-promotion.yml`
- `config/android-automation.yaml`
- `tests/test_android_ci_gate.py`
- `tests/android/visual-baselines/R07/APPROVAL.yaml`
- `tests/android/visual-baselines/R07/01-search-landing.png`
- `tests/android/visual-baselines/R07/02-search-results.png`
- `tests/android/visual-baselines/R07/03-publisher.png`
- `tests/android/visual-baselines/R07/04-clear-history-dialog.png`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `PITFALLS.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Android baseline bootstrap and promotion lifecycle`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `Python Android CI gate regression; YAML workflow parse; third R07 GitHub emulator capture; AI four-image review; lightweight baseline promotion Actions PASS`

## 版本

- `R07`

## 迁移与兼容策略

仅扩展Android候选治理与证据状态机；已有APPROVED基线继续原路径比较，已有R06流程兼容；首版晋升严格绑定同仓库run、release、commit、截图SHA和APK SHA，不允许产品代码在采集与晋升之间变化

## 用户确认

项目所有者已明确要求大版本完成才做一次最终模拟器测试并授权AI自主审核截图、持续推进

## 审批

- 审批人：`codex-release-audit`
- 决定：`APPROVED`
- 时间：`2026-07-21T22:51:39Z`
- 说明：方案将首版基线的必然双跑改为一次真实采集加一次无模拟器的证据晋升，仍保持来源、哈希、功能和视觉硬门禁，符合所有者效率要求

## 状态记录 · 2026-07-21T22:59:11Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：单次模拟器首版基线采集、BASELINE_REVIEW_REQUIRED状态、原工件轻量晋升工作流及回归已实现，21项Android CI Python测试通过

## 状态记录 · 2026-07-21T23:51:37Z

- Actor：`codex-root-r07-007`
- Status：`IMPLEMENTED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：批准范围已实现并由对应提交及R07候选/交付门禁验证。

## 状态记录 · 2026-07-21T23:51:40Z

- Actor：`codex-root-r07-007`
- Status：`CLOSED`
- Session：`SES-20260721T210252Z-D631F6E4`
- Note：实现提交已推送，相关受影响门禁均通过。
