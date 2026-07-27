---
cr_id: CR-0410
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-reviewer-r13-media-result-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T14:18:28Z
updated_at: 2026-07-27T14:19:04Z
---
# CR-0410 — 消除R13每行独立painter永久loading并集中复用Coil真实结果

## 用户需求摘要

R13必须完成桌面交付，候选失败由AI依据证据切换方案持续修复

## 原规则

CR-0408从state.items按同尺寸预取真实URL，但每个Lazy行仍各自创建rememberAsyncImagePainter并以其独立状态判定loaded/error；Attempt20证明一个行painter仍可永久loading

## 新规则

R13列表必须对安全去重URL并发执行官方Coil ImageLoader，保存每个URL的真实ImageResult；所有引用同一URL的行必须复用该结果，SuccessResult直接通过rememberDrawablePainter渲染已解码真实Drawable并标记loaded，ErrorResult标记error，未返回保持loading。禁止使用预取布尔值、占位图、回调镜像或独立行网络painter伪造成功，三张真实媒体和四张截图门槛不变

## 修改原因

Attempt20 Run 30272884567/Job90000972687在列表级预取后仍复现expected=3 observedSuccess=2 visibleSuccess=2 errors=0 loading=1；三条内容共用同一READY媒体对象，证明预取缓存不能消除每行rememberAsyncImagePainter独立生命周期竞态，需让列表级Coil ImageResult成为唯一真实加载事实并直接渲染成功Drawable

## 影响摘要

只重构R13收藏/历史媒体加载所有权，从每行独立异步请求改为列表级真实Coil结果复用；不改UI布局、API、数据库、媒体来源、数量或候选门槛

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

- `R13列表级Coil ImageResult唯一事实与真实Drawable渲染静态回归`
- `Android UI foundation及obx-test activity单测与AndroidTest编译`

## 版本

- `R13`

## 迁移与兼容策略

纯Android客户端Bug修复；同一URL只发起一次真实请求并复用成功Drawable，多个不同URL并发独立完成，失败继续暴露为error；既有页面、缓存、正式数据和候选语义兼容

## 用户确认

项目所有者要求候选失败由AI自行修复并持续完成R13桌面交付，不要求逐次候选审批

## 审批

- 审批人：`codex-reviewer-r13-media-result-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T14:19:04Z`
- 说明：Attempt20以同一2/0/1指纹否定缓存预取；三条夹具共享一个READY媒体对象，列表级真实ImageResult复用可消除重复行painter生命周期竞态，成功仍绑定Coil实际解码Drawable且不降低任何候选门槛
