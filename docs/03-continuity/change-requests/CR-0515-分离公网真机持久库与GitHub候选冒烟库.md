---
cr_id: CR-0515
status: APPROVED
requester_actor_id: codex-r14-owner-test-persistence
approver_actor_id: codex-r14-data-safety-authorizer
task_id: TASK-R14-008
session_id: SES-20260729T161557Z-6FCE6ACA
created_at: 2026-07-30T04:32:49Z
updated_at: 2026-07-30T04:33:28Z
---
# CR-0515 — 分离公网真机持久库与GitHub候选冒烟库

## 用户需求摘要

用户多次注册账号后跨版本无法登录且HHY2026TEST邀请码失效；要求解释是否清库并彻底避免再次发生

## 原规则

既有部署规则要求公网开发环境不属于可销毁演练，应用升级保持同一PostgreSQL容器和卷；但Android候选路由流程只证明候选自身正确，没有强制候选结束后恢复公网持久库，执行层因此把api.orbexa.cc长期留在版本冒烟库。

## 新规则

api.orbexa.cc必须常态指向独立owner-test持久数据库；版本升级只替换冻结镜像并前向迁移，不得重建、清空或换卷。GitHub候选数据库只能承载自动夹具，候选结束无论PASS/FAIL都必须恢复owner-test路由并由机器门禁证明数据库容器、卷、CI关闭、公开健康和邀请码连续性。

## 修改原因

公网api.orbexa.cc被长期指向R13/R14版本候选冒烟数据库，跨版本切换导致用户数据和临时邀请码不可见，违反既有公网开发环境不属于隔离演练且数据库同库同卷连续的规则

## 影响摘要

将当前R14数据复制到独立hhy-owner-test PostgreSQL卷，恢复指定账号和测试邀请码，公网切到持久后端；在既有全局边界和部署手册中加固同一规则，并增加可复用升级、候选恢复与环境核验入口。

## 影响文件

- `docs/00-baseline/正式商业系统全局硬性开发边界.md`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `config/owner-test-environment.yaml`
- `config/android-automation.yaml`
- `scripts/promote_owner_test_backend.sh`
- `scripts/restore_android_candidate_route.sh`
- `scripts/check_owner_test_environment.py`
- `scripts/switch_android_candidate_route.sh`
- `scripts/android_ci_gate.py`
- `tests/test_owner_test_environment.py`
- `tests/test_android_candidate_route.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `artifacts/validation/r14-owner-test-persistence/owner-test-persistence.json`

## 页面

- `无前端页面变更`

## API

- `api.orbexa.cc公网真机测试入口`

## 数据库与迁移

- `hhy-owner-test-postgres/hhy-owner-test-postgres-data，禁止候选库替代`

## 配置

- `owner-test机器事实、候选后恢复证明和当前R14持久后端身份`

## 资金/账本与历史数据

- `不修改资金与账本事实`

## 测试

- `静态配置门禁、脚本策略测试、服务器容器/卷/路由/健康/邀请码/密码哈希匹配实证`

## 版本

- `R14`

## 迁移与兼容策略

首次从R14冒烟库做一致性快照迁入独立持久卷；后续只允许Flyway前向迁移和应用镜像回切，禁止down -v、DROP DATABASE、TRUNCATE用户业务表或把公网持久入口永久切到hhy-r*-staging/smoke数据库。

## 用户确认

项目所有者报告多个账号跨版本后无法登录及HHY2026TEST失效，并要求确认是否清库；此前已明确要求发现问题立即修复、规则跨电脑和AI生效且无需逐次批准。

## 审批

- 审批人：`codex-r14-data-safety-authorizer`
- 决定：`APPROVED`
- 时间：`2026-07-30T04:33:28Z`
- 说明：本变更更新既有数据库连续性规则而非另建同义规则；持久库与候选库分离能消除跨版本账号丢失，且不保存手机号、密码或哈希到仓库。

## 状态记录 · 2026-07-30T04:33:35Z

- Actor：`codex-r14-owner-test-persistence`
- Status：`IMPLEMENTING`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：服务器持久库和公网路由已恢复，开始补齐仓库硬规则、可复用脚本、机器配置与回归。

## 状态记录 · 2026-07-30T04:42:07Z

- Actor：`codex-r14-owner-test-persistence`
- Status：`IMPLEMENTED`
- Session：`SES-20260729T161557Z-6FCE6ACA`
- Note：公网已切到独立持久库；账号、密码公网登录、邀请码、容器/卷/网络、CI关闭、恢复脚本与全部静态回归均PASS，仓库未保存任何手机号、密码、哈希或Token。
