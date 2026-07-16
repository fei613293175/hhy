# CURRENT CONTEXT PACK · 无对话接续上下文

- 生成时间：2026-07-16T23:28:23Z
- Context Hash：`c20a55a17585be0105c31ef35d2ff6422d63df08bdfd40e4b3d7b8858532bf2d`
- 对话依赖：`PROHIBITED`
- 事实源：`REPOSITORY_ONLY`
- 精确恢复命令：

```bash
python3 scripts/continuity.py checkpoint --summary '<完成内容>' --next-step '<下一步>'
```

## 当前状态

```yaml
project: hhy-pro-platform
baseline_version: 1.2.3
phase: P00
active_release: P00
active_task: TASK-P00-001
status: IN_PROGRESS
documentation_status: ZERO_BLOCKING_DOCUMENT_GAPS
last_green_commit: NOT_INITIALIZED
last_staging_apk: null
completed_tasks:
- V1.2.2_ENGINEERING_BASELINE
- V1.2.2_PAGE_SPEC_FREEZE
- V1.2.2_ADMIN_OPERATION_FREEZE
- V1.2.2_CONFIG_UX_FREEZE
- V1.2.2_TRACEABILITY_AND_DOR_FREEZE
- V1.2.3_CONTINUITY_PROTOCOL
- V1.2.3_GIT_HOOK_ENFORCEMENT
- V1.2.3_REPOSITORY_ONLY_HANDOFF
- V1.2.3_FULL_LIFECYCLE_REGRESSION
- V1.2.3_PORTABLE_HANDOFF_RECONSTRUCTION
- V1.2.3_TAMPER_DETECTION_VALIDATION
in_progress_tasks:
- TASK-P00-001
blocked_tasks: []
next_task: TASK-P00-001
updated_at: '2026-07-16T23:28:09Z'
notes:
- V1.2.2已补齐全部页面、字段、状态、动作、后台运营、配置角色与跨字段规则
- 全部REST/WebSocket操作均有页面或系统所有者
- P00—R32均已生成DoR和故事级任务
- Android/数据库/Docker/供应商/DNS/签名/压测属于开发或上线门禁，不属于文档缺口
- V1.2.3增加唯一开发会话、任务租约、强制检查点、逐Commit身份、CR合同、Handoff Bundle、事件哈希链和CI重验
- 对话不得作为事实源；任何WIP、决策、测试、变更和下一步必须写入仓库
- 另一个AI只需运行python3 scripts/continuity.py resume即可从仓库重建上下文
validation:
  documentation_contract: PASS_0_ERRORS_0_WARNINGS
  document_gap_count: 0
  unassigned_api_operations: 0
  releases_with_dor_and_stories: 33
  implementation_and_external_gates: RECORDED_NOT_BLOCKING_START
  deep_contract_audit: PASS
  interpretation: 开发前文档完整；软件实现、运行和上线验证按版本门禁执行
  continuity_protocol: ENFORCED
  repository_only_handoff: READY
  conversation_context_required: false
  continuity_doctor: PASS_0_ERRORS_0_WARNINGS
  combined_v123_gate: PASS_0_ERRORS_0_WARNINGS
  continuity_lifecycle: PASS_14_CHECKS_6_COMMITS
  portable_repository_reconstruction: PASS_9_CHECKS
  event_hash_chain_tamper_detection: PASS
  handoff_manifest_tamper_detection: PASS
  prepush_per_commit_validation: PASS
  ci_per_commit_validation: PASS
  clean_export: PASS
continuity:
  protocol_version: '1.0'
  mode: ENFORCED
  active_session_id: SES-20260716T232809Z-B4A980AF
  actor_id: codex-root
  story_id: STORY-P00-001
  lease_expires_at: '2026-07-17T03:28:09Z'
  latest_checkpoint: null
  project_fingerprint: null
  context_pack: null
  handoff_bundle: null
```

## 下一任务

```yaml
id: TASK-P00-001
title: 初始化私有Monorepo并启用V1.2.3持续开发无状态接续强制门禁
status: READY
release: P00
definition_of_ready: releases/P00/DEFINITION_OF_READY.yaml
stories: releases/P00/STORIES.yaml
requirements:
- REQ-ENG-001
- REQ-ARCH-001
- REQ-DATA-001
- REQ-EVENT-001
- REQ-ADMIN-BASE-001
- REQ-OBS-002
- REQ-DOR-001
steps:
- 先运行python3 scripts/continuity.py resume，禁止依赖旧对话判断项目状态
- 运行bootstrap创建不可变Baseline Commit、安装Git Hooks并切换任务分支
- 运行start领取唯一任务/Story和租约后才允许修改项目文件
- 每60分钟、关键测试后、提交前、交接前和上下文切换前运行checkpoint
- 冻结事实变化必须先创建、补齐并由不同Actor批准CR
- 提交、推送和CI必须通过逐Commit连续性门禁
acceptance:
- P00全部适用DoR为PASS
- 故事责任人、依赖和验收条件明确
- 页面/API/配置/数据/测试事实源未产生手工分叉
- CURRENT_STATUS、追踪、CR和Session Log持续更新
- 没有ACTIVE Session时项目提交被拒绝
- 每个Commit绑定Task/Story/Session/Checkpoint/Tests/CR
- WIP可仅凭仓库和Handoff Bundle由新AI恢复
- 事件哈希链、Context Pack和Manifest可检测篡改
out_of_scope:
- 在开发开始前完成全部业务功能
- 把供应商、DNS、生产签名和上线审查作为文档冻结条件
next_after: 按P00/STORIES.yaml顺序执行
continuity_protocol: .continuity/CONTINUITY_POLICY.yaml
conversation_context_required: false
repository_only_resume: true
cold_start_commands:
- python3 scripts/continuity.py resume
- python3 scripts/continuity.py bootstrap --actor <ACTOR_ID> --init-git --initial-commit --task TASK-P00-001 --branch task/TASK-P00-001
- python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-P00-001 --story <STORY_ID> --goal '<精确目标>'
mandatory_commands:
  resume: python3 scripts/continuity.py resume
  start: python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-P00-001 --story <STORY_ID> --goal '<精确目标>'
  checkpoint: python3 scripts/continuity.py checkpoint --summary '<完成内容>' --next-step '<精确下一步>' --test 'name|PASS|evidence|note'
  handoff: python3 scripts/continuity.py handoff --actor <ACTOR_ID> --reason '<交接原因>' --next-step '<精确下一步>' --portable-zip artifacts/handoff-exports/<name>.zip
  cr_create: python3 scripts/continuity.py cr-create --actor <REQUESTER> --task TASK-P00-001 --title '<标题>' --user-request '<用户要求>' --reason '<原因>'
  cr_amend: python3 scripts/continuity.py cr-amend --actor <REQUESTER> --cr <CR-ID> --original-rule '<原规则>' --new-rule '<新规则>' --impact-summary
    '<影响>' --migration-and-compatibility '<兼容>' --file '<文件>' --test '<测试>' --release P00
  export_clean: python3 scripts/continuity.py export-clean --portable-zip artifacts/exports/<name>.zip
```

## 活跃会话

```yaml
protocol_version: '1.0'
package_version: 1.2.3
session_id: SES-20260716T232809Z-B4A980AF
status: ACTIVE
actor:
  id: codex-root
  kind: AI_OR_HUMAN
  host: unknown
release: P00
task_id: TASK-P00-001
story_id: STORY-P00-001
goal: 修复P00工程基线漂移并恢复全量可重复验证
started_at: '2026-07-16T23:28:09Z'
updated_at: '2026-07-16T23:28:10Z'
takeover_of: null
change_requests: []
scope:
  allowed_paths:
  - apps/**
  - services/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - catalogs/**
  - tests/**
  - infra/**
  - design/**
  - docs/**
  - releases/**
  - scripts/**
  - templates/**
  - .github/**
  - .githooks/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  approved_exceptions: []
  source: story+explicit
git:
  initialized: false
  branch: NOT_INITIALIZED
  base_commit: NOT_INITIALIZED
  start_head: NOT_INITIALIZED
  upstream: null
  initial_worktree_state: CLEAN
lease:
  duration_minutes: 240
  renewed_at: '2026-07-16T23:28:09Z'
  expires_at: '2026-07-17T03:28:09Z'
checkpoint_sequence: 0
latest_checkpoint: null
session_log: docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md
next_step: 阅读当前Story、逐项验证事实源后开始实现
context_pack: THIS_CONTEXT_PACK
handoff_bundle: null
closure: null
```

## 最新检查点

```yaml
status: NO_CHECKPOINT
```

## 接续状态与事件头

```yaml
mode: ENFORCED
protocol_version: '1.0'
active_session_id: SES-20260716T232809Z-B4A980AF
last_session_id: null
last_session_result: null
last_closure_checkpoint_id: null
event_count: 12
event_head_hash: 95506ab06990301fa44dbb6dab57d23896d89310f6c1a1899fb966731fe314f4
event_chain_valid: true
```

## 最近会话与任务迁移

```yaml
recent_sessions: - session_id: SES-V123-PACKAGE-BASELINE
  task_id: TASK-P00-001
  story_id: STORY-P00-001
  actor_id: openai-package-builder
  status: CLOSED
  started_at: '2026-07-16T00:00:00Z'
  updated_at: '2026-07-16T00:30:00Z'
  closed_at: '2026-07-16T00:30:00Z'
  latest_checkpoint: .continuity/checkpoints/SES-V123-PACKAGE-BASELINE/0001-package-baseline.yaml
  handoff_bundle: artifacts/handoffs/HND-V123-PACKAGE-BASELINE
  record: .continuity/sessions/SES-V123-PACKAGE-BASELINE.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-V123-PACKAGE-BASELINE.md
- session_id: SES-20260716T232809Z-B4A980AF
  task_id: TASK-P00-001
  story_id: STORY-P00-001
  actor_id: codex-root
  status: ACTIVE
  started_at: '2026-07-16T23:28:09Z'
  record: .continuity/sessions/SES-20260716T232809Z-B4A980AF.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md
task_claims: - task_id: TASK-P00-001
  story_id: STORY-P00-001
  session_id: SES-V123-PACKAGE-BASELINE
  actor_id: openai-package-builder
  status: RELEASED
  claimed_at: '2026-07-16T00:00:00Z'
  closed_at: '2026-07-16T00:30:00Z'
  result: PACKAGE_BASELINE
- claim_id: CLM-1BBC8343F3B4
  session_id: SES-20260716T232809Z-B4A980AF
  task_id: TASK-P00-001
  story_id: STORY-P00-001
  actor_id: codex-root
  status: ACTIVE
  claimed_at: '2026-07-16T23:28:09Z'
  allowed_paths:
  - apps/**
  - services/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - catalogs/**
  - tests/**
  - infra/**
  - design/**
  - docs/**
  - releases/**
  - scripts/**
  - templates/**
  - .github/**
  - .githooks/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
recent_task_transitions: - transition_id: TRN-V123-PACKAGE-BASELINE
  timestamp: '2026-07-16T00:30:00Z'
  release: P00
  task_id: TASK-P00-001
  story_id: STORY-P00-001
  from_status: DOCUMENTATION_READY
  to_status: READY
  session_id: SES-V123-PACKAGE-BASELINE
  actor_id: openai-package-builder
  reason: V1.2.3接续门禁包构建完成；实际任务尚未执行
- transition_id: TRN-B05386F910BB
  timestamp: '2026-07-16T23:28:09Z'
  release: P00
  task_id: TASK-P00-001
  story_id: STORY-P00-001
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260716T232809Z-B4A980AF
  actor_id: codex-root
  reason: 会话领取任务
```

## Git 状态

