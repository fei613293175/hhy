---
cr_id: CR-0348
status: APPROVED
requester_actor_id: codex-root-r12-candidate-20260726
approver_actor_id: project-owner
task_id: TASK-R12-007
session_id: SES-20260725T192048Z-668BD05D
created_at: 2026-07-26T03:48:43Z
updated_at: 2026-07-26T03:49:19Z
---
# CR-0348 — 修复R12普通CI历史候选断言与Wrapper执行位漂移

## 用户需求摘要

全局检查开发文档、硬性门禁规则、开发进度、优点复用和踩坑记录，发现漂移立即加固。

## 原规则

历史APK具有不可变候选报告、构建证据和版本身份；当前versionCode单调递增。跨电脑Linux CI所需Wrapper必须直接可执行，普通提交门禁不得消耗最终Android候选。

## 新规则

历史Release回归只从该版归档候选报告和构建证据验证不可变身份，当前构建与发布策略只验证一致且严格大于历史versionCode；Android Gradle Wrapper与后端Maven Wrapper必须在Git索引保持100755并由既有严格Doctor机检。

## 修改原因

GitHub普通CI暴露R11历史候选测试锁死当前R11身份，以及services/backend/mvnw Git索引模式100644导致Linux Permission denied。

## 影响摘要

恢复普通tooling和backend CI可信度，阻断后续升级再次触发旧候选假失败并保证Linux干净检出可执行Wrapper；不改变产品功能、页面、API、数据库、候选次数或APK状态。

## 影响文件

- `tests/test_r11_candidate.py`
- `services/backend/mvnw`
- `scripts/check_v123_continuity.py`
- `tests/test_context_pack_parallel_policy.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/REUSABLE_PATTERNS.md`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `services/backend/mvnw Git mode 100755`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest -v tests.test_r11_candidate tests.test_context_pack_parallel_policy`
- `git ls-files -s -- apps/android/gradlew services/backend/mvnw`
- `python scripts/check_v123_continuity.py --strict --release R12`
- `GitHub普通CI tooling与backend Job PASS且不触发Android候选`

## 版本

- `R12`

## 迁移与兼容策略

纯测试和Git文件模式修复；历史R11证据不重写，R12候选请求不变，不启动或复用Android候选。

## 用户确认

项目所有者本轮明确要求发现开发漂移后立即加固相关规则。

## 审批

- 审批人：`project-owner`
- 决定：`APPROVED`
- 时间：`2026-07-26T03:49:19Z`
- 说明：原位修复既有APK追溯规则和严格Doctor，不新增同义硬规则，不扩大候选权限。

## 状态记录 · 2026-07-26T03:49:27Z

- Actor：`codex-root-r12-candidate-20260726`
- Status：`IMPLEMENTING`
- Session：`SES-20260725T192048Z-668BD05D`
- Note：已修复R11历史候选断言并恢复Maven Wrapper执行位，正在运行专项和严格回归。
