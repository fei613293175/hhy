---
cr_id: CR-0084
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: continuity-regression-review
task_id: TASK-R04-001
session_id: SES-20260719T083704Z-6E4CE28F
created_at: 2026-07-19T08:37:59Z
updated_at: 2026-07-19T08:38:01Z
---
# CR-0084 — 跨版本关闭自动回填last_green_commit并增加回归门禁

## 用户需求摘要

项目所有者要求R03通过后立即推进，并要求持续优化开发效率、避免简单问题重复耗时

## 原规则

跨版本close仅把code_commit写入Session、事件和日志，CURRENT_STATUS.last_green_commit保留历史版本值

## 新规则

close在更新CURRENT_STATUS终态时同步写入经调用方确认的code_commit；跨版本回归必须断言last_green_commit与关闭实现Commit一致

## 修改原因

continuity close已接收code_commit但未写入CURRENT_STATUS.last_green_commit，导致每个版本关闭后需人工修正且发布关闭门禁失败

## 影响摘要

修复连续性治理脚本和隔离回归，并将R03当前状态纠正为已验证APK源码Commit；不改变任何业务页面、API、数据库或用户交互

## 影响文件

- `scripts/continuity.py`
- `scripts/continuity_lib.py`
- `tests/test_continuity_cross_release_close.py`
- `CURRENT_STATUS.yaml`

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

- `python -m unittest tests.test_continuity_cross_release_close`
- `python scripts/check_v123_continuity.py --strict`
- `python scripts/check_release_artifacts.py --release R03 --close-gate`

## 版本

- `R03`
- `R04`

## 迁移与兼容策略

仅更新运行时连续性元数据；旧仓库可在下一次版本关闭时自动获得正确值，无数据库或客户端迁移

## 用户确认

项目所有者已确认R03真机通过并要求立即推进，同时要求持续优化开发流程避免重复人工收尾

## 审批

- 审批人：`continuity-regression-review`
- 决定：`APPROVED`
- 时间：`2026-07-19T08:38:01Z`
- 说明：独立确认：code_commit已由close前置验证和用户验收绑定，写回last_green_commit与Release/APK一致，且增加隔离跨版本回归；无业务范围变化

## 状态记录 · 2026-07-19T08:38:04Z

- Actor：`codex-root`
- Status：`IMPLEMENTING`
- Session：`SES-20260719T083704Z-6E4CE28F`
- Note：实施自动回填与跨版本回归测试

## 状态记录 · 2026-07-19T08:50:27Z

- Actor：`codex-root`
- Status：`IMPLEMENTED`
- Session：`SES-20260719T083704Z-6E4CE28F`
- Note：跨版本close自动回填last_green_commit、R03状态纠正和隔离回归已完成并全绿