```yaml
initialized: true
branch: master
head: UNBORN
upstream: null
ahead: null
behind: null
dirty: true
status_porcelain:
- ?? .continuity/ACTIVE_SESSION.yaml
- ?? .continuity/CHANGE_REQUEST_INDEX.yaml
- ?? .continuity/CONTINUITY_POLICY.yaml
- ?? .continuity/EVENT_LOG.jsonl
- ?? .continuity/SESSION_INDEX.yaml
- ?? .continuity/STATE.yaml
- ?? .continuity/TASK_CLAIMS.yaml
- ?? .continuity/TASK_TRANSITIONS.yaml
- ?? .continuity/change_requests/CR-0002.yaml
- ?? .continuity/checkpoints/SES-V123-PACKAGE-BASELINE/0001-package-baseline.yaml
- ?? .continuity/runtime/.gitkeep
- ?? .continuity/sessions/SES-20260716T232809Z-B4A980AF.yaml
- ?? .continuity/sessions/SES-V123-PACKAGE-BASELINE.yaml
- ?? .env.example
- ?? .githooks/commit-msg
- ?? .githooks/pre-commit
- ?? .githooks/pre-push
- ?? .githooks/prepare-commit-msg
- ?? .github/workflows/ci.yml
- ?? .github/workflows/continuity-gate.yml
- ?? .gitignore
- ?? .gitmessage
- ?? .npmrc
- ?? .nvmrc
- ?? .tool-versions
- ?? AGENTS.md
- ?? CHANGELOG.md
- ?? CONTINUITY_POLICY.yaml
- ?? CURRENT_STATUS.yaml
- ?? DEVELOPMENT_RISK_REGISTER.md
- ?? DOCUMENTATION_GAP_CLOSURE_REPORT.md
- ?? MANIFEST_SHA256.txt
- ?? Makefile
- ?? NEXT_TASK.yaml
- ?? PACKAGE_CONTENTS.md
- ?? PACKAGE_MANIFEST.json
- ?? PENDING_USER_ACTIONS.md
- ?? PENDING_USER_ACTIONS_DNS.md
- ?? PROJECT_BASELINE.json
- ?? PROJECT_BASELINE.yaml
- ?? PROJECT_EXECUTION_PLAN.md
- ?? PROJECT_MANIFEST.yaml
- ?? README.md
- ?? START_HERE.md
- ?? V1.2.2_最终文档冻结说明.md
- ?? V1.2.2_问题闭环清单.md
- ?? V1.2.3_持续开发无状态接续强制门禁发布说明.md
- ?? apps/admin-web/README.md
- ?? apps/admin-web/index.html
- ?? apps/admin-web/package.json
- ?? apps/admin-web/src/App.vue
- ?? apps/admin-web/src/catalog.test.ts
- ?? apps/admin-web/src/catalog.ts
- ?? apps/admin-web/src/env.d.ts
- ?? apps/admin-web/src/generated/admin-pages.json
- ?? apps/admin-web/src/main.ts
- ?? apps/admin-web/src/router.ts
- ?? apps/admin-web/src/styles.css
- ?? apps/admin-web/src/views/CatalogPage.vue
- ?? apps/admin-web/tsconfig.json
- ?? apps/admin-web/vite.config.ts
- ?? apps/android/.gitignore
- ?? apps/android/README.md
- ?? apps/android/app/build.gradle.kts
- ?? apps/android/app/proguard-rules.pro
- ?? apps/android/app/src/main/AndroidManifest.xml
- ?? apps/android/app/src/main/assets/android-screens.v1.2.2.json
- ?? apps/android/app/src/main/java/cc/orbexa/hhy/HhyApplication.kt
- ?? apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt
- ?? apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt
- ?? apps/android/app/src/main/res/values/strings.xml
- ?? apps/android/app/src/main/res/values/styles.xml
- ?? apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt
- ?? apps/android/build.gradle.kts
- ?? apps/android/core/designsystem/build.gradle.kts
- ?? apps/android/core/designsystem/src/main/AndroidManifest.xml
- ?? apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
- ?? apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTheme.kt
- ?? apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
- ?? apps/android/core/network/build.gradle.kts
- ?? apps/android/core/network/src/main/AndroidManifest.xml
- ?? apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
- ?? apps/android/core/network/src/main/java/cc/orbexa/hhy/network/RequestIdPolicy.kt
- ?? apps/android/core/network/src/test/java/cc/orbexa/hhy/network/RequestIdPolicyTest.kt
- ?? apps/android/feature/shell/build.gradle.kts
- ?? apps/android/feature/shell/src/main/AndroidManifest.xml
- ?? apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt
- ?? apps/android/gradle.properties
- ?? apps/android/gradle/libs.versions.toml
- ?? apps/android/gradle/wrapper/gradle-wrapper.jar
- ?? apps/android/gradle/wrapper/gradle-wrapper.properties
- ?? apps/android/gradlew
- ?? apps/android/gradlew.bat
- ?? apps/android/lint.xml
- ?? apps/android/settings.gradle.kts
- ?? apps/android/stability_config.conf
- ?? apps/h5/README.md
- ?? apps/h5/index.html
- ?? apps/h5/package.json
- ?? apps/h5/src/App.vue
- ?? apps/h5/src/catalog.test.ts
- ?? apps/h5/src/catalog.ts
- ?? apps/h5/src/env.d.ts
- ?? apps/h5/src/generated/h5-pages.json
- ?? apps/h5/src/main.ts
- ?? apps/h5/src/router.ts
- ?? apps/h5/src/styles.css
- ?? apps/h5/src/views/PublicPage.vue
- ?? apps/h5/tsconfig.json
- ?? apps/h5/vite.config.ts
- ?? artifacts/context/CURRENT_CONTEXT_PACK.md
- ?? artifacts/context/CURRENT_CONTEXT_PACK.yaml
- ?? artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
- ?? artifacts/handoffs/HND-V123-PACKAGE-BASELINE/HANDOFF.yaml
- ?? artifacts/handoffs/HND-V123-PACKAGE-BASELINE/MANIFEST_SHA256.txt
- ?? artifacts/handoffs/HND-V123-PACKAGE-BASELINE/README.md
- ?? artifacts/reports/2026-07-16/FINAL_VALIDATION_SUMMARY.json
- ?? artifacts/reports/2026-07-16/V1.2.2_FINAL_DOCUMENT_VALIDATION_SUMMARY.md
- ?? artifacts/reports/2026-07-16/V1.2.3_FINAL_VALIDATION_SUMMARY.json
- ?? artifacts/reports/2026-07-16/V1.2.3_FINAL_VALIDATION_SUMMARY.md
- ?? artifacts/validation/backend-maven-verify.log
- ?? artifacts/validation/continuity-gate-v1.2.3.json
- ?? artifacts/validation/continuity-integration-v1.2.3.json
- ?? artifacts/validation/continuity-integration-v1.2.3.log
- ?? artifacts/validation/continuity-lifecycle-integration-v1.2.3.json
- ?? artifacts/validation/continuity-lifecycle-integration-v1.2.3.log
- ?? artifacts/validation/contract-generation-status.json
- ?? artifacts/validation/database-pglite-smoke.log
- ?? artifacts/validation/deep-contract-audit-v1.2.2.json
- ?? artifacts/validation/doctor-initial.json
- ?? artifacts/validation/documentation-v1.2.3.json
- ?? artifacts/validation/pnpm-install.log
- ?? artifacts/validation/project-doctor-final.log
- ?? artifacts/validation/project-doctor-v1.2.2.json
- ?? artifacts/validation/project-doctor-v1.2.3.json
- ?? artifacts/validation/web-verify.log
- ?? catalogs/TRACEABILITY_MATRIX.csv
- ?? catalogs/admin_api_endpoints.csv
- ?? catalogs/admin_page_operation_specs.csv
- ?? catalogs/admin_pages.csv
- ?? catalogs/analytics_event_catalog.csv
- ?? catalogs/android_screens.csv
- ?? catalogs/api_endpoints.csv
- ?? catalogs/api_ui_ownership.csv
- ?? catalogs/capability_completion_matrix.csv
- ?? catalogs/change_request_index.csv
- ?? catalogs/config_cross_field_rules.csv
- ?? catalogs/config_registry.csv
- ?? catalogs/config_role_matrix.csv
- ?? catalogs/continuity_event_catalog.csv
- ?? catalogs/continuity_gate_catalog.csv
- ?? catalogs/continuity_required_records.csv
- ?? catalogs/data_tables.csv
- ?? catalogs/development_risk_register.csv
- ?? catalogs/frontend_backend_matrix.csv
- ?? catalogs/h5_screens.csv
- ?? catalogs/handoff_index.csv
- ?? catalogs/non_functional_requirements.csv
- ?? catalogs/release_artifact_index.csv
- ?? catalogs/release_definition_of_ready.csv
- ?? catalogs/release_plan.csv
- ?? catalogs/release_story_backlog.csv
- ?? catalogs/requirements_catalog.csv
- ?? catalogs/screen_visual_binding.csv
- ?? catalogs/session_index.csv
- ?? catalogs/task_transition_ledger.csv
- ?? catalogs/test_cases.csv
- ?? catalogs/ui_action_matrix.csv
- ?? catalogs/ui_navigation_specifications.csv
- ?? catalogs/ui_page_fields.csv
- ?? catalogs/ui_page_specifications.csv
- ?? catalogs/ui_page_states.csv
- ?? catalogs/ui_reference_index.csv
- ?? catalogs/ui_reference_index.json
- ?? catalogs/ui_templates.csv
- ?? config/CONFIG_REGISTRY.yaml
- ?? config/CONTINUITY_POLICY.yaml
- ?? config/DOMAIN_PLAN.yaml
- ?? contracts/README.md
- ?? contracts/admin-openapi.yaml
- ?? contracts/contract_status.csv
- ?? contracts/error-codes.csv
- ?? contracts/openapi.yaml
- ?? contracts/operation-error-matrix.csv
- ?? contracts/websocket-events.yaml
- ?? database/README.md
- ?? database/enum_registry.yaml
- ?? database/migrations/V001__extensions_and_schema.sql
- ?? database/migrations/V002__access_admin_and_platform.sql
- ?? database/migrations/V003__content_identity_and_communication.sql
- ?? database/migrations/V004__commerce_redpacket_and_finance.sql
- ?? database/migrations/V005__growth_operations_and_delivery.sql
- ?? database/migrations/V006__constraints_indexes_and_triggers.sql
- ?? database/migrations/V007__state_money_and_accounting_invariants.sql
- ?? database/migrations/V008__baseline_rbac_and_feature_flags.sql
- ?? database/migrations/V009__v122_operational_governance.sql
- ?? database/rollback/U001__drop_hhy_schema_DEV_ONLY.sql
- ?? database/rollback/U009__v122_operational_governance.sql
- ?? database/schema_dictionary.csv
- ?? database/schema_traceability.csv
- ?? database/state_machines.yaml
- ?? database/tests/postgres_smoke_success.sql
- ?? database/verification/verify_baseline.sql
- ?? design/component-catalog.csv
- ?? design/component-catalog.json
- ?? design/effect-previews/B01/HHY_B01_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B01/HHY_B01_MANIFEST.json
- ?? design/effect-previews/B02/HHY_B02_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B02/HHY_B02_MANIFEST.json
- ?? design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B03/HHY_B03_MANIFEST.json
- ?? design/effect-previews/B04/HHY_B04_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B04/HHY_B04_MANIFEST.json
- ?? design/effect-previews/B05/HHY_B05_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B05/HHY_B05_MANIFEST.json
- ?? design/effect-previews/B06/HHY_B06_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B06/HHY_B06_MANIFEST.json
- ?? design/effect-previews/B07/HHY_B07_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B07/HHY_B07_MANIFEST.json
- ?? design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B08/HHY_B08_MANIFEST.json
- ?? design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B09/HHY_B09_MANIFEST.json
- ?? design/effect-previews/B10/HHY_B10_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B10/HHY_B10_MANIFEST.json
- ?? design/effect-previews/B11/HHY_B11_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B11/HHY_B11_MANIFEST.json
- ?? design/effect-previews/B12/HHY_B12_8PAGE_UI_REFERENCE.png
- ?? design/effect-previews/B12/HHY_B12_MANIFEST.json
- ?? design/tokens/admin-design-tokens.v1.2.2.css
- ?? design/tokens/admin-design-tokens.v1.2.css
- ?? design/tokens/android-design-tokens.v1.2.2.json
- ?? design/tokens/android-design-tokens.v1.2.json
- ?? design/tokens/h5-design-tokens.v1.2.2.css
- ?? design/tokens/h5-design-tokens.v1.2.css
- ?? design/tokens/hhy_design_tokens_v1.2.2.json
- ?? design/tokens/hhy_design_tokens_v1.2.json
- ?? docs/00-baseline/SOURCE_OF_TRUTH.md
- ?? docs/00-baseline/V1.2.2_开发就绪说明.md
- ?? docs/00-baseline/style.css
- ?? docs/00-baseline/合伙云Pro_完整项目开发文档_V1.2.2_开发就绪版.md
- ?? docs/00-baseline/契约与前后端对应冻结门禁_V1.2.2.md
- ?? docs/01-architecture/adr/ADR-001-模块化单体而非初期微服务.md
- ?? docs/01-architecture/adr/ADR-002-事务Outbox与Inbox.md
- ?? docs/01-architecture/adr/ADR-003-统一复式记账.md
- ?? docs/01-architecture/adr/ADR-004-H5安全与部署边界.md
- ?? docs/01-architecture/adr/ADR-005-配置和秘密生命周期.md
- ?? docs/01-architecture/adr/ADR-006-契约生成与兼容.md
- ?? docs/01-architecture/技术栈与版本锁定_V1.2.2.md
- ?? docs/01-architecture/系统架构与模块边界_V1.2.2.md
- ?? docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
- ?? docs/02-config/配置中心运营表单权限与跨字段规则_V1.2.2.md
- ?? docs/02-contracts/API契约与兼容规范_V1.2.2.md
- ?? docs/02-contracts/状态机与枚举规范_V1.2.2.md
- ?? docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
- ?? docs/02-ui/UI参考图使用与开发约束_V1.2.2.md
- ?? docs/02-ui/page-specs/admin/ADM-ACCOUNTING-001_统一会计交易.md
- ?? docs/02-ui/page-specs/admin/ADM-ACCOUNTING-002_会计交易详情与冲正.md
- ?? docs/02-ui/page-specs/admin/ADM-APP-001_App版本.md
- ?? docs/02-ui/page-specs/admin/ADM-APPEAL-001_申诉处理.md
- ?? docs/02-ui/page-specs/admin/ADM-APPROVAL-001_双人审批中心.md
- ?? docs/02-ui/page-specs/admin/ADM-AUDIT-001_审计日志.md
- ?? docs/02-ui/page-specs/admin/ADM-AUTH-001_后台登录.md
- ?? docs/02-ui/page-specs/admin/ADM-AUTH-002_二次验证.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-001_构建中心首页.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-002_构建配置.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-003_签名配置.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-004_构建任务列表.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-005_构建任务详情.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-006_构建产物与下载.md
- ?? docs/02-ui/page-specs/admin/ADM-BUILD-007_发布与回滚.md
- ?? docs/02-ui/page-specs/admin/ADM-CHAT-001_聊天举报.md
- ?? docs/02-ui/page-specs/admin/ADM-CMS-001_首页CMS.md
- ?? docs/02-ui/page-specs/admin/ADM-CMS-002_H5宣传页CMS.md
- ?? docs/02-ui/page-specs/admin/ADM-CMS-003_公告帮助协议.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-001_系统配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-002_供应商配置中心.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-003_阿里云短信配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-004_对象存储配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-005_支付网关配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-006_支付宝企业付款配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-007_实名认证配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONFIG-008_域名与环境配置.md
- ?? docs/02-ui/page-specs/admin/ADM-CONTENT-001_统一内容列表.md
- ?? docs/02-ui/page-specs/admin/ADM-CONTENT-002_内容详情.md
- ?? docs/02-ui/page-specs/admin/ADM-CONTENT-003_字典与属性.md
- ?? docs/02-ui/page-specs/admin/ADM-DASH-001_数据驾驶舱.md
- ?? docs/02-ui/page-specs/admin/ADM-EXPORT-001_数据导出中心.md
- ?? docs/02-ui/page-specs/admin/ADM-ID-001_实名列表.md
- ?? docs/02-ui/page-specs/admin/ADM-ID-002_实名详情与复核.md
- ?? docs/02-ui/page-specs/admin/ADM-JOB-001_定时任务.md
- ?? docs/02-ui/page-specs/admin/ADM-MEMBER-001_会员SKU.md
- ?? docs/02-ui/page-specs/admin/ADM-MEMBER-002_用户会员.md
- ?? docs/02-ui/page-specs/admin/ADM-NOTIFY-001_通知模板.md
- ?? docs/02-ui/page-specs/admin/ADM-NOTIFY-002_通知发送记录.md
- ?? docs/02-ui/page-specs/admin/ADM-ORDER-001_订单列表.md
- ?? docs/02-ui/page-specs/admin/ADM-PAY-001_支付交易.md
- ?? docs/02-ui/page-specs/admin/ADM-PROD-001_商品与SKU.md
- ?? docs/02-ui/page-specs/admin/ADM-PROP-001_道具商品.md
- ?? docs/02-ui/page-specs/admin/ADM-PROP-002_资源位与排期.md
- ?? docs/02-ui/page-specs/admin/ADM-PROP-003_执行日志.md
- ?? docs/02-ui/page-specs/admin/ADM-RBAC-001_员工角色权限.md
- ?? docs/02-ui/page-specs/admin/ADM-RECON-001_支付对账.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-001_邀请关系.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-002_佣金策略.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-003_佣金记录.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-004_直推活跃规则.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-005_直推进度.md
- ?? docs/02-ui/page-specs/admin/ADM-REF-006_阶梯奖励矩阵.md
- ?? docs/02-ui/page-specs/admin/ADM-REPORT-001_内容举报处理.md
- ?? docs/02-ui/page-specs/admin/ADM-REVIEW-001_审核队列.md
- ?? docs/02-ui/page-specs/admin/ADM-REWARD-001_奖励账户.md
- ?? docs/02-ui/page-specs/admin/ADM-REWARD-002_奖励流水.md
- ?? docs/02-ui/page-specs/admin/ADM-RISK-001_风险事件.md
- ?? docs/02-ui/page-specs/admin/ADM-RISK-002_黑白名单.md
- ?? docs/02-ui/page-specs/admin/ADM-RISK-003_风控规则版本.md
- ?? docs/02-ui/page-specs/admin/ADM-RISK-004_风控规则模拟器.md
- ?? docs/02-ui/page-specs/admin/ADM-RP-001_红包活动列表.md
- ?? docs/02-ui/page-specs/admin/ADM-RP-002_红包活动详情.md
- ?? docs/02-ui/page-specs/admin/ADM-RP-003_红包预审核.md
- ?? docs/02-ui/page-specs/admin/ADM-RP-004_红包风险事件.md
- ?? docs/02-ui/page-specs/admin/ADM-SECURITY-001_管理员安全设置.md
- ?? docs/02-ui/page-specs/admin/ADM-SUPPORT-001_工单列表.md
- ?? docs/02-ui/page-specs/admin/ADM-SUPPORT-002_工单详情.md
- ?? docs/02-ui/page-specs/admin/ADM-TASK-001_任务定义.md
- ?? docs/02-ui/page-specs/admin/ADM-TASK-002_任务进度与预算.md
- ?? docs/02-ui/page-specs/admin/ADM-USER-001_用户列表.md
- ?? docs/02-ui/page-specs/admin/ADM-USER-002_用户详情.md
- ?? docs/02-ui/page-specs/admin/ADM-WD-001_提现列表.md
- ?? docs/02-ui/page-specs/admin/ADM-WD-002_提现详情.md
- ?? docs/02-ui/page-specs/android/DIALOG-CHAT-BLOCK-001_拉黑与解除拉黑确认.md
- ?? docs/02-ui/page-specs/android/DIALOG-CHAT-DELETE-001_删除会话确认.md
- ?? docs/02-ui/page-specs/android/DIALOG-SEARCH-001_清空搜索历史确认.md
- ?? docs/02-ui/page-specs/android/SCR-ABOUT-001_关于与检查更新.md
- ?? docs/02-ui/page-specs/android/SCR-ANN-001_公告列表.md
- ?? docs/02-ui/page-specs/android/SCR-ANN-002_公告详情.md
- ?? docs/02-ui/page-specs/android/SCR-APP-001_启动页.md
- ?? docs/02-ui/page-specs/android/SCR-APP-002_系统维护页.md
- ?? docs/02-ui/page-specs/android/SCR-APP-003_更新提示.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-005_账号冻结.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-006_登录设备.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-007_修改登录密码.md
- ?? docs/02-ui/page-specs/android/SCR-AUTH-008_注销账号.md
- ?? docs/02-ui/page-specs/android/SCR-CHAT-001_会话列表.md
- ?? docs/02-ui/page-specs/android/SCR-CHAT-002_私聊页.md
- ?? docs/02-ui/page-specs/android/SCR-DETAIL-001_项目详情.md
- ?? docs/02-ui/page-specs/android/SCR-DETAIL-002_App详情.md
- ?? docs/02-ui/page-specs/android/SCR-DETAIL-003_群聊详情.md
- ?? docs/02-ui/page-specs/android/SCR-DETAIL-004_团队长详情.md
- ?? docs/02-ui/page-specs/android/SCR-FAV-001_我的收藏.md
- ?? docs/02-ui/page-specs/android/SCR-HIS-001_浏览记录.md
- ?? docs/02-ui/page-specs/android/SCR-HOME-001_首页.md
- ?? docs/02-ui/page-specs/android/SCR-ID-001_实名认证首页.md
- ?? docs/02-ui/page-specs/android/SCR-ID-002_实名信息输入.md
- ?? docs/02-ui/page-specs/android/SCR-ID-003_活体检测容器.md
- ?? docs/02-ui/page-specs/android/SCR-ID-004_实名结果.md
- ?? docs/02-ui/page-specs/android/SCR-LIST-001_项目列表.md
- ?? docs/02-ui/page-specs/android/SCR-LIST-002_App列表.md
- ?? docs/02-ui/page-specs/android/SCR-LIST-003_群聊列表.md
- ?? docs/02-ui/page-specs/android/SCR-LIST-004_团队长列表.md
- ?? docs/02-ui/page-specs/android/SCR-ME-001_我的首页.md
- ?? docs/02-ui/page-specs/android/SCR-ME-002_个人资料.md
- ?? docs/02-ui/page-specs/android/SCR-MEMBER-001_会员中心.md
- ?? docs/02-ui/page-specs/android/SCR-MEMBER-002_会员购买确认.md
- ?? docs/02-ui/page-specs/android/SCR-MEMBER-003_会员升级.md
- ?? docs/02-ui/page-specs/android/SCR-MEMBER-004_会员权益明细.md
- ?? docs/02-ui/page-specs/android/SCR-MSG-001_消息中心.md
- ?? docs/02-ui/page-specs/android/SCR-MYC-001_我的发布.md
- ?? docs/02-ui/page-specs/android/SCR-MYC-002_草稿箱.md
- ?? docs/02-ui/page-specs/android/SCR-MYC-003_内容管理详情.md
- ?? docs/02-ui/page-specs/android/SCR-MYC-004_审核记录.md
- ?? docs/02-ui/page-specs/android/SCR-MYC-005_内容数据.md
- ?? docs/02-ui/page-specs/android/SCR-NOTICE-001_通知列表.md
- ?? docs/02-ui/page-specs/android/SCR-NOTICE-002_通知详情.md
- ?? docs/02-ui/page-specs/android/SCR-ORDER-001_订单列表.md
- ?? docs/02-ui/page-specs/android/SCR-ORDER-002_订单详情.md
- ?? docs/02-ui/page-specs/android/SCR-PROP-001_道具商城.md
- ?? docs/02-ui/page-specs/android/SCR-PROP-002_我的道具.md
- ?? docs/02-ui/page-specs/android/SCR-PROP-003_使用道具.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-001_发布入口.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-002_项目发布_编辑.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-003_App发布_编辑.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-004_群聊发布_编辑.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-005_团队长资料编辑.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-006_发布预览.md
- ?? docs/02-ui/page-specs/android/SCR-PUB-007_提交结果.md
- ?? docs/02-ui/page-specs/android/SCR-PUBLISHER-001_发布者主页.md
- ?? docs/02-ui/page-specs/android/SCR-REF-001_推广中心.md
- ?? docs/02-ui/page-specs/android/SCR-REF-002_邀请好友.md
- ?? docs/02-ui/page-specs/android/SCR-REF-003_邀请海报.md
- ?? docs/02-ui/page-specs/android/SCR-REF-004_直推用户.md
- ?? docs/02-ui/page-specs/android/SCR-REF-005_二级用户.md
- ?? docs/02-ui/page-specs/android/SCR-REF-006_消费佣金.md
- ?? docs/02-ui/page-specs/android/SCR-REF-007_直推活跃奖励.md
- ?? docs/02-ui/page-specs/android/SCR-REF-008_推广规则_教程.md
- ?? docs/02-ui/page-specs/android/SCR-REWARD-001_奖励账户.md
- ?? docs/02-ui/page-specs/android/SCR-REWARD-002_奖励明细.md
- ?? docs/02-ui/page-specs/android/SCR-RP-001_红包首页.md
- ?? docs/02-ui/page-specs/android/SCR-RP-002_红包任务资格.md
- ?? docs/02-ui/page-specs/android/SCR-RP-003_红包浏览任务.md
- ?? docs/02-ui/page-specs/android/SCR-RP-004_我的红包.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-001_红包活动列表.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-002_创建红包.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-003_预审核结果.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-004_红包报价确认.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-005_红包活动详情.md
- ?? docs/02-ui/page-specs/android/SCR-RP-ADV-006_提高红包金额.md
- ?? docs/02-ui/page-specs/android/SCR-SEARCH-001_全局搜索.md
- ?? docs/02-ui/page-specs/android/SCR-SEARCH-002_搜索结果.md
- ?? docs/02-ui/page-specs/android/SCR-SET-001_设置.md
- ?? docs/02-ui/page-specs/android/SCR-SUPPORT-001_帮助中心.md
- ?? docs/02-ui/page-specs/android/SCR-SUPPORT-002_创建工单.md
- ?? docs/02-ui/page-specs/android/SCR-SUPPORT-003_工单列表.md
- ?? docs/02-ui/page-specs/android/SCR-SUPPORT-004_工单详情.md
- ?? docs/02-ui/page-specs/android/SCR-SUPPORT-005_帮助文章详情.md
- ?? docs/02-ui/page-specs/android/SCR-TASK-001_任务中心.md
- ?? docs/02-ui/page-specs/android/SCR-TASK-002_个人任务积分.md
- ?? docs/02-ui/page-specs/android/SCR-WD-001_绑定支付宝.md
- ?? docs/02-ui/page-specs/android/SCR-WD-002_申请提现.md
- ?? docs/02-ui/page-specs/android/SCR-WD-003_提现记录.md
- ?? docs/02-ui/page-specs/android/SHEET-CHAT-001_发送联系方式.md
- ?? docs/02-ui/page-specs/android/SHEET-CHAT-002_聊天举报.md
- ?? docs/02-ui/page-specs/android/SHEET-CONTACT-001_联系方式面板.md
- ?? docs/02-ui/page-specs/android/SHEET-CONTENT-INVALID-001_联系方式失效反馈.md
- ?? docs/02-ui/page-specs/android/SHEET-MEDIA-001_媒体上传管理器.md
- ?? docs/02-ui/page-specs/android/SHEET-REPORT-001_举报面板.md
- ?? docs/02-ui/page-specs/android/SHEET-RP-001_红包领取面板.md
- ?? docs/02-ui/page-specs/android/SHEET-SHARE-001_分享面板.md
- ?? docs/02-ui/page-specs/h5/H5-001_品牌宣传下载页.md
- ?? docs/02-ui/page-specs/h5/H5-002_安装教程.md
- ?? docs/02-ui/page-specs/h5/H5-003_邀请落地页.md
- ?? docs/02-ui/page-specs/h5/H5-004_项目分享页.md
- ?? docs/02-ui/page-specs/h5/H5-005_App分享页.md
- ?? docs/02-ui/page-specs/h5/H5-006_群聊分享页.md
- ?? docs/02-ui/page-specs/h5/H5-007_团队长分享页.md
- ?? docs/02-ui/page-specs/h5/H5-008_统一收银台.md
- ?? docs/02-ui/page-specs/h5/H5-009_支付结果.md
- ?? docs/02-ui/page-specs/h5/H5-010_协议与规则.md
- ?? docs/02-ui/page-specs/h5/H5-011_帮助文章.md
- ?? docs/02-ui/page-specs/h5/H5-012_活体回跳.md
- ?? docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
- ?? docs/02-ui/效果图视觉规则_V1.0_原始规划.md
- ?? docs/02-ui/管理后台页面与运营操作完整规格_V1.2.2.md
- ?? docs/02-ui/页面施工规格总索引_V1.2.2.md
- ?? docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
- ?? docs/02-ui/页面状态错误恢复深链与返回栈规范_V1.2.2.md
- ?? docs/03-continuity/ADR_TEMPLATE.md
- ?? docs/03-continuity/CHANGE_REQUEST_TEMPLATE.md
- ?? docs/03-continuity/CHECKPOINT_SCHEMA.yaml
- ?? docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml
- ?? docs/03-continuity/EVENT_LOG_SCHEMA.yaml
- ?? docs/03-continuity/HANDOFF_SCHEMA.yaml
- ?? docs/03-continuity/PITFALLS.md
- ?? docs/03-continuity/PROBLEM_REGISTRY.yaml
- ?? docs/03-continuity/REUSABLE_PATTERNS.md
- ?? docs/03-continuity/SESSION_LOG_TEMPLATE.md
- ?? docs/03-continuity/SESSION_RECORD_SCHEMA.yaml
- ?? docs/03-continuity/adr/ADR-0001-模块化单体与必要服务隔离.md
- ?? docs/03-continuity/adr/ADR-0002-效果图仅作布局样式参考.md
- ?? docs/03-continuity/adr/ADR-0003-供应商配置中心.md
- ?? docs/03-continuity/adr/ADR-0004-官方App受控自助构建中心.md
- ?? docs/03-continuity/adr/ADR-0005-灵活迭代数量与能力矩阵.md
- ?? docs/03-continuity/change-requests/CR-0001-V1.2工程执行强化.md
- ?? docs/03-continuity/change-requests/CR-0002-增加持续开发无状态接续强制门禁.md
- ?? docs/03-continuity/sessions/2026-07/SES-20260715-V12-PACKAGE.md
- ?? docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md
- ?? docs/03-continuity/sessions/2026-07/SES-V123-PACKAGE-BASELINE.md
- ?? docs/03-continuity/异常中断与AI接管演练_V1.2.3.md
- ?? docs/03-continuity/持续开发无状态接续强制门禁_V1.2.3.md
- ?? docs/03-continuity/无状态接续与需求变更自动记录协议_V1.2.2.md
- ?? docs/03-continuity/无状态接续运行手册_V1.2.3.md
- ?? docs/04-vendors/identity/source/输出内容(1).txt
- ?? docs/04-vendors/identity/source/输出内容.txt
- ?? docs/04-vendors/identity/实名认证供应商接口摘要.md
- ?? docs/04-vendors/供应商配置中心详细规格_V1.2.2.md
- ?? docs/04-vendors/多对象存储适配规范_V1.2.2.md
- ?? docs/04-vendors/阿里云短信适配要求.md
- ?? docs/05-app-build/APK持续交付强制规则_V1.2.2.md
- ?? docs/05-app-build/自助App构建与版本发布中心详细规格_V1.2.2.md
- ?? docs/06-releases/P00_R01-R32_逐版本实施说明_V1.2.2.md
- ?? docs/06-releases/逐版本DoR与故事级工作分解_V1.2.2.md
- ?? docs/07-operations/DEPLOYMENT_RUNBOOK.md
- ?? docs/07-operations/ENVIRONMENT_MATRIX.md
- ?? docs/07-operations/ROLLBACK_RUNBOOK.md
- ?? docs/07-operations/orbexa.cc域名与DNS协作流程_V1.2.2.md
- ?? docs/08-testing/测试策略与质量门禁_V1.2.2.md
- ?? docs/09-development/本地开发与立即开工指南_V1.2.2.md
- ?? docs/99-archive/V1.2.1/FINAL_VALIDATION_SUMMARY.md
- ?? docs/99-archive/V1.2.1/V1.2.1_正式开发发布说明.md
- ?? docs/99-archive/V1.2.1/project-doctor-final.json
- ?? docs/99-archive/V1.2.1/project-doctor-v1.2.1.py
- ?? docs/99-archive/V1.2.1/合伙云Pro_完整项目开发文档_V1.2.2_开发就绪版.html
- ?? docs/99-archive/V1.2_工程执行强化审计报告.md
- ?? docs/99-archive/合伙云Pro_完整项目开发文档_V1.2.1_开发就绪版.md
- ?? infra/README.md
- ?? infra/docker-compose.yml
- ?? package.json
- ?? packages/api-client/package.json
- ?? packages/api-client/src/admin.generated.ts
- ?? packages/api-client/src/client.generated.ts
- ?? packages/api-client/src/index.ts
- ?? packages/api-client/src/request.ts
- ?? packages/api-client/tsconfig.json
- ?? packages/design-tokens/admin.css
- ?? packages/design-tokens/h5.css
- ?? packages/design-tokens/package.json
- ?? packages/domain-types/package.json
- ?? packages/domain-types/src/index.ts
- ?? packages/domain-types/tsconfig.json
- ?? pnpm-lock.yaml
- ?? pnpm-workspace.yaml
- ?? releases/P00/ACCEPTANCE_MATRIX.csv
- ?? releases/P00/DEFINITION_OF_READY.yaml
- ?? releases/P00/RELEASE_MANIFEST.yaml
- ?? releases/P00/STORIES.yaml
- ?? releases/P00/TASKS.yaml
- ?? releases/R01/ACCEPTANCE_MATRIX.csv
- ?? releases/R01/DEFINITION_OF_READY.yaml
- ?? releases/R01/RELEASE_MANIFEST.yaml
- ?? releases/R01/STORIES.yaml
- ?? releases/R01/TASKS.yaml
- ?? releases/R02/ACCEPTANCE_MATRIX.csv
- ?? releases/R02/DEFINITION_OF_READY.yaml
- ?? releases/R02/RELEASE_MANIFEST.yaml
- ?? releases/R02/STORIES.yaml
- ?? releases/R02/TASKS.yaml
- ?? releases/R03/ACCEPTANCE_MATRIX.csv
- ?? releases/R03/DEFINITION_OF_READY.yaml
- ?? releases/R03/RELEASE_MANIFEST.yaml
- ?? releases/R03/STORIES.yaml
- ?? releases/R03/TASKS.yaml
- ?? releases/R04/ACCEPTANCE_MATRIX.csv
- ?? releases/R04/DEFINITION_OF_READY.yaml
- ?? releases/R04/RELEASE_MANIFEST.yaml
- ?? releases/R04/STORIES.yaml
- ?? releases/R04/TASKS.yaml
- ?? releases/R05/ACCEPTANCE_MATRIX.csv
- ?? releases/R05/DEFINITION_OF_READY.yaml
- ?? releases/R05/RELEASE_MANIFEST.yaml
- ?? releases/R05/STORIES.yaml
- ?? releases/R05/TASKS.yaml
- ?? releases/R06/ACCEPTANCE_MATRIX.csv
- ?? releases/R06/DEFINITION_OF_READY.yaml
- ?? releases/R06/RELEASE_MANIFEST.yaml
- ?? releases/R06/STORIES.yaml
- ?? releases/R06/TASKS.yaml
- ?? releases/R07/ACCEPTANCE_MATRIX.csv
- ?? releases/R07/DEFINITION_OF_READY.yaml
- ?? releases/R07/RELEASE_MANIFEST.yaml
- ?? releases/R07/STORIES.yaml
- ?? releases/R07/TASKS.yaml
- ?? releases/R08/ACCEPTANCE_MATRIX.csv
- ?? releases/R08/DEFINITION_OF_READY.yaml
- ?? releases/R08/RELEASE_MANIFEST.yaml
- ?? releases/R08/STORIES.yaml
- ?? releases/R08/TASKS.yaml
- ?? releases/R09/ACCEPTANCE_MATRIX.csv
- ?? releases/R09/DEFINITION_OF_READY.yaml
- ?? releases/R09/RELEASE_MANIFEST.yaml
- ?? releases/R09/STORIES.yaml
- ?? releases/R09/TASKS.yaml
- ?? releases/R10/ACCEPTANCE_MATRIX.csv
- ?? releases/R10/DEFINITION_OF_READY.yaml
- ?? releases/R10/RELEASE_MANIFEST.yaml
- ?? releases/R10/STORIES.yaml
- ?? releases/R10/TASKS.yaml
- ?? releases/R11/ACCEPTANCE_MATRIX.csv
- ?? releases/R11/DEFINITION_OF_READY.yaml
- ?? releases/R11/RELEASE_MANIFEST.yaml
- ?? releases/R11/STORIES.yaml
- ?? releases/R11/TASKS.yaml
- ?? releases/R12/ACCEPTANCE_MATRIX.csv
- ?? releases/R12/DEFINITION_OF_READY.yaml
- ?? releases/R12/RELEASE_MANIFEST.yaml
- ?? releases/R12/STORIES.yaml
- ?? releases/R12/TASKS.yaml
- ?? releases/R13/ACCEPTANCE_MATRIX.csv
- ?? releases/R13/DEFINITION_OF_READY.yaml
- ?? releases/R13/RELEASE_MANIFEST.yaml
- ?? releases/R13/STORIES.yaml
- ?? releases/R13/TASKS.yaml
- ?? releases/R14/ACCEPTANCE_MATRIX.csv
- ?? releases/R14/DEFINITION_OF_READY.yaml
- ?? releases/R14/RELEASE_MANIFEST.yaml
- ?? releases/R14/STORIES.yaml
- ?? releases/R14/TASKS.yaml
- ?? releases/R15/ACCEPTANCE_MATRIX.csv
- ?? releases/R15/DEFINITION_OF_READY.yaml
- ?? releases/R15/RELEASE_MANIFEST.yaml
- ?? releases/R15/STORIES.yaml
- ?? releases/R15/TASKS.yaml
- ?? releases/R16/ACCEPTANCE_MATRIX.csv
- ?? releases/R16/DEFINITION_OF_READY.yaml
- ?? releases/R16/RELEASE_MANIFEST.yaml
- ?? releases/R16/STORIES.yaml
- ?? releases/R16/TASKS.yaml
- ?? releases/R17/ACCEPTANCE_MATRIX.csv
- ?? releases/R17/DEFINITION_OF_READY.yaml
- ?? releases/R17/RELEASE_MANIFEST.yaml
- ?? releases/R17/STORIES.yaml
- ?? releases/R17/TASKS.yaml
- ?? releases/R18/ACCEPTANCE_MATRIX.csv
- ?? releases/R18/DEFINITION_OF_READY.yaml
- ?? releases/R18/RELEASE_MANIFEST.yaml
- ?? releases/R18/STORIES.yaml
- ?? releases/R18/TASKS.yaml
- ?? releases/R19/ACCEPTANCE_MATRIX.csv
- ?? releases/R19/DEFINITION_OF_READY.yaml
- ?? releases/R19/RELEASE_MANIFEST.yaml
- ?? releases/R19/STORIES.yaml
- ?? releases/R19/TASKS.yaml
- ?? releases/R20/ACCEPTANCE_MATRIX.csv
- ?? releases/R20/DEFINITION_OF_READY.yaml
- ?? releases/R20/RELEASE_MANIFEST.yaml
- ?? releases/R20/STORIES.yaml
- ?? releases/R20/TASKS.yaml
- ?? releases/R21/ACCEPTANCE_MATRIX.csv
- ?? releases/R21/DEFINITION_OF_READY.yaml
- ?? releases/R21/RELEASE_MANIFEST.yaml
- ?? releases/R21/STORIES.yaml
- ?? releases/R21/TASKS.yaml
- ?? releases/R22/ACCEPTANCE_MATRIX.csv
- ?? releases/R22/DEFINITION_OF_READY.yaml
- ?? releases/R22/RELEASE_MANIFEST.yaml
- ?? releases/R22/STORIES.yaml
- ?? releases/R22/TASKS.yaml
- ?? releases/R23/ACCEPTANCE_MATRIX.csv
- ?? releases/R23/DEFINITION_OF_READY.yaml
- ?? releases/R23/RELEASE_MANIFEST.yaml
- ?? releases/R23/STORIES.yaml
- ?? releases/R23/TASKS.yaml
- ?? releases/R24/ACCEPTANCE_MATRIX.csv
- ?? releases/R24/DEFINITION_OF_READY.yaml
- ?? releases/R24/RELEASE_MANIFEST.yaml
- ?? releases/R24/STORIES.yaml
- ?? releases/R24/TASKS.yaml
- ?? releases/R25/ACCEPTANCE_MATRIX.csv
- ?? releases/R25/DEFINITION_OF_READY.yaml
- ?? releases/R25/RELEASE_MANIFEST.yaml
- ?? releases/R25/STORIES.yaml
- ?? releases/R25/TASKS.yaml
- ?? releases/R26/ACCEPTANCE_MATRIX.csv
- ?? releases/R26/DEFINITION_OF_READY.yaml
- ?? releases/R26/RELEASE_MANIFEST.yaml
- ?? releases/R26/STORIES.yaml
- ?? releases/R26/TASKS.yaml
- ?? releases/R27/ACCEPTANCE_MATRIX.csv
- ?? releases/R27/DEFINITION_OF_READY.yaml
- ?? releases/R27/RELEASE_MANIFEST.yaml
- ?? releases/R27/STORIES.yaml
- ?? releases/R27/TASKS.yaml
- ?? releases/R28/ACCEPTANCE_MATRIX.csv
- ?? releases/R28/DEFINITION_OF_READY.yaml
- ?? releases/R28/RELEASE_MANIFEST.yaml
- ?? releases/R28/STORIES.yaml
- ?? releases/R28/TASKS.yaml
- ?? releases/R29/ACCEPTANCE_MATRIX.csv
- ?? releases/R29/DEFINITION_OF_READY.yaml
- ?? releases/R29/RELEASE_MANIFEST.yaml
- ?? releases/R29/STORIES.yaml
- ?? releases/R29/TASKS.yaml
- ?? releases/R30/ACCEPTANCE_MATRIX.csv
- ?? releases/R30/DEFINITION_OF_READY.yaml
- ?? releases/R30/RELEASE_MANIFEST.yaml
- ?? releases/R30/STORIES.yaml
- ?? releases/R30/TASKS.yaml
- ?? releases/R31/ACCEPTANCE_MATRIX.csv
- ?? releases/R31/DEFINITION_OF_READY.yaml
- ?? releases/R31/RELEASE_MANIFEST.yaml
- ?? releases/R31/STORIES.yaml
- ?? releases/R31/TASKS.yaml
- ?? releases/R32/ACCEPTANCE_MATRIX.csv
- ?? releases/R32/DEFINITION_OF_READY.yaml
- ?? releases/R32/RELEASE_MANIFEST.yaml
- ?? releases/R32/STORIES.yaml
- ?? releases/R32/TASKS.yaml
- ?? releases/RELEASE_DEPENDENCIES.yaml
- ?? requirements-dev.txt
- ?? scripts/bootstrap_repository.py
- ?? scripts/build_context_pack.py
- ?? scripts/check_api_contract.py
- ?? scripts/check_config_registry.py
- ?? scripts/check_db_schema.py
- ?? scripts/check_frontend_backend_matrix.py
- ?? scripts/check_main_doc.py
- ?? scripts/check_release_artifacts.py
- ?? scripts/check_traceability.py
- ?? scripts/check_ui_tokens.py
- ?? scripts/check_v122_documentation.py
- ?? scripts/check_v123_continuity.py
- ?? scripts/check_v123_documentation.py
- ?? scripts/continuity.py
- ?? scripts/continuity_gate.py
- ?? scripts/continuity_lib.py
- ?? scripts/create_change_request.py
- ?? scripts/create_handoff_bundle.py
- ?? scripts/dns_action_report.py
- ?? scripts/export_clean_project_bundle.py
- ?? scripts/generate_android_identity.py
- ?? scripts/generate_android_scaffold.py
- ?? scripts/generate_apk_manifest.py
- ?? scripts/generate_contracts.py
- ?? scripts/generate_database.py
- ?? scripts/generate_frontend_types.py
- ?? scripts/generate_scaffolds.py
- ?? scripts/generate_sha_manifest.py
- ?? scripts/install_git_hooks.py
- ?? scripts/new_session_log.py
- ?? scripts/prepare_commit_message.py
- ?? scripts/project-doctor.py
- ?? scripts/project-doctor.sh
- ?? scripts/resume-project.sh
- ?? scripts/run_continuity_self_test.py
- ?? scripts/run_postgres_migration_smoke.sh
- ?? scripts/test_continuity_protocol.py
- ?? scripts/verify_ui_references.py
- ?? services/backend/.mvn/jvm.config
- ?? services/backend/.mvn/maven.config
- ?? services/backend/.mvn/wrapper/maven-wrapper.properties
- ?? services/backend/README.md
- ?? services/backend/access/pom.xml
- ?? services/backend/access/src/main/java/cc/orbexa/hhy/access/AccessModule.java
- ?? services/backend/access/src/main/java/cc/orbexa/hhy/access/package-info.java
- ?? services/backend/boot/pom.xml
- ?? services/backend/boot/src/main/java/cc/orbexa/hhy/HhyApplication.java
- ?? services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityConfiguration.java
- ?? services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/GlobalExceptionHandler.java
- ?? services/backend/boot/src/main/java/cc/orbexa/hhy/boot/web/RequestIdFilter.java
- ?? services/backend/boot/src/main/resources/application.yml
- ?? services/backend/boot/src/main/resources/contracts/admin-openapi.yaml
- ?? services/backend/boot/src/main/resources/contracts/error-codes.csv
- ?? services/backend/boot/src/main/resources/contracts/openapi.yaml
- ?? services/backend/boot/src/main/resources/contracts/websocket-events.yaml
- ?? services/backend/boot/src/main/resources/db/migration/V001__extensions_and_schema.sql
- ?? services/backend/boot/src/main/resources/db/migration/V002__access_admin_and_platform.sql
- ?? services/backend/boot/src/main/resources/db/migration/V003__content_identity_and_communication.sql
- ?? services/backend/boot/src/main/resources/db/migration/V004__commerce_redpacket_and_finance.sql
- ?? services/backend/boot/src/main/resources/db/migration/V005__growth_operations_and_delivery.sql
- ?? services/backend/boot/src/main/resources/db/migration/V006__constraints_indexes_and_triggers.sql
- ?? services/backend/boot/src/main/resources/db/migration/V007__state_money_and_accounting_invariants.sql
- ?? services/backend/boot/src/main/resources/db/migration/V008__baseline_rbac_and_feature_flags.sql
- ?? services/backend/boot/src/test/java/cc/orbexa/hhy/ModuleBoundaryTest.java
- ?? services/backend/boot/src/test/java/cc/orbexa/hhy/PublicEndpointsTest.java
- ?? services/backend/boot/src/test/resources/application-test.yml
- ?? services/backend/commerce/pom.xml
- ?? services/backend/commerce/src/main/java/cc/orbexa/hhy/commerce/CommerceModule.java
- ?? services/backend/commerce/src/main/java/cc/orbexa/hhy/commerce/package-info.java
- ?? services/backend/content/pom.xml
- ?? services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentModule.java
- ?? services/backend/content/src/main/java/cc/orbexa/hhy/content/package-info.java
- ?? services/backend/incentive/pom.xml
- ?? services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/IncentiveModule.java
- ?? services/backend/incentive/src/main/java/cc/orbexa/hhy/incentive/package-info.java
- ?? services/backend/mvnw
- ?? services/backend/mvnw.cmd
- ?? services/backend/platform/pom.xml
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/config/HhyPlatformProperties.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/config/PlatformConfiguration.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/release/AppVersionCheckRequest.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/release/AppVersionController.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/release/AppVersionPolicyView.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/release/AppVersionService.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/status/PlatformStatusController.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/status/PlatformStatusService.java
- ?? services/backend/platform/src/main/java/cc/orbexa/hhy/platform/status/PlatformStatusView.java
- ?? services/backend/pom.xml
- ?? services/backend/shared-kernel/pom.xml
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/api/ApiErrorResponse.java
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/api/ApiResponse.java
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/api/BusinessException.java
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/domain/Money.java
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/event/DomainEvent.java
- ?? services/backend/shared-kernel/src/main/java/cc/orbexa/hhy/shared/idempotency/IdempotencyKey.java
- ?? templates/ADR_TEMPLATE.md
- ?? templates/AGENTS.md
- ?? templates/CHANGE_REQUEST_TEMPLATE.md
- ?? templates/CURRENT_STATUS.yaml
- ?? templates/NEXT_TASK.yaml
- ?? templates/PROBLEM_REGISTRY_EXAMPLE.yaml
- ?? templates/SESSION_LOG_TEMPLATE.md
- ?? templates/START_HERE.md
- ?? tests/README.md
- ?? 合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.html
- ?? 合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.md
recent_commits: []
```

