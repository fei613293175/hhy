---
cr_id: CR-0469
status: APPROVED
requester_actor_id: codex-r14-observability-20260729
approver_actor_id: project-owner-continuous-development-20260729
task_id: TASK-R14-006
session_id: SES-20260728T195020Z-B1DAB2D3
created_at: 2026-07-28T20:13:49Z
updated_at: 2026-07-28T20:14:28Z
---
# CR-0469 — 按CR-0464目录源重建R14隔离Staging验收

## 用户需求摘要

持续推进R14至R32且不得依赖旧聊天或过期证据关闭版本

## 原规则

R14隔离Staging验收允许报告report_catalog_status=BLOCKED_PRODUCT_CATALOG，并绑定早于CR-0464的b6f5e285冻结Commit；当时仅因TASK-R14-005未完成而保持TASK-R14-006阻断。

## 新规则

不新增产品规则；复用CR-0464唯一举报原因目录。R14 Staging验收入口必须先运行目录生成一致性检查并证明CR-0464源提交是冻结Commit祖先，机器摘要记录目录源Commit、源SHA和PASS；仅在新冻结Commit重建镜像、Flyway V044、八Gauge、八告警、三组firing/resolved/回执、日志脱敏和同卷回滚全部PASS后，才可将staging_contains_report_catalog与TASK-R14-006验收更新为PASS。

## 修改原因

R14旧可观测性验收PASS但冻结Commit早于CR-0464举报原因目录，RELEASE_MANIFEST明确标记staging_contains_report_catalog=false和staging_redeploy_required=true，必须在包含目录源的新冻结Commit重建隔离Staging证据后才能完成TASK-R14-006

## 影响摘要

重新冻结并执行R14隔离Staging验收，替换过期证据；不改页面、API、数据库迁移、业务枚举、生产路由、DNS或公网WebSocket。

## 影响文件

- `scripts/run_r14_staging_acceptance.sh`
- `artifacts/validation/r14-task006-staging`
- `artifacts/reports/R14/TASK-R14-006-staging.md`
- `releases/R14/RELEASE_MANIFEST.yaml`

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

- `python scripts/generate_chat_report_reason_catalog.py --check`
- `HHY_R14_FROZEN_COMMIT=<commit> bash scripts/run_r14_staging_acceptance.sh`
- `python scripts/check_program_execution_plan.py --json`

## 版本

- `R14`

## 迁移与兼容策略

使用独立hhy-r14-staging Compose project；重跑前仅清理该隔离项目容器和卷，旧证据已入库可恢复。公网与现有生产/测试容器不受影响。

## 用户确认

用户要求持续开发至R32且所有版本必须按仓库证据闭环，不因其未即时真机反馈而停止。

## 审批

- 审批人：`project-owner-continuous-development-20260729`
- 决定：`APPROVED`
- 时间：`2026-07-28T20:14:28Z`
- 说明：项目所有者已明确要求持续推进且不得以旧证据或聊天状态造成版本漂移；本CR只在既有R14隔离验收事实源内补齐CR-0464目录源并重跑，不新增平行规则或生产变更。

## 状态记录 · 2026-07-28T20:16:08Z

- Actor：`codex-r14-observability-20260729`
- Status：`IMPLEMENTING`
- Session：`SES-20260728T195020Z-B1DAB2D3`
- Note：验收入口已加入CR-0464祖先校验、生成一致性检查和目录源机器证据，准备冻结脚本Commit并在obx-test重跑隔离Staging。
