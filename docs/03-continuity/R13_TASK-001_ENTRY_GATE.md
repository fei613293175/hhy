# R13 TASK-001 开发入口门禁

## 结论

R13 开发入口为 `PASS`。两项本版需求、五个 R13 归属 operationId、一个 Story 复用共享 operationId、二十五张相关表、四个 Android 页面和三个 Story 均有冻结事实源；R07 与 R12 已机器完成。该结论只允许进入 R13 实施，不代表运行时、最终候选、真机验收或生产激活完成。

## 已消除的入口漂移

- 新增 `PARALLEL_EXECUTION_PLAN.yaml`，保持单一权威 Session，并限制完整模拟器只在 `TASK-R13-007` 最终候选执行。
- Release Manifest 从 HTTP 路径统一为六个真实 OpenAPI operationId；失效反馈明确为 Story 复用共享接口，不改写中央 R13 归属接口计数。
- `SCR-FAV-001` 与 `SCR-HIS-001` 分别精确绑定 B08/P07、B08/P08。
- `SHEET-SHARE-001` 与 `SHEET-CONTENT-INVALID-001` 使用批准补充规格，复用相关效果图结构但不带入其虚构或异业务字段。
- 页面目录、视觉绑定目录、生成页面文档、Stories 和 Manifest 使用同一组视觉引用。

## 执行边界

- `TASK-R13-002` 先验证现有收藏与浏览数据结构并仅在真实缺口存在时新增迁移。
- `TASK-R13-003` 实现或复核六个 operationId 的领域服务、权限、幂等、审计与 Outbox。
- `TASK-R13-004` 按四页视觉规格实现 Android，并把实际页面登记为 `IN_REVIEW`。
- `TASK-R13-005/006` 完成专项测试与 Staging；`TASK-R13-007` 才运行完整 Android 候选和 AI 截图验收。
- 项目所有者真机反馈异步，不阻断后续开发；正式验收和生产激活仍需真实门禁。