## 会话累计项目变更

- 指纹：`f734411423b38ca5fca4b418b6f65367b6790dec7a746c140102bdf4bca63300`
- 文件数：740

- `.env.example`
- `.githooks/commit-msg`
- `.githooks/pre-commit`
- `.githooks/pre-push`
- `.githooks/prepare-commit-msg`
- `.github/workflows/ci.yml`
- `.github/workflows/continuity-gate.yml`
- `.gitignore`
- `.gitmessage`
- `.npmrc`
- `.nvmrc`
- `.tool-versions`
- `AGENTS.md`
- `CHANGELOG.md`
- `CONTINUITY_POLICY.yaml`
- `DEVELOPMENT_RISK_REGISTER.md`
- `DOCUMENTATION_GAP_CLOSURE_REPORT.md`
- `MANIFEST_SHA256.txt`
- `Makefile`
- `PACKAGE_CONTENTS.md`
- `PACKAGE_MANIFEST.json`
- `PENDING_USER_ACTIONS.md`
- `PENDING_USER_ACTIONS_DNS.md`
- `PROJECT_BASELINE.json`
- `PROJECT_BASELINE.yaml`
- `PROJECT_EXECUTION_PLAN.md`
- `PROJECT_MANIFEST.yaml`
- `README.md`
- `START_HERE.md`
- `V1.2.2_最终文档冻结说明.md`
- `V1.2.2_问题闭环清单.md`
- `V1.2.3_持续开发无状态接续强制门禁发布说明.md`
- `apps/admin-web/README.md`
- `apps/admin-web/index.html`
- `apps/admin-web/package.json`
- `apps/admin-web/src/App.vue`
- `apps/admin-web/src/catalog.test.ts`
- `apps/admin-web/src/catalog.ts`
- `apps/admin-web/src/env.d.ts`
- `apps/admin-web/src/generated/admin-pages.json`
- `apps/admin-web/src/main.ts`
- `apps/admin-web/src/router.ts`
- `apps/admin-web/src/styles.css`
- `apps/admin-web/src/views/CatalogPage.vue`
- `apps/admin-web/tsconfig.json`
- `apps/admin-web/vite.config.ts`
- `apps/android/.gitignore`
- `apps/android/README.md`
- `apps/android/app/build.gradle.kts`
- `apps/android/app/proguard-rules.pro`
- `apps/android/app/src/main/AndroidManifest.xml`
- `apps/android/app/src/main/assets/android-screens.v1.2.2.json`
- `apps/android/app/src/main/java/cc/orbexa/hhy/HhyApplication.kt`
- `apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt`
- `apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt`
- `apps/android/app/src/main/res/values/strings.xml`
- `apps/android/app/src/main/res/values/styles.xml`
- `apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt`
- `apps/android/build.gradle.kts`
- `apps/android/core/designsystem/build.gradle.kts`
- `apps/android/core/designsystem/src/main/AndroidManifest.xml`
- `apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTheme.kt`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `apps/android/core/network/build.gradle.kts`
- `apps/android/core/network/src/main/AndroidManifest.xml`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/RequestIdPolicy.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/RequestIdPolicyTest.kt`
- `apps/android/feature/shell/build.gradle.kts`
- `apps/android/feature/shell/src/main/AndroidManifest.xml`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`
- `apps/android/gradle.properties`
- `apps/android/gradle/libs.versions.toml`
- `apps/android/gradle/wrapper/gradle-wrapper.jar`
- `apps/android/gradle/wrapper/gradle-wrapper.properties`
- `apps/android/gradlew`
- `apps/android/gradlew.bat`
- `apps/android/lint.xml`
- `apps/android/settings.gradle.kts`
- `apps/android/stability_config.conf`
- `apps/h5/README.md`
- `apps/h5/index.html`
- `apps/h5/package.json`
- `apps/h5/src/App.vue`
- `apps/h5/src/catalog.test.ts`
- `apps/h5/src/catalog.ts`
- `apps/h5/src/env.d.ts`
- `apps/h5/src/generated/h5-pages.json`
- `apps/h5/src/main.ts`
- `apps/h5/src/router.ts`
- `apps/h5/src/styles.css`
- `apps/h5/src/views/PublicPage.vue`
- `apps/h5/tsconfig.json`
- `apps/h5/vite.config.ts`
- `catalogs/TRACEABILITY_MATRIX.csv`
- `catalogs/admin_api_endpoints.csv`
- `catalogs/admin_page_operation_specs.csv`
- `catalogs/admin_pages.csv`
- `catalogs/analytics_event_catalog.csv`
- `catalogs/android_screens.csv`
- `catalogs/api_endpoints.csv`
- `catalogs/api_ui_ownership.csv`
- `catalogs/capability_completion_matrix.csv`
- `catalogs/config_cross_field_rules.csv`
- `catalogs/config_registry.csv`
- `catalogs/config_role_matrix.csv`
- `catalogs/continuity_event_catalog.csv`
- `catalogs/continuity_gate_catalog.csv`
- `catalogs/continuity_required_records.csv`
- `catalogs/data_tables.csv`
- `catalogs/development_risk_register.csv`
- `catalogs/frontend_backend_matrix.csv`
- `catalogs/h5_screens.csv`
- `catalogs/non_functional_requirements.csv`
- `catalogs/release_artifact_index.csv`
- `catalogs/release_definition_of_ready.csv`
- `catalogs/release_plan.csv`
- `catalogs/release_story_backlog.csv`
- `catalogs/requirements_catalog.csv`
- `catalogs/screen_visual_binding.csv`
- `catalogs/test_cases.csv`
- `catalogs/ui_action_matrix.csv`
- `catalogs/ui_navigation_specifications.csv`
- `catalogs/ui_page_fields.csv`
- `catalogs/ui_page_specifications.csv`
- `catalogs/ui_page_states.csv`
- `catalogs/ui_reference_index.csv`
- `catalogs/ui_reference_index.json`
- `catalogs/ui_templates.csv`
- `config/CONFIG_REGISTRY.yaml`
- `config/CONTINUITY_POLICY.yaml`
- `config/DOMAIN_PLAN.yaml`
- `contracts/README.md`
- `contracts/admin-openapi.yaml`
- `contracts/contract_status.csv`
- `contracts/error-codes.csv`
- `contracts/openapi.yaml`
- `contracts/operation-error-matrix.csv`
- `contracts/websocket-events.yaml`
- `database/README.md`
- `database/enum_registry.yaml`
- `database/migrations/V001__extensions_and_schema.sql`
- `database/migrations/V002__access_admin_and_platform.sql`
- `database/migrations/V003__content_identity_and_communication.sql`
- `database/migrations/V004__commerce_redpacket_and_finance.sql`
- `database/migrations/V005__growth_operations_and_delivery.sql`
- `database/migrations/V006__constraints_indexes_and_triggers.sql`
- `database/migrations/V007__state_money_and_accounting_invariants.sql`
- `database/migrations/V008__baseline_rbac_and_feature_flags.sql`
- `database/migrations/V009__v122_operational_governance.sql`
- `database/rollback/U001__drop_hhy_schema_DEV_ONLY.sql`
- `database/rollback/U009__v122_operational_governance.sql`
- `database/schema_dictionary.csv`
- `database/schema_traceability.csv`
- `database/state_machines.yaml`
- `database/tests/postgres_smoke_success.sql`
- `database/verification/verify_baseline.sql`
- `design/component-catalog.csv`
- `design/component-catalog.json`
- `design/effect-previews/B01/HHY_B01_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B01/HHY_B01_MANIFEST.json`
- `design/effect-previews/B02/HHY_B02_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B02/HHY_B02_MANIFEST.json`
- `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B03/HHY_B03_MANIFEST.json`
- `design/effect-previews/B04/HHY_B04_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B04/HHY_B04_MANIFEST.json`
- `design/effect-previews/B05/HHY_B05_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B05/HHY_B05_MANIFEST.json`
- `design/effect-previews/B06/HHY_B06_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B06/HHY_B06_MANIFEST.json`
- `design/effect-previews/B07/HHY_B07_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B07/HHY_B07_MANIFEST.json`
- `design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B08/HHY_B08_MANIFEST.json`
- `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B09/HHY_B09_MANIFEST.json`
- `design/effect-previews/B10/HHY_B10_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B10/HHY_B10_MANIFEST.json`
- `design/effect-previews/B11/HHY_B11_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B11/HHY_B11_MANIFEST.json`
- `design/effect-previews/B12/HHY_B12_8PAGE_UI_REFERENCE.png`
- `design/effect-previews/B12/HHY_B12_MANIFEST.json`
- `design/tokens/admin-design-tokens.v1.2.2.css`
- `design/tokens/admin-design-tokens.v1.2.css`
- `design/tokens/android-design-tokens.v1.2.2.json`
- `design/tokens/android-design-tokens.v1.2.json`
- `design/tokens/h5-design-tokens.v1.2.2.css`
- `design/tokens/h5-design-tokens.v1.2.css`
- `design/tokens/hhy_design_tokens_v1.2.2.json`
- `design/tokens/hhy_design_tokens_v1.2.json`
- `docs/00-baseline/SOURCE_OF_TRUTH.md`
- `docs/00-baseline/V1.2.2_开发就绪说明.md`
- `docs/00-baseline/style.css`
- `docs/00-baseline/合伙云Pro_完整项目开发文档_V1.2.2_开发就绪版.md`
- `docs/00-baseline/契约与前后端对应冻结门禁_V1.2.2.md`
- `docs/01-architecture/adr/ADR-001-模块化单体而非初期微服务.md`
- `docs/01-architecture/adr/ADR-002-事务Outbox与Inbox.md`
- `docs/01-architecture/adr/ADR-003-统一复式记账.md`

