---
cr_id: CR-0010
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: codex-engineering-audit
task_id: TASK-P00-008
session_id: SES-20260717T054147Z-7AE51A86
created_at: 2026-07-17T05:44:40Z
updated_at: 2026-07-17T05:49:35Z
---
# CR-0010 — P00封板清单以OpenAPI operationId机器化绑定

## 用户需求摘要

P00版本完成后先暂停推进；项目所有者已确认真机测试正常并要求尽快完成P00

## 原规则

P00 RELEASE_MANIFEST.contracts.client_api仅记录3条HTTP方法与路径，status为READY且没有release_commit/release_tag

## 新规则

P00终态清单保留原路径并精确绑定3个现有OpenAPI operationId，status为DONE且绑定最终源码Commit与标签

## 修改原因

终态关闭门禁要求RELEASE_MANIFEST精确声明3个已实现operationId，现有HTTP路径列表无法通过机器校验

## 影响摘要

仅变更P00发布元数据表达与验收状态，不修改OpenAPI、运行时代码、数据库、Android APK或线上服务

## 影响文件

- `releases/P00/RELEASE_MANIFEST.yaml`
- `releases/P00/ACCEPTANCE_MATRIX.csv`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- `publicGetPlatformStatus`
- `appReleasePostAppVersionCheck`
- `appReleaseGetAppVersionCheck`

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python scripts/check_release_artifacts.py --release P00 --close-gate`
- `python scripts/check_v123_documentation.py --strict --release P00`

## 版本

- `P00`

## 迁移与兼容策略

无需运行时迁移；保留原paths供人工阅读，新增operation_ids供机器门禁验证，现有消费者行为不变

## 用户确认

P00版本完成后先暂停推进；项目所有者已确认真机测试正常并要求尽快完成P00

## 审批

- 审批人：`codex-engineering-audit`
- 决定：`APPROVED`
- 时间：`2026-07-17T05:49:35Z`
- 说明：确认仅收口P00 RELEASE_MANIFEST与ACCEPTANCE_MATRIX终态元数据：保留3条HTTP paths并绑定现有3个OpenAPI operationId，6项证据均为仓库内实际文件，59项矩阵PASS，release commit、tag与APK基线一致；禁止修改运行时代码或接口
