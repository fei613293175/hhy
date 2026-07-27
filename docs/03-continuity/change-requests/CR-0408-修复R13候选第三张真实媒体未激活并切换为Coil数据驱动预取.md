---
cr_id: CR-0408
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: codex-reviewer-r13-media-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-27T13:33:56Z
updated_at: 2026-07-27T13:34:24Z
---
# CR-0408 — 修复R13候选第三张真实媒体未激活并切换为Coil数据驱动预取

## 用户需求摘要

R13版本必须完成并交付桌面，候选失败由AI自行修复且不得原地打转

## 原规则

R13候选通过Compose滚动逐项激活三张媒体；页面仅在可见行组合时发起Coil请求，离屏第三项可持续停留loading

## 新规则

R13收藏与历史列表必须从正式API返回的state.items提取经secureActivityMediaUrl过滤并去重的真实媒体URL，使用项目既有Coil imageLoader.execute按与R13ActivityRow完全一致的确定像素尺寸预取；页面painter仍独立验证Success/Error，禁止把预取结果伪造成loaded、禁止降低三张真实媒体要求或使用固定sleep

## 修改原因

Attempt19已越过认证与导航，但三张真实媒体仅2张成功、1张持续loading；多轮滚动激活方案同指纹复发，必须切换为基于真实state.items的官方Coil确定尺寸预取

## 影响摘要

仅加固R13活动列表媒体加载确定性和静态回归；不改API、数据库、UI结构、正式数据来源、三张媒体及四张截图门槛

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

- `R13 Coil数据驱动预取静态回归与候选治理回归`
- `Android UI foundation与obx-test固定镜像模块编译测试`

## 版本

- `R13`

## 迁移与兼容策略

纯Android客户端Bug修复；沿用Coil 2.7现有依赖与默认缓存，旧接口和页面契约不变，失败仍由现有painter错误状态及候选门禁暴露

## 用户确认

项目所有者要求立即解决R13未交付并持续开发，且长期授权候选失败由AI自行修复

## 审批

- 审批人：`codex-reviewer-r13-media-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-27T13:34:24Z`
- 说明：同一2成功1加载指纹已跨多轮滚动激活复发；数据驱动预取复用既有官方Coil、真实URL与页面同尺寸缓存键，不降低最终painter和候选断言，影响集中且可由模块测试验证

## 状态记录 · 2026-07-27T13:51:37Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：精确实现提交4d6fd4d7已形成并完成本地MODULE与远端固定镜像验证，进入证据落盘

## 状态记录 · 2026-07-27T13:51:42Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：Coil数据驱动预取已实现；90项候选治理、Android UI基础、YAML、diff与严格连续性PASS；精确Commit 4d6fd4d7在obx-test固定镜像完成feature:activity:testDebugUnitTest和app:compileDebugAndroidTestKotlin，223任务BUILD SUCCESSFUL in 1m48s，本机/远端源码SHA-256均为154C23A1C52FF22BF19D93CCF8134DFCED4C4901F54B202B44813DD9A65AC690；不直接授权Attempt20
