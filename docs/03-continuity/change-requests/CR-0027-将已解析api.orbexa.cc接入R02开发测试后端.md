---
cr_id: CR-0027
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-runtime-governance-reviewer
task_id: TASK-R02-002
session_id: SES-20260717T210927Z-13B07A7D
created_at: 2026-07-18T01:22:21Z
updated_at: 2026-07-18T01:22:47Z
---
# CR-0027 — 将已解析api.orbexa.cc接入R02开发测试后端

## 用户需求摘要

项目所有者已解析api和download子域名，并在真机反馈R02 APK启动暂时无法连接后，授权持续开发与服务器操作。

## 原规则

R02当前会话范围不含infra，api.orbexa.cc虽已解析但没有受版本控制的开发反向代理，Android启动门禁只能进入暂时无法连接兜底页。

## 新规则

R02开发测试允许新增并部署精确的infra/nginx/api.orbexa.cc.conf：Cloudflare公网TLS入口转发到服务器loopback-only的P00/R02开发后端；不得暴露上游端口、不得包含秘密。

## 修改原因

真机证据确认公网DNS可达但Nginx无api虚拟主机，导致启动门禁无法调用平台状态和版本策略。

## 影响摘要

新增开发API虚拟主机和真机启动环境对齐；不改变冻结业务契约、数据库语义或生产短信配置。

## 影响文件

- `infra/nginx/api.orbexa.cc.conf`
- `apps/android/app/build.gradle.kts`

## 页面

- `SCR-AUTH-001,启动门禁`

## API

- `GET /public-api/v1/platform/status;POST /public-api/v1/app/version-check`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Cloudflare api.orbexa.cc,Nginx loopback proxy,HHY_APP_ENVIRONMENT`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `public HTTPS platform status and STAGING version-policy probes;Nginx syntax validation;Android cloud build`

## 版本

- `R02`

## 迁移与兼容策略

先执行nginx -t后reload；代理上游限定127.0.0.1:28080，失败可移除唯一新增虚拟主机恢复默认404；Android可用HHY_APP_ENVIRONMENT覆盖，默认对齐已发布STAGING渠道。

## 用户确认

项目所有者在本会话确认api/download已解析，并要求持续开发、服务器由Codex操作；真机截图提供了该修复的直接验收证据。

## 审批

- 审批人：`codex-runtime-governance-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-18T01:22:47Z`
- 说明：独立审核确认：用户已授权服务器和已解析子域名操作；该变更仅接通受控开发代理，保留loopback上游和公网验证。
