---
cr_id: CR-0481
status: APPROVED
requester_actor_id: codex-r14-close-20260729
approver_actor_id: project-owner-continuity-directive-20260729
task_id: TASK-R14-008
session_id: SES-20260728T220632Z-FE7D82FD
created_at: 2026-07-29T02:25:50Z
updated_at: 2026-07-29T02:26:49Z
---
# CR-0481 — 修复R14候选启动版本策略夹具缺失并递增Attempt5

## 用户需求摘要

持续完成R14至R32；候选失败由AI读取日志、修复并自行重测，不得盲目重跑或开发漂移

## 原规则

R14候选切流已校验源码、镜像、网络、数据库、MFA卷、Flyway、平台状态和鉴权会话接口，但未校验Android启动必经的official/STAGING公开版本策略记录及POST版本检查

## 新规则

R14候选数据库必须先通过幂等夹具建立与已交付TEST_APK一致的official/STAGING发布策略；受控切流必须在本机和公网分别证明POST /public-api/v1/app/version-check为HTTP 200，任一失败不得启动GitHub模拟器。Attempt4失败证据入库后，只有绑定本修复Commit、CR-0481和唯一request005的Attempt5可运行一次

## 修改原因

Attempt4真实日志与服务器请求证明自动会话、平台状态和用户自查询成功，但official/STAGING发布记录为空导致POST /public-api/v1/app/version-check返回404，必须补幂等夹具和切流前硬门禁

## 影响摘要

消除自动会话成功但启动门禁因空版本策略表返回404的候选环境漂移；增加无秘密数据库夹具、HTTP硬门禁、失败证据和精确Attempt5；不改变生产数据、公开API合同或Android产品行为

## 影响文件

- `scripts/prepare_r14_candidate_fixture.sh`
- `scripts/switch_android_candidate_route.sh`
- `tests/test_android_candidate_route.py`
- `config/android-automation.yaml`
- `scripts/android_ci_gate.py`
- `tests/test_android_ci_gate.py`
- `config/android-candidate-request.yaml`
- `tests/test_android_candidate_request.py`
- `artifacts/validation/r14-candidate-attempt4/failure-evidence.json`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `POST /public-api/v1/app/version-check contract unchanged; add local and public HTTP 200 readiness probes`

## 数据库与迁移

- `R14 isolated staging fixture only: idempotent app build artifact, official/STAGING channel and published release record; no migration and no production data`

## 配置

- `android automation route proofs add local/public startup version policy HTTP 200; authorize exact Attempt5 exception CR-0481`

## 资金/账本与历史数据

- `无资金、账本或结算影响`

## 测试

- `tests.test_android_candidate_route tests.test_android_ci_gate tests.test_android_candidate_request; obx-test fixture idempotency and controlled route activation; Attempt5 complete R14 emulator journey`

## 版本

- `R14`

## 迁移与兼容策略

仅R14隔离Staging候选数据库执行幂等夹具，使用已交付TEST_APK 10224的公开下载身份；无数据库迁移、无生产写入。已有正确记录时只校验不重复创建，失败保持旧公网upstream并阻断候选

## 用户确认

用户当前持续开发Goal及既有明确指令：候选失败由AI自行判断修复，不停下等待逐次批准，严格保持R14至R32顺序

## 审批

- 审批人：`project-owner-continuity-directive-20260729`
- 决定：`APPROVED`
- 时间：`2026-07-29T02:26:49Z`
- 说明：项目所有者已授权持续完成R14至R32并要求候选失败由AI读取日志、自行修复和重测。本CR只修复已证实的R14隔离Staging版本策略夹具缺失，增加启动前HTTP硬门禁并授权绑定修复Commit的唯一Attempt5，不涉及生产、秘密、资金或新业务合同
