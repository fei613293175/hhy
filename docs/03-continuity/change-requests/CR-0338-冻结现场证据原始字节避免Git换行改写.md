---
cr_id: CR-0338
status: APPROVED
requester_actor_id: codex-root-r12-observability-20260726
approver_actor_id: codex-reviewer-r12-evidence
task_id: TASK-R12-006
session_id: SES-20260725T180922Z-D7231210
created_at: 2026-07-25T19:11:37Z
updated_at: 2026-07-25T19:12:01Z
---
# CR-0338 — 冻结现场证据原始字节避免Git换行改写

## 用户需求摘要

按仓库事实源连续开发并保证交付证据可验证

## 原规则

现场证据必须保留服务器原始字节并可由SHA256SUMS复核

## 新规则

artifacts/validation下的现场证据按原始字节入库，禁止Git换行过滤；提交对象必须逐文件对照SHA256SUMS

## 修改原因

未推送Commit对象校验发现trace-headers.txt被Git文本规范化，提交字节哈希与obx-test原始证据不一致

## 影响摘要

为现场证据目录增加Git -text规则，重新暂存原始字节并登记PROB-0112，不修改产品代码或运行时

## 影响文件

- `.gitattributes`
- `artifacts/validation/r12-task006-staging`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`

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

- `Git index blob SHA256 versus artifacts/validation/r12-task006-staging/SHA256SUMS`

## 版本

- `R12`

## 迁移与兼容策略

仅证据存储格式治理；既有产品、数据库、API和现场运行结果不变

## 用户确认

项目所有者要求证据和规则跨电脑跨AI可直接复用

## 审批

- 审批人：`codex-reviewer-r12-evidence`
- 决定：`APPROVED`
- 时间：`2026-07-25T19:12:01Z`
- 说明：原始现场证据必须字节级可追溯，目录级-text规则和提交对象复核是最小修复

## 状态记录 · 2026-07-25T19:13:05Z

- Actor：`codex-root-r12-observability-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T180922Z-D7231210`
- Note：已加入artifacts/validation原始字节规则，Git索引25文件与现场SHA256SUMS逐项一致
