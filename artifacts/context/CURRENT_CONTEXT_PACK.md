# CURRENT CONTEXT PACK · 无对话接续上下文

- 生成时间：2026-07-18T22:48:40Z
- Context Hash：`abd072f66c5317d77c5d8c76542149b5c4da0a5fff71f441f269f47b977f8c5c`
- 对话依赖：`PROHIBITED`
- 事实源：`REPOSITORY_ONLY`
- 精确恢复命令：

```bash
python3 scripts/continuity.py checkpoint --summary '<完成内容>' --next-step '<下一步>' --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'
```

## 当前状态

```yaml
project: hhy-pro-platform
baseline_version: 1.2.3
phase: R03
active_release: R03
active_task: TASK-R03-007
status: IN_PROGRESS
documentation_status: ZERO_BLOCKING_DOCUMENT_GAPS
last_green_commit: db3cfddcc4cda84265ec18f24de972804d62a08f
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
- TASK-P00-001
- TASK-P00-002
- TASK-P00-003
- TASK-P00-004
- TASK-P00-005
- TASK-P00-006
- TASK-P00-007
- TASK-P00-008
- TASK-R01-001
- TASK-R01-002
- TASK-R01-003
- TASK-R01-004
- TASK-R01-005
- TASK-R01-006
- TASK-R01-007
- TASK-R01-008
- TASK-R02-001
- TASK-R02-002
- TASK-R02-003
- TASK-R02-004
- TASK-R02-005
- TASK-R02-006
- TASK-R03-001
- TASK-R03-002
- TASK-R03-003
- TASK-R03-004
- TASK-R03-005
- TASK-R03-006
in_progress_tasks:
- TASK-R03-007
blocked_tasks:
- TASK-R02-007
- TASK-R03-007
next_task: TASK-R03-007
updated_at: '2026-07-18T22:48:38Z'
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
  active_session_id: SES-20260718T200607Z-3569D212
  actor_id: codex-root
  story_id: STORY-R03-004
  lease_expires_at: '2026-07-19T02:48:38Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T200607Z-3569D212/0010.yaml
  project_fingerprint: f4bd8ed068e019a4aa7cded0eca1c9607617f2e3c44a3aefcc285cb233fdae93
  context_pack:
    yaml: artifacts/context/CURRENT_CONTEXT_PACK.yaml
    markdown: artifacts/context/CURRENT_CONTEXT_PACK.md
    manifest: artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
    context_hash: c167902a05b516ca79caab396e55582b1ff7c6d15b1761ee82f366db16831166
    generated_at: '2026-07-18T22:46:52Z'
  handoff_bundle: null
```

## 默认并行规则

```yaml
authorization:
  status: PROJECT_OWNER_STANDING_AUTHORIZATION
  granted_on: '2026-07-18'
  granted_at: '2026-07-17T22:16:06Z'
  scope: ALL_R02_R32_DEVELOPMENT
default_delegation_mode: AUTO_WHEN_SAFE_PARALLEL_WORK_EXISTS
review_triggers:
- TASK_START
- SCOPE_CHANGE
per_task_user_confirmation_required: false
non_delegation_requires_checkpoint_reason: true
capability_fallback: RECORD_LIMITATION_AND_DO_NOT_FABRICATE_PARALLEL_EVIDENCE
user_override_allowed: true
authoritative_active_sessions: 1
max_delegated_workers: 3
worker_workspace: ISOLATED_SCRATCH_WORKTREE
require_disjoint_path_leases: true
worker_may_modify_continuity_records: false
worker_may_commit_or_push: false
worker_output:
- patch
- changed_paths
- module_test_evidence
integration_owner: ACTIVE_SESSION_ACTOR
coordinator_serial_review_domains:
- FUNDS
- SECURITY
- STATE_MACHINE
- CONCURRENCY
- DESTRUCTIVE_MIGRATION
- FINAL_INTEGRATION
source_of_truth_rule: 受托执行代理的scratch worktree不是事实源；主控验证范围与证据并应用到事实分支后才形成项目变化。
```

## 模型路由与云端既有环境

```yaml
execution_routing_policy: complex_or_high_risk:
  model: Sol
  reasoning_effort: high
medium:
  model: Terra
  reasoning_effort: medium
lightweight_or_mechanical_or_read_only:
  model: Luna
  reasoning_effort: low
  unavailable_fallback:
    model: Terra
    reasoning_effort: low
    audit_status: LUNA_UNAVAILABLE_FALLBACK
    prohibition: 不得宣称已使用 Luna
development_runtime: version: 1
purpose: 跨电脑、跨 AI 的模型分级和云端既有开发环境门禁
model_routing:
  complex_or_high_risk:
    model: Sol
    reasoning_effort: high
  medium:
    model: Terra
    reasoning_effort: medium
  lightweight_or_mechanical_or_read_only:
    model: Luna
    reasoning_effort: low
    unavailable_fallback:
      model: Terra
      reasoning_effort: low
      audit_status: LUNA_UNAVAILABLE_FALLBACK
      prohibition: 不得宣称已使用 Luna
cloud_environment:
  default_assumption: CODEX_ALREADY_CONNECTED_UNLESS_USER_DECLARES_DISCONNECTED
  user_declaration_required_to_override: true
  ssh_alias: obx-test
  startup_preflight_required: true
  preflight_failure: BLOCK_DEVELOPMENT_AND_REPORT
  android:
    execution_location: REMOTE_CONTAINER_ONLY
    image: hhy-android-toolchain:r01-46fb273
    image_id: sha256:97a5b2d7ae4d6c4abab0c985b502597d0612f3a1e0941fd842008e30a4632607
    gradle_cache: hhy-r01-android-gradle-cache
    required_commands:
    - testDebugUnitTest
    - lintDebug
    - assembleDebug
  prohibited:
  - LOCAL_ANDROID_SDK_BOOTSTRAP
  - LOCAL_ANDROID_SDK_REBUILD
  - REMOTE_ANDROID_IMAGE_REBUILD
  - REMOTE_GRADLE_CACHE_REBUILD
```

## 仓库传输恢复

```yaml
schema_version: 1
repository:
  name: hhy
  canonical_url: https://github.com/fei613293175/hhy.git
  remote_name: origin
  default_branch: main
security:
  prohibit_url_credentials: true
  prohibit_force_push: true
  tracked_secrets_forbidden:
  - personal_access_token
  - ssh_private_key
  - ssh_identity_path
  - credential_helper_output
  - .git/config
branch_policy:
  allowed_patterns:
  - main
  - master
  - task/*
  - release/*
  - hotfix/*
  upstream_remote: origin
  upstream_merge_template: refs/heads/{branch}
recovery:
  bundle_filename: repository.bundle
  require_bundle_verification: true
  raw_source_without_git_or_bundle: BLOCK
  repair_mismatched_remote_requires_explicit_flag: true
push_preflight:
  fetch_before_compare: true
  require_clean_worktree: true
  require_exact_remote_url: true
  require_exact_upstream: true
  reject_behind: true
  reject_diverged: true
  allow_new_remote_branch: true
```

## 下一任务

```yaml
id: TASK-R03-007
title: 外部激活证据、Android回归APK与产物追溯
status: BLOCKED
release: R03
requirements:
- REQ-CONFIG-001
- REQ-DOMAIN-001
- REQ-STORAGE-001
- REQ-SECRET-001
- REQ-CONFIG-UX-001
- REQ-APK-001
depends_on:
- TASK-R03-006
definition_of_ready: releases/R03/DEFINITION_OF_READY.yaml
stories: releases/R03/STORIES.yaml
steps:
- 外部激活清单、连接报告、SecretRef和证书指纹
- DNS、TLS、服务健康三层状态和责任人
- APK Commit、versionName、versionCode、签名指纹、四方SHA和桌面副本
acceptance:
- 每项外部依赖为VERIFIED或具备负责人、截止版本和阻断状态
- 连接测试失败不得激活对应配置版本
- APK机器交付PASS和项目所有者真机PASS分别记录；真机反馈前保持PENDING
claim_required: true
start_command: python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-R03-007
commands:
  resume: python3 scripts/continuity.py resume
  start: python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-R03-007
  checkpoint: python3 scripts/continuity.py checkpoint --summary '<阶段完成>' --next-step '<精确下一步>' --test 'name|PASS|evidence|note' --parallel-assessment
    <ASSESSMENT> --parallel-reason '<未委托原因>'
  handoff: python3 scripts/continuity.py handoff --actor <ACTOR_ID> --reason '<移交原因>' --next-step '<精确下一步>'
  export_clean: python3 scripts/continuity.py export-clean --portable-zip <OUTPUT.zip>
  cr_amend: python3 scripts/continuity.py cr-amend --actor <ACTOR_ID> --cr <CR_ID> --original-rule '<原规则>' --new-rule '<新规则>' --impact-summary
    '<影响摘要>' --migration-and-compatibility '<迁移兼容说明>' --file <PATH> --test '<TEST>' --release <RELEASE>
next_after: 由当前TASKS.yaml依赖关系决定
blocker: R03机器实现、测试、外部清单和固定签名APK四方交付均PASS；等待项目所有者对hhy-r03-3a913c9-debug.apk完成真机安装、启动与自动验证码路径验收
resume_command: python3 scripts/continuity.py start --actor <ACTOR_ID> --task TASK-R03-007
```

## 活跃会话

```yaml
protocol_version: '1.0'
package_version: 1.2.3
session_id: SES-20260718T200607Z-3569D212
status: ACTIVE
actor:
  id: codex-root
  kind: AI_OR_HUMAN
  host: unknown
release: R03
task_id: TASK-R03-007
story_id: STORY-R03-004
goal: ??BLOCKED?????????R02???????????????????
started_at: '2026-07-18T20:06:07Z'
updated_at: '2026-07-18T22:48:38Z'
takeover_of: null
change_requests:
- CR-0051
- CR-0052
- CR-0053
- CR-0054
- CR-0055
- CR-0056
- CR-0057
- CR-0058
- CR-0059
- CR-0060
- CR-0061
- CR-0062
- CR-0063
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  approved_exceptions:
  - scripts/continuity.py
  - tests/test_continuity_blocked_resume.py
  - .continuity/CONTINUITY_POLICY.yaml
  - docs/03-continuity/REUSABLE_PATTERNS.md
  - docs/03-continuity/PROBLEM_REGISTRY.yaml
  - docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
  - scripts/check_r02_security_challenge_design_package.py
  - tests/test_r02_security_challenge_design_package.py
  - templates/AGENTS.md
  - artifacts/validation/continuity-integration-v1.2.3.json
  - artifacts/validation/continuity-integration-v1.2.3.log
  - artifacts/validation/continuity-lifecycle-integration-v1.2.3.json
  - artifacts/validation/continuity-lifecycle-integration-v1.2.3.log
  - CHANGELOG.md
  - AGENTS.md
  - START_HERE.md
  - docs/00-baseline/正式商业系统全局硬性开发边界.md
  - docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
  - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - docs/02-ui/页面施工规格总索引_V1.2.2.md
  - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
  - design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
  - design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
  - design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
  - design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
  - design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
  - design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
  - design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
  - design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
  - design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
  - scripts/check_commercial_ui_boundaries.py
  - tests/test_commercial_ui_boundaries.py
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
  - services/backend/boot/src/main/resources/application.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
  - contracts/openapi.yaml
  - services/backend/boot/src/main/resources/contracts/openapi.yaml
  - packages/api-client/src/generated/openapi.yaml
  - tests/test_r02_auth_slice_contract.py
  - config/PROBLEM_REGISTRY.yaml
  - releases/R02/R02_AUTH_HOTFIX_20260719.md
  - docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  - .continuity/change_requests/CR-0054.yaml
  - .continuity/CHANGE_REQUEST_INDEX.yaml
  - apps/admin-web/src/components/StatusNotice.vue
  - apps/admin-web/src/views/AdminLoginPage.vue
  - apps/admin-web/src/views/AdminMfaPage.vue
  - apps/admin-web/src/views/AdminUserDetailPage.vue
  - apps/admin-web/src/views/AdminUsersListPage.vue
  - apps/admin-web/src/views/AdminSecurityPage.vue
  - apps/admin-web/src/views/DomainConfigPage.vue
  - apps/admin-web/src/views/ProviderConfigPage.vue
  - apps/admin-web/src/components/ProviderCertificatePanel.vue
  - apps/admin-web/src/r01Pages.test.ts
  - apps/admin-web/src/r03DomainPage.test.ts
  - apps/admin-web/src/r03ProviderPage.test.ts
  - docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  - .continuity/change_requests/CR-0055.yaml
  - services/backend/boot/src/main/resources/application.yml
  - docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  - .continuity/change_requests/CR-0056.yaml
  - packages/api-client/src/client.generated.ts
  - docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  - .continuity/change_requests/CR-0057.yaml
  - apps/h5/src/views/InviteRegistrationPage.vue
  - apps/h5/src/services/inviteRegistration.ts
  - apps/h5/src/views/InviteRegistrationPage.test.ts
  - apps/h5/src/services/inviteRegistration.test.ts
  - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  - .continuity/change_requests/CR-0058.yaml
  - apps/admin-web/src/components/AuthShell.vue
  - docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  - .continuity/change_requests/CR-0059.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
  - contracts/contract_status.csv
  - design/tokens/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - packages/design-tokens/h5.css
  - packages/design-tokens/admin.css
  - scripts/check_ui_tokens.py
  - scripts/check_main_doc.py
  source: story+explicit+approved-cr:CR-0051+approved-cr:CR-0052+approved-cr:CR-0053+approved-cr:CR-0054+approved-cr:CR-0055+approved-cr:CR-0056+approved-cr:CR-0057+approved-cr:CR-0058+approved-cr:CR-0059+approved-cr:CR-0060+approved-cr:CR-0061+approved-cr:CR-0062+approved-cr:CR-0063
git:
  initialized: true
  branch: task/TASK-R03-001
  base_commit: 18e2db65b65afeb1e69d0c382ab53963fc97adec
  start_head: 18e2db65b65afeb1e69d0c382ab53963fc97adec
  upstream: origin/task/TASK-R03-001
  initial_worktree_state: DIRTY_TAKEOVER
lease:
  duration_minutes: 240
  renewed_at: '2026-07-18T22:48:38Z'
  expires_at: '2026-07-19T02:48:38Z'
checkpoint_sequence: 10
latest_checkpoint: .continuity/checkpoints/SES-20260718T200607Z-3569D212/0010.yaml
session_log: docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md
next_step: 使用绑定Python执行提交钩子，提交推送后部署测试邀请码并构建交付桌面APK。
context_pack: THIS_CONTEXT_PACK
handoff_bundle: null
closure: null
parallel_execution:
  assessment: NO_SAFE_PARALLEL
  delegated_workers: 0
  workers: []
  reason: 提交、推送、部署和APK构建是严格前后依赖。
```

## 最新检查点

