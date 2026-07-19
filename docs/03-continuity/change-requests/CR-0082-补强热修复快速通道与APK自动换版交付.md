---
cr_id: CR-0082
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-delegated
task_id: TASK-R03-007
session_id: SES-20260718T200607Z-3569D212
created_at: 2026-07-19T07:31:10Z
updated_at: 2026-07-19T07:31:39Z
---
# CR-0082 — 补强热修复快速通道与APK自动换版交付

## 用户需求摘要

项目所有者明确要求立即按既定方案继续优化开发速度和Bug修复效率

## 原规则

现有统一工作流能分级、整体指纹续跑和APK路由预检，但换版交付仍需人工移动当前清单，任一文件变化会让全部检查缓存失效，Git Hooks依赖外部PATH，跨层检查只能串行

## 新规则

热修复入口必须提供10分钟定位、15分钟实现、10分钟交付时间盒；缓存按单项输入指纹复用；只有工作区干净且HEAD等于冻结Commit时才允许最多2路模块检查并行；统一Git入口自动注入Python和Git运行时；APK prepare可显式replace-existing并在新产物完整验证后原子归档旧清单

## 修改原因

消除本次注册提示修复中暴露的PATH、重复测试、串行等待、旧交付手工归档和参数易错问题

## 影响摘要

在既有轻重分级脚本上增量加入hotfix、便携Git、逐检查缓存、冻结Commit并行和APK事务式换版，不改变业务、API、数据库及正式发布门禁

## 影响文件

- `config/development-workflow.yaml`
- `config/test-impact-map.yaml`
- `scripts/hhy_workflow.py`
- `scripts/deliver_android_test_apk.py`
- `tests/test_hhy_workflow.py`
- `tests/test_android_apk_delivery.py`
- `docs/09-development/统一开发与交付效率规范.md`
- `CHANGELOG.md`

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

- `python -m unittest tests.test_hhy_workflow tests.test_android_apk_delivery`
- `python scripts/hhy_workflow.py hotfix --changed-file scripts/hhy_workflow.py`
- `python scripts/deliver_android_test_apk.py prepare --help`

## 版本

- `R03`

## 迁移与兼容策略

旧plan/run及APK prepare默认行为保持不变；并行、hotfix和replace-existing均为显式增量选项，旧交付只有在新APK四方验证通过后才归档

## 用户确认

立即按你的方案优化

## 审批

- 审批人：`project-owner-delegated`
- 决定：`APPROVED`
- 时间：`2026-07-19T07:31:39Z`
- 说明：项目所有者明确要求立即按已提出的针对性方案优化，并要求避免把简单任务复杂化

## 状态记录 · 2026-07-19T07:45:59Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260718T200607Z-3569D212`
- Note：开始补强现有热修复快速通道，不新建第二套开发体系

## 状态记录 · 2026-07-19T07:46:00Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260718T200607Z-3569D212`
- Note：热修复35分钟时间盒、逐检查输入指纹缓存、冻结Commit最多2路并行、便携Git及APK事务式replace-existing已实现；针对性41项、完整Python 135项和统一工作流45项均PASS，旧冗余路径264秒缩短为14.3秒且二次复用0秒
