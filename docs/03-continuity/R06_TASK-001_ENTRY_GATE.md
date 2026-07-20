# TASK-R06-001 开发就绪核验

- Release：`R06` - 统一内容基础、首页与 CMS
- Session：`SES-20260720T155546Z-F13C645A`
- Story：`STORY-R06-005`（契约、数据、配置、测试与交接）
- 顺序前置：R05 已完成，连续推进依据为已批准 `CR-0145`

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | `REQ-CONTENT-001`、`REQ-HOME-001`，另含 APK 通用门禁 |
| 页面/交互面 | 5 个：Android 2 个、后台 3 个 |
| API | R06 范围 10 个冻结接口引用 |
| Story | 5 个，均为 `READY_FOR_IMPLEMENTATION` |
| 数据 | 18 张相关表，核心为 `content_posts`、版本/状态/审核/媒体/联系人及首页模块 |
| DoR | `PASS_DOCUMENTATION_READY`，全部适用项为 PASS |
| 文档门禁 | `check_v122_documentation.py --release R06` 为 PASS，0 errors / 0 warnings / 0 open gaps |
| Release 产物结构 | `check_release_artifacts.py --release R06` 为 PASS |

## 依赖与历史事实

- R05 的 TASK-R05-001 至 TASK-R05-008、测试 APK、项目所有者真机验收和 Android 长期自动化门禁均已完成。
- `CR-0145` 已将 R05 登记为 R06 的顺序依赖，允许 R05 关闭后直接进入 R06。
- `TASK-R02-007` 继续保留为历史外部验收阻断事实，不得伪造为 DONE；它不阻断已经声明为 READY 的 R06 实施入口。
- `CURRENT_STATUS.yaml` 仍保留 `TASK-R03-007` 的历史 blocked 记录，而 CHANGELOG 已记录其后续完成；该元数据需要在版本治理过程中校准，但不改变 R06 的冻结业务范围。

## 实施顺序

1. `TASK-R06-002`：内容与首页数据迁移、唯一约束、状态历史、幂等和回滚验证。
2. `TASK-R06-003`：实现首页及后台内容管理冻结接口、权限、错误码、审计和 Outbox。
3. `TASK-R06-004`：按精确视觉合同实现 Android 首页/关于页和管理端内容列表、详情与字典页。
4. `TASK-R06-005`：执行主路径、拒绝、幂等、并发、超时和故障注入。
5. `TASK-R06-006` 至 `008`：观测与 Staging、Android 自动候选、桌面交付、真机验收、关闭与无状态交接。

## 不可降级规则

- 页面结构、字号、行高、间距、宽高、圆角、颜色、阴影、动效和状态必须来自冻结效果图、Design Token、页面规格和组件目录。
- Android 必须通过 `check_android_ui_foundation.py` 和 R06 起生效的 Android Actions 全链门禁。
- 测试 APK 必须注入 `https://api.orbexa.cc`，使用稳定测试签名、递增 versionCode，并完成仓库、桌面、服务器、公网四方 SHA-256 核对。
- 机器候选 `PASS` 且 `owner_test_allowed=true` 后才能交给项目所有者真机验收。

## 结论

R06 的页面、字段、状态、动作、API、配置、数据和测试输入无阻断性 TBD。允许关闭 TASK-R06-001，并按任务依赖进入 TASK-R06-002 数据迁移与领域不变量开发。
