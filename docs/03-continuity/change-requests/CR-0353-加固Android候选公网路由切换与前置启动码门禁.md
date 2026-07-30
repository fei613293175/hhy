---
cr_id: CR-0353
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: project-owner
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-26T05:15:52Z
updated_at: 2026-07-26T05:16:33Z
---
# CR-0353 — 加固Android候选公网路由切换与前置启动码门禁

## 用户需求摘要

全局检查开发文档、硬性门禁、开发进度、优点复用和踩坑记录；存在漂移立即加固优化。

## 原规则

候选后端部署只要求容器健康和公网状态200；启动码请求在Java、Android SDK、KVM准备之后执行，curl失败只返回笼统HTTP状态。

## 新规则

候选切流必须通过唯一受控脚本完成：精确验证旧/新upstream、目标容器端口与本机健康，备份Nginx配置后切换，nginx -t成功才reload，并以唯一X-Request-ID从公网访问后证明请求命中目标容器；任一步失败自动回滚。GitHub必须在安装Java、Android SDK和KVM之前分别验证OIDC与Staging bootstrap并输出不含令牌/响应体的阶段化HTTP状态。未取得端到端命中证据禁止候选。

## 修改原因

R12候选修复容器已部署健康，但api.orbexa.cc反向代理仍指向旧端口，公网bootstrap连续命中旧镜像；现有门禁缺少目标容器到公网路由的端到端命中证明，且启动码检查位于Android工具链安装之后造成无效耗时。

## 影响摘要

原位加固既有Android自动候选和固定远程环境规则，新增受控可回滚路由脚本及静态回归，前置启动码门禁减少错误路由时的无效Android准备；不修改产品页面、业务API、数据库、资金规则或全局候选次数。

## 影响文件

- `.github/workflows/android-quality-gate.yml`
- `config/android-automation.yaml`
- `scripts/android_ci_gate.py`
- `scripts/switch_android_candidate_route.sh`
- `tests/test_android_ci_gate.py`
- `tests/test_android_candidate_route.py`
- `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `CHANGELOG.md`
- `docs/03-continuity/change-requests/CR-0353-加固Android候选公网路由切换与前置启动码门禁.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `POST /internal-ci/v1/android/bootstrap（只调整候选前置验证与诊断，不改合同）`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `api.orbexa.cc Nginx upstream和Android automation public_route_activation`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest -v tests.test_android_ci_gate tests.test_android_candidate_route tests.test_r12_candidate`

## 版本

- `R12`

## 迁移与兼容策略

现役api.orbexa.cc已从旧28099切至已验证的新28098并保留pre-r12-fbde523e备份；脚本对既有Nginx主机部署，要求显式YES、精确旧/新upstream与目标容器。当前attempt5第二次工作流失败证据保留，不绕过max_same_commit_reruns=1，不自动登记attempt6。

## 用户确认

用户明确要求：全局检查开发文档要求、硬性门禁规则、开发进度、优点复用和踩坑记录，存在漂移立即加固优化相关规则。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-26T05:16:33Z`
- 说明：项目所有者本轮明确要求全局检查并对发现的漂移立即加固；批准原位更新既有规则、受控路由脚本和前置启动码门禁，不批准放宽候选次数或登记attempt6。

## 状态记录 · 2026-07-26T05:16:52Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：开始实现受控可回滚公网路由切换、策略机检、GitHub前置启动码诊断与治理知识同步。

## 状态记录 · 2026-07-26T05:18:24Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`SUPERSEDED`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：实现前影响范围复核发现遗漏config/test-impact-map.yaml与tests/test_run_affected_tests.py；为避免门禁代码本身未进入远端tooling测试，CR-0353未实施即停止，由完整影响范围的新CR取代。