```yaml
protocol_version: '1.0'
checkpoint_id: CP-SES-20260718T200607Z-3569D212-0010
session_id: SES-20260718T200607Z-3569D212
sequence: 10
created_at: '2026-07-18T22:48:37Z'
summary: R02认证热修复实现、全量测试、冻结包校验及连续性演练通过；提交前文档行尾规范已清理。
next_step: 使用绑定Python执行提交钩子，提交推送后部署测试邀请码并构建交付桌面APK。
blockers: []
decisions: []
note: ''
tests:
- name: continuity-strict
  result: PASS
  evidence: check_v123_continuity --strict
  note: all 42 rules passed
- name: design-package
  result: PASS
  evidence: 35 hashes and 23 changes verified
  note: frozen package read-only
- name: diff-check
  result: PASS
  evidence: trailing whitespace cleaned
  note: commit format gate ready
git:
  initialized: true
  branch: task/TASK-R03-001
  head: 655e1eebbdd2df3ce1f527b7d77601826cb11c55
  upstream: origin/task/TASK-R03-001
  ahead: 0
  behind: 0
  dirty: true
  status_porcelain:
  - M  .continuity/ACTIVE_SESSION.yaml
  - M  .continuity/CHANGE_REQUEST_INDEX.yaml
  - M  .continuity/CONTINUITY_POLICY.yaml
  - M  .continuity/EVENT_LOG.jsonl
  - M  .continuity/SESSION_INDEX.yaml
  - M  .continuity/STATE.yaml
  - A  .continuity/change_requests/CR-0054.yaml
  - A  .continuity/change_requests/CR-0055.yaml
  - A  .continuity/change_requests/CR-0056.yaml
  - A  .continuity/change_requests/CR-0057.yaml
  - A  .continuity/change_requests/CR-0058.yaml
  - A  .continuity/change_requests/CR-0059.yaml
  - A  .continuity/change_requests/CR-0060.yaml
  - A  .continuity/change_requests/CR-0061.yaml
  - A  .continuity/change_requests/CR-0062.yaml
  - A  .continuity/change_requests/CR-0063.yaml
  - A  .continuity/checkpoints/SES-20260718T200607Z-3569D212/0008.yaml
  - A  .continuity/checkpoints/SES-20260718T200607Z-3569D212/0009.yaml
  - M  .continuity/sessions/SES-20260718T200607Z-3569D212.yaml
  - M  AGENTS.md
  - M  CHANGELOG.md
  - M  CURRENT_STATUS.yaml
  - M  START_HERE.md
  - M  apps/admin-web/src/components/AuthShell.vue
  - M  apps/admin-web/src/components/ProviderCertificatePanel.vue
  - M  apps/admin-web/src/components/StatusNotice.vue
  - M  apps/admin-web/src/r01Pages.test.ts
  - M  apps/admin-web/src/views/AdminSecurityPage.vue
  - M  apps/admin-web/src/views/AdminUserDetailPage.vue
  - M  apps/admin-web/src/views/AdminUsersListPage.vue
  - M  apps/admin-web/src/views/DomainConfigPage.vue
  - M  apps/admin-web/src/views/ProviderConfigPage.vue
  - M  apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - M  apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - M  apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - M  apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - M  apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - M  apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - M  apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - M  apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - M  apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - M  apps/h5/src/services/inviteRegistration.test.ts
  - M  apps/h5/src/views/InviteRegistrationPage.test.ts
  - M  apps/h5/src/views/InviteRegistrationPage.vue
  - M  artifacts/context/CURRENT_CONTEXT_PACK.md
  - M  artifacts/context/CURRENT_CONTEXT_PACK.yaml
  - M  artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
  - M  artifacts/validation/continuity-integration-v1.2.3.json
  - M  artifacts/validation/continuity-lifecycle-integration-v1.2.3.json
  - M  artifacts/validation/continuity-lifecycle-integration-v1.2.3.log
  - M  artifacts/validation/project-doctor-v1.2.3.json
  - M  catalogs/change_request_index.csv
  - M  catalogs/session_index.csv
  - M  contracts/contract_status.csv
  - M  contracts/openapi.yaml
  - A  design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
  - A  design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
  - A  design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
  - A  design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
  - A  design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
  - A  design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
  - A  design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
  - A  design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
  - A  design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
  - A  design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
  - A  design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
  - M  design/tokens/hhy_design_tokens_v1.2.2.json
  - AM docs/00-baseline/正式商业系统全局硬性开发边界.md
  - M  docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
  - M  docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - AM docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - M  docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - M  docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - M  docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - M  docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - M  docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - M  docs/02-ui/页面施工规格总索引_V1.2.2.md
  - M  docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  - M  docs/03-continuity/PROBLEM_REGISTRY.yaml
  - A  docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  - A  docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  - A  docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  - A  docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  - A  docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  - A  docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  - A  docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
  - A  docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
  - A  docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
  - A  docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
  - M  docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md
  - M  packages/api-client/src/client.generated.ts
  - M  packages/design-tokens/admin.css
  - M  packages/design-tokens/h5.css
  - A  releases/R02/R02_AUTH_HOTFIX_20260719.md
  - A  scripts/check_commercial_ui_boundaries.py
  - M  scripts/check_main_doc.py
  - M  scripts/check_r02_security_challenge_design_package.py
  - M  scripts/check_ui_tokens.py
  - A  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
  - M  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
  - M  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
  - M  services/backend/boot/src/main/resources/application.yml
  - M  services/backend/boot/src/main/resources/contracts/openapi.yaml
  - M  services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
  - M  services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
  - M  templates/AGENTS.md
  - M  templates/START_HERE.md
  - A  tests/test_commercial_ui_boundaries.py
  - M  tests/test_r02_auth_slice_contract.py
  - M  tests/test_r02_security_challenge_design_package.py
  recent_commits:
  - "655e1eebbdd2df3ce1f527b7d77601826cb11c55\t2026-07-19T04:50:09+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): restore desktop\
    \ apk evidence"
  - "ab17752eabb6db73f0438fd5048ec45ef0247921\t2026-07-19T04:46:42+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close resume\
    \ safety changes"
  - "30a397bf2f2dbffbde976cbb93f6f3b216eb16a4\t2026-07-19T04:45:06+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] fix(continuity): resume blocked\
    \ work safely"
  - "18e2db65b65afeb1e69d0c382ab53963fc97adec\t2026-07-19T01:25:54+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close TASK-R03-007\
    \ as blocked"
  - "16023e53e214d0f82486783578657814f4671f85\t2026-07-19T01:24:12+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): sign external\
    \ activation gate"
  - "5cdfd38e50c563b345203b1255dc7fb67e618c46\t2026-07-19T01:23:07+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] test(r03): deliver regression\
    \ apk evidence"
  - "3a913c95265f93b815042389d573f4804137c0c0\t2026-07-19T01:05:16+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): freeze external\
    \ and apk baseline"
  - "25d8171c76e0cef323a717ad66ac85ccd69371c3\t2026-07-19T00:57:28+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close TASK-R03-006\
    \ as completed"
project_fingerprint:
  sha256: f4bd8ed068e019a4aa7cded0eca1c9607617f2e3c44a3aefcc285cb233fdae93
  files:
  - AGENTS.md
  - CHANGELOG.md
  - START_HERE.md
  - apps/admin-web/src/components/AuthShell.vue
  - apps/admin-web/src/components/ProviderCertificatePanel.vue
  - apps/admin-web/src/components/StatusNotice.vue
  - apps/admin-web/src/r01Pages.test.ts
  - apps/admin-web/src/views/AdminSecurityPage.vue
  - apps/admin-web/src/views/AdminUserDetailPage.vue
  - apps/admin-web/src/views/AdminUsersListPage.vue
  - apps/admin-web/src/views/DomainConfigPage.vue
  - apps/admin-web/src/views/ProviderConfigPage.vue
  - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - apps/h5/src/services/inviteRegistration.test.ts
  - apps/h5/src/views/InviteRegistrationPage.test.ts
  - apps/h5/src/views/InviteRegistrationPage.vue
  - contracts/contract_status.csv
  - contracts/openapi.yaml
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
  - design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
  - design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
  - design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
  - design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
  - design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
  - design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
  - design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
  - design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
  - design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
  - design/tokens/hhy_design_tokens_v1.2.2.json
  - docs/00-baseline/正式商业系统全局硬性开发边界.md
  - docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
  - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
  - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - docs/02-ui/页面施工规格总索引_V1.2.2.md
  - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  - docs/03-continuity/PROBLEM_REGISTRY.yaml
  - docs/03-continuity/REUSABLE_PATTERNS.md
  - docs/03-continuity/change-requests/CR-0051-修复BLOCKED任务接续命令死锁并登记非阻断设计回传.md
  - docs/03-continuity/change-requests/CR-0052-同步连续性模板并重生成协议验证报告.md
  - docs/03-continuity/change-requests/CR-0053-补齐非阻断设计接入与连续性修复Changelog.md
  - docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  - docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  - docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  - docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  - docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  - docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  - docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
  - docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
  - docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
  - docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
  - packages/api-client/src/client.generated.ts
  - packages/design-tokens/admin.css
  - packages/design-tokens/h5.css
  - releases/R02/R02_AUTH_HOTFIX_20260719.md
  - scripts/check_commercial_ui_boundaries.py
  - scripts/check_main_doc.py
  - scripts/check_r02_security_challenge_design_package.py
  - scripts/check_ui_tokens.py
  - scripts/continuity.py
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
  - services/backend/boot/src/main/resources/application.yml
  - services/backend/boot/src/main/resources/contracts/openapi.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
  - templates/AGENTS.md
  - templates/START_HERE.md
  - tests/test_commercial_ui_boundaries.py
  - tests/test_continuity_blocked_resume.py
  - tests/test_r02_auth_slice_contract.py
  - tests/test_r02_security_challenge_design_package.py
  file_count: 87
  payload:
    base_commit: 18e2db65b65afeb1e69d0c382ab53963fc97adec
    files:
    - path: AGENTS.md
      state: FILE
      size: 7562
      sha256: fe60549fb106fb4371d39eca6a583191a6661cadaa6b4b7d6ccb44c2b58473db
    - path: CHANGELOG.md
      state: FILE
      size: 31770
      sha256: 4e4f33cb325d1ef024395ffdb617569c8b2fbbcca7b342bedace7e17808eb45a
    - path: START_HERE.md
      state: FILE
      size: 3477
      sha256: 038f713d50267cc0c1598d2399f13ee5ca9ad82bc03311747f3c3ef7f2ae232a
    - path: apps/admin-web/src/components/AuthShell.vue
      state: FILE
      size: 1185
      sha256: 37c8b2602087379193a89121ef8ed82863da9bda84de008be4c9975d02a10266
    - path: apps/admin-web/src/components/ProviderCertificatePanel.vue
      state: FILE
      size: 10452
      sha256: 50e16696e9f151abd819b106f368e022759c5b7407c52e7883feb59c8ef7f600
    - path: apps/admin-web/src/components/StatusNotice.vue
      state: FILE
      size: 507
      sha256: d481f8e59d123b134434888cd156741aabfe4427c7cb23cd075a12a4c81ccac5
    - path: apps/admin-web/src/r01Pages.test.ts
      state: FILE
      size: 21713
      sha256: e34fb53da035233396170e26c3e8d46f7a056d83fb78b4b20ba202b267914dee
    - path: apps/admin-web/src/views/AdminSecurityPage.vue
      state: FILE
      size: 19616
      sha256: 15a237bd11e68beb7460b16301ed288022d0aa7ee330b2898d78fda8767a2c80
    - path: apps/admin-web/src/views/AdminUserDetailPage.vue
      state: FILE
      size: 14109
      sha256: d5d13159aa8efc11d5f2d833467028fffd533cbde24c6ed7c8e5d582a6e31921
    - path: apps/admin-web/src/views/AdminUsersListPage.vue
      state: FILE
      size: 7823
      sha256: 5d3c8d2d16c05dd8bc126854a2ef41af5631e30a6019ff43aeadddf52377f569
    - path: apps/admin-web/src/views/DomainConfigPage.vue
      state: FILE
      size: 15096
      sha256: 11b488670e6abaf8458bb7bb00b5ad54cf1f01beb6cff79e3391fa007e5acd44
    - path: apps/admin-web/src/views/ProviderConfigPage.vue
      state: FILE
      size: 24637
      sha256: 228e853280771db6392ad2cc95dce654b5cfc4745c6befb0707af6db5abd26e9
    - path: apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
      state: FILE
      size: 6193
      sha256: 5e79cdc6dc8dddb216ad5b112c2c95ec3af30bbed47b81e06cecbcacfc86dffa
    - path: apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
      state: FILE
      size: 2584
      sha256: 3a6fe59078489c5a42d2f8fcbddf1dda40cfa5b920fc154fa0e8aa170b5ad621
    - path: apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
      state: FILE
      size: 6058
      sha256: 9eb361a3aac3448201848e1fd7feeaa0f8e1595a964baa97242fd374e910307a
    - path: apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
      state: FILE
      size: 16831
      sha256: 88b1bee21798941819a178950a0a211e01c35516230d93626a38f6a2afa2a557
    - path: apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
      state: FILE
      size: 10077
      sha256: 82b4d087adab4d4d13acefb1e7839c639236262356a1e38d73100eb7f22977e4
    - path: apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
      state: FILE
      size: 825
      sha256: 3ebb3b116f83fd02e24b278fd19c6c6ac01083b14fef1a2382989a2c23b2c6e6
    - path: apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
      state: FILE
      size: 54003
      sha256: 4a8204f295d6239fd24e023248b047434afff7b41b10338a55e7f27b67cf6d82
    - path: apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
      state: FILE
      size: 884
      sha256: dfad8a3496d9713680c64113d2688c50be8995788843234a9b853087d26beb80
    - path: apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
      state: FILE
      size: 5292
      sha256: ea6480d20a8dc47987fea175dac93d8ab6224ae3e4f5fc38c3b7f2b9dad4b5d2
    - path: apps/h5/src/services/inviteRegistration.test.ts
      state: FILE
      size: 3876
      sha256: d1708c53a54f6d2a57d90e1d56798b5180b4315e92e5e5b414f5f97b9edb338a
    - path: apps/h5/src/views/InviteRegistrationPage.test.ts
      state: FILE
      size: 5175
      sha256: 94504517996f705a3feb9b0350ef21074d42ef0fb13b1b5c909bf150e799d8a4
    - path: apps/h5/src/views/InviteRegistrationPage.vue
      state: FILE
      size: 12176
      sha256: 92ef0dca8f59c69d050b8a2e1adc037f6859b49f693082abf5b70eab92832188
    - path: contracts/contract_status.csv
      state: FILE
      size: 139188
      sha256: 98fcaf2e72706b2b4ec6a7659edbf58434089a0948a884caeed0d9f9d1f00752
    - path: contracts/openapi.yaml
      state: FILE
      size: 591151
      sha256: 591a160ffb96ec075ffbc8aa5c306f2abeaba843bd675b6fe02eb0efd7f5bb7a
    - path: design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
      state: FILE
      size: 1001984
      sha256: f2e6edf161ae4cc0a8f48d35971339744930dccc9f2e8b99e5ea5c27adebdee7
    - path: design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
      state: FILE
      size: 6167
      sha256: 29bcc72e188a3dc28325ec47b0ece5a3ea51e8fe633f37c7c9150a10904f04cf
    - path: design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
      state: FILE
      size: 1422
      sha256: 35b1549411755a027d36b26c41dc960f9fd5e93b336ac98afb75fafc2ee1eada
    - path: design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
      state: FILE
      size: 119975
      sha256: eacc805f6893da22a0a5e1613407c53aa4589cc7187f0dde134466ef1537da9c
    - path: design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
      state: FILE
      size: 131822
      sha256: e3037d8725979225b6fc0544e1537b9ac4461aa0421fbbd2aa21285d4109aa82
    - path: design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
      state: FILE
      size: 162949
      sha256: 1b3ad9ade36ec143aca8b67ac94321767d306aec01fe96fa759bf390400c9ab0
    - path: design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
      state: FILE
      size: 160038
      sha256: 5945bc2f7bcb1f0414b89eb1cba932e440052f8c087ba2aa403d83564c3dde0b
    - path: design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
      state: FILE
      size: 162601
      sha256: f955446654f43fb4f68c17bdfcb9b0a354094b555752fcb346af02b2771e9035
    - path: design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
      state: FILE
      size: 162073
      sha256: 39a2f9f12d3478b4b41fee4692e90e8f263c7863ffc13a92e9ae6941e2be1cbe
    - path: design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
      state: FILE
      size: 119319
      sha256: 9d6b81b8feb2801fb4800ec808d1e269ff28e3997310a1e3a2226a36175d2ce9
    - path: design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
      state: FILE
      size: 142249
      sha256: 6b90985a44e8c4d03b7d08a84071d74b5f2a54f68f1166c39e6d93e88b538b8e
    - path: design/tokens/hhy_design_tokens_v1.2.2.json
      state: FILE
      size: 6193
      sha256: 5e79cdc6dc8dddb216ad5b112c2c95ec3af30bbed47b81e06cecbcacfc86dffa
    - path: docs/00-baseline/正式商业系统全局硬性开发边界.md
      state: FILE
      size: 2991
      sha256: 16fd5a076da34019a484243795bd8300c447ee5db5100f08b7838943bade902d
    - path: docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
      state: FILE
      size: 11809
      sha256: d0ec25ff64cb643385ac8b8ea960e2c0e788407f6b472819da3524a38053ba55
    - path: docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
      state: FILE
      size: 7441
      sha256: fde6931eb35d48e56f3e89178bcc821c84dbddb9b207adf75d324dd49d966ca9
    - path: docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
      state: FILE
      size: 2240
      sha256: c71c5da647afd89f5562840664279e2cbde5bb9fa8506ec5a07b1139813c95a4
    - path: docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
      state: FILE
      size: 26375
      sha256: c28e7cfd0ff2acc53ded58a9b47f0f17b4b295bb533df57a3a15d0cafe5d9b26
    - path: docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
      state: FILE
      size: 22541
      sha256: aab898e0d28e1198a4b7c0daafc4942fdb040afafa32bdf33cf23136c25e26cb
    - path: docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
      state: FILE
      size: 24388
      sha256: 9fe7bb4931d7ccb11ae3160bfbb4bdb0ad334a08b4139b50b7a02df21a563d35
    - path: docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
      state: FILE
      size: 26166
      sha256: 4f306c2e376af7b36a9394c80b315b537d2b9d764a591c0c3c771dc71bc93319
    - path: docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
      state: FILE
      size: 21893
      sha256: 91383a54487567d43b37cc90d7f0914a8d3b133ae01bdfb2df23678800cdb381
    - path: docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
      state: FILE
      size: 32401
      sha256: 2d16a7369dd66534754a0ed3159886edb55dea1d9edc616c1982491b7fffb581
    - path: docs/02-ui/页面施工规格总索引_V1.2.2.md
      state: FILE
      size: 29625
      sha256: 4a1d60097ae663039d5556a1666d72af59ac3b837182bd613895524407dba484
    - path: docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
      state: FILE
      size: 18305
      sha256: 306eb10e65c24655d543aac54094c2b99d4fc192c7907e24e9041ffeb12a72e3
    - path: docs/03-continuity/PROBLEM_REGISTRY.yaml
      state: FILE
      size: 31103
      sha256: 7ec32b7a833696922cac1d5d9c53e4694bf630bb7897244f7b8516400aedc9fe
    - path: docs/03-continuity/REUSABLE_PATTERNS.md
      state: FILE
      size: 2093
      sha256: 6ba8e39f6a98d3ceb4b019ea66aa6a954ada7f07c55e5f1c533200f0beda7969
    - path: docs/03-continuity/change-requests/CR-0051-修复BLOCKED任务接续命令死锁并登记非阻断设计回传.md
      state: FILE
      size: 3955
      sha256: 09124eace840cbac05b094cb44ba6e9b2baf894d33b663a5eecc21a271fd9f27
    - path: docs/03-continuity/change-requests/CR-0052-同步连续性模板并重生成协议验证报告.md
      state: FILE
      size: 3106
      sha256: 657fe94cc6f260b297cb45642322801c84f680a89018c9e6679469eaecfc7689
    - path: docs/03-continuity/change-requests/CR-0053-补齐非阻断设计接入与连续性修复Changelog.md
      state: FILE
      size: 2615
      sha256: d34b7054137e69b320623c2971b6b89fdc8c26644170a1cbc9ab29dbc2fb1ab0
    - path: docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
      state: FILE
      size: 7188
      sha256: 570569e64f28338335fdac3500133ad7f40abec4bf8090430a2dc9e78af43838
    - path: docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
      state: FILE
      size: 3292
      sha256: 2f7db76799a9cb572ce609aa9afb8be0187d694ac15fd220d562b91f7cfbedd3
    - path: docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
      state: FILE
      size: 2465
      sha256: b93de35bf64993bf081bcdad7925c9622d380cee943c5e57e63092cc0d7432c4
    - path: docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
      state: FILE
      size: 2387
      sha256: 12bc90c1d1dc3e298b346ed9698fd14680d63a32b96ddce49b453e81002f18f8
    - path: docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
      state: FILE
      size: 2907
      sha256: 92b48e492037e26be0f617c90490599e84b0531df853f2f2446811df5437f0f5
    - path: docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
      state: FILE
      size: 2314
      sha256: fe30c350a27877d95f97627ead6e233589a510c552d70248b2143002def3f6ce
    - path: docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
      state: FILE
      size: 2381
      sha256: 54f30119acbf54bb77ce6ff56d4062832df36764dd971f4c8de17330b997e14f
    - path: docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
      state: FILE
      size: 2129
      sha256: 295e4066edcb0c8045b5f20d050bee97574b383ee50ef3254f52a21f71b16933
    - path: docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
      state: FILE
      size: 2044
      sha256: 474b814936d47e04b628904e99ef438c7220f2d2611faafefbf950783e1dacfa
    - path: docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
      state: FILE
      size: 2834
      sha256: 2c09fe83410224d6c2b15d4237a85d8c55edcab204906156fe5345c6763bd4b8
    - path: packages/api-client/src/client.generated.ts
      state: FILE
      size: 610915
      sha256: 041ca68b7bfa0e379fbbe2b27757bd9b70fb1416eae35d0f39cbd0284e242a5d
    - path: packages/design-tokens/admin.css
      state: FILE
      size: 4796
      sha256: 4644905aa4bfb0e194d9ecb0f7044fed5c250fbbc98f2fac7b943d1e3e357467
    - path: packages/design-tokens/h5.css
      state: FILE
      size: 4382
      sha256: 7d7730836de5d729b9522ca3fab2bb20555df8fab8212f9e8f52fac699957f2c
    - path: releases/R02/R02_AUTH_HOTFIX_20260719.md
      state: FILE
      size: 3033
      sha256: 1c31e969306cd88e94f0b1d8b018163127c1986147b20c5d997d3a6511494199
    - path: scripts/check_commercial_ui_boundaries.py
      state: FILE
      size: 1984
      sha256: f4ec4bb352eddd95271b45c24e5e7bb03530dfd2918cf9721242974006265305
    - path: scripts/check_main_doc.py
      state: FILE
      size: 449
      sha256: 3a41ea779d47fe35fc3873a2ebcb1942194f1845f11576f246d4b3f58ebe8136
    - path: scripts/check_r02_security_challenge_design_package.py
      state: FILE
      size: 12737
      sha256: e54ca6abd0f80f5df5febcd903873bf71469726586396f8acb6d43b94ad1c220
    - path: scripts/check_ui_tokens.py
      state: FILE
      size: 15254
      sha256: f459d91410f35c60b383c32a5d2d362c5f04e310421ff8ae804a306d6bcc4d62
    - path: scripts/continuity.py
      state: FILE
      size: 65887
      sha256: bc77db7ccffa3bd20979c5cf14446a9a95bf425a2272b5910fad9ddc51feb457
    - path: services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
      state: FILE
      size: 1705
      sha256: 7844f5b7eda97fd9abb2fb9475cb85065f73c0107c2ca812bbd494309f53df0c
    - path: services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
      state: FILE
      size: 6383
      sha256: f1ef13bc8409e6452ebe7b5ed8527b9e395091527ffb5163333799f52a5b4917
    - path: services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
      state: FILE
      size: 31810
      sha256: 578df62ffcb182a5002c1175394852a3227e9bd9923de97bee9ee34e95b83572
    - path: services/backend/boot/src/main/resources/application.yml
      state: FILE
      size: 2932
      sha256: 5e80309a68044b73d4ed53ccd61f9ae47ca7a2e95b0e47a41ef012fb10cb1bcc
    - path: services/backend/boot/src/main/resources/contracts/openapi.yaml
      state: FILE
      size: 591151
      sha256: 591a160ffb96ec075ffbc8aa5c306f2abeaba843bd675b6fe02eb0efd7f5bb7a
    - path: services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
      state: FILE
      size: 26571
      sha256: 5e8f6320eaa231b83cfba818039817ac56f7ba363daccc8b421ba174d8d52172
    - path: services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
      state: FILE
      size: 10109
      sha256: 9e541c669759cb49f83dc078acac5529af1ed0990bfddc528724ebc6c7b707ac
    - path: templates/AGENTS.md
      state: FILE
      size: 7562
      sha256: fe60549fb106fb4371d39eca6a583191a6661cadaa6b4b7d6ccb44c2b58473db
    - path: templates/START_HERE.md
      state: FILE
      size: 3477
      sha256: 038f713d50267cc0c1598d2399f13ee5ca9ad82bc03311747f3c3ef7f2ae232a
    - path: tests/test_commercial_ui_boundaries.py
      state: FILE
      size: 431
      sha256: 96a54848aae1a8c5b81f49500cba3b8728c019b1bf4d49915ccdd82469bbb8f3
    - path: tests/test_continuity_blocked_resume.py
      state: FILE
      size: 2469
      sha256: 93473df248dd8d7cbf514945c9087c6bd9830b9094dcdbe2c729503bc40feeb2
    - path: tests/test_r02_auth_slice_contract.py
      state: FILE
      size: 10081
      sha256: 164666f10fff5cdc66fad075c5274e33861cfb3c8545b193195b00e7605c96b2
    - path: tests/test_r02_security_challenge_design_package.py
      state: FILE
      size: 7080
      sha256: cb6b58642e846c18349fb674693046cd3b09a9da21ec7e9e46c087bba159b6d8
change_classification:
  other:
  - AGENTS.md
  - CHANGELOG.md
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
  - design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
  - design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
  - design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
  - design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
  - design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
  - design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
  - design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
  - design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
  - design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
  - design/tokens/hhy_design_tokens_v1.2.2.json
  - docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
  - releases/R02/R02_AUTH_HOTFIX_20260719.md
  - templates/AGENTS.md
  - templates/START_HERE.md
  user_visible:
  - START_HERE.md
  - apps/admin-web/src/components/AuthShell.vue
  - apps/admin-web/src/components/ProviderCertificatePanel.vue
  - apps/admin-web/src/components/StatusNotice.vue
  - apps/admin-web/src/r01Pages.test.ts
  - apps/admin-web/src/views/AdminSecurityPage.vue
  - apps/admin-web/src/views/AdminUserDetailPage.vue
  - apps/admin-web/src/views/AdminUsersListPage.vue
  - apps/admin-web/src/views/DomainConfigPage.vue
  - apps/admin-web/src/views/ProviderConfigPage.vue
  - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - apps/h5/src/services/inviteRegistration.test.ts
  - apps/h5/src/views/InviteRegistrationPage.test.ts
  - apps/h5/src/views/InviteRegistrationPage.vue
  - contracts/contract_status.csv
  - contracts/openapi.yaml
  - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
  - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - docs/02-ui/页面施工规格总索引_V1.2.2.md
  - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  code:
  - apps/admin-web/src/components/AuthShell.vue
  - apps/admin-web/src/components/ProviderCertificatePanel.vue
  - apps/admin-web/src/components/StatusNotice.vue
  - apps/admin-web/src/r01Pages.test.ts
  - apps/admin-web/src/views/AdminSecurityPage.vue
  - apps/admin-web/src/views/AdminUserDetailPage.vue
  - apps/admin-web/src/views/AdminUsersListPage.vue
  - apps/admin-web/src/views/DomainConfigPage.vue
  - apps/admin-web/src/views/ProviderConfigPage.vue
  - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - apps/h5/src/services/inviteRegistration.test.ts
  - apps/h5/src/views/InviteRegistrationPage.test.ts
  - apps/h5/src/views/InviteRegistrationPage.vue
  - packages/api-client/src/client.generated.ts
  - packages/design-tokens/admin.css
  - packages/design-tokens/h5.css
  - scripts/check_commercial_ui_boundaries.py
  - scripts/check_main_doc.py
  - scripts/check_r02_security_challenge_design_package.py
  - scripts/check_ui_tokens.py
  - scripts/continuity.py
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
  - services/backend/boot/src/main/resources/application.yml
  - services/backend/boot/src/main/resources/contracts/openapi.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
  source_of_truth:
  - contracts/contract_status.csv
  - contracts/openapi.yaml
  - docs/00-baseline/正式商业系统全局硬性开发边界.md
  - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
  - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - docs/02-ui/页面施工规格总索引_V1.2.2.md
  - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  contracts:
  - contracts/contract_status.csv
  - contracts/openapi.yaml
  continuity:
  - docs/03-continuity/PROBLEM_REGISTRY.yaml
  - docs/03-continuity/REUSABLE_PATTERNS.md
  - docs/03-continuity/change-requests/CR-0051-修复BLOCKED任务接续命令死锁并登记非阻断设计回传.md
  - docs/03-continuity/change-requests/CR-0052-同步连续性模板并重生成协议验证报告.md
  - docs/03-continuity/change-requests/CR-0053-补齐非阻断设计接入与连续性修复Changelog.md
  - docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  - docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  - docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  - docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  - docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  - docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  - docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
  - docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
  - docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
  - docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
  tests:
  - tests/test_commercial_ui_boundaries.py
  - tests/test_continuity_blocked_resume.py
  - tests/test_r02_auth_slice_contract.py
  - tests/test_r02_security_challenge_design_package.py
required_records:
- SESSION_RECORD
- SESSION_LOG
- CHECKPOINT
- CURRENT_STATUS
- EVENT_LOG
- APPROVED_CHANGE_REQUEST
- CONTRACT_TEST_EVIDENCE
- GENERATED_CLIENTS_OR_GENERATION_RECORD
- CHANGELOG
change_requests:
- CR-0051
- CR-0052
- CR-0053
- CR-0054
- CR-0055
- CR-0056
- CR-0057
- CR-0058
- CR-0059
- CR-0060
- CR-0061
- CR-0062
- CR-0063
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  approved_exceptions:
  - scripts/continuity.py
  - tests/test_continuity_blocked_resume.py
  - .continuity/CONTINUITY_POLICY.yaml
  - docs/03-continuity/REUSABLE_PATTERNS.md
  - docs/03-continuity/PROBLEM_REGISTRY.yaml
  - docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md
  - scripts/check_r02_security_challenge_design_package.py
  - tests/test_r02_security_challenge_design_package.py
  - templates/AGENTS.md
  - artifacts/validation/continuity-integration-v1.2.3.json
  - artifacts/validation/continuity-integration-v1.2.3.log
  - artifacts/validation/continuity-lifecycle-integration-v1.2.3.json
  - artifacts/validation/continuity-lifecycle-integration-v1.2.3.log
  - CHANGELOG.md
  - AGENTS.md
  - START_HERE.md
  - docs/00-baseline/正式商业系统全局硬性开发边界.md
  - docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
  - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
  - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
  - docs/02-ui/页面施工规格总索引_V1.2.2.md
  - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
  - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
  - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
  - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
  - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
  - design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
  - design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
  - design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
  - design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
  - design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
  - design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
  - design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
  - design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
  - design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
  - scripts/check_commercial_ui_boundaries.py
  - tests/test_commercial_ui_boundaries.py
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
  - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
  - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
  - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
  - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
  - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
  - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
  - services/backend/boot/src/main/resources/application.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
  - contracts/openapi.yaml
  - services/backend/boot/src/main/resources/contracts/openapi.yaml
  - packages/api-client/src/generated/openapi.yaml
  - tests/test_r02_auth_slice_contract.py
  - config/PROBLEM_REGISTRY.yaml
  - releases/R02/R02_AUTH_HOTFIX_20260719.md
  - docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  - .continuity/change_requests/CR-0054.yaml
  - .continuity/CHANGE_REQUEST_INDEX.yaml
  - apps/admin-web/src/components/StatusNotice.vue
  - apps/admin-web/src/views/AdminLoginPage.vue
  - apps/admin-web/src/views/AdminMfaPage.vue
  - apps/admin-web/src/views/AdminUserDetailPage.vue
  - apps/admin-web/src/views/AdminUsersListPage.vue
  - apps/admin-web/src/views/AdminSecurityPage.vue
  - apps/admin-web/src/views/DomainConfigPage.vue
  - apps/admin-web/src/views/ProviderConfigPage.vue
  - apps/admin-web/src/components/ProviderCertificatePanel.vue
  - apps/admin-web/src/r01Pages.test.ts
  - apps/admin-web/src/r03DomainPage.test.ts
  - apps/admin-web/src/r03ProviderPage.test.ts
  - docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  - .continuity/change_requests/CR-0055.yaml
  - services/backend/boot/src/main/resources/application.yml
  - docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  - .continuity/change_requests/CR-0056.yaml
  - packages/api-client/src/client.generated.ts
  - docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  - .continuity/change_requests/CR-0057.yaml
  - apps/h5/src/views/InviteRegistrationPage.vue
  - apps/h5/src/services/inviteRegistration.ts
  - apps/h5/src/views/InviteRegistrationPage.test.ts
  - apps/h5/src/services/inviteRegistration.test.ts
  - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
  - docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  - .continuity/change_requests/CR-0058.yaml
  - apps/admin-web/src/components/AuthShell.vue
  - docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  - .continuity/change_requests/CR-0059.yaml
  - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
  - contracts/contract_status.csv
  - design/tokens/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
  - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
  - packages/design-tokens/h5.css
  - packages/design-tokens/admin.css
  - scripts/check_ui_tokens.py
  - scripts/check_main_doc.py
  source: story+explicit+approved-cr:CR-0051+approved-cr:CR-0052+approved-cr:CR-0053+approved-cr:CR-0054+approved-cr:CR-0055+approved-cr:CR-0056+approved-cr:CR-0057+approved-cr:CR-0058+approved-cr:CR-0059+approved-cr:CR-0060+approved-cr:CR-0061+approved-cr:CR-0062+approved-cr:CR-0063
parallel_execution:
  assessment: NO_SAFE_PARALLEL
  delegated_workers: 0
  workers: []
  reason: 提交、推送、部署和APK构建是严格前后依赖。
event_hash: e29ba5173ff624ed258fa66145b2075402206903955a92a10241fcb26b520953
```