## 当前 Release

```yaml
RELEASE_MANIFEST.yaml:
  release: P00
  title: 仓库、环境、契约与无状态接续
  status: READY
  milestone: M0_ENGINEERING_FOUNDATION
  depends_on: []
  scope: 私有仓库、工程骨架、配置注册表、契约骨架、CI、开发/测试/预发布环境、冷启动接手
  android_test_apk_required: true
  requirements:
  - REQ-UI-001
  - REQ-CONT-001
  - REQ-ENG-001
  - REQ-CONTRACT-001
  - REQ-APK-001
  - REQ-ARCH-001
  - REQ-DATA-001
  - REQ-EVENT-001
  - REQ-LEDGER-001
  - REQ-ADMIN-BASE-001
  - REQ-OBS-002
  - REQ-SECRET-001
  - REQ-PAGE-SPEC-001
  - REQ-DOR-001
  ui:
    android: []
    h5: []
    admin: []
  contracts:
    client_api: []
    admin_api: []
    websocket: []
  database_tables:
  - outbox_events
  - inbox_messages
  - ledger_accounts
  - accounting_transactions
  - accounting_entries
  - balance_snapshots
  - reconciliation_runs
  - reconciliation_differences
  - h5_page_configs
  - cms_articles
  - agreements
  - app_versions
  - content_posts
  - app_release_records
  - app_build_artifacts
  - users
  - user_credentials
  - auth_security_challenges
  - user_sessions
  - user_devices
  - sms_verification_codes
  - login_logs
  - app_build_profiles
  - app_build_jobs
  - app_build_job_steps
  - app_signing_profiles
  - reward_accounts
  - reward_ledger
  - reward_settlements
  - reward_freezes
  - payout_accounts
  - withdrawal_requests
  - payout_transactions
  - payout_callbacks
  - payout_reconciliation_records
  - payment_transactions
  - payment_callbacks
  - payment_exception_orders
  - payment_reconciliation_records
  - admin_users
  - admin_roles
  - admin_permissions
  - admin_user_roles
  - admin_role_permissions
  - system_configs
  - system_config_versions
  - feature_flags
  - admin_approval_requests
  - provider_config_definitions
  - provider_config_versions
  - provider_connection_tests
  - provider_certificates
  - provider_certificate_access_logs
  - domain_configs
  - dns_action_items
  tests:
  - TST-ADMIN_BASE_001-HAPPY
  - TST-ADMIN_BASE_001-IDEMPOTENT
  - TST-ADMIN_BASE_001-REJECT
  - TST-APK_001-HAPPY
  - TST-APK_001-IDEMPOTENT
  - TST-APK_001-REJECT
  - TST-ARCH_001-HAPPY
  - TST-ARCH_001-IDEMPOTENT
  - TST-ARCH_001-REJECT
  - TST-CONTRACT_001-HAPPY
  - TST-CONTRACT_001-IDEMPOTENT
  - TST-CONTRACT_001-REJECT
  - TST-CONT_001-HAPPY
  - TST-CONT_001-IDEMPOTENT
  - TST-CONT_001-REJECT
  - TST-DATA_001-HAPPY
  - TST-DATA_001-IDEMPOTENT
  - TST-DATA_001-REJECT
  - TST-DOR_001-DRIFT
  - TST-DOR_001-HAPPY
  - TST-DOR_001-REJECT
  - TST-DOR_001-SECURITY
  - TST-ENG_001-HAPPY
  - TST-ENG_001-IDEMPOTENT
  - TST-ENG_001-REJECT
  - TST-EVENT_001-HAPPY
  - TST-EVENT_001-IDEMPOTENT
  - TST-EVENT_001-REJECT
  - TST-GATE-ANDROID-BUILD
  - TST-GATE-BACKEND-BUILD
  - TST-GATE-DB-MIGRATION
  - TST-GATE-LEDGER
  - TST-GATE-MAPPING
  - TST-GATE-MODULE-BOUNDARY
  - TST-GATE-OPENAPI
  - TST-GATE-OUTBOX
  - TST-GATE-SECRETS
  - TST-GATE-STATE
  - TST-GATE-WEB-BUILD
  - TST-LEDGER_001-CONCURRENCY
  - TST-LEDGER_001-HAPPY
  - TST-LEDGER_001-IDEMPOTENT
  - TST-LEDGER_001-RECOVERY
  - TST-LEDGER_001-REJECT
  - TST-OBS_002-HAPPY
  - TST-OBS_002-IDEMPOTENT
  - TST-OBS_002-REJECT
  - TST-PAGE_SPEC_001-DRIFT
  - TST-PAGE_SPEC_001-HAPPY
  - TST-PAGE_SPEC_001-REJECT
  - TST-PAGE_SPEC_001-SECURITY
  - TST-SECRET_001-HAPPY
  - TST-SECRET_001-IDEMPOTENT
  - TST-SECRET_001-REJECT
  - TST-SECRET_001-SECURITY
  - TST-UI_001-HAPPY
  - TST-UI_001-IDEMPOTENT
  - TST-UI_001-REJECT
  - TST-V122-011
  entry_gate:
  - releases/P00/DEFINITION_OF_READY.yaml 全部适用项为PASS
  - releases/P00/STORIES.yaml 中每个故事均绑定页面/API/配置/数据/测试或显式N/A
  - 本版本页面字段、状态、动作、导航和后台运营规格不存在TBD/RELEASE_BOUND
  - 全部依赖版本为GREEN或按发布计划允许的并行依赖已记录
  - 冻结契约发生变化时已创建CR并重新生成追踪和SHA
  exit_gate:
  - 领域代码与前端真实闭环
  - 数据库迁移和不变量测试通过
  - 契约/单元/集成/E2E/安全专项测试通过
  - 日志指标链路和业务告警可验证
  - Session Log、追踪矩阵、产物Manifest和下一任务更新
  - Staging APK可安装且SHA256、Commit、版本信息完整
  change_policy: V1.2.2 页面、字段、状态、动作、API、配置和数据目录是唯一事实源；破坏性变更必须CR+版本升级+迁移+消费者兼容窗口
  documentation_baseline: V1.2.2
  document_gap_status: ZERO_BLOCKING_DOCUMENT_GAPS
  definition_of_ready: releases/P00/DEFINITION_OF_READY.yaml
  story_backlog: releases/P00/STORIES.yaml
  continuity:
    policy: .continuity/CONTINUITY_POLICY.yaml
    protocol_version: '1.0'
    mode: ENFORCED
    session_required_before_change: true
    checkpoint_required_before_commit: true
    old_conversation_required: false
    handoff_bundle_required_for_actor_switch: true
DEFINITION_OF_READY.yaml:
  version: 1.2.2
  release: P00
  title: 仓库、环境、契约与无状态接续
  status: PASS_DOCUMENTATION_READY
  interpretation: 该结论仅表示开发前文档和施工契约完整；软件编译、运行、供应商联调、压测和上线验收仍在对应实施/退出门禁完成。
  gates:
  - 版本: P00
    门禁ID: P00-DOR-01
    类别: 范围与需求
    门禁条件: 本版本需求、非目标、业务规则和变更边界已冻结；每个需求在追踪矩阵有明确行
    适用性: 是
    证据: catalogs/requirements_catalog.csv;catalogs/TRACEABILITY_MATRIX.csv
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-02
    类别: 页面与模板
    门禁条件: 本版本所有页面/弹层已绑定标准模板，入口、退出、角色、数据分级和主要区域无TBD
    适用性: 不适用
    证据: catalogs/ui_page_specifications.csv;catalogs/ui_templates.csv
    当前结论: PASS
    说明: 本版本为纯工程地基；页面项显式N/A
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-03
    类别: 字段施工规格
    门禁条件: 每个页面展示、输入、筛选、路由、敏感字段均有类型、控件、必填、校验、显示/编辑条件和错误文案
    适用性: 不适用
    证据: catalogs/ui_page_fields.csv
    当前结论: PASS
    说明: 本版本为纯工程地基；页面项显式N/A
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-04
    类别: 状态与恢复
    门禁条件: 首屏、内容、空、刷新、局部失败、无权限、404、离线、提交、成功、冲突和领域状态已定义
    适用性: 不适用
    证据: catalogs/ui_page_states.csv
    当前结论: PASS
    说明: 本版本为纯工程地基；页面项显式N/A
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-05
    类别: 动作与导航
    门禁条件: 每个操作定义触发、显示/可用、确认、请求映射、幂等/版本、加载、成功、错误、重试、导航和审计
    适用性: 不适用
    证据: catalogs/ui_action_matrix.csv;catalogs/ui_navigation_specifications.csv
    当前结论: PASS
    说明: 本版本为纯工程地基；页面项显式N/A
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-06
    类别: 接口所有权
    门禁条件: 本版本每个OpenAPI operationId均有页面动作或显式系统所有者；核心请求响应禁止自由对象代替领域Schema
    适用性: 是
    证据: catalogs/api_ui_ownership.csv;contracts/openapi.yaml;contracts/admin-openapi.yaml
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-07
    类别: 数据与状态机
    门禁条件: 涉及表、约束、状态机、历史、幂等和账务不变量已登记；新增运营能力有明确数据事实源
    适用性: 是
    证据: catalogs/data_tables.csv;database/schema_dictionary.csv;database/state_machines.yaml
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-08
    类别: 配置与权限
    门禁条件: 相关配置具有控件、单位、范围、依赖、跨字段规则、编辑角色、复核角色、生效预览和回滚；权限与数据分级明确
    适用性: 是
    证据: catalogs/config_registry.csv;catalogs/config_cross_field_rules.csv;catalogs/config_role_matrix.csv
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-09
    类别: 后台运营规格
    门禁条件: 涉及后台页面时，筛选、表格列、排序、批量/行操作、Tab、导出、脱敏、审批、确认和审计已冻结
    适用性: 不适用
    证据: catalogs/admin_page_operation_specs.csv
    当前结论: PASS
    说明: 本版本无后台页面；仍需确认无后台操作遗漏
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-10
    类别: 测试与验收
    门禁条件: 主路径、拒绝、幂等、并发、故障、安全和防漂移测试ID已存在并绑定到页面/动作/需求
    适用性: 是
    证据: catalogs/test_cases.csv
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-11
    类别: 故事与责任
    门禁条件: 本版本工作已拆为可领取的用户故事/工程治理故事，包含责任角色、依赖、页面、接口、数据、配置和验收条件
    适用性: 是
    证据: catalogs/release_story_backlog.csv;releases/P00/STORIES.yaml
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-12
    类别: 外部事项记录
    门禁条件: 需要SDK、数据库、供应商、域名、证书、签名、法务或上线验证的事项已分类到实施/外部/生产门禁；不再误标为开发前文档缺口
    适用性: 是
    证据: DEVELOPMENT_RISK_REGISTER.md;catalogs/development_risk_register.csv
    当前结论: PASS
    说明: 需求14条，页面/交互面0个，接口0个，故事1个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: P00
    门禁ID: P00-DOR-CONTINUITY
    类别: 持续开发无状态接续
    门禁条件: 编码前必须可仅凭仓库恢复任务；唯一Session/Claim、检查点、Commit Trailer、CR、Context Pack和交接协议已启用
    适用性: 是
    证据: .continuity/CONTINUITY_POLICY.yaml;scripts/continuity.py;scripts/continuity_gate.py
    当前结论: PASS
    说明: 对话不是事实源，切换AI必须handoff/takeover或recover
    阻断规则: 未通过不得修改项目或提交
    成熟度: ENFORCED_V1.2.3
  continuity:
    policy: .continuity/CONTINUITY_POLICY.yaml
    status: PASS
    conversation_context_required: false
STORIES.yaml:
  version: 1.2.2
  release: P00
  title: 仓库、环境、契约与无状态接续
  source_of_truth: catalogs/release_story_backlog.csv
  rules:
  - 故事未通过definition_of_ready不得进入编码
  - 页面故事不得增加未登记字段、状态、按钮、接口或配置
  - 工程治理故事负责契约、数据、配置、测试、观测和交接
  stories:
  - story_id: STORY-P00-001
    release: P00
    title: 仓库、环境、契约与无状态接续：契约、数据、配置、测试与交接
    user_story: 作为技术负责人，我需要按冻结的 P00 契约和DoR完成数据、后端、配置、测试、观测与交接，使前端故事能够稳定落地并可追溯。
    platform: CROSS_PLATFORM
    module: 工程治理
    template_id: N/A
    page_ids: []
    operation_ids: []
    api_contracts: []
    requirement_ids:
    - REQ-UI-001
    - REQ-CONT-001
    - REQ-ENG-001
    - REQ-CONTRACT-001
    - REQ-APK-001
    - REQ-ARCH-001
    - REQ-DATA-001
    - REQ-EVENT-001
    - REQ-LEDGER-001
    - REQ-ADMIN-BASE-001
    - REQ-OBS-002
    - REQ-SECRET-001
    - REQ-PAGE-SPEC-001
    - REQ-DOR-001
    config_keys: []
    data_tables:
    - outbox_events
    - inbox_messages
    - ledger_accounts
    - accounting_transactions
    - accounting_entries
    - balance_snapshots
    - reconciliation_runs
    - reconciliation_differences
    - h5_page_configs
    - cms_articles
    - agreements
    - app_versions
    - content_posts
    - app_release_records
    - app_build_artifacts
    - users
    - user_credentials
    - auth_security_challenges
    - user_sessions
    - user_devices
    - sms_verification_codes
    - login_logs
    - app_build_profiles
    - app_build_jobs
    - app_build_job_steps
    - app_signing_profiles
    - reward_accounts
    - reward_ledger
    - reward_settlements
    - reward_freezes
    - payout_accounts
    - withdrawal_requests
    - payout_transactions
    - payout_callbacks
    - payout_reconciliation_records
    - payment_transactions
    - payment_callbacks
    - payment_exception_orders
    - payment_reconciliation_records
    - admin_users
    - admin_roles
    - admin_permissions
    - admin_user_roles
    - admin_role_permissions
    - system_configs
    - system_config_versions
    - feature_flags
    - admin_approval_requests
    - provider_config_definitions
    - provider_config_versions
    - provider_connection_tests
    - provider_certificates
    - provider_certificate_access_logs
    - domain_configs
    - dns_action_items
    - N/A_DOCUMENT_CONTRACT
    test_ids:
    - TST-ADMIN_BASE_001-HAPPY
    - TST-ADMIN_BASE_001-IDEMPOTENT
    - TST-ADMIN_BASE_001-REJECT
    - TST-APK_001-HAPPY
    - TST-APK_001-IDEMPOTENT
    - TST-APK_001-REJECT
    - TST-ARCH_001-HAPPY
    - TST-ARCH_001-IDEMPOTENT
    - TST-ARCH_001-REJECT
    - TST-CONTRACT_001-HAPPY
    - TST-CONTRACT_001-IDEMPOTENT
    - TST-CONTRACT_001-REJECT
    - TST-CONT_001-HAPPY
    - TST-CONT_001-IDEMPOTENT
    - TST-CONT_001-REJECT
    - TST-DATA_001-HAPPY
    - TST-DATA_001-IDEMPOTENT
    - TST-DATA_001-REJECT
    - TST-DOR_001-DRIFT
    - TST-DOR_001-HAPPY
    - TST-DOR_001-REJECT
    - TST-DOR_001-SECURITY
    - TST-ENG_001-HAPPY
    - TST-ENG_001-IDEMPOTENT
    - TST-ENG_001-REJECT
    - TST-EVENT_001-HAPPY
    - TST-EVENT_001-IDEMPOTENT
    - TST-EVENT_001-REJECT
    - TST-GATE-ANDROID-BUILD
    - TST-GATE-BACKEND-BUILD
    - TST-GATE-DB-MIGRATION
    - TST-GATE-LEDGER
    - TST-GATE-MAPPING
    - TST-GATE-MODULE-BOUNDARY
    - TST-GATE-OPENAPI
    - TST-GATE-OUTBOX
    - TST-GATE-SECRETS
    - TST-GATE-STATE
    - TST-GATE-WEB-BUILD
    - TST-LEDGER_001-CONCURRENCY
    - TST-LEDGER_001-HAPPY
    - TST-LEDGER_001-IDEMPOTENT
    - TST-LEDGER_001-RECOVERY
    - TST-LEDGER_001-REJECT
    - TST-OBS_002-HAPPY
    - TST-OBS_002-IDEMPOTENT
    - TST-OBS_002-REJECT
    - TST-PAGE_SPEC_001-DRIFT
    - TST-PAGE_SPEC_001-HAPPY
    - TST-PAGE_SPEC_001-REJECT
    - TST-PAGE_SPEC_001-SECURITY
    - TST-SECRET_001-HAPPY
    - TST-SECRET_001-IDEMPOTENT
    - TST-SECRET_001-REJECT
    - TST-SECRET_001-SECURITY
    - TST-UI_001-HAPPY
    - TST-UI_001-IDEMPOTENT
    - TST-UI_001-REJECT
    - TST-V122-011
    dependencies: []
    owner_roles:
    - tech_lead
    - backend_engineer
    - database_engineer
    - test_engineer
    - devops_engineer
    acceptance_criteria:
    - OpenAPI、状态机、数据库迁移、配置Schema和页面契约引用一致，代码生成不得产生手工分叉
    - 每个需求可追溯到页面/API/表/配置/测试/故事；不适用项必须显式N/A并说明
    - 版本DoR全部PASS后方可编码；出口仍按原RELEASE_MANIFEST和ACCEPTANCE_MATRIX执行
    - 变更必须通过CR，更新唯一事实源并重新生成全部派生目录和哈希
    - 完成Session Log、追踪矩阵、发布证据和下一任务交接
    definition_of_ready: DEFINITION_OF_READY.yaml全部阻断项PASS
    status: READY_FOR_IMPLEMENTATION
  - story_id: STORY-P00-002
    release: P00
    title: 持续开发无状态接续强制门禁落地与演练
    user_story: 作为接手项目的AI或开发者，我需要仅凭仓库领取任务、记录检查点、提交、交接、接管和关闭，以便不依赖任何旧对话稳定继续开发。
    platform: CROSS_PLATFORM
    module: 持续开发治理
    template_id: N/A
    page_ids: []
    operation_ids: []
    api_contracts: []
    requirement_ids:
    - REQ-CONT-001
    - REQ-ENG-001
    - REQ-DOR-001
    config_keys: []
    data_tables:
    - N/A_DOCUMENT_CONTRACT
    test_ids:
    - TST-CONT_001-HAPPY
    - TST-CONT_001-REJECT
    - TST-DOR_001-DRIFT
    dependencies: []
    owner_roles:
    - tech_lead
    - devops_engineer
    - test_engineer
    acceptance_criteria:
    - 唯一ACTIVE Session和Task Claim
    - 项目变化必须检查点和测试证据
    - 每个Commit绑定Task/Session/Checkpoint/Tests/CR
    - WIP可通过Handoff Bundle和takeover无对话接管
    - pre-push和CI逐Commit重验
    - 事件与Manifest篡改可检测
    definition_of_ready: P00 DoR全部适用项PASS且.continuity策略为ENFORCED
    status: READY_FOR_IMPLEMENTATION
TASKS.yaml:
  release: P00
  title: 仓库、环境、契约与无状态接续
  tasks:
  - id: TASK-P00-001
    title: 仓库、环境、契约与无状态接续开发就绪核验、故事领取与变更基线
    status: READY
    depends_on: []
    requirements: &id001
    - REQ-UI-001
    - REQ-CONT-001
    - REQ-ENG-001
    - REQ-CONTRACT-001
    - REQ-APK-001
    - REQ-ARCH-001
    - REQ-DATA-001
    - REQ-EVENT-001
    - REQ-LEDGER-001
    - REQ-ADMIN-BASE-001
    - REQ-OBS-002
    - REQ-SECRET-001
    description: 核验 14 项需求、0 个接口、55 张相关表、0 个页面/交互面和 1 个故事；全部适用DoR必须PASS。
    deliverables:
    - releases/P00/DEFINITION_OF_READY.yaml 全部适用项PASS
    - releases/P00/STORIES.yaml 故事责任人和依赖已领取
    - 更新Release Manifest与CR记录
    - python scripts/check_v122_documentation.py --release P00
    acceptance:
    - 页面、字段、状态、动作、API、配置、数据和测试无TBD
    - 不适用项明确N/A及原因
    - releases/P00/DEFINITION_OF_READY.yaml 全部适用项PASS
    - releases/P00/STORIES.yaml 故事责任人和依赖已领取
    - 更新Release Manifest与CR记录
    - python scripts/check_v122_documentation.py --release P00
    session_log_required: true
  - id: TASK-P00-002
    title: 仓库、环境、契约与无状态接续数据迁移与领域不变量
    status: BLOCKED
    depends_on:
    - TASK-P00-001
    requirements: *id001
    description: 实现/演进outbox_events, inbox_messages, ledger_accounts, accounting_transactions, accounting_entries, balance_snapshots, reconciliation_runs,
      reconciliation_differences，补齐唯一约束、状态历史、幂等键和回滚验证
    deliverables:
    - Flyway前向迁移与空库/升级库测试
    - 不变量属性测试
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - Flyway前向迁移与空库/升级库测试
    - 不变量属性测试
    session_log_required: true
  - id: TASK-P00-003
    title: 仓库、环境、契约与无状态接续后端应用服务与接口
    status: BLOCKED
    depends_on:
    - TASK-P00-002
    requirements: *id001
    description: 实现范围内3个operationId；控制器仅编排应用服务，跨模块副作用写Outbox
    deliverables:
    - OpenAPI契约测试
    - 权限/幂等/错误码/审计覆盖
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - OpenAPI契约测试
    - 权限/幂等/错误码/审计覆盖
    session_log_required: true
  - id: TASK-P00-004
    title: 仓库、环境、契约与无状态接续客户端/H5/后台实现
    status: BLOCKED
    depends_on:
    - TASK-P00-003
    requirements: *id001
    description: 按 0 个页面/交互面的逐页施工规格和 1 个故事实现；禁止从参考图或通用摘要自行发明业务字段和按钮。
    deliverables:
    - 全部页面绑定catalogs/ui_page_specifications.csv
    - 字段/状态/动作/导航与后台运营规格通过契约测试
    - 禁止页面级手写重复DTO
    - 全部动作绑定生成API类型
    acceptance:
    - 所有页面故事验收条件通过
    - 加载、空、局部失败、无权限、404、离线、冲突和成功导航均有证据
    - 全部页面绑定catalogs/ui_page_specifications.csv
    - 字段/状态/动作/导航与后台运营规格通过契约测试
    - 禁止页面级手写重复DTO
    - 全部动作绑定生成API类型
    session_log_required: true
  - id: TASK-P00-005
    title: 仓库、环境、契约与无状态接续专项测试与故障注入
    status: BLOCKED
    depends_on:
    - TASK-P00-003
    - TASK-P00-004
    requirements: *id001
    description: 执行50项测试，覆盖重复请求、并发、超时、消息重复和供应商异常
    deliverables:
    - 测试报告和失败证据归档
    - 关键缺陷清零
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - 测试报告和失败证据归档
    - 关键缺陷清零
    session_log_required: true
  - id: TASK-P00-006
    title: 仓库、环境、契约与无状态接续可观测性与预发布验收
    status: BLOCKED
    depends_on:
    - TASK-P00-005
    requirements: *id001
    description: 部署Staging，验证结构化日志、TraceId、RED指标、业务指标、告警和回滚
    deliverables:
    - 运行手册演练
    - 验收矩阵签字
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - 运行手册演练
    - 验收矩阵签字
    session_log_required: true
  - id: TASK-P00-007
    title: 仓库、环境、契约与无状态接续Android测试APK与产物追溯
    status: BLOCKED
    depends_on:
    - TASK-P00-006
    requirements: *id001
    description: 使用固定工具链构建、签名、安装冒烟并生成APK_MANIFEST
    deliverables:
    - APK可下载/可安装
    - SHA256、Commit、versionName/versionCode、测试结果齐全
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - APK可下载/可安装
    - SHA256、Commit、versionName/versionCode、测试结果齐全
    session_log_required: true
  - id: TASK-P00-008
    title: 仓库、环境、契约与无状态接续版本关闭与无状态交接
    status: BLOCKED
    depends_on:
    - TASK-P00-007
    requirements: *id001
    description: 回填追踪矩阵、Session Log、问题登记、可复用模式、CURRENT_STATUS和NEXT_TASK
    deliverables:
    - 全新AI仅凭仓库可继续
    - 工作区干净且Tag可追溯
    acceptance:
    - 无TODO/生产Mock
    - 代码、文档、测试、追踪同步更新
    - 全新AI仅凭仓库可继续
    - 工作区干净且Tag可追溯
    session_log_required: true
  version: 1.2.2
  definition_of_ready: releases/P00/DEFINITION_OF_READY.yaml
  story_backlog: releases/P00/STORIES.yaml
  execution_rule: TASKS定义治理顺序，STORIES定义可领取纵向工作；二者必须同时满足，不得以通用任务替代页面故事验收。
```

