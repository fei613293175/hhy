---
cr_id: CR-0354
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: project-owner
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-26T05:18:38Z
updated_at: 2026-07-26T05:19:09Z
---
# CR-0354 — 完整加固Android候选公网路由与前置启动码门禁

## 用户需求摘要

全局检查开发文档、硬性门禁、开发进度、优点复用和踩坑记录；存在漂移立即加固优化。

## 原规则

候选后端部署只要求容器健康和公网状态200；启动码请求在Java、Android SDK、KVM准备之后执行，curl失败只返回笼统HTTP状态；新路由脚本若不进入既有影响映射，普通CI可能跳过对应Python回归。

## 新规则

候选切流必须通过唯一受控脚本完成：精确验证旧/新upstream、目标容器端口与本机健康，备份Nginx配置后切换，nginx -t成功才reload，并以唯一X-Request-ID从公网访问后证明请求命中目标容器；任一步失败自动回滚。GitHub必须在安装Java、Android SDK和KVM之前分别验证OIDC与Staging bootstrap并输出不含令牌/响应体的阶段化HTTP状态。路由脚本、策略与专项测试必须进入既有Android影响映射。未取得端到端命中证据禁止候选。

## 修改原因

CR-0353实现前发现遗漏测试影响映射而SUPERSEDED；R12候选修复容器健康但公网代理仍指向旧端口，现有门禁缺少端到端命中证明并在Android重型准备后才验证启动码。

## 影响摘要

原位加固既有Android自动候选、固定远程环境与测试影响映射，新增受控可回滚路由脚本及专项回归，前置启动码门禁减少错误路由时的无效Android准备；不修改产品页面、业务API、数据库、资金规则或全局候选次数。

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `config/android-automation.yaml`
- `config/test-impact-map.yaml`
- `scripts/android_ci_gate.py`
- `scripts/switch_android_candidate_route.sh`
- `tests/test_android_ci_gate.py`
- `tests/test_android_candidate_route.py`
- `tests/test_run_affected_tests.py`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `CHANGELOG.md`
- `docs/03-continuity/change-requests/CR-0354-完整加固Android候选公网路由与前置启动码门禁.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `POST /internal-ci/v1/android/bootstrap（只调整候选前置验证与诊断，不改合同）`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `api.orbexa.cc Nginx upstream、Android automation public_route_activation和既有Android影响映射`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest -v tests.test_android_ci_gate tests.test_android_candidate_route tests.test_r12_candidate tests.test_run_affected_tests`

## 版本

- `R12`

## 迁移与兼容策略

CR-0353未实施即SUPERSEDED；现役api.orbexa.cc已从旧28099切至已验证的新28098并保留pre-r12-fbde523e备份。脚本要求显式YES、精确旧/新upstream与目标容器。attempt5第二次工作流失败证据保留，不绕过max_same_commit_reruns=1，不登记attempt6。

## 用户确认

用户明确要求发现漂移后立即加固优化相关规则；本CR只完善既有门禁及其影响映射。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-26T05:19:09Z`
- 说明：本轮全局漂移检查明确授权立即加固；批准完整影响范围，保持候选重跑上限和attempt6禁令不变。

## 状态记录 · 2026-07-26T05:19:21Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：开始按完整影响范围实现并验证。

## 状态记录 · 2026-07-26T05:28:42Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTED`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：受控切流脚本在obx-test bash -n及现役28098幂等执行PASS，公网requestId命中目标容器；Android策略/请求/路由/R12/影响映射64项PASS，严格文档PASS。候选治理变化只选择tooling+contracts，不选择android；apps/android变化仍选择android。attempt5不第三次重跑，attempt6未登记。