## 接续状态与事件头

```yaml
mode: ENFORCED
protocol_version: '1.0'
active_session_id: SES-20260718T200607Z-3569D212
last_session_id: SES-20260718T165842Z-356A8138
last_session_result: BLOCKED
last_closure_checkpoint_id: CP-SES-20260718T165842Z-356A8138-0005
event_count: 713
event_head_hash: e29ba5173ff624ed258fa66145b2075402206903955a92a10241fcb26b520953
event_chain_valid: true
```

## 最近会话与任务迁移

```yaml
recent_sessions: - session_id: SES-20260718T084729Z-BD53B7C4
  task_id: TASK-R02-006
  story_id: STORY-R02-009
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T08:47:29Z'
  record: .continuity/sessions/SES-20260718T084729Z-BD53B7C4.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T084729Z-BD53B7C4.md
  updated_at: '2026-07-18T10:48:33Z'
  closed_at: '2026-07-18T10:48:33Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T084729Z-BD53B7C4/0013.yaml
  handoff_bundle: null
- session_id: SES-20260718T105025Z-0B8DE284
  task_id: TASK-R02-007
  story_id: STORY-R02-009
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T10:50:25Z'
  record: .continuity/sessions/SES-20260718T105025Z-0B8DE284.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T105025Z-0B8DE284.md
  updated_at: '2026-07-18T13:23:32Z'
  closed_at: '2026-07-18T13:23:32Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T105025Z-0B8DE284/0013.yaml
  handoff_bundle: null
- session_id: SES-20260718T132650Z-C5038104
  task_id: TASK-R03-001
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T13:26:50Z'
  record: .continuity/sessions/SES-20260718T132650Z-C5038104.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T132650Z-C5038104.md
  updated_at: '2026-07-18T13:30:31Z'
  closed_at: '2026-07-18T13:30:31Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T132650Z-C5038104/0002.yaml
  handoff_bundle: null
- session_id: SES-20260718T133151Z-12DB5949
  task_id: TASK-R03-002
  story_id: STORY-R03-001
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T13:31:51Z'
  record: .continuity/sessions/SES-20260718T133151Z-12DB5949.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T133151Z-12DB5949.md
  updated_at: '2026-07-18T14:21:48Z'
  closed_at: '2026-07-18T14:21:48Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T133151Z-12DB5949/0012.yaml
  handoff_bundle: null
- session_id: SES-20260718T142638Z-EF4C3723
  task_id: TASK-R03-003
  story_id: STORY-R03-002
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T14:26:38Z'
  record: .continuity/sessions/SES-20260718T142638Z-EF4C3723.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T142638Z-EF4C3723.md
  updated_at: '2026-07-18T14:52:08Z'
  closed_at: '2026-07-18T14:52:08Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T142638Z-EF4C3723/0011.yaml
  handoff_bundle: null
- session_id: SES-20260718T145327Z-DEA562CB
  task_id: TASK-R03-004
  story_id: STORY-R03-003
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T14:53:27Z'
  record: .continuity/sessions/SES-20260718T145327Z-DEA562CB.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T145327Z-DEA562CB.md
  updated_at: '2026-07-18T15:18:35Z'
  closed_at: '2026-07-18T15:18:35Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T145327Z-DEA562CB/0007.yaml
  handoff_bundle: null
- session_id: SES-20260718T152013Z-8B704646
  task_id: TASK-R03-005
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T15:20:13Z'
  record: .continuity/sessions/SES-20260718T152013Z-8B704646.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T152013Z-8B704646.md
  updated_at: '2026-07-18T16:21:46Z'
  closed_at: '2026-07-18T16:21:46Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T152013Z-8B704646/0011.yaml
  handoff_bundle: null
- session_id: SES-20260718T162320Z-23C14331
  task_id: TASK-R03-006
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T16:23:20Z'
  record: .continuity/sessions/SES-20260718T162320Z-23C14331.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T162320Z-23C14331.md
  updated_at: '2026-07-18T16:56:25Z'
  closed_at: '2026-07-18T16:56:25Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T162320Z-23C14331/0003.yaml
  handoff_bundle: null
- session_id: SES-20260718T165842Z-356A8138
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  started_at: '2026-07-18T16:58:42Z'
  record: .continuity/sessions/SES-20260718T165842Z-356A8138.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T165842Z-356A8138.md
  updated_at: '2026-07-18T17:24:52Z'
  closed_at: '2026-07-18T17:24:52Z'
  latest_checkpoint: .continuity/checkpoints/SES-20260718T165842Z-356A8138/0005.yaml
  handoff_bundle: null
- session_id: SES-20260718T200607Z-3569D212
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  actor_id: codex-root
  status: ACTIVE
  started_at: '2026-07-18T20:06:07Z'
  record: .continuity/sessions/SES-20260718T200607Z-3569D212.yaml
  session_log: docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md
  updated_at: '2026-07-18T22:48:38Z'
  closed_at: null
  latest_checkpoint: .continuity/checkpoints/SES-20260718T200607Z-3569D212/0010.yaml
  handoff_bundle: null
task_claims: - claim_id: CLM-3E379E115FA4
  session_id: SES-20260717T141717Z-A01412D7
  task_id: TASK-R01-006
  story_id: STORY-R01-003
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-17T14:17:17Z'
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
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-17T15:20:32Z'
- claim_id: CLM-4E518B8F5FFF
  session_id: SES-20260717T152721Z-016DB4B2
  task_id: TASK-R01-007
  story_id: STORY-R01-003
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-17T15:27:21Z'
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
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  - artifacts/apk/**
  - artifacts/reports/**
  - artifacts/validation/**
  closed_at: '2026-07-17T17:06:18Z'
- claim_id: CLM-E45608E07944
  session_id: SES-20260717T171412Z-7CD86701
  task_id: TASK-R01-008
  story_id: STORY-R01-003
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-17T17:14:12Z'
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
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-17T17:45:22Z'
- claim_id: CLM-C40E17896B67
  session_id: SES-20260717T183459Z-D64E7407
  task_id: TASK-R02-001
  story_id: STORY-R02-009
  actor_id: codex-master
  status: CLOSED
  claimed_at: '2026-07-17T18:34:59Z'
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
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-17T20:02:12Z'
- claim_id: CLM-9842C34B7B1D
  session_id: SES-20260717T200842Z-456B8F52
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  actor_id: codex-master
  status: HANDED_OFF
  claimed_at: '2026-07-17T20:08:42Z'
  allowed_paths:
  - apps/android/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  - STORY-R02-003,STORY-R02-004及其直接实现、测试与生成契约
  closed_at: '2026-07-17T20:45:10Z'
- claim_id: CLM-9A39E30266D5
  session_id: SES-20260717T204616Z-254B60A3
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  actor_id: codex-master
  status: HANDED_OFF
  claimed_at: '2026-07-17T20:46:16Z'
  allowed_paths:
  - apps/android/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  - STORY-R02-003,STORY-R02-004及其直接实现、测试与生成契约
  closed_at: '2026-07-17T20:46:50Z'
- claim_id: CLM-50B2FBE97B5F
  session_id: SES-20260717T210927Z-13B07A7D
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-17T21:09:27Z'
  allowed_paths:
  - apps/android/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  - STORY-R02-003,STORY-R02-004及其直接实现、测试与生成契约
  closed_at: '2026-07-18T05:31:36Z'
- claim_id: CLM-9F61C3DE5FFC
  session_id: SES-20260718T053331Z-F0ED92BF
  task_id: TASK-R02-003
  story_id: STORY-R02-005
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T05:33:31Z'
  allowed_paths:
  - apps/android/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T07:05:25Z'
- claim_id: CLM-496A3EFF20D2
  session_id: SES-20260718T070836Z-25E39817
  task_id: TASK-R02-004
  story_id: STORY-R02-001
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T07:08:36Z'
  allowed_paths:
  - apps/admin-web/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T08:14:58Z'
- claim_id: CLM-3476836A7869
  session_id: SES-20260718T081744Z-71EAAA84
  task_id: TASK-R02-005
  story_id: STORY-R02-008
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T08:17:44Z'
  allowed_paths:
  - apps/h5/**
  - apps/cashier-h5/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T08:45:02Z'
- claim_id: CLM-EA9453A4715D
  session_id: SES-20260718T084729Z-BD53B7C4
  task_id: TASK-R02-006
  story_id: STORY-R02-009
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T08:47:29Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T10:48:33Z'
- claim_id: CLM-B4B0636F4556
  session_id: SES-20260718T105025Z-0B8DE284
  task_id: TASK-R02-007
  story_id: STORY-R02-009
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T10:50:25Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T13:23:32Z'
- claim_id: CLM-1E804AB6298F
  session_id: SES-20260718T132650Z-C5038104
  task_id: TASK-R03-001
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T13:26:50Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T13:30:31Z'
- claim_id: CLM-36454E5FF743
  session_id: SES-20260718T133151Z-12DB5949
  task_id: TASK-R03-002
  story_id: STORY-R03-001
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T13:31:51Z'
  allowed_paths:
  - apps/admin-web/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T14:21:48Z'
- claim_id: CLM-05902C997273
  session_id: SES-20260718T142638Z-EF4C3723
  task_id: TASK-R03-003
  story_id: STORY-R03-002
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T14:26:38Z'
  allowed_paths:
  - apps/admin-web/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T14:52:08Z'
- claim_id: CLM-1F23C0F0A384
  session_id: SES-20260718T145327Z-DEA562CB
  task_id: TASK-R03-004
  story_id: STORY-R03-003
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T14:53:27Z'
  allowed_paths:
  - apps/admin-web/**
  - services/backend/**
  - packages/**
  - contracts/**
  - database/**
  - config/**
  - tests/**
  - docs/**
  - catalogs/**
  - releases/**
  - design/**
  - scripts/**
  closed_at: '2026-07-18T15:18:35Z'
- claim_id: CLM-1B426B903146
  session_id: SES-20260718T152013Z-8B704646
  task_id: TASK-R03-005
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T15:20:13Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T16:21:46Z'
- claim_id: CLM-D402CCF0F4A1
  session_id: SES-20260718T162320Z-23C14331
  task_id: TASK-R03-006
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T16:23:20Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T16:56:25Z'
- claim_id: CLM-C3E597EF76EC
  session_id: SES-20260718T165842Z-356A8138
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  actor_id: codex-root
  status: CLOSED
  claimed_at: '2026-07-18T16:58:42Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
  closed_at: '2026-07-18T17:24:52Z'
- claim_id: CLM-B88F73D8F717
  session_id: SES-20260718T200607Z-3569D212
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  actor_id: codex-root
  status: ACTIVE
  claimed_at: '2026-07-18T20:06:07Z'
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
  - .codex/**
  - AGENTS.md
  - START_HERE.md
  - README.md
  - CHANGELOG.md
  - Makefile
  - .gitignore
  - .gitattributes
  - .dockerignore
  - package.json
  - pnpm-lock.yaml
  - pnpm-workspace.yaml
  - requirements-dev.txt
  - PROJECT_*.yaml
  - PROJECT_*.json
recent_task_transitions: - transition_id: TRN-23D2BAB015EB
  timestamp: '2026-07-17T17:14:12Z'
  release: R01
  task_id: TASK-R01-008
  story_id: STORY-R01-003
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260717T171412Z-7CD86701
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-1F2460258754
  timestamp: '2026-07-17T18:35:00Z'
  release: R02
  task_id: TASK-R02-001
  story_id: STORY-R02-009
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260717T183459Z-D64E7407
  actor_id: codex-master
  reason: 会话领取任务
- transition_id: TRN-1C8D7CBE6956
  timestamp: '2026-07-17T20:08:42Z'
  release: R02
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260717T200842Z-456B8F52
  actor_id: codex-master
  reason: 会话领取任务
- transition_id: TRN-1EAFE057F615
  timestamp: '2026-07-17T20:45:10Z'
  release: R02
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  from_status: IN_PROGRESS
  to_status: HANDED_OFF
  session_id: SES-20260717T200842Z-456B8F52
  actor_id: codex-master
  reason: 用户要求全项目规划制定完成后暂停业务开发
- transition_id: TRN-06568A6A9C43
  timestamp: '2026-07-17T20:46:16Z'
  release: R02
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260717T204616Z-254B60A3
  actor_id: codex-master
  reason: 会话领取任务
- transition_id: TRN-CF07AD7C81E8
  timestamp: '2026-07-17T20:46:50Z'
  release: R02
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  from_status: IN_PROGRESS
  to_status: HANDED_OFF
  session_id: SES-20260717T204616Z-254B60A3
  actor_id: codex-master
  reason: 全项目规划已完成，用户要求暂停业务开发
- transition_id: TRN-E5F162DB4E05
  timestamp: '2026-07-17T21:09:27Z'
  release: R02
  task_id: TASK-R02-002
  story_id: STORY-R02-003
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260717T210927Z-13B07A7D
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-279B7C1340CD
  timestamp: '2026-07-18T05:33:31Z'
  release: R02
  task_id: TASK-R02-003
  story_id: STORY-R02-005
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T053331Z-F0ED92BF
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-3B63590CF0E6
  timestamp: '2026-07-18T07:08:36Z'
  release: R02
  task_id: TASK-R02-004
  story_id: STORY-R02-001
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T070836Z-25E39817
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-18005EF1B8F4
  timestamp: '2026-07-18T08:17:45Z'
  release: R02
  task_id: TASK-R02-005
  story_id: STORY-R02-008
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T081744Z-71EAAA84
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-7CFA31EBDBF8
  timestamp: '2026-07-18T08:47:29Z'
  release: R02
  task_id: TASK-R02-006
  story_id: STORY-R02-009
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T084729Z-BD53B7C4
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-0BCEACF492B2
  timestamp: '2026-07-18T10:50:25Z'
  release: R02
  task_id: TASK-R02-007
  story_id: STORY-R02-009
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T105025Z-0B8DE284
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-3BD6303A80B9
  timestamp: '2026-07-18T13:26:50Z'
  release: R03
  task_id: TASK-R03-001
  story_id: STORY-R03-004
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T132650Z-C5038104
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-C720E2A1E5C3
  timestamp: '2026-07-18T13:31:51Z'
  release: R03
  task_id: TASK-R03-002
  story_id: STORY-R03-001
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T133151Z-12DB5949
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-1686D9450233
  timestamp: '2026-07-18T14:26:39Z'
  release: R03
  task_id: TASK-R03-003
  story_id: STORY-R03-002
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T142638Z-EF4C3723
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-13BC662575B3
  timestamp: '2026-07-18T14:53:28Z'
  release: R03
  task_id: TASK-R03-004
  story_id: STORY-R03-003
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T145327Z-DEA562CB
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-4DA26C35977A
  timestamp: '2026-07-18T15:20:13Z'
  release: R03
  task_id: TASK-R03-005
  story_id: STORY-R03-004
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T152013Z-8B704646
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-AB73BBDF9FA9
  timestamp: '2026-07-18T16:23:21Z'
  release: R03
  task_id: TASK-R03-006
  story_id: STORY-R03-004
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T162320Z-23C14331
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-2A0B67091330
  timestamp: '2026-07-18T16:58:42Z'
  release: R03
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  from_status: READY
  to_status: IN_PROGRESS
  session_id: SES-20260718T165842Z-356A8138
  actor_id: codex-root
  reason: 会话领取任务
- transition_id: TRN-719577C27F1C
  timestamp: '2026-07-18T20:06:08Z'
  release: R03
  task_id: TASK-R03-007
  story_id: STORY-R03-004
  from_status: IN_PROGRESS
  to_status: IN_PROGRESS
  session_id: SES-20260718T200607Z-3569D212
  actor_id: codex-root
  reason: 会话领取任务
```

## Git 状态

```yaml
initialized: true
branch: task/TASK-R03-001
head: 655e1eebbdd2df3ce1f527b7d77601826cb11c55
upstream: origin/task/TASK-R03-001
ahead: 0
behind: 0
dirty: true
status_porcelain:
- MM .continuity/ACTIVE_SESSION.yaml
- M  .continuity/CHANGE_REQUEST_INDEX.yaml
- M  .continuity/CONTINUITY_POLICY.yaml
- MM .continuity/EVENT_LOG.jsonl
- MM .continuity/SESSION_INDEX.yaml
- MM .continuity/STATE.yaml
- A  .continuity/change_requests/CR-0054.yaml
- A  .continuity/change_requests/CR-0055.yaml
- A  .continuity/change_requests/CR-0056.yaml
- A  .continuity/change_requests/CR-0057.yaml
- A  .continuity/change_requests/CR-0058.yaml
- A  .continuity/change_requests/CR-0059.yaml
- A  .continuity/change_requests/CR-0060.yaml
- A  .continuity/change_requests/CR-0061.yaml
- A  .continuity/change_requests/CR-0062.yaml
- A  .continuity/change_requests/CR-0063.yaml
- A  .continuity/checkpoints/SES-20260718T200607Z-3569D212/0008.yaml
- A  .continuity/checkpoints/SES-20260718T200607Z-3569D212/0009.yaml
- MM .continuity/sessions/SES-20260718T200607Z-3569D212.yaml
- M  AGENTS.md
- M  CHANGELOG.md
- MM CURRENT_STATUS.yaml
- M  START_HERE.md
- M  apps/admin-web/src/components/AuthShell.vue
- M  apps/admin-web/src/components/ProviderCertificatePanel.vue
- M  apps/admin-web/src/components/StatusNotice.vue
- M  apps/admin-web/src/r01Pages.test.ts
- M  apps/admin-web/src/views/AdminSecurityPage.vue
- M  apps/admin-web/src/views/AdminUserDetailPage.vue
- M  apps/admin-web/src/views/AdminUsersListPage.vue
- M  apps/admin-web/src/views/DomainConfigPage.vue
- M  apps/admin-web/src/views/ProviderConfigPage.vue
- M  apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
- M  apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
- M  apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
- M  apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
- M  apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
- M  apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
- M  apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
- M  apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
- M  apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
- M  apps/h5/src/services/inviteRegistration.test.ts
- M  apps/h5/src/views/InviteRegistrationPage.test.ts
- M  apps/h5/src/views/InviteRegistrationPage.vue
- M  artifacts/context/CURRENT_CONTEXT_PACK.md
- M  artifacts/context/CURRENT_CONTEXT_PACK.yaml
- M  artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
- M  artifacts/validation/continuity-integration-v1.2.3.json
- M  artifacts/validation/continuity-lifecycle-integration-v1.2.3.json
- M  artifacts/validation/continuity-lifecycle-integration-v1.2.3.log
- M  artifacts/validation/project-doctor-v1.2.3.json
- M  catalogs/change_request_index.csv
- MM catalogs/session_index.csv
- M  contracts/contract_status.csv
- M  contracts/openapi.yaml
- A  design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
- A  design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
- A  design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
- A  design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
- A  design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
- A  design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
- A  design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
- A  design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
- A  design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
- A  design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
- A  design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
- M  design/tokens/hhy_design_tokens_v1.2.2.json
- AM docs/00-baseline/正式商业系统全局硬性开发边界.md
- M  docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
- M  docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
- AM docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
- M  docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
- M  docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
- M  docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
- M  docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
- M  docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
- M  docs/02-ui/页面施工规格总索引_V1.2.2.md
- M  docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
- M  docs/03-continuity/PROBLEM_REGISTRY.yaml
- A  docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
- A  docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
- A  docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
- A  docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
- A  docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
- A  docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
- A  docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
- A  docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
- A  docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
- A  docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
- MM docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md
- M  packages/api-client/src/client.generated.ts
- M  packages/design-tokens/admin.css
- M  packages/design-tokens/h5.css
- A  releases/R02/R02_AUTH_HOTFIX_20260719.md
- A  scripts/check_commercial_ui_boundaries.py
- M  scripts/check_main_doc.py
- M  scripts/check_r02_security_challenge_design_package.py
- M  scripts/check_ui_tokens.py
- A  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
- M  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
- M  services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
- M  services/backend/boot/src/main/resources/application.yml
- M  services/backend/boot/src/main/resources/contracts/openapi.yaml
- M  services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
- M  services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
- M  templates/AGENTS.md
- M  templates/START_HERE.md
- A  tests/test_commercial_ui_boundaries.py
- M  tests/test_r02_auth_slice_contract.py
- M  tests/test_r02_security_challenge_design_package.py
- ?? .continuity/checkpoints/SES-20260718T200607Z-3569D212/0010.yaml
recent_commits:
- "655e1eebbdd2df3ce1f527b7d77601826cb11c55\t2026-07-19T04:50:09+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): restore desktop\
  \ apk evidence"
- "ab17752eabb6db73f0438fd5048ec45ef0247921\t2026-07-19T04:46:42+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close resume\
  \ safety changes"
- "30a397bf2f2dbffbde976cbb93f6f3b216eb16a4\t2026-07-19T04:45:06+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] fix(continuity): resume blocked\
  \ work safely"
- "18e2db65b65afeb1e69d0c382ab53963fc97adec\t2026-07-19T01:25:54+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close TASK-R03-007\
  \ as blocked"
- "16023e53e214d0f82486783578657814f4671f85\t2026-07-19T01:24:12+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): sign external activation\
  \ gate"
- "5cdfd38e50c563b345203b1255dc7fb67e618c46\t2026-07-19T01:23:07+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] test(r03): deliver regression\
  \ apk evidence"
- "3a913c95265f93b815042389d573f4804137c0c0\t2026-07-19T01:05:16+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(r03): freeze external\
  \ and apk baseline"
- "25d8171c76e0cef323a717ad66ac85ccd69371c3\t2026-07-19T00:57:28+08:00\tHHY Continuity Bootstrap\t[STORY-R03-004] chore(continuity): close TASK-R03-006\
  \ as completed"
```

## 会话累计项目变更

- 指纹：`f4bd8ed068e019a4aa7cded0eca1c9607617f2e3c44a3aefcc285cb233fdae93`
- 文件数：87