## 开放 CR

```yaml
[]
```

## 上下文来源及哈希

- `AGENTS.md` — `387c1057c4698600a2340d17274664824018fd16c9218ed86222eeef8c77b440`
- `START_HERE.md` — `1b2dd0ea2da0c1f37bd9d5387e62e865052b45ad7e0b8ce7d75ee18efdce5afc`
- `CURRENT_STATUS.yaml` — `66455bd172372e9a826ca1bd8ca9da3a7d6c7857d9206c729cbd1217124a805e`
- `NEXT_TASK.yaml` — `4c5cb5e8a18378f7dc0c8377dea7a6fb1c9bf163031900e0491c0661951a4ec2`
- `DEVELOPMENT_RISK_REGISTER.md` — `7b5b054b6c9968bedf1ee9dbcd699394dd6a260ce35529e4d2fc9842e7f737bf`
- `docs/00-baseline/SOURCE_OF_TRUTH.md` — `045624e036f03cf5668982159cbdb433a63f7397475a8a4592ae5255f9ddc511`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml` — `aaef356cefcf221271ae4a2373923018e545511b0a92fcd3d14d43454f5f96ef`
- `docs/03-continuity/REUSABLE_PATTERNS.md` — `402b6f205aaa81ce2e936c31eb8a3f026c5fec324f50713899996a1c69d1f5e1`
- `docs/03-continuity/PITFALLS.md` — `ddd7ab31a638763a1e880c3e46c33f2ca20c1c75eb8469366346e03272082f30`
- `.continuity/CONTINUITY_POLICY.yaml` — `9dbcba88f0f2a0ac824fa8f53d693e8c38e661103e6603b88484a43f30446923`
- `.continuity/EVENT_LOG.jsonl` — `57a9d33852fa6eda82b9c240512dc888b21e19c98dccccefb7c5396134ced31b`
- `.continuity/SESSION_INDEX.yaml` — `2eea76e22b4ec8ed65e4d16c9b064bbaaca207e05dbefda0bf700a2c81cb0cee`
- `.continuity/TASK_CLAIMS.yaml` — `3a9aa7f7d64133f1a6dc48c8df4278dd9534252ad4bbe72f2f3527b02b095171`
- `.continuity/TASK_TRANSITIONS.yaml` — `721ed669a5c38218812d9d201d364c948f2b7ca0290f74d80409481e93f99f54`
- `.continuity/CHANGE_REQUEST_INDEX.yaml` — `d88d440ad4c0d76d9cf8d21831334085a28ae8ddcfc6622cbe020aa60c5942f2`
- `.continuity/ACTIVE_SESSION.yaml` — `e86cc4872fb029fdb33c200ec4b6d0ab99c53698c4dedabc82149e6c1f01c0a4`
- `releases/P00/RELEASE_MANIFEST.yaml` — `19a6e46c3a0086f12ac7aa95be79263f9fd1f7a56e5d1bc39eabd5fa82a963d7`
- `releases/P00/DEFINITION_OF_READY.yaml` — `ffe6940f64abc0bef4ca56719a9e442d86bf37460e7166acc00a70a893906cd9`
- `releases/P00/STORIES.yaml` — `56468c7be5472b108bdebfb4e79bb937912e2b22da0504cf6857c4cc54104c7d`
- `releases/P00/TASKS.yaml` — `1c75426b97e619eebc2763ea3eead3199ad5d0869025faa6f93eea3e57f6df75`
- `releases/P00/ACCEPTANCE_MATRIX.csv` — `19a71fd0a8386cd25417d38df7430a74600b6a277b40f6c6b706faae122e5ccf`
- `docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md` — `07ae0ac7ab6a38538982f13e514ab9ca154cfb4152f8ae4f3b18e20632c92719`

## 接手硬规则

1. 先运行精确恢复命令，不得直接编辑。
2. 不得要求用户重新输入仓库已有需求。
3. 所有变更必须在活跃会话、任务和故事范围内。
4. 每次上下文切换、关键测试、提交和交接前必须创建检查点。
5. 冻结事实变化必须关联已批准 CR。
