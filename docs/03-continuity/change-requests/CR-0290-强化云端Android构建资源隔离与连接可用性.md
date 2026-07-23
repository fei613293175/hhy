---
cr_id: CR-0290
status: APPROVED
requester_actor_id: codex-root-r11-client
approver_actor_id: codex-independent-infra-reviewer
task_id: TASK-R11-004
session_id: SES-20260723T183130Z-454A6E0D
created_at: 2026-07-23T20:35:47Z
updated_at: 2026-07-23T20:36:14Z
---
# CR-0290 — 强化云端Android构建资源隔离与连接可用性

## 用户需求摘要

解决服务器资源峰值导致SSH连接超时，并固化为跨电脑跨AI可复用方案

## 原规则

固定Android服务器容器必须复用既有镜像和Gradle缓存；短时无日志时先检查活动，禁止误判卡死、并行重启或重建镜像。

## 新规则

在原条款内增加：云端Android构建必须获取服务器唯一flock互斥锁，容器限制CPU/内存/PID，Gradle使用单worker与受控JVM堆；预检必须验证持久Swap和最低可用内存，构建完成后自动删除临时容器。SSH超时时先区分MaxStartups与资源饱和，禁止盲目提高SSH容量或并发重连。

## 修改原因

4核15GB服务器常驻约60个历史Staging容器且无Swap，Android MODULE峰值使可用内存降至198MB并造成SSH banner超时；现有规则仅约束固定镜像缓存和不重复构建，未强制互斥、资源上限与Swap预检。

## 影响摘要

增强现有云端Android经验与运维事实源，并由verify_cloud_environment.py检查Swap、内存和构建锁；不创建并列硬规则，不影响产品功能。

## 影响文件

- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`
- `scripts/verify_cloud_environment.py`
- `scripts/tests/test_verify_cloud_environment.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `云环境预检单测、脚本静态检查、真实obx-test资源预检`

## 版本

- `R11`

## 迁移与兼容策略

服务器已在线新增8GB持久Swap且swappiness=10；不重启业务、不删除历史容器。旧调用仍可用，但预检会对资源不足给出明确阻断。

## 用户确认

项目所有者已询问能否在服务器解决，并要求持续开发和跨AI复用既定方案。

## 审批

- 审批人：`codex-independent-infra-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-23T20:36:14Z`
- 说明：在既有规则原位增强，采用Swap保险、构建互斥和受限Gradle，不删除或重启线上容器；范围最小且可回滚。

## 状态记录 · 2026-07-23T20:36:20Z

- Actor：`codex-root-r11-client`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T183130Z-454A6E0D`
- Note：8GB持久Swap已在线启用，开始增强既有规则与云环境预检。
