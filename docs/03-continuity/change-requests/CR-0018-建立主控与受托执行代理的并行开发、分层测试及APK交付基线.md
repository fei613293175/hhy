---
cr_id: CR-0018
status: APPROVED
requester_actor_id: codex-master
approver_actor_id: codex-architecture-reviewer
task_id: TASK-R02-001
session_id: SES-20260717T183459Z-D64E7407
created_at: 2026-07-17T18:40:25Z
updated_at: 2026-07-17T18:42:55Z
---
# CR-0018 — 建立主控与受托执行代理的并行开发、分层测试及APK交付基线

## 用户需求摘要

用户要求立即采用可提升开发效率的开发方式：主控加并行执行代理、纵向Story切片、代码生成、分层测试与当天APK验收。

## 原规则

R02按数据库、后端、客户端横向串行；每次Git Hook无条件运行完整文档门禁；仅允许单一执行者直接在事实工作区开发；APK构建、桌面复制、上传和用户验收主要依赖人工命令。

## 新规则

保留唯一主控Session和事件哈希链；最多三个受托执行代理在独立scratch worktree按互斥路径并行，禁止修改连续性事实和直接提交推送，由主控校验补丁与模块证据后集成。R02改为四个纵向Story批次并行汇合。门禁分为FAST、MODULE、INTEGRATION、RELEASE；确定性生成物必须可漂移检查；APK执行固定签名、单调versionCode、桌面和公网四方哈希验证，用户真机验收独立置PASS。

## 修改原因

当前门禁重复、R02任务横向串行、APK签名与版本身份漂移，必须在R02业务编码前完成受控治理改造。

## 影响摘要

新增并行开发运行手册、代理角色、worktree编排、测试影响映射、生成漂移检查和APK交付入口；消除重复门禁与CI；修复冷启动遍历依赖目录；重排R02后续任务为纵向切片并统一19个新增端点、27个故事依赖operationId、25项专项测试口径。

## 影响文件

- `AGENTS.md`
- `.continuity/CONTINUITY_POLICY.yaml`
- `scripts/continuity_lib.py`
- `scripts/continuity_gate.py`
- `config/test-impact-map.yaml`
- `scripts/run_affected_tests.py`
- `scripts/prepare_parallel_worktrees.ps1`
- `scripts/check_generated_assets.py`
- `scripts/generate_release_slice.py`
- `scripts/deliver_android_test_apk.py`
- `tests/test_run_affected_tests.py`
- `tests/test_parallel_worktrees.py`
- `tests/test_generated_assets.py`
- `tests/test_android_apk_delivery.py`
- `.codex/agents/hhy-backend-data.toml`
- `.codex/agents/hhy-clients.toml`
- `.codex/agents/hhy-quality-delivery.toml`
- `.codex/agents/hhy-reviewer.toml`
- `docs/03-continuity/并行加速开发运行手册_V1.0.md`
- `docs/05-app-build/APK持续交付强制规则_V1.2.2.md`
- `releases/R02/TASKS.yaml`
- `releases/R02/PARALLEL_EXECUTION_PLAN.yaml`
- `releases/R02/RELEASE_MANIFEST.yaml`
- `package.json`
- `Makefile`
- `.github/workflows/ci.yml`
- `.github/workflows/daily-integration.yml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `development.parallel_workers=3`
- `development.test_gate_profiles=FAST,MODULE,INTEGRATION,RELEASE`
- `android.test_signing=SECRET_REF`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python scripts/test_continuity_protocol.py`
- `python tests/test_run_affected_tests.py`
- `python tests/test_parallel_worktrees.py`
- `python tests/test_generated_assets.py`
- `python tests/test_android_apk_delivery.py`
- `python scripts/check_v123_continuity.py --strict`
- `python scripts/check_v122_documentation.py --release R02`

## 版本

- `R02`

## 迁移与兼容策略

P00/R01历史记录和manifest保持只读兼容；连续性协议仍为1.0且只有一个ACTIVE主控Session；scratch工作区不是事实源。普通本地Debug仍可临时签名，R02正式测试APK缺少固定签名时失败。现有全量门禁保留在日集成、合并和Release阶段。

## 用户确认

用户于2026-07-18明确要求立即开始采用主控加并行执行代理、纵向Story、代码生成、分层测试和当天APK验收的提速方式。

## 审批

- 审批人：`codex-architecture-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-17T18:42:55Z`
- 说明：独立复核通过：保持单主控Session可避免事件链冲突；受托代理必须独占路径且不得改连续性事实或直接提交；全量质量门禁保留在集成与Release；APK签名仅使用SecretRef。

## 状态记录 · 2026-07-17T18:43:12Z

- Actor：`codex-master`
- Status：`IMPLEMENTING`
- Session：`SES-20260717T183459Z-D64E7407`
- Note：审批后开始并行落地治理、门禁、生成和APK交付基线。

## 状态记录 · 2026-07-17T19:28:10Z

- Actor：`codex-master`
- Status：`IMPLEMENTED`
- Session：`SES-20260717T183459Z-D64E7407`
- Note：并行开发治理、R02纵向批次、分层门禁、生成漂移检查、影响测试、每日全量CI和APK交付状态机已实现；严格文档与连续性门禁通过。

## 状态记录 · 2026-07-17T19:41:11Z

- Actor：`codex-master`
- Status：`CLOSED`
- Session：`SES-20260717T183459Z-D64E7407`
- Note：CR-0018全部治理、门禁、生成、CI与APK交付范围已实现并验证，后续由R02纵向批次执行。
