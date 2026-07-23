---
cr_id: CR-0294
status: APPROVED
requester_actor_id: codex-root-r11-client
approver_actor_id: codex-independent-cloud-cache-reviewer
task_id: TASK-R11-004
session_id: SES-20260723T183130Z-454A6E0D
created_at: 2026-07-23T22:05:58Z
updated_at: 2026-07-23T22:08:48Z
---
# CR-0294 — 持久化云端Android 36平台缓存

## 用户需求摘要

解决服务器构建期间资源拥塞与连接不稳定，并将可复用方案写入项目事实源。

## 原规则

CR-0290已限制构建并发和资源，但固定镜像不含Android 36，短生命周期容器每轮仍自动下载146MB平台包。

## 新规则

所有云端Android构建继续使用受控包装器，并额外挂载hhy-android-sdk-platform-36到/opt/android-sdk/platforms/android-36；预检同时验证平台卷和android.jar，禁止重新退回每轮临时安装。

## 修改原因

资源隔离已生效，但固定构建镜像缺少Android 36，短生命周期容器每轮仍重复下载约146MB并额外耗时1至2分钟。

## 影响摘要

消除每轮1至2分钟SDK重复下载、额外CPU内存和网络峰值，进一步降低SSH连接受资源拥塞影响的概率。

## 影响文件

- `scripts/verify_cloud_environment.py`
- `scripts/tests/test_verify_cloud_environment.py`
- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/07-operations/DEPLOYMENT_RUNBOOK.md`

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

- `云端平台卷验证、包装器SHA、无重复Install日志、预检单测与真实预检`

## 版本

- `R11`

## 迁移与兼容策略

仅增加专用平台目录命名卷，不挂载或覆盖整个Android SDK；固定镜像android-37.0和Gradle缓存保持不变。

## 用户确认

项目所有者已明确授权服务器侧解决连接拥塞，并要求终端命令由AI直接执行且经验写入事实源。

## 审批

- 审批人：`codex-independent-cloud-cache-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-23T22:08:48Z`
- 说明：独立复核确认专用卷只覆盖android-36平台目录，不覆盖完整SDK；受控包装器、资源锁和限制保持不变，回滚可恢复备份包装器并停止挂载。

## 状态记录 · 2026-07-23T22:08:51Z

- Actor：`codex-root-r11-client`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T183130Z-454A6E0D`
- Note：服务器平台卷和包装器挂载已完成，开始更新预检、测试和运维事实源。