- `AGENTS.md`
- `CHANGELOG.md`
- `START_HERE.md`
- `apps/admin-web/src/components/AuthShell.vue`
- `apps/admin-web/src/components/ProviderCertificatePanel.vue`
- `apps/admin-web/src/components/StatusNotice.vue`
- `apps/admin-web/src/r01Pages.test.ts`
- `apps/admin-web/src/views/AdminSecurityPage.vue`
- `apps/admin-web/src/views/AdminUserDetailPage.vue`
- `apps/admin-web/src/views/AdminUsersListPage.vue`
- `apps/admin-web/src/views/DomainConfigPage.vue`
- `apps/admin-web/src/views/ProviderConfigPage.vue`
- `apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json`
- `apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt`
- `apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt`
- `apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt`
- `apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt`
- `apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt`
- `apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt`
- `apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt`
- `apps/h5/src/services/inviteRegistration.test.ts`
- `apps/h5/src/views/InviteRegistrationPage.test.ts`
- `apps/h5/src/views/InviteRegistrationPage.vue`
- `contracts/contract_status.csv`
- `contracts/openapi.yaml`
- `design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png`
- `design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json`
- `design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md`
- `design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png`
- `design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png`
- `design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png`
- `design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png`
- `design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png`
- `design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png`
- `design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png`
- `design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png`
- `design/tokens/hhy_design_tokens_v1.2.2.json`
- `docs/00-baseline/正式商业系统全局硬性开发边界.md`
- `docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md`
- `docs/02-ui/12批UI参考图绑定索引_V1.2.2.md`
- `docs/02-ui/R02安全验证码UI设计回传接入记录_V1.2.2.md`
- `docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md`
- `docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md`
- `docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md`
- `docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md`
- `docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md`
- `docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md`
- `docs/02-ui/页面施工规格总索引_V1.2.2.md`
- `docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/03-continuity/change-requests/CR-0051-修复BLOCKED任务接续命令死锁并登记非阻断设计回传.md`
- `docs/03-continuity/change-requests/CR-0052-同步连续性模板并重生成协议验证报告.md`
- `docs/03-continuity/change-requests/CR-0053-补齐非阻断设计接入与连续性修复Changelog.md`
- `docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md`
- `docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md`
- `docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md`
- `docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md`
- `docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md`
- `docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md`
- `docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md`
- `docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md`
- `docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md`
- `docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md`
- `packages/api-client/src/client.generated.ts`
- `packages/design-tokens/admin.css`
- `packages/design-tokens/h5.css`
- `releases/R02/R02_AUTH_HOTFIX_20260719.md`
- `scripts/check_commercial_ui_boundaries.py`
- `scripts/check_main_doc.py`
- `scripts/check_r02_security_challenge_design_package.py`
- `scripts/check_ui_tokens.py`
- `scripts/continuity.py`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java`
- `services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java`
- `services/backend/boot/src/main/resources/application.yml`
- `services/backend/boot/src/main/resources/contracts/openapi.yaml`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java`
- `templates/AGENTS.md`
- `templates/START_HERE.md`
- `tests/test_commercial_ui_boundaries.py`
- `tests/test_continuity_blocked_resume.py`
- `tests/test_r02_auth_slice_contract.py`
- `tests/test_r02_security_challenge_design_package.py`

## 当前 Release

```yaml
RELEASE_MANIFEST.yaml:
  release: R03
  title: 供应商配置、密钥、证书与域名中心
  status: READY_WHEN_DEPENDENCIES_GREEN
  milestone: M0_ENGINEERING_FOUNDATION
  depends_on:
  - R01
  scope: 后台配置短信、R2/OSS、实名、支付、支付宝出款；orbexa.cc二级域名和DNS待办
  android_test_apk_required: true
  requirements:
  - REQ-CONFIG-001
  - REQ-DOMAIN-001
  - REQ-STORAGE-001
  - REQ-SECRET-001
  - REQ-CONFIG-UX-001
  - REQ-APK-001
  ui:
    android: []
    h5: []
    admin:
    - ADM-CONFIG-002
    - ADM-CONFIG-003
    - ADM-CONFIG-004
    - ADM-CONFIG-005
    - ADM-CONFIG-006
    - ADM-CONFIG-007
    - ADM-CONFIG-008
  contracts:
    client_api: []
    admin_api:
    - GET /admin-api/v1/provider-configs
    - GET /admin-api/v1/provider-configs/{provider}
    - POST /admin-api/v1/provider-configs/{provider}/versions
    - POST /admin-api/v1/provider-configs/{provider}/test
    - POST /admin-api/v1/provider-configs/{provider}/activate
    - POST /admin-api/v1/provider-configs/{provider}/rollback
    - POST /admin-api/v1/provider-certificates
    - GET /admin-api/v1/provider-certificates
    - POST /admin-api/v1/provider-certificates/{id}/rotate
    - GET /admin-api/v1/domains
    - PUT /admin-api/v1/domains/{code}
    - GET /admin-api/v1/domains/dns-actions
    - POST /admin-api/v1/domains/{code}/verify
    websocket: []
  database_tables:
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
  - home_modules
  - banners
  - cms_articles
  - agreements
  - agreement_versions
  - user_agreement_acceptances
  - h5_page_configs
  - app_versions
  - app_download_logs
  - media_objects
  - upload_sessions
  - media_access_tokens
  - storage_scope_bindings
  - storage_migration_jobs
  - admin_operation_logs
  tests:
  - TST-CONFIG_001-HAPPY
  - TST-CONFIG_001-IDEMPOTENT
  - TST-CONFIG_001-REJECT
  - TST-CONFIG_UX_001-DRIFT
  - TST-CONFIG_UX_001-HAPPY
  - TST-CONFIG_UX_001-REJECT
  - TST-CONFIG_UX_001-SECURITY
  - TST-DOMAIN_001-HAPPY
  - TST-DOMAIN_001-IDEMPOTENT
  - TST-DOMAIN_001-REJECT
  - TST-STORAGE_001-HAPPY
  - TST-STORAGE_001-IDEMPOTENT
  - TST-STORAGE_001-REJECT
  - TST-V122-030
  - TST-V122-031
  - TST-V122-032
  - TST-V122-033
  - TST-V122-034
  test_count: 18
  parallel_execution_plan: releases/R03/PARALLEL_EXECUTION_PLAN.yaml
  planning_depth: STORY_READY
  apk_gate: REGRESSION_APK_DESKTOP_PUBLIC_AND_OWNER_DEVICE_REQUIRED
  entry_gate:
  - releases/R03/DEFINITION_OF_READY.yaml 全部适用项为PASS
  - releases/R03/STORIES.yaml 中每个故事均绑定页面/API/配置/数据/测试或显式N/A
  - 本版本页面字段、状态、动作、导航和后台运营规格不存在TBD/RELEASE_BOUND
  - 全部依赖版本为GREEN或按发布计划允许的并行依赖已记录
  - 冻结契约发生变化时已创建CR并重新生成追踪和SHA
  exit_gate:
  - 领域代码与前端真实闭环
  - 数据库迁移和不变量测试通过
  - 契约/单元/集成/E2E/安全专项测试通过
  - 日志指标链路和业务告警可验证
  - Session Log、追踪矩阵、产物Manifest和下一任务更新
  - 供应商测试环境、SecretRef、证书和DNS外部激活项已完成或登记明确阻断
  - 即使无Android页面变更，也必须交付回归APK并完成桌面、公网和项目所有者真机验收
  change_policy: V1.2.2 页面、字段、状态、动作、API、配置和数据目录是唯一事实源；破坏性变更必须CR+版本升级+迁移+消费者兼容窗口
  documentation_baseline: V1.2.2
  document_gap_status: ZERO_BLOCKING_DOCUMENT_GAPS
  definition_of_ready: releases/R03/DEFINITION_OF_READY.yaml
  story_backlog: releases/R03/STORIES.yaml
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
  release: R03
  title: 供应商配置、密钥、证书与域名中心
  status: PASS_DOCUMENTATION_READY
  interpretation: 该结论仅表示开发前文档和施工契约完整；软件编译、运行、供应商联调、压测和上线验收仍在对应实施/退出门禁完成。
  gates:
  - 版本: R03
    门禁ID: R03-DOR-01
    类别: 范围与需求
    门禁条件: 本版本需求、非目标、业务规则和变更边界已冻结；每个需求在追踪矩阵有明确行
    适用性: 是
    证据: catalogs/requirements_catalog.csv;catalogs/TRACEABILITY_MATRIX.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-02
    类别: 页面与模板
    门禁条件: 本版本所有页面/弹层已绑定标准模板，入口、退出、角色、数据分级和主要区域无TBD
    适用性: 是
    证据: catalogs/ui_page_specifications.csv;catalogs/ui_templates.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-03
    类别: 字段施工规格
    门禁条件: 每个页面展示、输入、筛选、路由、敏感字段均有类型、控件、必填、校验、显示/编辑条件和错误文案
    适用性: 是
    证据: catalogs/ui_page_fields.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-04
    类别: 状态与恢复
    门禁条件: 首屏、内容、空、刷新、局部失败、无权限、404、离线、提交、成功、冲突和领域状态已定义
    适用性: 是
    证据: catalogs/ui_page_states.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-05
    类别: 动作与导航
    门禁条件: 每个操作定义触发、显示/可用、确认、请求映射、幂等/版本、加载、成功、错误、重试、导航和审计
    适用性: 是
    证据: catalogs/ui_action_matrix.csv;catalogs/ui_navigation_specifications.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-06
    类别: 接口所有权
    门禁条件: 本版本每个OpenAPI operationId均有页面动作或显式系统所有者；核心请求响应禁止自由对象代替领域Schema
    适用性: 是
    证据: catalogs/api_ui_ownership.csv;contracts/openapi.yaml;contracts/admin-openapi.yaml
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-07
    类别: 数据与状态机
    门禁条件: 涉及表、约束、状态机、历史、幂等和账务不变量已登记；新增运营能力有明确数据事实源
    适用性: 是
    证据: catalogs/data_tables.csv;database/schema_dictionary.csv;database/state_machines.yaml
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-08
    类别: 配置与权限
    门禁条件: 相关配置具有控件、单位、范围、依赖、跨字段规则、编辑角色、复核角色、生效预览和回滚；权限与数据分级明确
    适用性: 是
    证据: catalogs/config_registry.csv;catalogs/config_cross_field_rules.csv;catalogs/config_role_matrix.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-09
    类别: 后台运营规格
    门禁条件: 涉及后台页面时，筛选、表格列、排序、批量/行操作、Tab、导出、脱敏、审批、确认和审计已冻结
    适用性: 是
    证据: catalogs/admin_page_operation_specs.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-10
    类别: 测试与验收
    门禁条件: 主路径、拒绝、幂等、并发、故障、安全和防漂移测试ID已存在并绑定到页面/动作/需求
    适用性: 是
    证据: catalogs/test_cases.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-11
    类别: 故事与责任
    门禁条件: 本版本工作已拆为可领取的用户故事/工程治理故事，包含责任角色、依赖、页面、接口、数据、配置和验收条件
    适用性: 是
    证据: catalogs/release_story_backlog.csv;releases/R03/STORIES.yaml
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-12
    类别: 外部事项记录
    门禁条件: 需要SDK、数据库、供应商、域名、证书、签名、法务或上线验证的事项已分类到实施/外部/生产门禁；不再误标为开发前文档缺口
    适用性: 是
    证据: DEVELOPMENT_RISK_REGISTER.md;catalogs/development_risk_register.csv
    当前结论: PASS
    说明: 需求5条，页面/交互面7个，接口13个，故事4个
    阻断规则: 适用项非PASS时不得进入该版本编码；不适用必须有说明
    成熟度: FROZEN_DOR_V1.2.2
  - 版本: R03
    门禁ID: R03-DOR-CONTINUITY
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
  release: R03
  title: 供应商配置、密钥、证书与域名中心
  source_of_truth: catalogs/release_story_backlog.csv
  rules:
  - 故事未通过definition_of_ready不得进入编码
  - 页面故事不得增加未登记字段、状态、按钮、接口或配置
  - 工程治理故事负责契约、数据、配置、测试、观测和交接
  stories:
  - story_id: STORY-R03-001
    release: R03
    title: 配置中心：供应商配置中心、阿里云短信配置、对象存储配置、实名认证配置
    user_story: 作为具备 config.manage 的后台员工；写操作另需 config.manage，我需要完成供应商配置中心、阿里云短信配置、对象存储配置、实名认证配置的完整业务流程，以便在不依赖研发临时决策的情况下实现按短信、对象存储、实名、支付、出款分组管理，密钥脱敏、版本、测试、激活、回滚。
    platform: ADMIN
    module: 配置中心
    template_id: ADM-CONFIG
    page_ids:
    - ADM-CONFIG-002
    - ADM-CONFIG-003
    - ADM-CONFIG-004
    - ADM-CONFIG-007
    operation_ids:
    - adminConfigGetConfigs
    - adminConfigGetFeatureFlags
    - adminProviderConfigGetProviderConfigs
    - adminProviderConfigGetProviderConfigsByProvider
    - adminProviderCertificateGetProviderCertificates
    - adminProviderCertificatePostProviderCertificatesByIdRotate
    - adminDomainConfigGetDomains
    - adminDomainConfigGetDomainsDnsActions
    - adminProviderConfigPostProviderConfigsByProviderActivate
    - adminProviderConfigPostProviderConfigsByProviderRollback
    - adminProviderCertificatePostProviderCertificates
    - adminConfigPutConfigsByKey
    - adminConfigPutFeatureFlagsByKey
    - adminProviderConfigPostProviderConfigsByProviderVersions
    - adminProviderConfigPostProviderConfigsByProviderTest
    api_contracts:
    - GET /admin-api/v1/configs
    - GET /admin-api/v1/feature-flags
    - GET /admin-api/v1/provider-configs
    - GET /admin-api/v1/provider-configs/{provider}
    - GET /admin-api/v1/provider-certificates
    - POST /admin-api/v1/provider-certificates/{id}/rotate
    - GET /admin-api/v1/domains
    - GET /admin-api/v1/domains/dns-actions
    - POST /admin-api/v1/provider-configs/{provider}/activate
    - POST /admin-api/v1/provider-configs/{provider}/rollback
    - POST /admin-api/v1/provider-certificates
    - PUT /admin-api/v1/configs/{key}
    - PUT /admin-api/v1/feature-flags/{key}
    - POST /admin-api/v1/provider-configs/{provider}/versions
    - POST /admin-api/v1/provider-configs/{provider}/test
    requirement_ids:
    - REQ-CONFIG-001
    - REQ-SECRET-001
    - REQ-PAGE-SPEC-001
    - REQ-ADMIN-OPS-001
    config_keys:
    - system.maintenance.enabled
    - system.maintenance.message
    - system.registration.enabled
    - system.publish.enabled
    - system.red_packet.enabled
    - system.withdrawal.enabled
    - platform.base_domain
    - domain.www.host
    - domain.api.host
    - domain.ws.host
    - domain.admin.host
    - domain.h5.host
    - domain.download.host
    - domain.assets.host
    data_tables:
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
    - admin_users
    - admin_roles
    - admin_permissions
    - admin_user_roles
    - admin_role_permissions
    - admin_operation_logs
    test_ids:
    - TST-CONFIG_001-HAPPY
    - TST-SECRET_001-HAPPY
    - TST-CONFIG_001-IDEMPOTENT
    - TST-SECRET_001-IDEMPOTENT
    - TST-V122-030
    - TST-V122-031
    - TST-V122-032
    dependencies:
    - R01
    owner_roles:
    - admin_frontend_engineer
    - backend_engineer
    - test_engineer
    acceptance_criteria:
    - ADM-CONFIG-002;ADM-CONFIG-003;ADM-CONFIG-004;ADM-CONFIG-007 全部绑定模板 ADM-CONFIG，字段、状态、动作、导航和错误恢复无TBD
    - 页面调用 operationId：adminConfigGetConfigs;adminConfigGetFeatureFlags;adminProviderConfigGetProviderConfigs;adminProviderConfigGetProviderConfigsByProvider;adminProviderCertificateGetProviderCertificates;adminProviderCertificatePostProviderCertificatesByIdRotate;adminDomainConfigGetDomains;adminDomainConfigGetDomainsDnsActions;adminProviderConfigPostProviderConfigsByProviderActivate;adminProviderConfigPostProviderConfigsByProviderRollback;adminProviderCertificatePostProviderCertificates;adminConfigPutConfigsByKey;adminConfigPutFeatureFlagsByKey;adminProviderConfigPostProviderConfigsByProviderVersions;adminProviderConfigPostProviderConfigsByProviderTest；请求/响应字段不得另行发明
    - 按钮显示、可用和确认条件与服务端状态机、权限、expectedVersion、幂等策略一致
    - 首屏、空、刷新、翻页/局部失败、无权限、404、离线和版本冲突状态按页面状态目录实现
    - 敏感字段按分级脱敏；高敏读取和后台写操作完整审计
    - 自动化覆盖：TST-CONFIG_001-HAPPY;TST-SECRET_001-HAPPY;TST-CONFIG_001-IDEMPOTENT;TST-SECRET_001-IDEMPOTENT;TST-V122-030;TST-V122-031;TST-V122-032
    definition_of_ready: 全部引用的页面/字段/状态/动作/API/配置/数据/测试均为FROZEN且无空缺
    status: READY_FOR_IMPLEMENTATION
  - story_id: STORY-R03-002
    release: R03
    title: 配置中心：域名与环境配置
    user_story: 作为具备 config.manage 的后台员工；写操作另需 config.manage，我需要完成域名与环境配置的完整业务流程，以便在不依赖研发临时决策的情况下实现orbexa.cc二级域名、环境映射、DNS待办和验证状态。
    platform: ADMIN
    module: 配置中心
    template_id: ADM-CONFIG
    page_ids:
    - ADM-CONFIG-008
    operation_ids:
    - adminConfigGetConfigs
    - adminConfigPutConfigsByKey
    - adminConfigGetFeatureFlags
    - adminConfigPutFeatureFlagsByKey
    - adminProviderConfigGetProviderConfigs
    - adminProviderConfigGetProviderConfigsByProvider
    - adminProviderConfigPostProviderConfigsByProviderVersions
    - adminProviderConfigPostProviderConfigsByProviderTest
    - adminDomainConfigPutDomainsByCode
    - adminDomainConfigPostDomainsByCodeVerify
    api_contracts:
    - GET /admin-api/v1/configs
    - PUT /admin-api/v1/configs/{key}
    - GET /admin-api/v1/feature-flags
    - PUT /admin-api/v1/feature-flags/{key}
    - GET /admin-api/v1/provider-configs
    - GET /admin-api/v1/provider-configs/{provider}
    - POST /admin-api/v1/provider-configs/{provider}/versions
    - POST /admin-api/v1/provider-configs/{provider}/test
    - PUT /admin-api/v1/domains/{code}
    - POST /admin-api/v1/domains/{code}/verify
    requirement_ids:
    - REQ-CONFIG-001
    - REQ-SECRET-001
    - REQ-PAGE-SPEC-001
    - REQ-ADMIN-OPS-001
    config_keys:
    - system.maintenance.enabled
    - system.maintenance.message
    - system.registration.enabled
    - system.publish.enabled
    - system.red_packet.enabled
    - system.withdrawal.enabled
    data_tables:
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
    - admin_users
    - admin_roles
    - admin_permissions
    - admin_user_roles
    - admin_role_permissions
    - admin_operation_logs
    test_ids:
    - TST-CONFIG_001-HAPPY
    - TST-SECRET_001-HAPPY
    - TST-CONFIG_001-IDEMPOTENT
    - TST-SECRET_001-IDEMPOTENT
    - TST-V122-033
    - TST-V122-034
    dependencies:
    - R01
    owner_roles:
    - admin_frontend_engineer
    - backend_engineer
    - test_engineer
    acceptance_criteria:
    - ADM-CONFIG-008 全部绑定模板 ADM-CONFIG，字段、状态、动作、导航和错误恢复无TBD
    - 页面调用 operationId：adminConfigGetConfigs;adminConfigPutConfigsByKey;adminConfigGetFeatureFlags;adminConfigPutFeatureFlagsByKey;adminProviderConfigGetProviderConfigs;adminProviderConfigGetProviderConfigsByProvider;adminProviderConfigPostProviderConfigsByProviderVersions;adminProviderConfigPostProviderConfigsByProviderTest;adminDomainConfigPutDomainsByCode;adminDomainConfigPostDomainsByCodeVerify；请求/响应字段不得另行发明
    - 按钮显示、可用和确认条件与服务端状态机、权限、expectedVersion、幂等策略一致
    - 首屏、空、刷新、翻页/局部失败、无权限、404、离线和版本冲突状态按页面状态目录实现
    - 敏感字段按分级脱敏；高敏读取和后台写操作完整审计
    - 自动化覆盖：TST-CONFIG_001-HAPPY;TST-SECRET_001-HAPPY;TST-CONFIG_001-IDEMPOTENT;TST-SECRET_001-IDEMPOTENT;TST-V122-033;TST-V122-034
    definition_of_ready: 全部引用的页面/字段/状态/动作/API/配置/数据/测试均为FROZEN且无空缺
    status: READY_FOR_IMPLEMENTATION
  - story_id: STORY-R03-003
    release: R03
    title: 配置中心：支付网关配置、支付宝企业付款配置
    user_story: 作为具备 config.manage 的后台员工；写操作另需 config.manage，我需要完成支付网关配置、支付宝企业付款配置的完整业务流程，以便在不依赖研发临时决策的情况下实现彩虹易支付网关、商户、签名、渠道、回调和连接测试。
    platform: ADMIN
    module: 配置中心
    template_id: ADM-FINANCE
    page_ids:
    - ADM-CONFIG-005
    - ADM-CONFIG-006
    operation_ids:
    - adminConfigGetConfigs
    - adminConfigPutConfigsByKey
    - adminConfigGetFeatureFlags
    - adminConfigPutFeatureFlagsByKey
    - adminProviderConfigGetProviderConfigs
    - adminProviderConfigGetProviderConfigsByProvider
    - adminProviderConfigPostProviderConfigsByProviderVersions
    - adminProviderConfigPostProviderConfigsByProviderTest
    api_contracts:
    - GET /admin-api/v1/configs
    - PUT /admin-api/v1/configs/{key}
    - GET /admin-api/v1/feature-flags
    - PUT /admin-api/v1/feature-flags/{key}
    - GET /admin-api/v1/provider-configs
    - GET /admin-api/v1/provider-configs/{provider}
    - POST /admin-api/v1/provider-configs/{provider}/versions
    - POST /admin-api/v1/provider-configs/{provider}/test
    requirement_ids:
    - REQ-CONFIG-001
    - REQ-SECRET-001
    config_keys:
    - system.maintenance.enabled
    - system.maintenance.message
    - system.registration.enabled
    - system.publish.enabled
    - system.red_packet.enabled
    - system.withdrawal.enabled
    data_tables:
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
    test_ids:
    - TST-CONFIG_001-HAPPY
    - TST-SECRET_001-HAPPY
    - TST-CONFIG_001-IDEMPOTENT
    - TST-SECRET_001-IDEMPOTENT
    dependencies:
    - R01
    owner_roles:
    - admin_frontend_engineer
    - backend_engineer
    - test_engineer
    acceptance_criteria:
    - ADM-CONFIG-005;ADM-CONFIG-006 全部绑定模板 ADM-FINANCE，字段、状态、动作、导航和错误恢复无TBD
    - 页面调用 operationId：adminConfigGetConfigs;adminConfigPutConfigsByKey;adminConfigGetFeatureFlags;adminConfigPutFeatureFlagsByKey;adminProviderConfigGetProviderConfigs;adminProviderConfigGetProviderConfigsByProvider;adminProviderConfigPostProviderConfigsByProviderVersions;adminProviderConfigPostProviderConfigsByProviderTest；请求/响应字段不得另行发明
    - 按钮显示、可用和确认条件与服务端状态机、权限、expectedVersion、幂等策略一致
    - 首屏、空、刷新、翻页/局部失败、无权限、404、离线和版本冲突状态按页面状态目录实现
    - 敏感字段按分级脱敏；高敏读取和后台写操作完整审计
    - 自动化覆盖：TST-CONFIG_001-HAPPY;TST-SECRET_001-HAPPY;TST-CONFIG_001-IDEMPOTENT;TST-SECRET_001-IDEMPOTENT
    definition_of_ready: 全部引用的页面/字段/状态/动作/API/配置/数据/测试均为FROZEN且无空缺
    status: READY_FOR_IMPLEMENTATION
  - story_id: STORY-R03-004
    release: R03
    title: 供应商配置、密钥、证书与域名中心：契约、数据、配置、测试与交接
    user_story: 作为技术负责人，我需要按冻结的 R03 契约和DoR完成数据、后端、配置、测试、观测与交接，使前端故事能够稳定落地并可追溯。
    platform: CROSS_PLATFORM
    module: 工程治理
    template_id: N/A
    page_ids:
    - ADM-CONFIG-002
    - ADM-CONFIG-003
    - ADM-CONFIG-004
    - ADM-CONFIG-005
    - ADM-CONFIG-006
    - ADM-CONFIG-007
    - ADM-CONFIG-008
    operation_ids:
    - adminProviderConfigGetProviderConfigs
    - adminProviderConfigGetProviderConfigsByProvider
    - adminProviderConfigPostProviderConfigsByProviderVersions
    - adminProviderConfigPostProviderConfigsByProviderTest
    - adminProviderConfigPostProviderConfigsByProviderActivate
    - adminProviderConfigPostProviderConfigsByProviderRollback
    - adminProviderCertificatePostProviderCertificates
    - adminProviderCertificateGetProviderCertificates
    - adminProviderCertificatePostProviderCertificatesByIdRotate
    - adminDomainConfigGetDomains
    - adminDomainConfigPutDomainsByCode
    - adminDomainConfigGetDomainsDnsActions
    - adminDomainConfigPostDomainsByCodeVerify
    api_contracts:
    - GET /admin-api/v1/provider-configs
    - GET /admin-api/v1/provider-configs/{provider}
    - POST /admin-api/v1/provider-configs/{provider}/versions
    - POST /admin-api/v1/provider-configs/{provider}/test
    - POST /admin-api/v1/provider-configs/{provider}/activate
    - POST /admin-api/v1/provider-configs/{provider}/rollback
    - POST /admin-api/v1/provider-certificates
    - GET /admin-api/v1/provider-certificates
    - POST /admin-api/v1/provider-certificates/{id}/rotate
    - GET /admin-api/v1/domains
    - PUT /admin-api/v1/domains/{code}
    - GET /admin-api/v1/domains/dns-actions
    - POST /admin-api/v1/domains/{code}/verify
    requirement_ids:
    - REQ-CONFIG-001
    - REQ-DOMAIN-001
    - REQ-STORAGE-001
    - REQ-SECRET-001
    - REQ-CONFIG-UX-001
    config_keys:
    - system.maintenance.enabled
    - system.maintenance.message
    - system.registration.enabled
    - system.publish.enabled
    - system.red_packet.enabled
    - system.withdrawal.enabled
    - platform.base_domain
    - domain.www.host
    - domain.api.host
    - domain.ws.host
    - domain.admin.host
    - domain.h5.host
    - domain.download.host
    - domain.assets.host
    data_tables:
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
    - home_modules
    - banners
    - cms_articles
    - agreements
    - agreement_versions
    - user_agreement_acceptances
    - h5_page_configs
    - app_versions
    - app_download_logs
    - media_objects
    - upload_sessions
    - media_access_tokens
    - storage_scope_bindings
    - storage_migration_jobs
    - admin_operation_logs
    test_ids:
    - TST-CONFIG_001-HAPPY
    - TST-CONFIG_001-IDEMPOTENT
    - TST-CONFIG_001-REJECT
    - TST-CONFIG_UX_001-DRIFT
    - TST-CONFIG_UX_001-HAPPY
    - TST-CONFIG_UX_001-REJECT
    - TST-CONFIG_UX_001-SECURITY
    - TST-DOMAIN_001-HAPPY
    - TST-DOMAIN_001-IDEMPOTENT
    - TST-DOMAIN_001-REJECT
    - TST-STORAGE_001-HAPPY
    - TST-STORAGE_001-IDEMPOTENT
    - TST-STORAGE_001-REJECT
    - TST-V122-030
    - TST-V122-031
    - TST-V122-032
    - TST-V122-033
    - TST-V122-034
    dependencies:
    - R01
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
TASKS.yaml:
  release: R03
  title: 供应商配置、密钥、证书与域名中心
  tasks:
  - id: TASK-R03-001
    title: 供应商配置、密钥、证书与域名中心开发就绪核验、故事领取与变更基线
    status: DONE
    depends_on: []
    requirements: &id001
    - REQ-CONFIG-001
    - REQ-DOMAIN-001
    - REQ-STORAGE-001
    - REQ-SECRET-001
    - REQ-CONFIG-UX-001
    - REQ-APK-001
    description: 核验6项需求、13个本版本后台接口、26张相关表、7个后台页面、4个故事和18项测试；即使无Android页面变更也必须交付回归APK。
    deliverables:
    - releases/R03/DEFINITION_OF_READY.yaml 全部适用项PASS
    - releases/R03/STORIES.yaml 故事责任人和依赖已领取
    - releases/R03/PARALLEL_EXECUTION_PLAN.yaml 的三条业务切片与互斥路径已冻结
    - R03回归APK、固定Staging签名、桌面副本和真机验收门禁明确
    - python scripts/check_v122_documentation.py --release R03
    acceptance:
    - 页面、字段、状态、动作、API、配置、数据和测试无TBD
    - 不适用项明确N/A及原因
    - 供应商、域名、支付/出款的共享API所有权和主控合并边界明确
    - python scripts/check_v122_documentation.py --release R03
    session_log_required: true
    completed_at: '2026-07-18T13:30:28Z'
  - id: TASK-R03-002
    title: 纵向批次A：短信、存储与实名供应商配置闭环
    status: DONE
    depends_on:
    - TASK-R03-001
    parallelizable_with:
    - TASK-R03-003
    - TASK-R03-004
    stories:
    - STORY-R03-001
    requirements: *id001
    description: 完成供应商定义、不可变版本、连接测试、SecretRef、证书状态、双人审批、激活/回滚、后台页面和自动化测试的真实闭环。
    deliverables:
    - 短信、R2/OSS和实名配置的数据、API、Admin页面与测试闭环
    - 连接测试失败不得激活，秘密只暴露配置状态和SecretRef元数据
    - 幂等、乐观锁、审批、审计和回滚证据
    acceptance:
    - 无TODO/生产Mock或明文秘密
    - STORY-R03-001全部页面动作使用冻结生成类型
    - MODULE门禁通过并可独立交给主控集成
    session_log_required: true
    completed_at: '2026-07-18T14:21:44Z'
  - id: TASK-R03-003
    title: 纵向批次B：orbexa.cc域名与环境配置闭环
    status: DONE
    depends_on:
    - TASK-R03-001
    parallelizable_with:
    - TASK-R03-002
    - TASK-R03-004
    stories:
    - STORY-R03-002
    requirements: *id001
    description: 完成域名版本、环境隔离、DNS动作、DNS/TLS/服务健康验证、负责人和截止条件、Admin页面及自动化测试。
    deliverables:
    - 域名数据、API、Admin页面和测试真实闭环
    - api/download当前验证状态与其余域名待办可追踪
    - expectedVersion、重复verify幂等和失败阻断证据
    acceptance:
    - 不把DNS解析成功等同于服务健康或生产激活
    - STORY-R03-002全部页面动作使用冻结生成类型
    - MODULE门禁通过并可独立交给主控集成
    session_log_required: true
    completed_at: '2026-07-18T14:52:05Z'
  - id: TASK-R03-004
    title: 纵向批次C：支付、出款与证书生命周期配置闭环
    status: DONE
    depends_on:
    - TASK-R03-001
    parallelizable_with:
    - TASK-R03-002
    - TASK-R03-003
    stories:
    - STORY-R03-003
    requirements: *id001
    description: 完成支付网关、支付宝出款和证书上传/轮换的版本化、SecretRef、双人审批、连接测试、后台页面和安全测试。
    deliverables:
    - 支付、出款、证书的数据、API、Admin页面与测试闭环
    - 证书原文不可回读，指纹、轮换、访问和审批审计完整
    - 幂等、乐观锁、错误码和回滚证据
    acceptance:
    - 无明文私钥、口令或生产凭据进入仓库和日志
    - STORY-R03-003全部页面动作使用冻结生成类型
    - MODULE门禁通过并可独立交给主控集成
    session_log_required: true
    completed_at: '2026-07-18T15:18:31Z'
  - id: TASK-R03-005
    title: 三批次汇合、工程治理、专项测试与故障注入
    status: DONE
    depends_on:
    - TASK-R03-002
    - TASK-R03-003
    - TASK-R03-004
    stories:
    - STORY-R03-004
    requirements: *id001
    description: 主控汇合三条业务切片，统一共享迁移、权限、SecretRef、审批、审计、契约生成，并执行18项权威测试和供应商故障注入。
    deliverables:
    - Flyway空库、升级库、回滚和数据不变量证据
    - 18项权威测试、契约、页面动作、安全和故障注入报告
    - 生成物漂移为零，关键缺陷清零
    acceptance:
    - STORY-R03-004工程治理验收全部通过
    - INTEGRATION门禁、数据库测试和安全专项全部PASS
    - 共享API和DTO只有主控最终集成版本
    session_log_required: true
    completed_at: '2026-07-18T16:21:43Z'
  - id: TASK-R03-006
    title: 可观测性、Staging部署与回滚演练
    status: DONE
    depends_on:
    - TASK-R03-005
    requirements: *id001
    description: 部署Staging，验证结构化日志、TraceId、RED指标、连接测试指标、业务告警、配置激活和回滚演练。
    deliverables:
    - 运行手册与回滚演练证据
    - 验收矩阵自动化条目签字
    acceptance:
    - 日志不包含秘密、证书原文或敏感供应商响应
    - Staging与INTEGRATION门禁通过
    session_log_required: true
    completed_at: '2026-07-18T16:56:21Z'
  - id: TASK-R03-007
    title: 外部激活证据、Android回归APK与产物追溯
    status: BLOCKED
    depends_on:
    - TASK-R03-006
    requirements: *id001
    description: 完成短信、实名、支付、出款、存储的测试环境连接与脱敏证据；使用固定Staging签名构建回归APK，完成桌面、服务器、公网下载和项目所有者真机验收。
    deliverables:
    - 外部激活清单、连接报告、SecretRef和证书指纹
    - DNS、TLS、服务健康三层状态和责任人
    - APK Commit、versionName、versionCode、签名指纹、四方SHA和桌面副本
    acceptance:
    - 每项外部依赖为VERIFIED或具备负责人、截止版本和阻断状态
    - 连接测试失败不得激活对应配置版本
    - APK机器交付PASS和项目所有者真机PASS分别记录；真机反馈前保持PENDING
    session_log_required: true
    blocker: R03机器实现、测试、外部清单和固定签名APK四方交付均PASS；等待项目所有者对hhy-r03-3a913c9-debug.apk完成真机安装、启动与自动验证码路径验收
    blocked_at: '2026-07-18T17:24:49Z'
  - id: TASK-R03-008
    title: 供应商配置、密钥、证书与域名中心版本关闭与无状态交接
    status: BLOCKED
    depends_on:
    - TASK-R03-007
    requirements: *id001
    description: 回填追踪矩阵、Session Log、问题登记、可复用模式、CURRENT_STATUS和NEXT_TASK。
    deliverables:
    - 全新AI仅凭仓库可继续
    - 工作区干净且Tag可追溯
    acceptance:
    - 无TODO/生产Mock或未登记外部阻断
    - 代码、文档、测试、追踪同步更新
    - 全新AI仅凭仓库可继续
    session_log_required: true
  version: 1.2.2
  definition_of_ready: releases/R03/DEFINITION_OF_READY.yaml
  story_backlog: releases/R03/STORIES.yaml
  execution_rule: TASK-R03-001完成后，TASK-R03-002至004是三个候选纵向业务切片；现行协议下一次只领取一个Task并在Claim内部三分区并行。TASK-R03-005由主控汇合；TASK-R03-007必须完成外部证据和回归APK桌面/公网/真机验收。
PARALLEL_EXECUTION_PLAN.yaml:
  version: '1.0'
  release: R03
  change_request: CR-0019
  mode: ONE_MASTER_THREE_DELEGATED_WORKERS
  authoritative_session_count: 1
  max_parallel_workers: 3
  simultaneous_claim_limit: 1
  worker_parallelism_scope: WITHIN_ACTIVE_TASK_ONLY
  default_delegation_mode: AUTO_WHEN_SAFE_PARALLEL_WORK_EXISTS
  review_triggers:
  - TASK_START
  - SCOPE_CHANGE
  per_task_user_confirmation_required: false
  non_delegation_requires_checkpoint_reason: true
  capability_fallback: RECORD_LIMITATION_AND_DO_NOT_FABRICATE_PARALLEL_EVIDENCE
  user_override_allowed: true
  source_of_truth_branch: task/TASK-R03-001
  rules:
  - 主控一次只领取一个Task/Story；三条业务切片是依赖满足后的候选集合，不是三个并行事实分支。
  - 当前Claim内最多三个执行代理按后端数据、Admin客户端、质量证据的互斥路径并行。
  - 执行代理不得修改连续性、Release状态、公共契约、共享生成类型和Flyway编号，不得提交、推送、合并或发布。
  - 共享API、DTO、迁移、权限、SecretRef、审批和审计由主控先冻结边界并最终集成。
  - 供应商测试环境、DNS、证书和SecretRef只能记录真实脱敏证据；缺失项保持BLOCKED。
  worker_partitions:
  - partition: BACKEND_DATA
    allowed_paths:
    - services/backend/**
    - database/**
    forbidden_shared_paths:
    - services/backend/boot/src/main/resources/contracts/**
  - partition: ADMIN_CLIENT
    allowed_paths:
    - apps/admin-web/**
    forbidden_shared_paths:
    - apps/admin-web/src/router.ts
    - apps/admin-web/src/App.vue
  - partition: QUALITY_EVIDENCE
    allowed_paths:
    - tests/r03/**
    - artifacts/validation/r03-*/**
    forbidden_shared_paths: []
  coordinator_owned_paths:
  - .continuity/**
  - CURRENT_STATUS.yaml
  - NEXT_TASK.yaml
  - releases/**
  - contracts/**
  - packages/api-client/**
  - catalogs/**
  - docs/03-continuity/**
  - services/backend/boot/src/main/resources/db/migration/**
  lanes:
  - lane: SLICE-A-PROVIDERS
    task_id: TASK-R03-002
    stories:
    - STORY-R03-001
    outcome: 短信、R2/OSS与实名供应商版本、连接测试、审批、激活/回滚及Admin页面端到端闭环
    preferred_agents:
    - backend_data
    - admin_client
    - quality
  - lane: SLICE-B-DOMAINS
    task_id: TASK-R03-003
    stories:
    - STORY-R03-002
    outcome: orbexa.cc域名版本、DNS动作、DNS/TLS/服务健康验证及Admin页面端到端闭环
    preferred_agents:
    - backend_data
    - admin_client
    - quality
  - lane: SLICE-C-FINANCIAL-PROVIDERS
    task_id: TASK-R03-004
    stories:
    - STORY-R03-003
    outcome: 支付、出款和证书生命周期的版本、审批、连接测试、激活/回滚及Admin页面端到端闭环
    preferred_agents:
    - backend_data_high_reasoning
    - admin_client
    - security_quality
  - lane: GOVERNANCE-INTEGRATION
    task_id: TASK-R03-005
    stories:
    - STORY-R03-004
    outcome: 三切片共享迁移、权限、SecretRef、审计、18项测试、故障注入和生成物汇合
    preferred_agents:
    - coordinator
  integration:
    task_id: TASK-R03-005
    depends_on:
    - TASK-R03-002
    - TASK-R03-003
    - TASK-R03-004
    profiles:
    - MODULE
    - INTEGRATION
  external_activation:
    task_id: TASK-R03-007
    apk_required: true
    required_evidence:
    - supplier_connection_tests
    - secret_refs
    - certificate_fingerprints
    - dns_tls_service_health
    - owners_and_deadlines_for_unfinished_items
  apk:
    task_id: TASK-R03-007
    regression_build_when_no_android_change: true
    desktop_copy_required: true
    public_download_required: true
    stable_test_signing_required: true
    minimum_version_code: 10203
    owner_physical_test_initial_status: PENDING
```

