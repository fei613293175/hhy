---
cr_id: CR-0341
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: codex-independent-r12-release-fixture-reviewer
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-25T21:31:45Z
updated_at: 2026-07-25T21:33:47Z
---
# CR-0341 — 补齐R12隔离候选official-STAGING版本发布夹具

## 用户需求摘要

持续推进R12最终候选，避免在GitHub模拟器上盲目重跑；终端静默执行并复用obx-test环境

## 原规则

R12专用候选夹具只创建登录用户、会员奖励和发布内容事实，未创建StartupGate调用official+STAGING版本检查所需的构建配置、构建任务、APK产物、渠道与已发布记录；空发布表使正确的10221请求返回404，十页旅程无法开始

## 新规则

R12专用候选夹具必须在同一事务中幂等创建唯一official+STAGING活动渠道、10221对应的确定性构建配置/任务/APK产物和PUBLISHED发布记录；重复执行输出严格一致，POST version-check对10221返回HTTP 200且updateType=NONE，任何完整候选触发前必须先在obx-test验证两次夹具与公网启动门禁

## 修改原因

Run 30175242918已完成OIDC自动登录，但启动门禁对official+STAGING+10221调用version-check返回404；R12隔离数据库app_build_artifacts、app_release_channels、app_release_records均为空，候选夹具遗漏启动必需的已发布版本链

## 影响摘要

仅补充R12隔离STAGING候选测试数据及静态回归和唯一问题记录；不修改生产数据库迁移、版本检查合同、Android业务页面、生产PROD发布链或既有R01-R11基线

## 影响文件

- `scripts/prepare_r12_ci_fixture.sh`
- `tests/test_r12_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `POST /public-api/v1/app/version-check（合同不变，仅补候选数据）`

## 数据库与迁移

- `R12隔离夹具：app_build_profiles/app_build_jobs/app_build_artifacts/app_release_channels/app_release_records`

## 配置

- `R12 candidate fixture official/STAGING/versionCode 10221`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests.test_r12_candidate；夹具连续执行两次输出严格一致；version-check HTTP 200且updateType=NONE；Problem Registry YAML唯一性`

## 版本

- `R12`

## 迁移与兼容策略

无迁移；仅向专用hhy-r12-staging-*-postgres-1隔离数据库写入带R12固定标识的可重复夹具，已存在且一致时复用，发现冲突或漂移时硬失败，不触碰PROD渠道或生产发布记录

## 用户确认

项目所有者已明确授权持续开发、静默执行终端命令、复用obx-test既有环境并避免反复浪费GitHub模拟器时间。

## 审批

- 审批人：`codex-independent-r12-release-fixture-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-25T21:33:47Z`
- 说明：独立复核确认根因成立且不与PROB-0053/CR-0127重复：既有问题修复owner-test渠道漂移，本CR修复R12隔离STAGING数据库发布链为空。范围仅限prepare_r12_ci_fixture、其静态回归、唯一Problem Registry和Changelog；夹具必须受YES确认、hhy-r12-ci-candidate-*后端、hhy-r12-staging-*-postgres-1数据库、staging Profile及CI automation五重约束，同事务幂等写入official+STAGING+10221，发现漂移硬失败。回归必须明确拒绝PROD写入，连续两次输出一致，并在模拟器前验证公网version-check为200且updateType=NONE；不得修改API合同、迁移或生产发布记录。

## 状态记录 · 2026-07-25T21:36:44Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：独立审批完成；开始补齐R12隔离候选official+STAGING+10221发布链、静态回归和唯一问题登记，模拟器前先做obx-test双次幂等与公网200/NONE验证。

## 状态记录 · 2026-07-25T21:42:05Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：R12隔离STAGING发布链已实现：tests.test_r12_candidate、android candidate request/gate共30项通过，Problem Registry 114项唯一；obx-test Bash语法通过，夹具连续两次输出逐字一致并显示release=1/version=10221/update=NONE；服务器五层链各1且PROD渠道0；公网version-check HTTP 200、latestVersionCode=10221、updateType=NONE。
