# TASK-R08-008 R08 机器完成与异步真机交接

## 结论

R08 项目列表、详情、发布编辑及其数据、后端、H5/后台复用、专项测试、隔离 Staging、Android 候选、三页视觉基线和桌面交付均已完成。机器状态为 `PASS`；项目所有者真机状态保持 `PENDING`，不伪造正式 Release 验收或生产激活。按项目所有者最新顺序，R08 关闭后立即开展 P00/R01—R08 全部已实现前端页面的全局 UI 审计与返工。

## 版本事实

- Task：`TASK-R08-008`
- Story：`STORY-R08-004`
- Session：`SES-20260722T093645Z-C7DB8EF0`
- 机器关闭 CR：`CR-0243`
- Android 产品候选 Commit：`49f40f2dfeb71b5f5210d2e03a500307bf42c31c`
- 首版视觉采集 Run：`29905158793`
- 同源轻量晋升 Run：`29906167593`
- APK：`hhy-r08-49f40f2-debug.apk`
- versionName / versionCode：`1.2.2-debug` / `10216`
- SHA-256：`d424c880ccf45cb1f092bef5a88a6459ec17747c95f3b9ed1a833de09d63557a`
- 公网下载：`https://download.orbexa.cc/r08-artifacts/hhy-r08-49f40f2-debug.apk`
- 桌面副本：`C:\Users\小白\Desktop\hhy-r08-49f40f2-debug.apk`
- API：`https://api.orbexa.cc`

## 验收矩阵证据

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| AC-R08-001 领域代码与前端闭环 | PASS | `TASK-R08-004-client.md`；项目列表、详情、发布编辑及 H5/后台真实复用闭环 |
| AC-R08-002 数据迁移与不变量 | PASS | `TASK-R08-002-database.md`；V034/U034 与真实 PostgreSQL 17.10 验证 |
| AC-R08-003 契约/单元/集成/E2E/安全 | PASS | `TASK-R08-005-specialized-tests.md`；冻结专项矩阵及故障注入通过 |
| AC-R08-004 观测与告警 | PASS | `TASK-R08-006-staging.md`；RED、TraceId、项目指标、告警和同库回切通过 |
| AC-R08-005 无状态交接 | MACHINE PASS | 本报告、Session/Checkpoint、Release Manifest、CR 链和后续全局 UI 精确起点 |
| AC-R08-006 APK 追溯 | PASS | `TASK-R08-007-android-apk.md`、`artifacts/apk/R08/APK_MANIFEST.yaml` 和四方 SHA 证据 |

## Android 与视觉证据边界

- Run `29905158793` 完成固定工具链构建、安装、OIDC 自动认证和 R08 三页真实旅程；Run `29906167593` 只复用原工件完成轻量基线晋升，候选报告状态为 `PASS`。
- `SCR-LIST-001`、`SCR-DETAIL-001`、`SCR-PUB-002` 三张截图由 AI 逐图审核，未展示技术码、虚构媒体、虚构收益或联系方式明文，视觉验收状态为 `PASS`。
- Run `29927212504` 在同一产品代码上再次完成模拟器与三页截图；按 `CR-0242` 的 R08 三页清单本地重算为 0 个视觉失败，跨屏比例为 `0.163367`、`0.160908`，相对批准基线变化比例均低于 `0.002`。
- Run `29929926432` 的当前 HEAD 编译、lint、单测和 APK 打包通过；模拟器阶段因服务器重启后专用 CI 容器未自动恢复而在引导接口收到 HTTP 502。一次受控失败作业重试同因失败后停止，没有继续消耗 GitHub。该失败不覆盖已通过候选，也不伪装为当前 Run PASS。

## Staging 恢复与后续预防

- `api.orbexa.cc` 的宿主 Nginx 正确指向专用 CI 后端 `127.0.0.1:28093`；服务器重启后容器 `hhy-r08-ci-candidate-94ba1fee` 因 `restart=no` 停止，形成确定性 502。
- 已启动原验证容器并等待应用及 9091 健康端口就绪；公网 `/public-api/v1/platform/status` 恢复 HTTP 200，CI bootstrap 无令牌探针恢复为业务 400 而非上游 502。
- 后续候选在进入 GitHub 构建前必须先核对公网 API 与专用 CI 上游，避免把可在数秒内发现的基础设施漂移拖到构建之后。

## 历史 UI 与异步所有者门禁

- P00/R01—R08 历史页面仍以 `catalogs/ui_visual_acceptance.csv` 的实际状态为准；`IN_REVIEW` 不得因为 R08 关闭而改成 `PASS`。
- `owner_physical_test=PENDING`：保留真实状态，不强制项目所有者逐版本反馈。
- `formal_release_acceptance=PENDING_OWNER_PHYSICAL_TEST`：继续阻断正式 Release 验收。
- `production_activation=BLOCKED_OWNER_PHYSICAL_TEST`：继续阻断生产激活。
- `next_work=GLOBAL_UI_AUDIT_P00_R01_TO_R08`：R08 机器关闭后立即执行，不等待真机反馈，也不先进入 R09 业务开发。

