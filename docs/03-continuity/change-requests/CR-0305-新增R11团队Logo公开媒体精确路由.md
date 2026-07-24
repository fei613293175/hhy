---
cr_id: CR-0305
status: APPROVED
requester_actor_id: codex-root-r11-candidate
approver_actor_id: codex-reviewer-r11-media-route
task_id: TASK-R11-007
session_id: SES-20260724T032308Z-98D6D10A
created_at: 2026-07-24T05:04:20Z
updated_at: 2026-07-24T05:05:06Z
---
# CR-0305 — 新增R11团队Logo公开媒体精确路由

## 用户需求摘要

AI自主修复不合格候选并继续开发

## 原规则

download.orbexa.cc默认拒绝未登记路径，仅R09既有两张候选图片有精确location

## 新规则

新增且仅新增/r09-candidate-media/attempt-2/r11-team-logo.png精确只读image/png路由，保留默认404、nosniff和短缓存；部署前备份配置并通过nginx -t

## 修改原因

download.orbexa.cc保持默认404且只放行精确资源，新增团队Logo文件后必须登记并部署单文件Nginx路由才可供候选加载

## 影响摘要

新增一个版本控制的Nginx include并部署到现有download站点；不开放目录浏览、不放宽通用路径、不改历史资源

## 影响文件

- `infra/nginx/r11-candidate-media-location.inc`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `download.orbexa.cc:r11-team-logo-exact-route`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `nginx-t;HTTP200-image/png;SHA256-equality;unknown-path-404`

## 版本

- `R11`

## 迁移与兼容策略

旧R09图片和APK路由保持不变；R11文件删除或回滚include即可恢复原状态

## 用户确认

项目所有者已授权AI自主修复候选并静默执行服务器命令

## 审批

- 审批人：`codex-reviewer-r11-media-route`
- 决定：`APPROVED`
- 时间：`2026-07-24T05:05:06Z`
- 说明：默认404策略要求精确白名单；单文件include、备份、nginx -t和未知路径404验证是最小安全改动

## 状态记录 · 2026-07-24T05:13:16Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTING`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：精确媒体include已版本化并部署，nginx -t、200 image/png、SHA一致及未知路径404通过

## 状态记录 · 2026-07-24T05:13:26Z

- Actor：`codex-root-r11-candidate`
- Status：`IMPLEMENTED`
- Session：`SES-20260724T032308Z-98D6D10A`
- Note：download.orbexa.cc仅新增单文件精确路由，默认404保持不变
