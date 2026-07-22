# TASK-R08-004 客户端、H5 与后台实现证据

## 当前范围

- Android：`SCR-LIST-001`、`SCR-DETAIL-001`、`SCR-PUB-002`
- H5：复用冻结 `H5-004` 项目分享页与 `publicGetShareContentsById`
- Admin：复用 `ADM-CONTENT-001`、`ADM-CONTENT-002` 统一内容运营页面
- 变更请求：`CR-0217`、`CR-0218`、`CR-0219`

## 已实现事实

- Android 使用 `ContractR08Api` 一对一绑定项目列表、详情、创建、编辑、收藏、分享、直接会话和联系方式访问；页面 DTO 不在 Compose 页面重复定义。
- 列表按项目类型读取、ID 去重和游标追加；详情写操作使用稳定幂等键，编辑与收藏绑定最近服务端 `version`。
- 联系方式响应要求 `no-store`，明文只在 `FLAG_SECURE` 底部面板内存短时展示，关闭即清除。
- 发布/编辑页只包含冻结字段，项目图片使用现有真实媒体上传闭环，上传完成后才把媒体 ID 写入项目请求。
- H5 分享使用生成 `ClientContract.operations['publicGetShareContentsById']` 类型，公开页不展示联系方式、内部状态或来源归因。
- 后台不创建第二套项目运营页面，现有内容列表/详情以 `contentType=PROJECT`、资源版本和既有权限动作处理项目。

## 验证证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Android 首轮 network/project/app MODULE | PASS | 精确提交 `298544b5688ff43b87a85992a572a23eada720f5`；固定镜像 `hhy-android-toolchain:r01-46fb273`；日志 `/tmp/hhy-r08-004-android-298544b5.log`；SHA-256 `1ff3796419eb1faf36ac711ac34bb10875191b552e8ca4f409e8e7c499c22b5f`；88 tasks，BUILD SUCCESSFUL |
| H5 单元与页面状态 | PASS | `pnpm --filter @hhy/h5 test`：6 files / 27 tests |
| H5 类型检查与生产构建 | PASS | `pnpm --filter @hhy/h5 build`，Vue TSC 与 Vite BUILD PASS |
| 后台项目复用回归 | PASS | `pnpm --filter @hhy/admin-web test`：16 files / 88 tests |
| R08 视觉合同目录 | PASS | `python scripts/check_ui_visual_acceptance.py --release R08 --catalog-only`：3 pages |
| 视觉门禁单测 | PASS | `python -m unittest tests.test_ui_visual_acceptance`：8 tests |

## 候选阶段保留项

- 本任务仍需在最终候选阶段采集三张 R08 Android 页面真实截图，由 AI 按精确视觉合同判断后更新 `ui_visual_acceptance.csv`。
- R02-R07 历史页面视觉回补仍是 R08 最终候选关闭前置条件；不得以本报告的代码/单测结果替代截图和肉眼结论。
- 完整 lint、候选 APK、模拟器旅程和桌面交付只在 R08 最终候选阶段执行，不在本次普通实现提交重复触发。
