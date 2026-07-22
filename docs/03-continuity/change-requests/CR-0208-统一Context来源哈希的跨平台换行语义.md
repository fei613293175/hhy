---
cr_id: CR-0208
status: APPROVED
requester_actor_id: codex-root-r08-001
approver_actor_id: codex-reviewer-portable-hash
task_id: TASK-R08-001
session_id: SES-20260722T002444Z-8BEC3CA6
created_at: 2026-07-22T00:38:16Z
updated_at: 2026-07-22T00:38:46Z
---
# CR-0208 — 统一Context来源哈希的跨平台换行语义

## 用户需求摘要

彻底解决可重复环境问题并写入踩坑和经验复用记录，确保换电脑换AI无缝接续

## 原规则

Context Pack生成器用portable_source_record规范化文本换行后记录来源SHA，但check_v123_continuity.py用原始文件字节sha256复核，Windows CRLF与Linux LF会对同一Git内容产生不同结果。

## 新规则

严格连续性校验必须使用与Context Pack生成器相同的portable_source_record复核每个来源的规范化SHA和字节数；LF与CRLF工作树必须得到一致结论，真实内容变化仍必须阻断。

## 修改原因

R08严格连续性校验在Windows把Context Pack规范化LF来源哈希与CRLF原始字节哈希比较，导致内容相同的ACCEPTANCE_MATRIX被误报CONTEXT_SOURCE_STALE

## 影响摘要

修复跨Windows/Linux Context来源哈希假过期，增加LF/CRLF正向兼容和真实内容变化负向回归，并登记Problem与全局踩坑规则。

## 影响文件

- `scripts/check_v123_continuity.py`
- `tests/test_context_pack_parallel_policy.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`
- `artifacts/validation/project-doctor-v1.2.3.json`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `Context Pack来源哈希统一使用跨平台规范化文本记录`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_context_pack_parallel_policy`
- `python scripts/check_v123_continuity.py --strict`

## 版本

- `R08`

## 迁移与兼容策略

纯校验兼容修复；不修改Context Manifest格式，既有规范化SHA继续有效，真实字节内容变化仍由规范化记录检测。

## 用户确认

项目所有者明确要求环境问题彻底解决、写入踩坑和经验复用并支持跨电脑换AI。

## 审批

- 审批人：`codex-reviewer-portable-hash`
- 决定：`APPROVED`
- 时间：`2026-07-22T00:38:46Z`
- 说明：修复只统一生成与校验算法，必须同时比较规范化SHA和字节数；真实内容变化负向回归继续阻断。

## 状态记录 · 2026-07-22T00:38:50Z

- Actor：`codex-root-r08-001`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T002444Z-8BEC3CA6`
- Note：开始统一Context来源哈希算法并增加跨换行正反向回归

## 状态记录 · 2026-07-22T00:42:46Z

- Actor：`codex-root-r08-001`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T002444Z-8BEC3CA6`
- Note：Context来源哈希已统一为跨平台规范化语义并完成正反向回归

## 状态记录 · 2026-07-22T00:42:50Z

- Actor：`codex-root-r08-001`
- Status：`CLOSED`
- Session：`SES-20260722T002444Z-8BEC3CA6`
- Note：10项携带回归与V1.2.3严格门禁均PASS
