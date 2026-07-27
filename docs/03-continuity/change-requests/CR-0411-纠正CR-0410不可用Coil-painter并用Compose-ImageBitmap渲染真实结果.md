---
cr_id: CR-0411
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-reviewer-r13-imagebitmap-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T14:28:19Z
updated_at: 2026-07-27T14:28:51Z
---
# CR-0411 — 纠正CR-0410不可用Coil painter并用Compose ImageBitmap渲染真实结果

## 用户需求摘要

R13必须完成桌面交付，候选失败和编译问题由AI自行修复持续推进

## 原规则

CR-0410要求SuccessResult通过rememberDrawablePainter渲染，但项目当前Coil 2.7依赖不导出该API，固定镜像compileDebugKotlin失败

## 新规则

保留列表级安全URL去重、并发ImageLoader.execute和真实ImageResult唯一事实；SuccessResult的Android Drawable必须复制后用标准Bitmap/Canvas转换为Compose ImageBitmap并由Image(bitmap=...)直接渲染，ErrorResult标记error，未返回保持loading。禁止新增依赖、回退独立行网络painter、预取布尔值或占位图伪成功

## 修改原因

CR-0410提交f523abce在obx-test固定镜像精确失败于rememberDrawablePainter未导出；不得新增未验证依赖，必须保留列表级真实ImageResult并改用Android Drawable到现有Compose ImageBitmap的直接渲染

## 影响摘要

只纠正CR-0410的不可编译渲染投影；不改变列表级加载所有权、UI布局、API、数据库、媒体来源、三张真实媒体或四截图门槛

## 影响文件

- `apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt`
- `tests/test_r13_candidate.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `SCR-FAV-001`
- `SCR-HIS-001`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `R13列表级ImageResult与Drawable到ImageBitmap渲染静态回归`
- `obx-test feature:activity:testDebugUnitTest与app:compileDebugAndroidTestKotlin`

## 版本

- `R13`

## 迁移与兼容策略

使用Android标准Bitmap/Canvas及现有Compose asImageBitmap，无新增依赖；BitmapDrawable复用原始Bitmap，其他Drawable按固有尺寸绘制到ARGB_8888，真实Coil SuccessResult仍是loaded唯一来源

## 用户确认

项目所有者要求AI自行解决编译与候选问题并持续完成R13桌面交付

## 审批

- 审批人：`codex-reviewer-r13-imagebitmap-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T14:28:51Z`
- 说明：固定镜像已证明rememberDrawablePainter不可用；Android标准Bitmap/Canvas到现有Compose ImageBitmap无需新增依赖，仍直接渲染Coil实际SuccessResult，范围与候选门槛不变

## 状态记录 · 2026-07-27T14:48:03Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：精确Commit 78cf6c91已推送并进入obx-test固定镜像验证

## 状态记录 · 2026-07-27T14:48:09Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：obx-test固定镜像feature activity单测与app AndroidTest编译223任务BUILD SUCCESSFUL in 2m49s
