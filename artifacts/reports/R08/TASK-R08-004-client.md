# TASK-R08-004 客户端、H5 与后台实现证据

## 当前范围

- Android：`SCR-LIST-001`、`SCR-DETAIL-001`、`SCR-PUB-002`
- H5：复用冻结 `H5-004` 项目分享页与 `publicGetShareContentsById`
- Admin：复用 `ADM-CONTENT-001`、`ADM-CONTENT-002` 统一内容运营页面
- 变更请求：`CR-0217`、`CR-0218`、`CR-0219`、`CR-0221`、`CR-0222`、`CR-0223`、`CR-0224`

## 已实现事实

- Android 使用 `ContractR08Api` 一对一绑定项目列表、详情、创建、编辑、收藏、分享、直接会话和联系方式访问；页面 DTO 不在 Compose 页面重复定义。
- 列表按项目类型读取、ID 去重和游标追加；详情写操作使用稳定幂等键，编辑与收藏绑定最近服务端 `version`。
- 联系方式响应要求 `no-store`，明文只在 `FLAG_SECURE` 底部面板内存短时展示，关闭即清除。
- 发布/编辑页只包含冻结字段，项目图片使用现有真实媒体上传闭环，上传完成后才把媒体 ID 写入项目请求。
- H5 分享使用生成 `ClientContract.operations['publicGetShareContentsById']` 类型，公开页不展示联系方式、内部状态或来源归因。
- 后台不创建第二套项目运营页面，现有内容列表/详情以 `contentType=PROJECT`、资源版本和既有权限动作处理项目。
- R07 历史视觉回补已按 `CR-0221` 启动：搜索组合区、真实关键词云、结果卡、发布者资料卡与公开内容卡已按 B02/B03 视觉语言重构，不增加服务端未返回的图片、统计、关注或营销能力。
- R06 历史视觉回补按 `CR-0222` 执行：首页只用已实现搜索/项目动作与服务端模块形成多层结构，关于页只用真实版本策略与协议形成品牌摘要、状态和内容卡；三个后台内容页面经 AI 复核符合标准管理模板并复用既有真实浏览器截图。
- R02—R05 历史视觉目录按 `CR-0223` 补齐为 29 个页面：R02 启动与会话恢复、R04 媒体上传弹层完成效果图级代码回补；R02 认证与 R05 实名 Android 页面完成源码/规格审计并进入最终候选截图清单。
- R02 用户后台、R03 配置后台、R05 实名后台及 R02/R05 H5 页面使用 `CR-0224` 约束的本机 loopback fixture 代理完成真实浏览器渲染；fixture 不进入生产构建或运行时。
- 后台历史复核同时修正两项真实问题：身份/会员状态不再误用账号状态字典；复用同一 Vue 组件切换供应商路由时会重新加载对应配置详情。