## 开放 CR

```yaml
- protocol_version: '1.0'
  cr_id: CR-0007
  title: 修复Spring Boot 4 staging Flyway自动迁移未启用
  status: IMPLEMENTED
  created_at: '2026-07-17T03:05:39Z'
  updated_at: '2026-07-17T03:18:29Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-engineering-audit
  task_id: TASK-P00-005
  session_id: SES-20260717T024407Z-B03C9375
  user_request: 完成P00后暂停推进
  reason: 真实staging空库启动后未创建Flyway历史表与198张业务表，运行手册承诺与实际依赖不一致
  original_rule: Spring Boot 4.1.0应用启动时由现有flyway-core依赖自动执行classpath迁移
  new_rule: 使用Spring Boot 4.1.0官方spring-boot-starter-flyway触发自动配置，保留PostgreSQL专用Flyway模块，启动失败即阻断发布
  impact_summary: 仅修复后端启动迁移链；不改变API、数据库DDL、业务语义或正式版本号
  impact:
    files:
    - services/backend/boot/pom.xml
    - services/backend/boot/src/test/java/cc/orbexa/hhy/P00FlywayAutoConfigurationTest.java
    - docs/03-continuity/PROBLEM_REGISTRY.yaml
    pages: []
    apis: []
    database: []
    configuration:
    - spring-boot-starter-flyway
    ledger: []
    tests:
    - services/backend/boot/src/test/java/cc/orbexa/hhy/P00FlywayAutoConfigurationTest.java
    releases:
    - P00
    migration_and_compatibility: 现有V001-V011文件不变；空库自动升级到V011，已升级库由Flyway校验后保持不变
  user_confirmation: 用户已授权开始项目开发，并要求P00完成后暂停推进
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T03:11:05Z'
    note: 真实staging空库复现且Spring Boot 4.1官方要求starter，范围和回归测试充分
  machine_record: .continuity/change_requests/CR-0007.yaml
  document: docs/03-continuity/change-requests/CR-0007-修复Spring-Boot-4-staging-Flyway自动迁移未启用.md
  decision_log:
  - at: '2026-07-17T03:11:40Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 按批准范围修复Boot4 Flyway自动配置并复验空库staging
    session_id: SES-20260717T024407Z-B03C9375
  - at: '2026-07-17T03:18:29Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 官方starter修复、回归测试和PostgreSQL17.10空库自动迁移均通过
    session_id: SES-20260717T024407Z-B03C9375
  session_ids:
  - SES-20260717T024407Z-B03C9375
  implementation_commits:
  - 7e6bfd70c9f4127f2778b1423d03ed619bcab25b
- protocol_version: '1.0'
  cr_id: CR-0010
  title: P00封板清单以OpenAPI operationId机器化绑定
  status: APPROVED
  created_at: '2026-07-17T05:44:40Z'
  updated_at: '2026-07-17T05:49:35Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-engineering-audit
  task_id: TASK-P00-008
  session_id: SES-20260717T054147Z-7AE51A86
  user_request: P00版本完成后先暂停推进；项目所有者已确认真机测试正常并要求尽快完成P00
  reason: 终态关闭门禁要求RELEASE_MANIFEST精确声明3个已实现operationId，现有HTTP路径列表无法通过机器校验
  original_rule: P00 RELEASE_MANIFEST.contracts.client_api仅记录3条HTTP方法与路径，status为READY且没有release_commit/release_tag
  new_rule: P00终态清单保留原路径并精确绑定3个现有OpenAPI operationId，status为DONE且绑定最终源码Commit与标签
  impact_summary: 仅变更P00发布元数据表达与验收状态，不修改OpenAPI、运行时代码、数据库、Android APK或线上服务
  impact:
    files:
    - releases/P00/RELEASE_MANIFEST.yaml
    - releases/P00/ACCEPTANCE_MATRIX.csv
    pages: []
    apis:
    - publicGetPlatformStatus
    - appReleasePostAppVersionCheck
    - appReleaseGetAppVersionCheck
    database: []
    configuration: []
    ledger: []
    tests:
    - python scripts/check_release_artifacts.py --release P00 --close-gate
    - python scripts/check_v123_documentation.py --strict --release P00
    releases:
    - P00
    migration_and_compatibility: 无需运行时迁移；保留原paths供人工阅读，新增operation_ids供机器门禁验证，现有消费者行为不变
  user_confirmation: P00版本完成后先暂停推进；项目所有者已确认真机测试正常并要求尽快完成P00
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T05:49:35Z'
    note: 确认仅收口P00 RELEASE_MANIFEST与ACCEPTANCE_MATRIX终态元数据：保留3条HTTP paths并绑定现有3个OpenAPI operationId，6项证据均为仓库内实际文件，59项矩阵PASS，release commit、tag与APK基线一致；禁止修改运行时代码或接口
  machine_record: .continuity/change_requests/CR-0010.yaml
  document: docs/03-continuity/change-requests/CR-0010-P00封板清单以OpenAPI-operationId机器化绑定.md
- protocol_version: '1.0'
  cr_id: CR-0017
  title: R01版本关闭元数据与无会话Context Pack门禁修复
  status: APPROVED
  created_at: '2026-07-17T17:22:49Z'
  updated_at: '2026-07-17T17:25:50Z'
  requester_actor_id: codex-root
  approver_actor_id: r01-cr-review
  task_id: TASK-R01-008
  session_id: SES-20260717T171412Z-7CD86701
  user_request: 项目所有者要求立即完成R01版本收尾并暂停
  reason: 将已完成的R01验收证据、APK来源提交和发布标签写入终态元数据，并修复无会话Context Pack生成后立即被判过期的关闭门禁缺陷。
  original_rule: R01 Release Manifest保持IN_PROGRESS，关闭验收矩阵未关联各任务的具体证据；无会话Context Pack把STATE.yaml计入仓库树指纹。
  new_rule: R01关闭时以既有8个接口中的3个代表operationId绑定Release Commit与Tag，验收矩阵引用既有报告；Context Pack仓库树指纹排除运行时STATE.yaml。
  impact_summary: 仅变更R01关闭元数据、证据指针、连续性运行时门禁和其隔离回归；不增加、删除或改变页面、API、数据库、配置、资金或业务语义。
  impact:
    files:
    - releases/R01/RELEASE_MANIFEST.yaml
    - releases/R01/ACCEPTANCE_MATRIX.csv
    - scripts/continuity_lib.py
    - tests/test_continuity_cross_release_close.py
    - docs/03-continuity/PROBLEM_REGISTRY.yaml
    pages: []
    apis:
    - R01既有8个admin API路径不变
    database: []
    configuration:
    - 无配置语义变更
    ledger: []
    tests:
    - tests/test_continuity_cross_release_close.py
    - tests/test_release_close_gate.py
    releases:
    - R01
    migration_and_compatibility: 无需数据库迁移或消费者迁移。保留原有8条API路径；仅将Manifest中的表示方式改为带operationId和paths的结构。旧Context Pack可重新生成。
  user_confirmation: 项目所有者于2026-07-18明确要求立即完成R01收尾并暂停；此前已确认R01 APK真机安装与启动通过。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T17:25:50Z'
    note: 独立审阅：实际差异仅含R01关闭元数据、验收证据指针、Context Pack运行时指纹排除与隔离回归；8条既有admin API路径逐项与HEAD基线一致，且首3个operationId与contracts/admin-openapi.yaml一致。未变更页面、API、数据库、配置、资金或业务语义；tests.test_continuity_cross_release_close与tests.test_release_close_gate共6项通过。
  machine_record: .continuity/change_requests/CR-0017.yaml
  document: docs/03-continuity/change-requests/CR-0017-R01版本关闭元数据与无会话Context-Pack门禁修复.md
- protocol_version: '1.0'
  cr_id: CR-0021
  title: 将1主控加3执行代理设为跨AI跨设备默认自动并行规则
  status: IMPLEMENTED
  created_at: '2026-07-17T22:16:06Z'
  updated_at: '2026-07-17T23:07:15Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-governance-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 用户于2026-07-18明确授权主控从现在起可自行使用多线程代理开发，无需逐次确认，并要求硬性记录为后续任意AI工具和任意电脑默认规则
  reason: 现有仓库仅记录最多3个执行代理的能力上限，未规定主控在可并行任务中默认自动启用，导致实际开发可能退化为单线程并依赖聊天授权
  original_rule: 仓库仅声明主控最多可委托3个执行代理；是否启用可能依赖当次聊天授权，未强制主控主动评估并行机会，也未结构化记录未委托原因或拒绝第4个活跃代理。
  new_rule: 项目所有者提供长期仓库级授权：主控必须在Task开始及范围变化时评估并行机会；存在边界清晰、路径互斥且可安全并行的工作包时，应自行启用1至3个执行代理且不得再次请求逐次授权；未委托时必须在Checkpoint记录原因。始终只允许1个ACTIVE主控Session、1个Claim、1个事实分支和1个提交/推送/发布者；高风险变更与最终集成由主控串行复核；不支持代理的AI必须记录能力限制。
  impact_summary: 将1主控加最多3执行代理的长期授权写入机器策略、启动入口、上下文交接包、计划生成器、并行worktree门禁、Checkpoint结构和严格测试，确保跨AI与跨设备恢复。
  impact:
    files:
    - AGENTS.md
    - START_HERE.md
    - .continuity/CONTINUITY_POLICY.yaml
    - config/CONTINUITY_POLICY.yaml
    - templates/AGENTS.md
    - releases/PROGRAM_EXECUTION_PLAN.yaml
    - releases/R02/PARALLEL_EXECUTION_PLAN.yaml
    - releases/R03/PARALLEL_EXECUTION_PLAN.yaml
    - releases/R04/PARALLEL_EXECUTION_PLAN.yaml
    - scripts/generate_program_execution_plan.py
    - scripts/check_program_execution_plan.py
    - tests/test_program_execution_plan.py
    - scripts/prepare_parallel_worktrees.ps1
    - tests/test_parallel_worktrees.py
    - scripts/check_v123_continuity.py
    - scripts/continuity.py
    - scripts/continuity_lib.py
    - scripts/test_continuity_protocol.py
    - docs/03-continuity/CHECKPOINT_SCHEMA.yaml
    - docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml
    - docs/03-continuity/全项目滚动开发总计划_R02-R32_V1.0.md
    - docs/03-continuity/并行加速开发运行手册_V1.0.md
    - artifacts/context/CURRENT_CONTEXT_PACK.yaml
    - artifacts/context/CURRENT_CONTEXT_PACK.md
    - artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
    - MANIFEST_SHA256.txt
    - CHANGELOG.md
    pages: []
    apis: []
    database: []
    configuration:
    - parallel_development.default_delegation_mode
    - parallel_development.per_task_user_confirmation_required
    - parallel_development.non_delegation_requires_checkpoint_reason
    - parallel_development.max_delegated_workers
    - checkpoint.parallel_execution
    ledger: []
    tests:
    - program-plan-default-delegation-strict-validation
    - parallel-worktree-three-worker-cap-and-atomic-registry
    - checkpoint-structured-parallel-decision-validation
    - context-pack-cross-ai-authorization-reconstruction
    - continuity-strict-and-secret-scope-gates
    - deterministic-plan-generation
    - documentation-strict-gate
    releases:
    - R02
    - R03
    - R04
    - R05
    - R06
    - R07
    - R08
    - R09
    - R10
    - R11
    - R12
    - R13
    - R14
    - R15
    - R16
    - R17
    - R18
    - R19
    - R20
    - R21
    - R22
    - R23
    - R24
    - R25
    - R26
    - R27
    - R28
    - R29
    - R30
    - R31
    - R32
    migration_and_compatibility: 纯工程治理与调度规则增强，不改变业务API、数据库、页面或发布语义；旧AI或不具备代理能力的环境允许单主控继续，但必须记录CAPABILITY_UNAVAILABLE且不得伪造并行证据。
  user_confirmation: 项目所有者于2026-07-18明确授权：从现在起主控可自行代理使用多线程开发，无需逐次确认；该规则硬性记录并作为后续任意AI工具及任意电脑开发的默认规则。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T22:25:22Z'
    note: 独立只读审查确认：长期授权应为MUST；实际委托为有安全并行工作时SHOULD；保留单主控、最多3执行代理、隔离路径租约、主控最终集成和高风险串行复核，并补齐容量、上下文与Checkpoint机器门禁。
  machine_record: .continuity/change_requests/CR-0021.yaml
  document: docs/03-continuity/change-requests/CR-0021-将1主控加3执行代理设为跨AI跨设备默认自动并行规则.md
  decision_log:
  - at: '2026-07-17T22:25:24Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 按已批准长期授权开始落地机器策略、上下文包、计划、并行worktree、Checkpoint和严格验证。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-17T23:07:15Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 长期1主控加最多3执行代理规则已写入权威策略、入口、计划、worktree与Checkpoint门禁，并通过模块及重建验证。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0022
  title: 补齐并行长期授权的模板与跨设备恢复回归
  status: IMPLEMENTED
  created_at: '2026-07-17T22:30:46Z'
  updated_at: '2026-07-17T23:07:17Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-governance-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求长期多代理授权在任意AI工具和任意电脑上默认恢复。
  reason: CR-0021独立审查后发现导出模板START_HERE和无状态恢复自测未列入原影响文件，若不补齐可能在新包或新电脑恢复时丢失授权。
  original_rule: 根入口可更新，但导出模板START_HERE仍可能保留旧规则，且自测未断言新目录只靠仓库恢复长期授权。
  new_rule: 导出模板必须携带与根入口一致的长期自动委托规则；生命周期自测和专门回归必须证明复制到新目录或新电脑后Context Pack仍显式恢复该授权。
  impact_summary: 补齐模板入口和跨设备Context Pack恢复回归，防止打包、换AI或换电脑时授权漂移。
  impact:
    files:
    - templates/START_HERE.md
    - scripts/run_continuity_self_test.py
    - tests/test_context_pack_parallel_policy.py
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - template-entry-parallel-policy-drift
    - repository-only-context-parallel-policy-reconstruction
    releases:
    - R02
    - R03
    - R04
    migration_and_compatibility: 只增强模板和测试，不改变业务行为；旧包重新生成后获得相同长期授权。
  user_confirmation: 项目所有者要求长期多代理默认规则可由其他AI工具和任意电脑仅凭项目包恢复。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T22:31:08Z'
    note: 独立审查已确认模板和无状态恢复回归是跨AI跨设备持久化的必要补充，范围仅限模板与测试。
  machine_record: .continuity/change_requests/CR-0022.yaml
  document: docs/03-continuity/change-requests/CR-0022-补齐并行长期授权的模板与跨设备恢复回归.md
  decision_log:
  - at: '2026-07-17T22:31:10Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 补齐模板入口与repository-only恢复测试。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-17T23:07:17Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 导出模板与repository-only Context Pack授权恢复回归已通过。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0023
  title: 补齐Checkpoint并行决策的提交门禁与兼容回归
  status: IMPLEMENTED
  created_at: '2026-07-17T22:36:43Z'
  updated_at: '2026-07-17T23:07:19Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-governance-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求默认多代理规则成为硬性、可跨AI恢复的仓库规则。
  reason: CR-0021引入结构化并行决策后，提交门禁与现有bootstrap/cross-release回归必须同步，否则规则不能在每次提交强制且测试会漂移。
  original_rule: Checkpoint和提交门禁只验证摘要、下一步、测试与指纹，不验证任务开始或范围变化时是否评估并行机会。
  new_rule: Checkpoint必须携带结构化parallel_execution；DELEGATED记录1至3个代理及互斥路径，未委托必须使用受控原因并填写说明；提交和严格门禁拒绝缺失、超过3个代理或伪造/空原因。
  impact_summary: 让长期自动委托授权进入每个Checkpoint和提交门禁，并同步现有生命周期回归。
  impact:
    files:
    - scripts/continuity_gate.py
    - tests/test_continuity_bootstrap_recovery.py
    - tests/test_continuity_cross_release_close.py
    - tests/test_parallel_checkpoint_policy.py
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - checkpoint-parallel-policy-strict-gate
    - bootstrap-parallel-decision-compatibility
    - cross-release-parallel-decision-compatibility
    releases:
    - R02
    - R03
    - R04
    migration_and_compatibility: 现有历史Checkpoint保持可读；新建Checkpoint必须提供结构化并行决策，测试夹具显式记录NO_SAFE_PARALLEL原因。
  user_confirmation: 项目所有者要求长期多代理默认规则硬性记录并供后续AI与电脑默认执行。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T22:37:05Z'
    note: 独立审查要求Checkpoint结构化并行决策必须由提交门禁验证，并同步既有生命周期回归；影响限于门禁和测试。
  machine_record: .continuity/change_requests/CR-0023.yaml
  document: docs/03-continuity/change-requests/CR-0023-补齐Checkpoint并行决策的提交门禁与兼容回归.md
  decision_log:
  - at: '2026-07-17T22:37:07Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 实现Checkpoint并行决策与提交门禁。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-17T23:07:19Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Checkpoint结构化并行决策、提交门禁与兼容回归已完成。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0024
  title: 固化跨电脑Git推送、模型分级与云端既有环境前置规则
  status: IMPLEMENTED
  created_at: '2026-07-17T23:18:43Z'
  updated_at: '2026-07-18T00:01:48Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-runtime-governance-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求换电脑或换AI后可恢复Git远程并正常推送；复杂/中等/轻量场景分别自动选择Sol/Terra/Luna；Codex默认已连接obx-test且既有环境可用，连接异常必须阻断而不能无服务器开发或重建本地Android环境。
  reason: 当前Git remote仅存在.git/config，Git Bundle不携带remote/upstream；模型路由和云端环境只存在聊天/会话日志，无法被新AI和新电脑可靠恢复。
  original_rule: GitHub remote和upstream仅存在本机.git/config，Handoff Git Bundle不携带；模型选择依赖当前客户端默认，未按复杂度路由；obx-test与固定Android镜像只在会话日志中，换AI或电脑可能误判为无服务器并重建本地环境。
  new_rule: 仓库跟踪非秘密Git transport descriptor并提供受控恢复/推送预检；复杂、高风险任务选择Sol，中等任务选择Terra，轻量任务选择Luna，模型不可用时必须可审计fallback且不得伪称；正常开发硬前提为Codex已连接obx-test且既有云环境可用，启动必须预检，失败立即阻断并报告，禁止以无服务器状态继续或重建本地Android
    SDK，Android固定复用hhy-android-toolchain:r01-46fb273与hhy-r01-android-gradle-cache。
  impact_summary: 把远程仓库、分支推送、模型分级和云端既有环境从本机/聊天事实提升为跨AI跨电脑的跟踪配置、Context Pack、恢复脚本和严格门禁。
  impact:
    files:
    - AGENTS.md
    - START_HERE.md
    - templates/AGENTS.md
    - templates/START_HERE.md
    - README.md
    - CHANGELOG.md
    - .gitignore
    - .continuity/CONTINUITY_POLICY.yaml
    - config/CONTINUITY_POLICY.yaml
    - config/REPOSITORY_TRANSPORT.yaml
    - config/DEVELOPMENT_RUNTIME.yaml
    - PROJECT_MANIFEST.yaml
    - apps/android/README.md
    - scripts/continuity.py
    - scripts/continuity_lib.py
    - scripts/continuity_gate.py
    - scripts/check_v123_continuity.py
    - scripts/restore_git_transport.py
    - scripts/select_execution_profile.py
    - scripts/verify_cloud_environment.py
    - scripts/run_continuity_self_test.py
    - tests/test_git_transport_recovery.py
    - tests/test_model_routing.py
    - tests/test_cloud_environment.py
    - tests/test_context_pack_parallel_policy.py
    - docs/03-continuity/CONTEXT_PACK_SCHEMA.yaml
    - docs/03-continuity/无状态接续运行手册_V1.2.3.md
    - .github/workflows/continuity-gate.yml
    - artifacts/context/CURRENT_CONTEXT_PACK.yaml
    - artifacts/context/CURRENT_CONTEXT_PACK.md
    - artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json
    pages: []
    apis: []
    database: []
    configuration:
    - git_transport
    - runtime_policy.task_model_routing
    - runtime_policy.execution_environment
    - context.development_runtime
    ledger: []
    tests:
    - git-transport-three-mode-recovery
    - git-push-preflight-no-credentials-no-force
    - model-routing-sol-terra-luna-and-auditable-fallback
    - cloud-obx-test-required-preflight-and-fixed-android-image
    - context-pack-runtime-policy-reconstruction
    - continuity-strict-and-documentation-gate
    releases:
    - R02
    - R03
    - R04
    - R05
    - R06
    - R07
    - R08
    - R09
    - R10
    - R11
    - R12
    - R13
    - R14
    - R15
    - R16
    - R17
    - R18
    - R19
    - R20
    - R21
    - R22
    - R23
    - R24
    - R25
    - R26
    - R27
    - R28
    - R29
    - R30
    - R31
    - R32
    migration_and_compatibility: 不改变业务API、数据库或页面；remote URL和SSH alias可跟踪但PAT、私钥和credential内容禁止入库。已有.git仓库校验/修复origin与upstream；Git Bundle恢复后重建transport；纯源码无历史时禁止覆盖远端。运行时缺Luna时退回Terra低推理并记录，不得冒充Luna。
  user_confirmation: 项目所有者于2026-07-18明确确认：若未主动说明未连接，必须默认Codex客户端已连接项目云服务器且已有对应环境，禁止按无服务器状态开发或重建本地Android环境；同时要求跨电脑Git推送恢复及Sol/Terra/Luna分级成为硬性规则。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T23:21:03Z'
    note: 独立审核通过：规则与用户最新澄清一致；默认云端已连接且既有环境可用，只有用户明确报告未连接时才能改变此前提。Git传输仅跟踪非秘密信息，模型降级必须审计。
  machine_record: .continuity/change_requests/CR-0024.yaml
  document: docs/03-continuity/change-requests/CR-0024-固化跨电脑Git推送、模型分级与云端既有环境前置规则.md
  decision_log:
  - at: '2026-07-17T23:21:06Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始实现跨电脑Git transport、模型分级与云端既有环境强制前置门禁。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T00:01:48Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Git transport、模型分级、obx-test既有环境预检、Context Pack与严格门禁均已实现；专项、真实云端、14项生命周期和11项仓库重建通过。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0025
  title: 绑定Git pre-push实际目标以消除跨remote绕过
  status: IMPLEMENTED
  created_at: '2026-07-17T23:31:46Z'
  updated_at: '2026-07-18T00:01:49Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-git-safety-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求跨电脑和跨AI均可安全恢复并正常推送，且推送必须不可绕过transport规则。
  reason: CR-0024集成复核发现现有pre-push Hook未把Git传入的remote名称和URL交给门禁，直接push到非origin目标可能绕过受控descriptor校验。
  original_rule: pre-push Hook仅调用通用门禁，不传递Git提供的实际remote名称和URL，门禁只能读取tracked origin。
  new_rule: pre-push Hook必须把实际remote名称和URL传入continuity_gate；门禁将其与tracked descriptor精确比对，任何非origin或URL漂移均拒绝。
  impact_summary: 消除直接git push到错误remote时绕过transport descriptor的路径。
  impact:
    files:
    - .githooks/pre-push
    - scripts/continuity_gate.py
    - tests/test_git_transport_recovery.py
    pages: []
    apis: []
    database: []
    configuration:
    - git_transport.actual_push_target
    ledger: []
    tests:
    - pre-push hook actual remote and URL rejection
    releases:
    - R02
    migration_and_compatibility: 仅扩展Hook与门禁参数；正常origin推送保持兼容，错误remote/URL将被明确拒绝。
  user_confirmation: 项目所有者已要求跨电脑/跨AI能够正常且安全推送；该修复是CR-0024推送安全规则的必要实现。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T23:32:08Z'
    note: 独立安全复核通过：透传Git实际目标是实现不可绕过推送门禁所必需的最小变更。
  machine_record: .continuity/change_requests/CR-0025.yaml
  document: docs/03-continuity/change-requests/CR-0025-绑定Git-pre-push实际目标以消除跨remote绕过.md
  decision_log:
  - at: '2026-07-17T23:32:10Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始实现pre-push实际remote/URL绑定。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T00:01:49Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: pre-push Hook已透传Git实际remote/URL，错误目标和force路径不可绕过；本地bare回归通过。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0026
  title: 让连续性生命周期演练使用真实本地transport
  status: IMPLEMENTED
  created_at: '2026-07-17T23:46:06Z'
  updated_at: '2026-07-18T00:01:52Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-continuity-test-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求跨电脑Git恢复和安全推送可由项目测试持续验证。
  reason: 新push门禁正确拒绝了没有remote/upstream的旧生命周期夹具；夹具必须建立本地bare remote并验证合法transport路径。
  original_rule: 生命周期演练在无remote/upstream仓库中直接运行pre-push。
  new_rule: 生命周期演练在baseline前生成本地bare remote descriptor，baseline后建立origin/upstream，再验证pre-push；不访问GitHub。
  impact_summary: 让真实Git生命周期演练覆盖新transport门禁的合法推送路径。
  impact:
    files:
    - scripts/test_continuity_protocol.py
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - lifecycle local bare transport pre-push
    releases:
    - R02
    migration_and_compatibility: 仅影响隔离测试夹具和验证证据，不改变生产Git远程或业务实现。
  user_confirmation: 项目所有者要求跨电脑和跨AI的Git推送能力成为可持续验证的硬规则。
  approval:
    decision: APPROVED
    decided_at: '2026-07-17T23:46:09Z'
    note: 独立复核通过：本地bare remote可验证transport且不会写GitHub。
  machine_record: .continuity/change_requests/CR-0026.yaml
  document: docs/03-continuity/change-requests/CR-0026-让连续性生命周期演练使用真实本地transport.md
  decision_log:
  - at: '2026-07-17T23:46:11Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始适配生命周期本地transport夹具。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T00:01:52Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 生命周期夹具已使用本地bare transport和真实upstream；14项演练与Handoff重建通过。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0027
  title: 将已解析api.orbexa.cc接入R02开发测试后端
  status: IMPLEMENTED
  created_at: '2026-07-18T01:22:21Z'
  updated_at: '2026-07-18T01:36:33Z'
  requester_actor_id: codex-root
  approver_actor_id: codex-runtime-governance-reviewer
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者已解析api和download子域名，并在真机反馈R02 APK启动暂时无法连接后，授权持续开发与服务器操作。
  reason: 真机证据确认公网DNS可达但Nginx无api虚拟主机，导致启动门禁无法调用平台状态和版本策略。
  original_rule: R02当前会话范围不含infra，api.orbexa.cc虽已解析但没有受版本控制的开发反向代理，Android启动门禁只能进入暂时无法连接兜底页。
  new_rule: R02开发测试允许新增并部署精确的infra/nginx/api.orbexa.cc.conf：Cloudflare公网TLS入口转发到服务器loopback-only的P00/R02开发后端；不得暴露上游端口、不得包含秘密。
  impact_summary: 新增开发API虚拟主机和真机启动环境对齐；不改变冻结业务契约、数据库语义或生产短信配置。
  impact:
    files:
    - infra/nginx/api.orbexa.cc.conf
    - apps/android/app/build.gradle.kts
    pages:
    - SCR-AUTH-001,启动门禁
    apis:
    - GET /public-api/v1/platform/status;POST /public-api/v1/app/version-check
    database: []
    configuration:
    - Cloudflare api.orbexa.cc,Nginx loopback proxy,HHY_APP_ENVIRONMENT
    ledger: []
    tests:
    - public HTTPS platform status and STAGING version-policy probes;Nginx syntax validation;Android cloud build
    releases:
    - R02
    migration_and_compatibility: 先执行nginx -t后reload；代理上游限定127.0.0.1:28080，失败可移除唯一新增虚拟主机恢复默认404；Android可用HHY_APP_ENVIRONMENT覆盖，默认对齐已发布STAGING渠道。
  user_confirmation: 项目所有者在本会话确认api/download已解析，并要求持续开发、服务器由Codex操作；真机截图提供了该修复的直接验收证据。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T01:22:47Z'
    note: 独立审核确认：用户已授权服务器和已解析子域名操作；该变更仅接通受控开发代理，保留loopback上游和公网验证。
  machine_record: .continuity/change_requests/CR-0027.yaml
  document: docs/03-continuity/change-requests/CR-0027-将已解析api.orbexa.cc接入R02开发测试后端.md
  decision_log:
  - at: '2026-07-18T01:36:33Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始部署并公网验证api.orbexa.cc受控loopback反向代理。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T01:36:33Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 已部署并验证api.orbexa.cc受控loopback反向代理，公网平台状态与official/STAGING版本策略均返回200；R02新后端镜像因独立PROB-0013密钥来源缺口自动回退，旧健康后端保持服务。
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
  implementation_commits:
  - b4ec470
- protocol_version: '1.0'
  cr_id: CR-0029
  title: 新增R02只读协议版本配置接口以完成Android受控注册
  status: IMPLEMENTED
  created_at: '2026-07-18T05:03:14Z'
  updated_at: '2026-07-18T05:23:08Z'
  requester_actor_id: codex-root
  approver_actor_id: user-project-owner
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 项目所有者要求立即完成R02并进入下一版本，不允许无限做不完。
  reason: R02注册必须提交服务端生效协议版本ID；现有R28通用页面读取契约不承载该数据，需最小非破坏性R02接口，且不得伪造法律协议数据。
  original_rule: R02 Android注册页只能手工输入协议版本ID，无法从受控来源取得可提交版本。
  new_rule: R02新增只读兼容端点GET /api/v1/auth/registration-config，返回当前生效协议版本ID与公开协议代码；Android只提交该端点返回的ID。协议内容、版本创建和后台管理仍归R28。
  impact_summary: 解除R02协议版本来源缺口并移除客户端手输版本ID；不生成或写入法律协议数据，数据为空时注册不可提交。
  impact:
    files:
    - contracts/openapi.yaml
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthStore.java
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
    - services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserAuthController.java
    - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthPublicSuccessContractTest.java
    - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
    - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
    - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
    - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
    - tests/test_r02_auth_slice_contract.py
    - docs/03-continuity/PROBLEM_REGISTRY.yaml
    pages:
    - SCR-AUTH-004
    - SCR-AUTH-005
    apis:
    - GET /api/v1/auth/registration-config
    database:
    - hhy.agreements,hhy.agreement_versions (read only)
    configuration:
    - 无；仍由受控协议版本数据发布决定生效内容
    ledger:
    - 无资金或账本影响
    tests:
    - OpenAPI contract;backend registration-config HTTP contract;Android auth form regression;cloud Maven and feature:auth unit tests
    releases:
    - R02
    migration_and_compatibility: 新增非破坏性R02公开读取接口和DTO；复用agreements/agreement_versions只读查询，无迁移。Android在读取失败或无生效版本时禁用注册并显示通用可恢复提示，绝不回退到客户端默认值。
  user_confirmation: 项目所有者在当前对话明确要求：现在立即开始开发，这次必须把这个版本收尾做完，不允许进入无限做不完的状态，做完后立即推进下一版本。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T05:03:39Z'
    note: 项目所有者要求完成R02；批准最小只读R02兼容接口，以移除Android客户端手输协议版本。
  machine_record: .continuity/change_requests/CR-0029.yaml
  document: docs/03-continuity/change-requests/CR-0029-新增R02只读协议版本配置接口以完成Android受控注册.md
  decision_log:
  - at: '2026-07-18T05:03:40Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始实现R02只读协议版本配置接口及Android绑定。
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T05:23:08Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Read-only current agreement-version source, Android controlled submission, and regression evidence are implemented; R28 agreement-management
      ownership remains unchanged.
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0030
  title: 同步R02注册协议配置接口的契约镜像与生成客户端
  status: IMPLEMENTED
  created_at: '2026-07-18T05:06:12Z'
  updated_at: '2026-07-18T05:23:08Z'
  requester_actor_id: codex
  approver_actor_id: user-project-owner
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 现在立即开始开发，这次必须把这个版本收尾做完，不允许进入无限做不完的状态，做完后立即推进下一版本
  reason: CR-0029 adds one public endpoint. The API gate requires its boot contract mirror, endpoint catalog, generated client, and contract-status
    hashes to stay synchronized.
  original_rule: contracts/openapi.yaml is the only approved contract artifact in CR-0029.
  new_rule: The same R02 read-only registration configuration operation must remain identical across the source contract, boot mirror, endpoint
    catalog, generated TypeScript client, and contract-status hashes.
  impact_summary: Adds only the required contract-governance mirrors; no business behavior, schema migration, or R28 agreement-management ownership
    changes.
  impact:
    files:
    - services/backend/boot/src/main/resources/contracts/openapi.yaml
    - catalogs/api_endpoints.csv
    - packages/api-client/src/client.generated.ts
    - contracts/contract_status.csv
    pages: []
    apis:
    - GET /api/v1/auth/registration-config
    database: []
    configuration: []
    ledger: []
    tests:
    - python scripts/check_api_contract.py
    - generated client operation/type parity inspection
    releases:
    - R02
    migration_and_compatibility: Additive public GET endpoint. No database migration and no modification to existing frozen R28 registration-config
      contract.
  user_confirmation: User explicitly authorized immediate R02 closure and the next version after completion in the current task.
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T05:07:39Z'
    note: User ordered immediate bounded R02 closure; approve exact API-governance companion scope.
  machine_record: .continuity/change_requests/CR-0030.yaml
  document: docs/03-continuity/change-requests/CR-0030-同步R02注册协议配置接口的契约镜像与生成客户端.md
  decision_log:
  - at: '2026-07-18T05:07:41Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: Implementing synchronized contract mirrors alongside CR-0029.
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T05:23:08Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Source contract, runtime mirror, endpoint catalog, generated TypeScript client, and status hashes are synchronized and passed contract
      gates.
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0031
  title: 允许R02公开读取注册协议版本配置
  status: IMPLEMENTED
  created_at: '2026-07-18T05:08:50Z'
  updated_at: '2026-07-18T05:23:09Z'
  requester_actor_id: codex-root
  approver_actor_id: user-project-owner
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 现在立即开始开发，这次必须把这个版本收尾做完，不允许进入无限做不完的状态，做完后立即推进下一版本
  reason: SecurityConfiguration currently permits only listed auth POST operations. The new read-only public registration configuration GET must
    be explicitly allowed or Android registration cannot load governed agreement versions.
  original_rule: Only the enumerated auth POST operations are publicly accessible.
  new_rule: GET /api/v1/auth/registration-config is publicly accessible; all other auth routes retain their existing authorization.
  impact_summary: One allow-list entry only; no authentication policy relaxation beyond the new read-only configuration endpoint.
  impact:
    files:
    - services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityConfiguration.java
    pages: []
    apis:
    - GET /api/v1/auth/registration-config
    database: []
    configuration: []
    ledger: []
    tests:
    - UserAuthPublicSuccessContractTest public GET contract test
    - Spring Security route authorization through MockMvc
    releases:
    - R02
    migration_and_compatibility: Additive route authorization. No database migration or change to existing route access.
  user_confirmation: User explicitly authorized immediate R02 closure and the exact supporting changes required to complete it.
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T05:08:52Z'
    note: User directs immediate bounded R02 closure; approve the single public GET allow-list entry.
  machine_record: .continuity/change_requests/CR-0031.yaml
  document: docs/03-continuity/change-requests/CR-0031-允许R02公开读取注册协议版本配置.md
  decision_log:
  - at: '2026-07-18T05:08:53Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: Implementing the reviewed read-only route authorization with the endpoint.
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T05:23:09Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Only GET /api/v1/auth/registration-config was added to the public allow-list; MockMvc proved anonymous access.
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0032
  title: 登记R02注册协议配置接口的页面所有权与变更日志
  status: IMPLEMENTED
  created_at: '2026-07-18T05:24:40Z'
  updated_at: '2026-07-18T05:26:22Z'
  requester_actor_id: codex-root
  approver_actor_id: user-project-owner
  task_id: TASK-R02-002
  session_id: SES-20260717T210927Z-13B07A7D
  user_request: 现在立即开始开发，这次必须把这个版本收尾做完，不允许进入无限做不完的状态，做完后立即推进下一版本
  reason: The strict documentation gate requires every REST operation to have exact UI/system ownership, and the pre-commit gate requires the
    new user-visible registration flow to be recorded in CHANGELOG.
  original_rule: The frozen registration screen owns only challenge, SMS, invite validation, and register operations.
  new_rule: The registration screen also owns the read-only current agreement-version configuration load; CHANGELOG records that users no longer
    enter version IDs manually.
  impact_summary: Documentation and ownership metadata only; no product behavior, endpoint, or database change.
  impact:
    files:
    - catalogs/api_ui_ownership.csv
    - catalogs/ui_action_matrix.csv
    - catalogs/ui_page_specifications.csv
    - CHANGELOG.md
    pages: []
    apis:
    - GET /api/v1/auth/registration-config
    database: []
    configuration: []
    ledger: []
    tests:
    - python scripts/check_v122_documentation.py --strict
    - pre-commit continuity gate
    releases:
    - R02
    migration_and_compatibility: No migration. Existing registration action remains unchanged; one additive GET action is documented as prerequisite
      to registration.
  user_confirmation: User explicitly authorized immediate R02 closure and the exact gate-mandated records needed to deliver it.
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T05:24:42Z'
    note: User directs bounded immediate R02 closure; approve the mandatory ownership and changelog records.
  machine_record: .continuity/change_requests/CR-0032.yaml
  document: docs/03-continuity/change-requests/CR-0032-登记R02注册协议配置接口的页面所有权与变更日志.md
  decision_log:
  - at: '2026-07-18T05:24:43Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: Adding the exact UI ownership/action metadata and user-visible release note.
    session_id: SES-20260717T210927Z-13B07A7D
  - at: '2026-07-18T05:26:22Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: Exact UI ownership/action metadata and changelog are recorded; strict documentation gate passes.
    session_id: SES-20260717T210927Z-13B07A7D
  session_ids:
  - SES-20260717T210927Z-13B07A7D
- protocol_version: '1.0'
  cr_id: CR-0033
  title: 设备会话列表移除令牌回显字段
  status: IMPLEMENTING
  created_at: '2026-07-18T05:53:43Z'
  updated_at: '2026-07-18T05:57:15Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner
  task_id: TASK-R02-003
  session_id: SES-20260718T053331Z-F0ED92BF
  user_request: R02必须收尾并继续推进；不得将开发快照误标正式产物。
  reason: 冻结 AuthGetAuthSessionsResponse 复用了 AuthSessionResource，其中 required accessToken/refreshToken 会在设备管理列表中回显凭据，违反页面敏感字段禁止回显规范。拟改为仅返回会话标识、设备摘要、创建/过期时间、状态与当前会话标记。
  original_rule: AuthGetAuthSessionsResponse.items references AuthSessionResource and therefore requires accessToken and refreshToken in every
    device-list item.
  new_rule: AuthGetAuthSessionsResponse.items references UserSecuritySessionResource, containing only sessionId, device summary, timestamps, status
    and current flag; credentials are never returned by the list operation.
  impact_summary: Security-only response-schema correction for the R02 device management operation; no persistent data or endpoint path changes.
  impact:
    files:
    - contracts/openapi.yaml
    - services/backend/boot/src/main/resources/contracts/openapi.yaml
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
    - services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserAuthController.java
    pages:
    - SCR-AUTH-006
    apis:
    - GET /api/v1/auth/sessions
    database: []
    configuration: []
    ledger: []
    tests:
    - 'UserSecurityWebSecurityTest: verifies list response has no accessToken/refreshToken'
    - 'UserBearerAuthenticationFilterTest: verifies DB-bound bearer authentication'
    releases:
    - R02
    migration_and_compatibility: Additive named schema replacement on a frozen R02 endpoint. Android client must regenerate/update the response
      DTO before consuming the endpoint. Existing unsafe client assumptions are intentionally incompatible and must not be retained.
  user_confirmation: 后续这种问题不要问我确认，你自己决定
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T05:57:14Z'
    note: 项目所有者授权 Codex 对同类明确安全问题自行决定；本变更以最小安全会话摘要替换令牌回显。
  machine_record: .continuity/change_requests/CR-0033.yaml
  document: docs/03-continuity/change-requests/CR-0033-设备会话列表移除令牌回显字段.md
  decision_log:
  - at: '2026-07-18T05:57:15Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 已完成后端实现和定向测试，进入 Android 客户端适配与全量门禁阶段。
    session_id: SES-20260718T053331Z-F0ED92BF
  session_ids:
  - SES-20260718T053331Z-F0ED92BF
- protocol_version: '1.0'
  cr_id: CR-0034
  title: 补录R02安全会话用户可见变更日志
  status: IMPLEMENTING
  created_at: '2026-07-18T06:19:27Z'
  updated_at: '2026-07-18T06:19:30Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner
  task_id: TASK-R02-003
  session_id: SES-20260718T053331Z-F0ED92BF
  user_request: 持续开发R02-R32，同类问题由Codex自行决定并完整留痕。
  reason: 严格预提交门禁要求用户可见的登录设备与改密能力同步CHANGELOG.md。
  original_rule: 当前会话精确路径未包含 CHANGELOG.md。
  new_rule: 允许仅在 R02 现有章节补录安全会话、设备下线和改密的用户可见行为。
  impact_summary: 仅变更 CHANGELOG.md，不改变运行时代码。
  impact:
    files:
    - CHANGELOG.md
    pages: []
    apis: []
    database: []
    configuration: []
    ledger:
    - R02 changelog
    tests:
    - continuity pre-commit strict gate
    releases:
    - R02
    migration_and_compatibility: 纯文档追加，无迁移和兼容影响。
  user_confirmation: 后续这种问题不要问我确认，你自己决定
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T06:19:29Z'
    note: 依据项目所有者对同类开发治理问题的自主决策授权，批准最小变更日志补录。
  machine_record: .continuity/change_requests/CR-0034.yaml
  document: docs/03-continuity/change-requests/CR-0034-补录R02安全会话用户可见变更日志.md
  decision_log:
  - at: '2026-07-18T06:19:30Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: CHANGELOG 已补录，准备通过严格提交门禁。
    session_id: SES-20260718T053331Z-F0ED92BF
  session_ids:
  - SES-20260718T053331Z-F0ED92BF
- protocol_version: '1.0'
  cr_id: CR-0035
  title: 补齐注销短信发送所需手机号确认字段
  status: IMPLEMENTED
  created_at: '2026-07-18T06:48:41Z'
  updated_at: '2026-07-18T06:49:59Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner
  task_id: TASK-R02-003
  session_id: SES-20260718T053331Z-F0ED92BF
  user_request: 后续这种问题不要问我确认，你自己决定；持续推进R02-R32。
  reason: SCR-AUTH-008要求smsCode但既有sms/send合同必须提供完整phone，GET /me仅返回phoneMasked，需最小UI补足才能形成可用闭环。
  original_rule: SCR-AUTH-008仅登记reason、smsCode、expectedVersion，但依赖的authPostAuthSmsSend必须接收完整phone，当前页面无可用短信发送来源。
  new_rule: 注销页允许用户重新输入与phoneMasked匹配的完整手机号，仅用于调用既有SENSITIVE_OPERATION短信发送操作；phone不进入注销请求、不持久化、不记录日志，提交或离开页面立即清空。
  impact_summary: 仅补齐Android注销页面的短信获取可用性；不修改冻结OpenAPI、后端注销请求或数据库结构。
  impact:
    files:
    - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
    pages:
    - SCR-AUTH-008
    apis:
    - POST /api/v1/auth/security-challenges;POST /api/v1/auth/sms/send;POST /api/v1/me/cancellation
    database: []
    configuration: []
    ledger: []
    tests:
    - Android cancellation screen compile; cancellation request serialization excludes phone
    releases:
    - R02
    migration_and_compatibility: 无数据迁移；旧客户端不受影响，新客户端复用现有安全挑战和短信发送合同。
  user_confirmation: 后续这种问题不要问我确认，你自己决定
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T06:49:06Z'
    note: 依据项目所有者对同类开发缺口自行决策的长期授权，批准最小手机号确认字段；禁止进入注销请求或持久化。
  machine_record: .continuity/change_requests/CR-0035.yaml
  document: docs/03-continuity/change-requests/CR-0035-补齐注销短信发送所需手机号确认字段.md
  decision_log:
  - at: '2026-07-18T06:49:07Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: Android实现及编译已完成，准备形成检查点和提交。
    session_id: SES-20260718T053331Z-F0ED92BF
  - at: '2026-07-18T06:49:59Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 注销页面、网络请求、后端状态机和测试均已实现并提交。
    session_id: SES-20260718T053331Z-F0ED92BF
  session_ids:
  - SES-20260718T053331Z-F0ED92BF
  implementation_commits:
  - 4ea46c0
- protocol_version: '1.0'
  cr_id: CR-0036
  title: TASK-R02-004允许同步用户可见变更日志
  status: APPROVED
  created_at: '2026-07-18T07:25:29Z'
  updated_at: '2026-07-18T07:25:55Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-standing-authorization
  task_id: TASK-R02-004
  session_id: SES-20260718T070836Z-25E39817
  user_request: 用户要求R02-R32持续开发并自行处理非紧急确认事项
  reason: 提交门禁要求用户可见后台页面更新CHANGELOG.md，但当前会话允许路径遗漏该治理文件。
  original_rule: TASK-R02-004会话仅允许apps、services、packages、contracts、database、config、tests、docs、catalogs、releases、design与scripts路径。
  new_rule: TASK-R02-004在用户可见变更触发门禁时额外允许同步根目录CHANGELOG.md，其他范围保持不变。
  impact_summary: 仅补充本任务真实用户可见变更的版本记录，不改变产品契约、版本号、数据库或发布范围。
  impact:
    files:
    - CHANGELOG.md
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - continuity pre-commit CHANGELOG_REQUIRED gate
    releases:
    - R02
    migration_and_compatibility: 仅文档追加，向后兼容，无运行时迁移。
  user_confirmation: 后续这种问题不要问我确认，你自己决定
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T07:25:55Z'
    note: 依据项目所有者长期授权，仅批准CHANGELOG.md单文件范围补充，不扩大产品或发布范围。
  machine_record: .continuity/change_requests/CR-0036.yaml
  document: docs/03-continuity/change-requests/CR-0036-TASK-R02-004允许同步用户可见变更日志.md
- protocol_version: '1.0'
  cr_id: CR-0048
  title: R03域名命令持久幂等回执与200表基线
  status: IMPLEMENTED
  created_at: '2026-07-18T16:07:30Z'
  updated_at: '2026-07-18T16:20:08Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-005
  session_id: SES-20260718T152013Z-8B704646
  user_request: 后续这种问题不要问我确认，你自己决定；继续推进开发下一个版本，我不让你暂停就不用停止
  reason: 真实PostgreSQL17迁移证明R03新增不可变域名命令回执表后运行时为200表，旧基线验证和数据目录必须同步
  original_rule: R03冻结目录只有199张基线表，域名命令幂等回执没有独立持久化表，基线验证只接受198或199张表
  new_rule: R03新增domain_command_receipts不可变幂等回执表并纳入数据目录与字段字典；升级后基线验证接受200张表且PostgreSQL17执行正负不变量
  impact_summary: 只扩展R03域名更新和验证命令的持久幂等与审计能力，同一资源操作键永久绑定请求指纹和首个结果，不改变冻结API字段
  impact:
    files:
    - catalogs/data_tables.csv
    - database/schema_dictionary.csv
    - database/verification/verify_baseline.sql
    - scripts/check_db_schema.py
    - scripts/run_postgres_migration_smoke.sh
    - database/migrations/V019__r03_provider_configuration_center.sql
    - database/rollback/U019__r03_provider_configuration_center.sql
    - database/tests/r03_provider_configuration_invariants.sql
    - services/backend/boot/src/main/resources/db/migration/V019__r03_provider_configuration_center.sql
    - tests/test_r03_provider_configuration_migration.py
    pages: []
    apis: []
    database:
    - domain_command_receipts
    - provider_config_versions
    - provider_certificates
    - domain_configs
    - dns_action_items
    configuration: []
    ledger: []
    tests:
    - python scripts/check_db_schema.py
    - python -m unittest tests.test_r03_provider_configuration_migration
    - PostgreSQL17 scripts/run_postgres_migration_smoke.sh
    releases:
    - R03
    migration_and_compatibility: V019前向新增表和追加字段；U019按依赖顺序移除；V1.2.2和R02的198/199表验证继续兼容，R03为200表
  user_confirmation: 后续这种问题不要问我确认，你自己决定；继续推进开发下一个版本，我不让你暂停就不用停止
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T16:07:51Z'
    note: 项目所有者已授权常规实现由Codex自行决定并持续推进；该变更是R03冻结API落地所需的可逆数据库与目录同步
  machine_record: .continuity/change_requests/CR-0048.yaml
  document: docs/03-continuity/change-requests/CR-0048-R03域名命令持久幂等回执与200表基线.md
  decision_log:
  - at: '2026-07-18T16:14:51Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: V019与R03不变量已在obx-test一次性PostgreSQL17.10容器完整通过，正在执行全量回归和提交绑定
    session_id: SES-20260718T152013Z-8B704646
  - at: '2026-07-18T16:20:08Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 真实PostgreSQL17.10、200表、R03不变量、Java21全量193测试、管理端73测试与生产构建、文档和生成物门禁全部通过并推送
    session_id: SES-20260718T152013Z-8B704646
  session_ids:
  - SES-20260718T152013Z-8B704646
  implementation_commits:
  - 650fdee862405db673ed5f3f856611356508e337
- protocol_version: '1.0'
  cr_id: CR-0054
  title: 接入R02安全验证冻结设计并简化测试注册流程
  status: IMPLEMENTED
  created_at: '2026-07-18T21:38:39Z'
  updated_at: '2026-07-18T22:34:32Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 接入20260719冻结安全验证码需求包；注册无需短信验证码；提供测试万能邀请码；全局禁止技术性提示；严格按文档参数；每个大版本桌面交付测试说明。
  reason: 项目所有者提供新的冻结UI事实和注册契约变更，并新增跨AI跨电脑商业产品硬边界，需要在当前R03线上以R02认证热修方式完整接入。
  original_rule: R02旧实现把图形验证码作为页面内联字段并要求用户二次点击；注册请求强制短信验证码和协议版本；仓库未统一禁止技术字段面向用户展示，也未强制每个大版本生成桌面测试说明。
  new_rule: 四个认证页面按冻结B01-CAPTCHA参数使用业务动作触发的居中安全验证弹窗，成功后自动续办原动作；注册可见必填仅手机号、密码、确认密码、邀请码且不发送注册短信，注册请求以图形挑战证明保护；万能邀请码仅在DEV/TEST/STAGING且显式配置时可用，PROD硬拒绝；全局禁止用户界面展示请求编号和内部技术字段；每个大版本必须文档核对并向桌面交付完整测试反馈说明。
  impact_summary: 接入冻结视觉资产和规格，修改注册OpenAPI、后端和Android认证流程，新增测试环境邀请码策略、商业UI静态门禁、回包验收兼容修复及R02热修追溯。
  impact:
    files:
    - AGENTS.md
    - templates/AGENTS.md
    - .continuity/CONTINUITY_POLICY.yaml
    - START_HERE.md
    - CHANGELOG.md
    - docs/00-baseline/正式商业系统全局硬性开发边界.md
    - docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md
    - docs/02-ui/12批UI参考图绑定索引_V1.2.2.md
    - docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md
    - docs/02-ui/页面施工规格总索引_V1.2.2.md
    - docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md
    - docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md
    - docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md
    - docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md
    - docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md
    - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png
    - design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json
    - design/effect-previews/B01-CAPTCHA/OUTPUT_REQUIREMENTS.md
    - design/effect-previews/B01-CAPTCHA/states/P01_初始页_验证码隐藏.png
    - design/effect-previews/B01-CAPTCHA/states/P02_点击登录_挑战加载.png
    - design/effect-previews/B01-CAPTCHA/states/P03_验证码就绪_等待输入.png
    - design/effect-previews/B01-CAPTCHA/states/P04_答案错误_原位重试.png
    - design/effect-previews/B01-CAPTCHA/states/P05_过期-刷新_旧答案清空.png
    - design/effect-previews/B01-CAPTCHA/states/P06_短信-注册_发送前验证.png
    - design/effect-previews/B01-CAPTCHA/states/P07_验证通过_自动续办.png
    - design/effect-previews/B01-CAPTCHA/states/P08_网络失败_键盘-小屏适配.png
    - scripts/check_r02_security_challenge_design_package.py
    - tests/test_r02_security_challenge_design_package.py
    - scripts/check_commercial_ui_boundaries.py
    - tests/test_commercial_ui_boundaries.py
    - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
    - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthFormRules.kt
    - apps/android/feature/auth/src/test/java/cc/orbexa/hhy/auth/AuthFormRulesTest.kt
    - apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt
    - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ApiModels.kt
    - apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt
    - apps/android/core/network/src/test/java/cc/orbexa/hhy/network/ApiModelsSerializationTest.kt
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthContracts.java
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java
    - services/backend/access/src/main/java/cc/orbexa/hhy/access/user/TestRegistrationInvitePolicy.java
    - services/backend/boot/src/main/resources/application.yaml
    - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthServiceTest.java
    - contracts/openapi.yaml
    - services/backend/boot/src/main/resources/contracts/openapi.yaml
    - packages/api-client/src/generated/openapi.yaml
    - tests/test_r02_auth_slice_contract.py
    - config/PROBLEM_REGISTRY.yaml
    - releases/R02/R02_AUTH_HOTFIX_20260719.md
    - docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
    - .continuity/change_requests/CR-0054.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages:
    - SCR-AUTH-001
    - SCR-AUTH-002
    - SCR-AUTH-003
    - SCR-AUTH-004
    apis:
    - POST /api/v1/auth/register
    - POST /api/v1/auth/security-challenges
    - POST /api/v1/auth/invite-codes/validate
    database: []
    configuration:
    - HHY_TEST_UNIVERSAL_INVITE_CODE
    - HHY_TEST_UNIVERSAL_INVITER_ID
    - Spring profiles DEV/TEST/STAGING only
    ledger: []
    tests:
    - python -m unittest tests.test_r02_security_challenge_design_package
    - python -m unittest tests.test_commercial_ui_boundaries
    - python -m unittest tests.test_r02_auth_slice_contract
    - Gradle Android auth/network tests
    - Gradle backend UserAuthServiceTest
    - python scripts/check_v123_continuity.py --strict
    releases:
    - R02
    - R03
    migration_and_compatibility: 注册API为有意的不兼容契约升级：移除smsCode和agreementVersions，新增challengeId和challengeProof；Android与后端在同一热修交付中同步。数据库无结构迁移。万能邀请码默认关闭，仅受控非生产环境变量启用，生产无法启用。
  user_confirmation: R02的安全验证码开发问题已由ChatGPT制作好需求包，现在已经给你了，请你理解后开始开发；注册无需短信验证码；新增四项全局硬性开发边界。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:39:46Z'
    note: 项目所有者本轮明确提供冻结设计包及注册和全局硬边界，批准按完整影响范围接入；万能邀请码必须保持非生产环境硬隔离。
  machine_record: .continuity/change_requests/CR-0054.yaml
  document: docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md
  decision_log:
  - at: '2026-07-18T21:39:49Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始接入冻结设计事实、注册契约和商业UI硬门禁
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:32Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0055
  title: 移除管理端技术请求标识展示
  status: IMPLEMENTED
  created_at: '2026-07-18T21:41:48Z'
  updated_at: '2026-07-18T22:34:33Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 正式商业系统前端全局任何页面不允许展示技术性提示词和请求编号。
  reason: 全局扫描发现管理端StatusNotice和多个错误空态仍会渲染requestId，违反本轮新增硬边界。
  original_rule: 管理端错误组件和部分空态直接显示请求标识或请求编号。
  new_rule: 请求标识仅保留在网络诊断数据中，不得由正式用户界面组件或页面渲染；错误页面只展示可操作的商业文案。
  impact_summary: 删除StatusNotice和管理端页面的requestId展示绑定，保留内部网络错误对象以支持日志排障，并更新相关UI测试。
  impact:
    files:
    - apps/admin-web/src/components/StatusNotice.vue
    - apps/admin-web/src/views/AdminLoginPage.vue
    - apps/admin-web/src/views/AdminMfaPage.vue
    - apps/admin-web/src/views/AdminUserDetailPage.vue
    - apps/admin-web/src/views/AdminUsersListPage.vue
    - apps/admin-web/src/views/AdminSecurityPage.vue
    - apps/admin-web/src/views/DomainConfigPage.vue
    - apps/admin-web/src/views/ProviderConfigPage.vue
    - apps/admin-web/src/components/ProviderCertificatePanel.vue
    - apps/admin-web/src/r01Pages.test.ts
    - apps/admin-web/src/r03DomainPage.test.ts
    - apps/admin-web/src/r03ProviderPage.test.ts
    - config/PROBLEM_REGISTRY.yaml
    - CHANGELOG.md
    - docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
    - .continuity/change_requests/CR-0055.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages:
    - ADMIN-LOGIN
    - ADMIN-MFA
    - ADMIN-USERS
    - ADMIN-SECURITY
    - R03-DOMAIN
    - R03-PROVIDER
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - pnpm --filter @hhy/admin-web test
    - python -m unittest tests.test_commercial_ui_boundaries
    releases:
    - R01
    - R03
    migration_and_compatibility: 纯前端展示兼容修复，不改变API、网络诊断结构或数据库；自动化测试改为断言请求标识不出现在DOM。
  user_confirmation: 前端全局任何页面不允许展示技术性提示词和请求编号，即便换电脑换AI都要明确。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:41:50Z'
    note: 用户明确要求前端全局移除技术提示，批准清理管理端所有请求标识展示。
  machine_record: .continuity/change_requests/CR-0055.yaml
  document: docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md
  decision_log:
  - at: '2026-07-18T21:41:52Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始清理管理端技术请求标识展示
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:33Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0056
  title: 更正测试邀请码运行配置文件范围
  status: IMPLEMENTED
  created_at: '2026-07-18T21:45:21Z'
  updated_at: '2026-07-18T22:34:34Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 注册测试阶段需要万能邀请码且生产不得形成后门。
  reason: CR-0054影响清单误写application.yaml，实际Spring Boot配置文件为application.yml，必须通过派生CR精确更正。
  original_rule: CR-0054登记了不存在的services/backend/boot/src/main/resources/application.yaml。
  new_rule: 在实际application.yml中声明默认关闭的HHY_TEST_UNIVERSAL_INVITE_CODE和HHY_TEST_UNIVERSAL_INVITER_ID，并由非生产Profile策略共同约束。
  impact_summary: 仅更正测试邀请码配置文件事实路径，不扩大功能范围。
  impact:
    files:
    - services/backend/boot/src/main/resources/application.yml
    - docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
    - .continuity/change_requests/CR-0056.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages: []
    apis: []
    database: []
    configuration:
    - HHY_TEST_UNIVERSAL_INVITE_CODE
    - HHY_TEST_UNIVERSAL_INVITER_ID
    ledger: []
    tests:
    - Gradle backend UserAuthServiceTest
    releases:
    - R02
    - R03
    migration_and_compatibility: 默认值为空和0，未配置时行为不变；生产Profile仍硬拒绝。
  user_confirmation: 测试阶段提供万能注册邀请码，且正式商业系统不得留生产后门。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:45:23Z'
    note: 批准修正到实际application.yml并保持默认关闭和生产硬隔离。
  machine_record: .continuity/change_requests/CR-0056.yaml
  document: docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md
  decision_log:
  - at: '2026-07-18T21:45:24Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 更正运行配置路径并同步实施
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:34Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0057
  title: 更正注册契约生成客户端范围
  status: IMPLEMENTED
  created_at: '2026-07-18T21:47:45Z'
  updated_at: '2026-07-18T22:34:35Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 注册契约取消短信验证码并由图形安全验证保护。
  reason: CR-0054误写不存在的generated/openapi.yaml，实际生成产物为packages/api-client/src/client.generated.ts。
  original_rule: CR-0054登记了不存在的packages/api-client/src/generated/openapi.yaml。
  new_rule: 从更新后的contracts/openapi.yaml重新生成packages/api-client/src/client.generated.ts并通过生成资产门禁。
  impact_summary: 更正注册OpenAPI的生成客户端事实路径。
  impact:
    files:
    - packages/api-client/src/client.generated.ts
    - docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
    - .continuity/change_requests/CR-0057.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages: []
    apis:
    - POST /api/v1/auth/register
    database: []
    configuration: []
    ledger: []
    tests:
    - python scripts/check_generated_assets.py
    - pnpm --filter @hhy/api-client typecheck
    releases:
    - R02
    - R03
    migration_and_compatibility: 生成类型同步移除smsCode和agreementVersions并加入challengeId和challengeProof，与同批Android和后端升级一致。
  user_confirmation: 注册无需短信验证码，安全验证按新冻结设计接入。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:47:47Z'
    note: 批准更正到实际生成客户端并保持同批契约同步。
  machine_record: .continuity/change_requests/CR-0057.yaml
  document: docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md
  decision_log:
  - at: '2026-07-18T21:47:49Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 重新生成注册契约客户端
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:35Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0058
  title: 同步H5无短信注册与自动安全验证
  status: IMPLEMENTED
  created_at: '2026-07-18T21:54:47Z'
  updated_at: '2026-07-18T22:34:36Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 注册账号无需发送短信验证码，正式前端不展示技术性提示。
  reason: 注册OpenAPI变化同时影响H5邀请注册；旧H5仍依赖短信、协议版本和手动获取安全验证，必须同批同步避免契约断裂。
  original_rule: H5邀请注册要求用户手动获取安全验证、发送注册短信、输入短信码并提交协议版本。
  new_rule: H5注册以手机号、密码、确认密码和URL邀请码为可见注册字段；点击注册后自动弹出安全验证，验证并继续直接提交注册；不发送注册短信、不提交协议版本、不展示技术提示。
  impact_summary: 同步H5页面、服务类型、测试与H5页面规格到新注册契约。
  impact:
    files:
    - apps/h5/src/views/InviteRegistrationPage.vue
    - apps/h5/src/services/inviteRegistration.ts
    - apps/h5/src/views/InviteRegistrationPage.test.ts
    - apps/h5/src/services/inviteRegistration.test.ts
    - docs/02-ui/page-specs/h5/H5-013_H5邀请注册页.md
    - CHANGELOG.md
    - docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
    - .continuity/change_requests/CR-0058.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages:
    - H5-013
    apis:
    - POST /api/v1/auth/register
    - POST /api/v1/auth/security-challenges
    database: []
    configuration: []
    ledger: []
    tests:
    - pnpm --filter @hhy/h5 test
    - pnpm --filter @hhy/h5 typecheck
    releases:
    - R02
    - R03
    migration_and_compatibility: 与CR-0054注册API同步升级；旧H5请求不再兼容，前后端必须同批部署。协议内容可继续作为只读链接/说明，但不作为本次注册请求字段。
  user_confirmation: 注册账号无需发送短信验证码，前端全局不允许技术性提示。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:54:49Z'
    note: 批准H5与Android、后端同批同步，避免旧短信注册契约残留。
  machine_record: .continuity/change_requests/CR-0058.yaml
  document: docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md
  decision_log:
  - at: '2026-07-18T21:54:51Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 同步H5注册流程与自动安全验证
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:36Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0059
  title: 补清管理端说明文案中的技术请求标识
  status: IMPLEMENTED
  created_at: '2026-07-18T21:58:02Z'
  updated_at: '2026-07-18T22:34:37Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 正式前端全局不允许展示技术性提示词。
  reason: 商业UI静态门禁发现AuthShell安全说明和安全空态仍提到请求标识，属于CR-0055遗漏。
  original_rule: 管理端安全说明将内部请求标识作为用户可见排障说明。
  new_rule: 改为业务化的操作审计和重新加载说明，不向管理员展示或提及内部请求标识。
  impact_summary: 清理两个遗漏文案并纳入全局静态门禁。
  impact:
    files:
    - apps/admin-web/src/components/AuthShell.vue
    - apps/admin-web/src/views/AdminSecurityPage.vue
    - docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
    - .continuity/change_requests/CR-0059.yaml
    - .continuity/CHANGE_REQUEST_INDEX.yaml
    pages:
    - ADMIN-LOGIN
    - ADMIN-SECURITY
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - python -m unittest tests.test_commercial_ui_boundaries
    - pnpm --filter @hhy/admin-web test
    releases:
    - R01
    - R03
    migration_and_compatibility: 纯文案，无接口或数据变化。
  user_confirmation: 前端全局任何页面不允许展示技术性提示词。
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T21:58:04Z'
    note: 批准清理静态门禁发现的遗漏技术文案。
  machine_record: .continuity/change_requests/CR-0059.yaml
  document: docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md
  decision_log:
  - at: '2026-07-18T21:58:06Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 清理遗漏技术文案
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:37Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0060
  title: 同步注册成功契约测试至无短信注册模型
  status: IMPLEMENTED
  created_at: '2026-07-18T22:03:28Z'
  updated_at: '2026-07-18T22:34:39Z'
  requester_actor_id: codex
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: R02安全验证码冻结需求包及无短信注册硬性要求
  reason: 主注册契约已移除短信码和协议版本字段，旧成功契约测试仍引用已删除访问器，必须同步才能恢复后端测试门禁
  original_rule: 注册成功契约测试提交短信验证码及协议版本并断言agreementVersions访问器
  new_rule: 注册成功契约测试提交手机号、密码、邀请码、图片验证challengeId与challengeProof，并断言新字段及不再要求短信注册
  impact_summary: 仅同步UserAuthSuccessContractTest到冻结后的注册请求契约，不扩大业务范围
  impact:
    files:
    - services/backend/boot/src/test/java/cc/orbexa/hhy/access/user/UserAuthSuccessContractTest.java
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - Maven UserAuthSuccessContractTest与UserAuthServiceTest
    releases:
    - R02安全验证码交互热修复
    migration_and_compatibility: 保留短信验证码登录和找回密码测试；仅注册接口移除短信码与协议版本
  user_confirmation: 2026-07-19用户明确提出手机号+密码+重复密码+邀请码即可注册
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T22:03:46Z'
    note: 用户已明确要求无短信注册并授权后续此类问题自行决定
  machine_record: .continuity/change_requests/CR-0060.yaml
  document: docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md
  decision_log:
  - at: '2026-07-18T22:03:48Z'
    actor_id: codex
    status: IMPLEMENTING
    note: 开始同步注册成功契约测试
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:39Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0061
  title: 更正全局问题登记文件并补记R02认证偏差
  status: IMPLEMENTED
  created_at: '2026-07-18T22:16:09Z'
  updated_at: '2026-07-18T22:34:40Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 硬性记录后续跨电脑跨AI开发边界
  reason: CR-0054误列不存在的config/PROBLEM_REGISTRY.yaml，仓库实际权威问题登记位于docs/03-continuity/PROBLEM_REGISTRY.yaml
  original_rule: 将本次问题追加到不存在的config/PROBLEM_REGISTRY.yaml
  new_rule: 将设计校验器兼容、技术信息泄漏和手动验证码交互偏差追加到实际权威docs/03-continuity/PROBLEM_REGISTRY.yaml
  impact_summary: 修正问题登记路径并确保跨AI接续能够检索本次根因和防复发措施
  impact:
    files:
    - docs/03-continuity/PROBLEM_REGISTRY.yaml
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - continuity context与YAML解析门禁
    releases:
    - R02安全验证码交互热修复
    migration_and_compatibility: 不改业务接口；仅更正治理记录路径
  user_confirmation: 2026-07-19全局硬性开发边界
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T22:16:11Z'
    note: 用户已明确要求硬性记录并保证换电脑换AI无缝接续
  machine_record: .continuity/change_requests/CR-0061.yaml
  document: docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md
  decision_log:
  - at: '2026-07-18T22:16:12Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始更新实际权威问题登记
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:40Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0062
  title: 同步无短信注册契约状态哈希
  status: IMPLEMENTED
  created_at: '2026-07-18T22:19:08Z'
  updated_at: '2026-07-18T22:34:41Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 严格按开发文档开发且不遗漏任何契约细节
  reason: OpenAPI注册请求变更后contract_status.csv的131项客户端操作源哈希必须确定性刷新
  original_rule: contract_status.csv记录变更前OpenAPI事实源哈希
  new_rule: 保持全部契约元数据不变，仅用安全状态同步器刷新OpenAPI事实源SHA-256
  impact_summary: 同步无短信注册OpenAPI派生状态，恢复API契约门禁
  impact:
    files:
    - contracts/contract_status.csv
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - scripts/generate_contracts.py --check和scripts/check_api_contract.py
    releases:
    - R02安全验证码交互热修复
    migration_and_compatibility: 不运行旧全量契约重建，不覆盖富化Schema
  user_confirmation: 2026-07-19无短信注册与严格契约要求
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T22:19:11Z'
    note: 属于用户明确注册契约变更的确定性派生资产
  machine_record: .continuity/change_requests/CR-0062.yaml
  document: docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md
  decision_log:
  - at: '2026-07-18T22:19:12Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 刷新契约事实源哈希
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:41Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
- protocol_version: '1.0'
  cr_id: CR-0063
  title: 固化安全验证设计Token并修复主文档门禁路径
  status: IMPLEMENTED
  created_at: '2026-07-18T22:26:31Z'
  updated_at: '2026-07-18T22:34:42Z'
  requester_actor_id: codex-root
  approver_actor_id: project-owner-delegated
  task_id: TASK-R03-007
  session_id: SES-20260718T200607Z-3569D212
  user_request: 后续UI必须严格按效果图字号圆角等硬性参数且不遗漏
  reason: 冻结弹层精确值仍以内联dp/sp/颜色存在并触发Token门禁；主文档门禁仍指向旧根目录文件名
  original_rule: 页面代码禁止原始dp/sp/颜色但安全验证冻结值尚无专用Token，主文档检查读取已迁移的旧路径
  new_rule: 把安全验证精确尺寸、字体、颜色引用纳入全局Token派生，H5与Android只引用Token；主文档检查读取docs/00-baseline权威文件
  impact_summary: 消除页面原始视觉值并恢复UI Token和主文档机器门禁
  impact:
    files:
    - design/tokens/hhy_design_tokens_v1.2.2.json
    - apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json
    - apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt
    - packages/design-tokens/h5.css
    - packages/design-tokens/admin.css
    - scripts/check_ui_tokens.py
    - scripts/check_main_doc.py
    - apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt
    - apps/h5/src/views/InviteRegistrationPage.vue
    - apps/admin-web/src/components/ProviderCertificatePanel.vue
    pages: []
    apis: []
    database: []
    configuration: []
    ledger: []
    tests:
    - scripts/check_ui_tokens.py、scripts/check_main_doc.py、tests/test_ui_tokens.py、Android/H5/admin编译测试
    releases:
    - R02安全验证码交互热修复
    migration_and_compatibility: 视觉数值保持冻结包一致，不改变业务接口
  user_confirmation: 2026-07-19严格按效果图字号布局圆角参数开发
  approval:
    decision: APPROVED
    decided_at: '2026-07-18T22:26:33Z'
    note: 落实用户新增的全局UI参数硬边界并恢复现有门禁
  machine_record: .continuity/change_requests/CR-0063.yaml
  document: docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md
  decision_log:
  - at: '2026-07-18T22:26:35Z'
    actor_id: codex-root
    status: IMPLEMENTING
    note: 开始Token化冻结安全验证视觉参数并修复文档门禁
    session_id: SES-20260718T200607Z-3569D212
  - at: '2026-07-18T22:34:42Z'
    actor_id: codex-root
    status: IMPLEMENTED
    note: 冻结安全验证、无短信注册、商业UI边界及对应测试门禁已完成，等待精确提交与发布交付
    session_id: SES-20260718T200607Z-3569D212
  session_ids:
  - SES-20260718T200607Z-3569D212
```

