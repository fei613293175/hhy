---
cr_id: CR-0487
status: APPROVED
requester_actor_id: codex-r14-resume-20260730
approver_actor_id: project-owner-continuity-directive-20260730
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-29T20:09:47Z
updated_at: 2026-07-29T20:10:27Z
---
# CR-0487 — 原位加固GitHub OIDC引导5xx有限重试并保留非重试失败

## 用户需求摘要

持续完成R14至R32，不因GitHub瞬态故障或权限操作打断开发，并把可复用经验写入全局事实源

## 原规则

现有authentication只冻结OIDC一次性身份、TTL与绑定声明；android-quality-gate对Staging bootstrap只请求一次，任意非200立即exit 22。PROB-0091只覆盖候选容器离线造成的502及人工失败Job重跑，没有覆盖OIDC discovery/JWKS短暂恢复的5xx。

## 新规则

不新增平行硬规则；在既有config/android-automation.yaml authentication下增加唯一bootstrap_retry合同：只允许HTTP 500/502/503/504最多3次总尝试，固定2秒与4秒退避；200立即成功，400/401/403/404/422及其他状态立即失败，令牌和响应体不得输出。android-quality-gate必须严格实现该合同并在最终失败注解中报告状态与尝试数。原位扩展PROB-0091登记Run 30486463619的JwtException、后续discovery/JWKS 200及浏览器控制不可用证据，禁止通过重复推送替代失败Job重跑。

## 修改原因

Run 30486463619完成编译打包后在模拟器启动前POST internal-ci bootstrap返回500；候选日志为JwtException，随后同容器访问GitHub OIDC discovery与JWKS均200，现有工作流没有5xx有限重试而直接浪费完整构建。先检索现有规则，决定原位扩展authentication与PROB-0091，不新建平行硬规则。

## 影响摘要

仅加固GitHub候选模拟器启动前的Staging一次性引导网络容错和治理验证；不改变OIDC仓库/工作流/Commit/Run绑定、一次性码、产品UI、业务API、数据库或候选业务断言。Attempt9尚未运行R14旅程，不计入同一拉黑指纹第三轮。

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `config/android-automation.yaml`
- `scripts/android_ci_gate.py`
- `tests/test_android_ci_gate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/validation/r14-candidate-attempt9/failure-evidence.json`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `/internal-ci/v1/android/bootstrap（合同不变，仅调用方有限重试）`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `authentication.bootstrap_retry`

## 资金/账本与历史数据

- `PROB-0091原位扩展与R14 Attempt9基础设施失败证据`

## 测试

- `python -m unittest tests.test_android_ci_gate tests.test_android_candidate_request tests.test_android_candidate_route`
- `shell source gate locks exact retry statuses, attempts, backoff and fail-fast behavior`

## 版本

- `R14`

## 迁移与兼容策略

旧候选Run和已消费授权保持不可变；新增有限重试只对后续GitHub候选生效。服务端状态和响应体仍不泄露，非5xx错误仍即时失败。CR-0486业务修复和Attempt9第三轮语义继续保留，但新候选必须使用新的精确请求授权。

## 用户确认

项目所有者已授权持续开发、终端静默执行、GitHub候选由AI审核，并要求可复用经验写入全局事实源；无需逐项再次确认。

## 审批

- 审批人：`project-owner-continuity-directive-20260730`
- 决定：`APPROVED`
- 时间：`2026-07-29T20:10:27Z`
- 说明：独立复审通过：本CR原位扩展既有OIDC authentication和PROB-0091，有限重试只覆盖明确5xx且总次数、退避、非5xx即时失败、无秘密输出均冻结；不会弱化身份绑定、业务断言或把Attempt9基础设施预检失败计入拉黑业务第三轮。

## 状态记录 · 2026-07-29T20:10:45Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：开始在既有authentication事实源和唯一Android质量门禁中实现严格5xx有限重试，并原位扩展PROB-0091与Attempt9证据。

## 状态记录 · 2026-07-29T20:16:37Z

- Actor：`codex-r14-resume-20260730`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：Commit a4b88424实现既有authentication.bootstrap_retry严格合同、唯一工作流有限重试、96项治理回归、实际bash语义复验、PROB-0091原位扩展和Attempt9无秘密证据；未改业务断言。
