# TASK-R07-008 R07 机器完成与异步真机交接

## 结论

R07 搜索、发布者主页与联系方式保护的业务实现、数据库、专项测试、隔离 Staging、Android 最终候选、五页视觉合同和桌面交付均已完成。机器状态为 `PASS`；项目所有者真机状态保持 `PENDING`，因此不伪造正式 Release 验收或生产激活，但按批准的异步反馈规则允许切换到 R08 继续开发。

## 版本事实

- Task：`TASK-R07-008`
- Story：`STORY-R07-005`
- Session：`SES-20260721T235847Z-F9109B61`
- 机器关闭 CR：`CR-0203`
- Android 产品 Commit：`69406d918b4c2aa6f1eea78baed48464e4f94277`
- 完整候选 GitHub Run：`29875839322`
- 同源轻量晋升 Run：`29877272491`
- APK：`hhy-r07-69406d9-debug.apk`
- versionName / versionCode：`1.2.2-debug` / `10207`
- SHA-256：`48d1486878c562246759432bce2b4b9645df80c24a9ccc0017a20f3e7799813f`
- 公网下载：`https://download.orbexa.cc/r07-artifacts/hhy-r07-69406d9-debug.apk`
- API：`https://api.orbexa.cc`

## 验收矩阵证据

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| AC-R07-001 领域代码与前端闭环 | PASS | 搜索、热词、历史、发布者主页、内容列表和联系方式访问的后端与 Android 真实闭环；五页候选旅程通过 |
| AC-R07-002 数据迁移与不变量 | PASS | `TASK-R07-002-database.md`；V031/U031 空库、升级、负向不变量、回滚和重放均通过 PostgreSQL 17.10 |
| AC-R07-003 契约/单元/集成/E2E/安全 | PASS | `TASK-R07-005-specialized-testing.md`；冻结 10 项测试 10/10 自动化，P0/P1 缺陷为 0 |
| AC-R07-004 观测与告警 | PASS | `TASK-R07-006-staging.md`；TraceId、RED、六项业务 Gauge、七条规则、关键告警触发恢复与同库同卷回切通过 |
| AC-R07-005 无状态交接 | MACHINE PASS | 本报告、Session/Checkpoint、Release Manifest、CR 链与 `BLOCKED_EXTERNAL_GATE → R08` 转换记录；正式验收仍待真机反馈 |
| AC-R07-006 APK 追溯 | PASS | `TASK-R07-007-android-apk.md`、`artifacts/apk/R07/APK_MANIFEST.yaml` 和仓库/桌面/服务器/HTTPS 四方 SHA 证据 |

## 五页视觉与安全验收

`python scripts/check_ui_visual_acceptance.py --release R07` 返回 `UI_VISUAL_ACCEPTANCE_OK R07 pages=5`。

| 页面 | 证据 | AI 审查 |
| --- | --- | --- |
| `SCR-SEARCH-001` | `01-search-landing.png` | 页面身份、真实历史热词、字号边距、无技术字段通过 |
| `SCR-SEARCH-002` | `02-search-results.png` | 真实搜索结果、筛选、返回栈恢复与卡片边界通过 |
| `SCR-PUBLISHER-001` | `03-publisher.png` | 真实发布者夹具、公开内容、掩码入口与 Token 通过 |
| `DIALOG-SEARCH-001` | `04-clear-history-dialog.png` | 遮罩、不可恢复范围、主次动作和上下文通过 |
| `SHEET-CONTACT-001` | `candidate-report.json` | 按安全规则禁止截图；标题、语义资源、获取与关闭按钮、`FLAG_SECURE` 通过 |

四张非敏感截图均绑定同一已认证真实旅程，互不重复且没有登录/注册页、联系方式明文、内部错误、请求编号或 TraceId。首次视觉基线使用单次模拟器采集和 23 秒同源轻量晋升，没有为批准同一批截图重复完整构建。

## 异步所有者门禁

- `owner_physical_test=PENDING`：保留真实状态，不能改成 PASS。
- `formal_release_acceptance=PENDING_OWNER_PHYSICAL_TEST`：继续阻断 R07 正式 Release 验收。
- `production_activation=BLOCKED_OWNER_PHYSICAL_TEST`：继续阻断生产激活。
- `next_release_development=ALLOWED`：不阻断 R08 及后续依赖满足版本的开发、机器候选和桌面 APK 累积交付。

收到项目所有者反馈后，必须回到 R07 执行受控 `accept`；若发现问题，进入当前适用版本或批准热修队列，不覆盖本报告中的历史机器证据。
