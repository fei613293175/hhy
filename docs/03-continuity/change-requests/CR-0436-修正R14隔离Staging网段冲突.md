---
cr_id: CR-0436
status: APPROVED
requester_actor_id: codex-root-r14-client-20260728
approver_actor_id: codex-r14-staging-network-independent-review-20260728
task_id: TASK-R14-004
session_id: SES-20260727T221444Z-FD353AD3
created_at: 2026-07-28T07:37:56Z
updated_at: 2026-07-28T07:39:01Z
---
# CR-0436 — 修正R14隔离Staging网段冲突

## 用户需求摘要

持续推进R14隔离Staging和TEST_APK，不修改旧版本或公网资源

## 原规则

CR-0434将R14隔离Staging网段冻结为172.31.240.0/24。

## 新规则

仅将R14隔离Staging网段修正为现场已验证未占用的172.31.238.0/24；Compose project仍为hhy-r14-staging，端口仍为38114/39618/39619，所有卷仍为该project独占。禁止删除、断开、复用或修改占用172.31.240.0/24的R12网络以及R13和任何公网资源。CR-0434其余指标、告警、回滚、APK和外部门禁规则全部不变。

## 修改原因

obx-test真实现场证明172.31.240.0/24已由hhy-r12-staging-a2_smoke占用，Docker在R14创建网络前以Pool overlaps拒绝。必须改用未占用的172.31.238.0/24，保留所有端口、项目名、独占卷及不触碰旧版本资源的规则。

## 影响摘要

消除Docker地址池冲突，使R14在不清理旧版本资源的前提下创建真正独占网络。

## 影响文件

- `infra/staging/r14-smoke/docker-compose.yml`
- `scripts/run_r14_staging_acceptance.sh`
- `artifacts/validation/r14-task006-staging/metadata.txt`
- `artifacts/reports/R14/TASK-R14-006-staging.md`
- `CHANGELOG.md`

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

- `docker network inventory证明172.31.240.0/24冲突且172.31.238.0/24未占用；compose config、空项目启动和完整R14 Staging验收必须重跑通过。`

## 版本

- `R14`

## 迁移与兼容策略

无数据库或API迁移；失败尝试在创建R14网络前终止且未产生R14容器或卷。改用新网段后从空hhy-r14-staging项目完整重跑。

## 用户确认

用户已要求持续推进且所有终端自主执行；该修正用于避免修改旧版本资源。

## 审批

- 审批人：`codex-r14-staging-network-independent-review-20260728`
- 决定：`APPROVED`
- 时间：`2026-07-28T07:39:01Z`
- 说明：现场清单确认240被旧R12网络占用且238未占用；仅更换R14隔离网段是最小修正，并明确禁止清理R12、R13或公网资源，其余CR-0434边界不变。

## 状态记录 · 2026-07-28T07:39:11Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTING`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：独立审批通过，开始将R14独占网段修正为172.31.238.0/24并从空项目重跑。

## 状态记录 · 2026-07-28T08:00:31Z

- Actor：`codex-root-r14-client-20260728`
- Status：`IMPLEMENTED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：R14独占网段172.31.238.0/24已在空hhy-r14-staging项目真实创建，未修改R12/R13/公网资源；精确Commit完成完整Staging验收，现场证据已回收并通过双端SHA256。

## 状态记录 · 2026-07-28T08:53:32Z

- Actor：`codex-root-r14-client-20260728`
- Status：`CLOSED`
- Session：`SES-20260727T221444Z-FD353AD3`
- Note：Staging验收脚本数值测试用户ID纠偏已通过真实隔离验收并随证据提交关闭
