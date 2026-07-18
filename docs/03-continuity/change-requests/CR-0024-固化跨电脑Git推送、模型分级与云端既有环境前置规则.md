---
cr_id: CR-0024
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-runtime-governance-reviewer
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-17T23:18:43Z
updated_at: 2026-07-17T23:21:03Z
---
# CR-0024 — 固化跨电脑Git推送、模型分级与云端既有环境前置规则

## 用户需求摘要

项目所有者要求换电脑或换AI后可恢复Git远程并正常推送；复杂/中等/轻量场景分别自动选择Sol/Terra/Luna；Codex默认已连接obx-test且既有环境可用，连接异常必须阻断而不能无服务器开发或重建本地Android环境。

## 原规则

GitHub remote和upstream仅存在本机.git/config，Handoff Git Bundle不携带；模型选择依赖当前客户端默认，未按复杂度路由；obx-test与固定Android镜像只在会话日志中，换AI或电脑可能误判为无服务器并重建本地环境。

## 新规则

仓库跟踪非秘密Git transport descriptor并提供受控恢复/推送预检；复杂、高风险任务选择Sol，中等任务选择Terra，轻量任务选择Luna，模型不可用时必须可审计fallback且不得伪称；正常开发硬前提为Codex已连接obx-test且既有云环境可用，启动必须预检，失败立即阻断并报告，禁止以无服务器状态继续或重建本地Android SDK，Android固定复用hhy-android-toolchain:r01-46fb273与hhy-r01-android-gradle-cache。

## 修改原因

当前Git remote仅存在.git/config，Git Bundle不携带remote/upstream；模型路由和云端环境只存在聊天/会话日志，无法被新AI和新电脑可靠恢复。

## 影响摘要

把远程仓库、分支推送、模型分级和云端既有环境从本机/聊天事实提升为跨AI跨电脑的跟踪配置、Context Pack、恢复脚本和严格门禁。

## 影响文件

- `AGENTS.md`
- `START_HERE.md`
- `templates/AGENTS.md`
- `templates/START_HERE.md`
- `README.md`
- `CHANGELOG.md`
- `.gitignore`
- `.continuity/CONTINUITY_POLICY.yaml`
- `config/CONTINUITY_POLICY.yaml`
- `config/REPOSITORY_TRANSPORT.yaml`
- `config/DEVELOPMENT_RUNTIME.yaml`
- `PROJECT_MANIFEST.yaml`
- `apps/android/README.md`
- `scripts/continuity.py`
- `scripts/continuity_lib.py`
- `scripts/continuity_gate.py`
- `scripts/check_v123_continuity.py`
- `scripts/restore_git_transport.py`
- `scripts/select_execution_profile.py`
- `scripts/verify_cloud_environment.py`
- `scripts/run_continuity_self_test.py`
- `tests/test_git_transport_recovery.py`
- `tests/test_model_routing.py`
- `tests/test_cloud_environment.py`
- `tests/test_context_pack_parallel_policy.py`
- `docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml`
- `docs/03-continuity/无状态接续运行手册_V1.2.3.md`
- `.github/workflows/continuity-gate.yml`
- `artifacts/context/CURRENT_CONTEXT_PACK.yaml`
- `artifacts/context/CURRENT_CONTEXT_PACK.md`
- `artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `git_transport`
- `runtime_policy.task_model_routing`
- `runtime_policy.execution_environment`
- `context.development_runtime`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `git-transport-three-mode-recovery`
- `git-push-preflight-no-credentials-no-force`
- `model-routing-sol-terra-luna-and-auditable-fallback`
- `cloud-obx-test-required-preflight-and-fixed-android-image`
- `context-pack-runtime-policy-reconstruction`
- `continuity-strict-and-documentation-gate`

## 版本

- `R02`
- `R03`
- `R04`
- `R05`
- `R06`
- `R07`
- `R08`
- `R09`
- `R10`
- `R11`
- `R12`
- `R13`
- `R14`
- `R15`
- `R16`
- `R17`
- `R18`
- `R19`
- `R20`
- `R21`
- `R22`
- `R23`
- `R24`
- `R25`
- `R26`
- `R27`
- `R28`
- `R29`
- `R30`
- `R31`
- `R32`

## 迁移与兼容策略

不改变业务API、数据库或页面；remote URL和SSH alias可跟踪但PAT、私钥和credential内容禁止入库。已有.git仓库校验/修复origin与upstream；Git Bundle恢复后重建transport；纯源码无历史时禁止覆盖远端。运行时缺Luna时退回Terra低推理并记录，不得冒充Luna。

## 用户确认

项目所有者于2026-07-18明确确认：若未主动说明未连接，必须默认Codex客户端已连接项目云服务器且已有对应环境，禁止按无服务器状态开发或重建本地Android环境；同时要求跨电脑Git推送恢复及Sol/Terra/Luna分级成为硬性规则。

## 审批

- 审批人：`codex-runtime-governance-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-17T23:21:03Z`
- 说明：独立审核通过：规则与用户最新澄清一致；默认云端已连接且既有环境可用，只有用户明确报告未连接时才能改变此前提。Git传输仅跟踪非秘密信息，模型降级必须审计。

## 状态记录 · 2026-07-17T23:21:06Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：开始实现跨电脑Git transport、模型分级与云端既有环境强制前置门禁。

## 状态记录 · 2026-07-18T00:01:48Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T210927Z-13B07A7D`
- Note：Git transport、模型分级、obx-test既有环境预检、Context Pack与严格门禁均已实现；专项、真实云端、14项生命周期和11项仓库重建通过。