## 验证证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Android 首轮 network/project/app MODULE | PASS | 精确提交 `298544b5688ff43b87a85992a572a23eada720f5`；固定镜像 `hhy-android-toolchain:r01-46fb273`；日志 `/tmp/hhy-r08-004-android-298544b5.log`；SHA-256 `1ff3796419eb1faf36ac711ac34bb10875191b552e8ca4f409e8e7c499c22b5f`；88 tasks，BUILD SUCCESSFUL |
| Android 最终 media/network/project/app MODULE | PASS | 精确提交 `9946433742eb9f7ba7ade9909ad11ec0774e8afb`；固定镜像 `hhy-android-toolchain:r01-46fb273`；命名缓存卷 `hhy-r01-android-gradle-cache:/root/.gradle`；日志 `/tmp/hhy-r08-004-android-99464337.log`；SHA-256 `86c78eb2ec08c1a271dffd29f08530ca6e12b8eff01c37a0a3596c935b84b257`；103 tasks（75 from cache），BUILD SUCCESSFUL in 8m 8s |
| R02/R04 历史视觉 Android MODULE | PASS | 精确提交 `35484da74a8c0483bdb6a236b8eca23bfe606cc9`；Android 源归档 SHA-256 `1ed51413020365143eacf521f11bd723935baeb9aab06eed53db7f0e61d4844e`；固定镜像与命名缓存卷；`:feature:startup:test :feature:media:test :app:testDebugUnitTest :app:compileDebugKotlin`；日志 `/tmp/hhy-r08-historical-android-35484da7.log`；SHA-256 `9a0ecb89c47ce151f1dd9b2be0317f900a490366bde101147553b6cd5da93244`；172 tasks，BUILD SUCCESSFUL in 3m 11s |
| H5 单元与页面状态 | PASS | `pnpm --filter @hhy/h5 test`：6 files / 27 tests |
| H5 类型检查与生产构建 | PASS | `pnpm --filter @hhy/h5 build`，Vue TSC 与 Vite BUILD PASS |
| 后台项目复用回归 | PASS | `pnpm --filter @hhy/admin-web test`：16 files / 88 tests |
| 后台历史页面真实浏览器复核 | PASS | `artifacts/validation/r08-historical-ui/admin`：12 张 1440x1100 证据；ADM-USER-001/002、ADM-CONFIG-002—008、ADM-ID-001/002 均由 AI 按六项效果图级标准判定 PASS |
| H5 历史页面真实浏览器复核 | PASS | `H5-013-browser-360x800.png` 与既有 `H5-012-browser-360x800.png`；品牌、表单/状态、操作层级和真实业务映射均 PASS |
| 后台历史回补单测与构建 | PASS | `pnpm --filter @hhy/admin-web test`：16 files / 88 tests；`pnpm --filter @hhy/admin-web build`：Vue TSC 与 Vite BUILD PASS |
| H5 历史回补单测与构建 | PASS | `pnpm --filter @hhy/h5 test`：6 files / 27 tests；`pnpm --filter @hhy/h5 build`：Vue TSC 与 Vite BUILD PASS |
| R02—R05 历史视觉合同目录 | PASS | `check_ui_visual_acceptance.py --catalog-only`：R02 14 pages、R03 7 pages、R04 1 page、R05 7 pages |
| R08 视觉合同目录 | PASS | `python scripts/check_ui_visual_acceptance.py --release R08 --catalog-only`：3 pages |
| 视觉门禁单测 | PASS | `python -m unittest tests.test_ui_visual_acceptance`：8 tests |
| R07 历史视觉回补 Android MODULE | PASS | 精确提交 `d40f0b091b4af3224b56663b8e8d19e8c64129a2`；固定镜像与命名缓存卷；`:feature:discovery:test :app:compileDebugKotlin`；日志 `/tmp/hhy-r07-visual-d40f0b09.log`；SHA-256 `c101b98ebe18511ff31c5e7321138b8c351ba0eef48ef5cc116c5a14ed9e5230`；91 tasks（71 from cache），BUILD SUCCESSFUL in 2m 51s |
| R06 历史视觉回补 Android MODULE | PASS | 精确提交 `a217dd276df6bcbd35a019d0e9974b75d5201bce`；固定镜像与命名缓存卷；`:feature:shell:test :app:testDebugUnitTest :app:compileDebugKotlin`；日志 `/tmp/hhy-r06-visual-a217dd27.log`；SHA-256 `932dd419e613fd0bc3be95eaedc5eab35ccb6df64007f76787b038f586bda2d0`；164 tasks（104 from cache），BUILD SUCCESSFUL in 4m |

## 候选阶段保留项

- 本任务仍需在最终候选阶段采集三张 R08 Android 页面真实截图，由 AI 按精确视觉合同判断后更新 `ui_visual_acceptance.csv`。
- R02、R04—R08 Android 历史页面的最终候选截图与 AI 肉眼结论仍是 R08 关闭前置条件；不得以本报告的代码/单测结果替代模拟器证据。
- 完整 lint、候选 APK、模拟器旅程和桌面交付只在 R08 最终候选阶段执行，不在本次普通实现提交重复触发。
