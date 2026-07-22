---
cr_id: CR-0220
status: APPROVED
requester_actor_id: codex-root-r08-004
approver_actor_id: codex-rule-reviewer
task_id: TASK-R08-004
session_id: SES-20260722T031604Z-105CF4C6
created_at: 2026-07-22T04:06:01Z
updated_at: 2026-07-22T04:08:02Z
---
# CR-0220 — 固化固定Android容器Gradle缓存挂载与SDK安装诊断经验

## 用户需求摘要

用户要求可复用开发方案及踩坑经验写入既有事实源，跨电脑跨AI生效

## 原规则

既有踩坑记录已要求使用固定服务器环境和固定Android镜像，但未写明Gradle命名卷的精确挂载方式，也未区分SDK平台自动安装与构建停滞。

## 新规则

固定Android容器验证必须复用hhy-r01-android-gradle-cache命名卷并挂载到/root/.gradle；禁止改用宿主/root/.gradle。短生命周期容器首次补装稳定API 36时，须以容器状态、CPU和增量日志判断进度，不得仅因短时无日志认定卡死、重建镜像或重复启动验证。

## 修改原因

R08受影响模块验证再次证明宿主/root/.gradle挂载无法复用固定命名卷，API 36在短生命周期容器中的确定性安装阶段容易被误判为卡死；必须更新既有PITFALLS与REUSABLE_PATTERNS，禁止新增并行规则文件

## 影响摘要

只更新既有PITFALLS与REUSABLE_PATTERNS，固化可跨电脑和AI复用的诊断路径；不修改产品、API、数据库、工具链镜像或候选门禁频率。

## 影响文件

- `docs/03-continuity/PITFALLS.md`
- `docs/03-continuity/REUSABLE_PATTERNS.md`

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

- `fixed Android image affected-module verification`

## 版本

- `R08`

## 迁移与兼容策略

纯文档经验固化；兼容现有固定镜像和命名卷，不重建镜像、不修改历史证据。

## 用户确认

用户明确要求可复用经验跨电脑跨AI生效，并要求相似规则只更新既有事实源

## 审批

- 审批人：`codex-rule-reviewer`
- 决定：`APPROVED`
- 时间：`2026-07-22T04:08:02Z`
- 说明：用户已明确要求把可行方案写入既有全局踩坑和经验复用事实源；本CR先查重后仅更新两份既有文档，不新增并行规则。

## 状态记录 · 2026-07-22T04:08:05Z

- Actor：`codex-root-r08-004`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T031604Z-105CF4C6`
- Note：开始更新既有PITFALLS与REUSABLE_PATTERNS，不新增规则文件。
