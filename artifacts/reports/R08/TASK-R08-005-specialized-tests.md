# TASK-R08-005 专项测试与故障注入证据

## 权威范围

- `TST-PROJECT_001-HAPPY`
- `TST-PROJECT_001-REJECT`
- `TST-PROJECT_001-IDEMPOTENT`

三项测试继续使用 `catalogs/test_cases.csv` 的唯一测试 ID；`tests/r08` 仅保存可机检适配器，不建立平行测试清单。

## 新增故障覆盖

- 完成后的创建请求延迟重放返回首次加密快照，不重复创建项目、查询详情或写 Outbox。
- 8 路真实 PostgreSQL 并发争用同一幂等键时只有一个所有者，其他请求读取同一记录。
- 媒体存储超时在草稿额度、项目写入、Outbox 和完成快照之前失败。
- H5 域名配置提供方超时不产生分享记录、Outbox 或完成快照。
- 未实名、双向拉黑、冲突和离线恢复继续复用既有服务与 Android 回归。

## 冻结证据

待精确提交后在 `obx-test` 生成 Java 21、PostgreSQL 17 和固定 Android JDK 21 原始日志，并写入 `artifacts/validation/r08-test-evidence/evidence.json`。