## 上下文来源及哈希

- `AGENTS.md` — `fe60549fb106fb4371d39eca6a583191a6661cadaa6b4b7d6ccb44c2b58473db`
- `START_HERE.md` — `038f713d50267cc0c1598d2399f13ee5ca9ad82bc03311747f3c3ef7f2ae232a`
- `CURRENT_STATUS.yaml` — `bb3a6ac171ba3f2c206a6ed1d630c0c73533f1f997235b9ca39674731ef1844f`
- `NEXT_TASK.yaml` — `d3331935c15deb5ebd04b02689837403e53e8bc65c8813529359515257a7edc1`
- `DEVELOPMENT_RISK_REGISTER.md` — `7b5b054b6c9968bedf1ee9dbcd699394dd6a260ce35529e4d2fc9842e7f737bf`
- `docs/00-baseline/SOURCE_OF_TRUTH.md` — `045624e036f03cf5668982159cbdb433a63f7397475a8a4592ae5255f9ddc511`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml` — `7ec32b7a833696922cac1d5d9c53e4694bf630bb7897244f7b8516400aedc9fe`
- `docs/03-continuity/REUSABLE_PATTERNS.md` — `6ba8e39f6a98d3ceb4b019ea66aa6a954ada7f07c55e5f1c533200f0beda7969`
- `docs/03-continuity/PITFALLS.md` — `ddd7ab31a638763a1e880c3e46c33f2ca20c1c75eb8469366346e03272082f30`
- `releases/PROGRAM_EXECUTION_PLAN.yaml` — `a16351c5e292e352990e58efa1941911b73b16b35b8b7c0a9528c4f937ee30d0`
- `config/REPOSITORY_TRANSPORT.yaml` — `383dc5933fa901a08a97274fec4ad968099e5c062db7872d1bb6ac64cbe3a407`
- `config/DEVELOPMENT_RUNTIME.yaml` — `ef5582b1ca989f509d21aae6a3e0880dd7292bcb587fe85ef7cf29c60897c244`
- `.continuity/CONTINUITY_POLICY.yaml` — `35e8f79e24ab6d69cafd2e12d8fc909f878ba86340a8a2c52729febc1be01ed6`
- `.continuity/EVENT_LOG.jsonl` — `728b29117e7893ff1eb7653e615fa72ce8b1ac8860103261465e8167d7a8165f`
- `.continuity/SESSION_INDEX.yaml` — `9259d9bbe30adf24e829baa380ec2d384a6005f070e274eed56b24c9857f6600`
- `.continuity/TASK_CLAIMS.yaml` — `dae3c46345cf6361b9487cc9878bf5cd6bd3775ec390315b8036208feb5d7b19`
- `.continuity/TASK_TRANSITIONS.yaml` — `49b2f0e5fdabc44f56af3c9fdeefd1971b4d3462167a3c93ba796bb4b3be1ada`
- `.continuity/CHANGE_REQUEST_INDEX.yaml` — `1a46d7a2c270cd61c3ed009a50e37ea7f0dc9589ea0c8d09d8939ba0774f2847`
- `.continuity/ACTIVE_SESSION.yaml` — `0cac465c8c1f88366678e827d13ba8b34f2e60c6ead06465528c7a17dabcf6e3`
- `releases/R03/RELEASE_MANIFEST.yaml` — `d1acc083503e2e080590867ab83eebda2b74d27f37bb9f821316fb1f46e35318`
- `releases/R03/DEFINITION_OF_READY.yaml` — `dc19f2cd6f6ad4fae44b6a48db39a44bc61bff0060017d6684e44f19e3079575`
- `releases/R03/STORIES.yaml` — `9576b7a773ee335a8e3a8445bef9a947918083267f40b27dabb5b094b3512522`
- `releases/R03/TASKS.yaml` — `577a231d7cc655f126187950d05277a67d9e877ddb87a52721b2b634a21f12eb`
- `releases/R03/ACCEPTANCE_MATRIX.csv` — `de226ffec9d6cb0da7242d9febfae3ac023875c3b1d167894739ca4f8dc9ac47`
- `releases/R03/PARALLEL_EXECUTION_PLAN.yaml` — `1d9a8638d51807f353b8a7815fcbed28e63fa621fa9bc37638bd83191ed49e39`
- `docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md` — `1c4f3225d7175d6e42877694fbd6af669904926a6ef51de47422584468e7dc4e`
- `.continuity/checkpoints/SES-20260718T200607Z-3569D212/0010.yaml` — `202a18965cff6fb041b1a4fb730d9ae272322f44d8a8da6a31034388bce01396`
- `docs/03-continuity/change-requests/CR-0051-修复BLOCKED任务接续命令死锁并登记非阻断设计回传.md` — `09124eace840cbac05b094cb44ba6e9b2baf894d33b663a5eecc21a271fd9f27`
- `docs/03-continuity/change-requests/CR-0052-同步连续性模板并重生成协议验证报告.md` — `657fe94cc6f260b297cb45642322801c84f680a89018c9e6679469eaecfc7689`
- `docs/03-continuity/change-requests/CR-0053-补齐非阻断设计接入与连续性修复Changelog.md` — `d34b7054137e69b320623c2971b6b89fdc8c26644170a1cbc9ab29dbc2fb1ab0`
- `docs/03-continuity/change-requests/CR-0054-接入R02安全验证冻结设计并简化测试注册流程.md` — `570569e64f28338335fdac3500133ad7f40abec4bf8090430a2dc9e78af43838`
- `docs/03-continuity/change-requests/CR-0055-移除管理端技术请求标识展示.md` — `2f7db76799a9cb572ce609aa9afb8be0187d694ac15fd220d562b91f7cfbedd3`
- `docs/03-continuity/change-requests/CR-0056-更正测试邀请码运行配置文件范围.md` — `b93de35bf64993bf081bcdad7925c9622d380cee943c5e57e63092cc0d7432c4`
- `docs/03-continuity/change-requests/CR-0057-更正注册契约生成客户端范围.md` — `12bc90c1d1dc3e298b346ed9698fd14680d63a32b96ddce49b453e81002f18f8`
- `docs/03-continuity/change-requests/CR-0058-同步H5无短信注册与自动安全验证.md` — `92b48e492037e26be0f617c90490599e84b0531df853f2f2446811df5437f0f5`
- `docs/03-continuity/change-requests/CR-0059-补清管理端说明文案中的技术请求标识.md` — `fe30c350a27877d95f97627ead6e233589a510c552d70248b2143002def3f6ce`
- `docs/03-continuity/change-requests/CR-0060-同步注册成功契约测试至无短信注册模型.md` — `54f30119acbf54bb77ce6ff56d4062832df36764dd971f4c8de17330b997e14f`
- `docs/03-continuity/change-requests/CR-0061-更正全局问题登记文件并补记R02认证偏差.md` — `295e4066edcb0c8045b5f20d050bee97574b383ee50ef3254f52a21f71b16933`
- `docs/03-continuity/change-requests/CR-0062-同步无短信注册契约状态哈希.md` — `474b814936d47e04b628904e99ef438c7220f2d2611faafefbf950783e1dacfa`
- `docs/03-continuity/change-requests/CR-0063-固化安全验证设计Token并修复主文档门禁路径.md` — `2c09fe83410224d6c2b15d4237a85d8c55edcab204906156fe5345c6763bd4b8`

## 接手硬规则

1. 先运行精确恢复命令，不得直接编辑。
2. 不得要求用户重新输入仓库已有需求。
3. 所有变更必须在活跃会话、任务和故事范围内。
4. 每次上下文切换、关键测试、提交和交接前必须创建检查点。
5. 冻结事实变化必须关联已批准 CR。
6. 存在安全且路径互斥的工作包时自动委托 1 至 3 个执行代理，无需逐次用户确认。
7. 未委托或运行时不支持代理时必须记录原因，禁止伪造并行证据。
8. 复杂/高风险使用 Sol，中等使用 Terra，轻量使用 Luna；不可用时记录实际回退，禁止虚构模型使用记录。
9. 除非项目所有者明确报告未连接，默认 Codex 已连接 `obx-test` 且既有项目环境可用；预检失败必须阻断并报告。
10. 禁止以无服务器状态开发或重建本地 Android SDK；只能复用登记的远端镜像和 Gradle 缓存。
11. Git remote/upstream 仅按 tracked transport descriptor 受控恢复，推送前必须通过 preflight，禁止 force push。
