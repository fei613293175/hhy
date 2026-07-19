# R05 TASK-002 PostgreSQL 17 数据门禁证据

- 任务：`TASK-R05-002`
- 故事：`STORY-R05-008`
- 验证提交：`fc002aa2ce8103a12aa2af12e396238690ae5b63`
- 验证日期：`2026-07-19`
- 环境：`obx-test` 上无宿主端口的一次性 `postgres:17.10-alpine` 容器
- 容器前缀：`hhy-r05-`
- 清理：脚本 `trap cleanup EXIT`，验证结束后容器已删除

## 执行范围

1. 空库按文件顺序执行 `V001` 至 `V023`。
2. 运行 `database/tests/r05_identity_invariants.sql`，验证成功数据可写入、错误数据被拒绝、人工复核与敏感访问记录不可修改。
3. 执行 `U023__r05_identity_invariants.sql`，核对 R05 约束、索引、触发器和新增字段全部清除。
4. 重新执行 `V023__r05_identity_invariants.sql`，再次运行全部身份不变量测试。

## 真实执行结果

```text
R05_EMPTY_DATABASE_TO_V023 PASS tables=200
R05_IDENTITY_INVARIANTS PASS
R05_U023_ROLLBACK PASS
R05_V023_REAPPLY PASS constraints=30 indexes=12 trigger=1 columns=28
R05_DISPOSABLE_POSTGRES_CONTAINER PASS
```

## 发现并关闭的问题

首次真实执行发现 `CHECK` 的 PostgreSQL 三值逻辑会放行 `status=NULL` 且存在完成时间的第三方记录。修复为新记录必须明确提供状态，并严格绑定处理中/终态与完成时间；防复发规则登记在 `PROB-0042`。第二次执行确认不可变触发器按 SQLSTATE `55000` 正确拒绝修改，测试已按真实错误码断言。最终完整链路通过。

## 结论

`TASK-R05-002` 的迁移、唯一约束、状态历史、幂等、私有媒体绑定、人工复核不可变记录、敏感访问审计、回滚和重放门禁全部通过，可进入 R05 后端身份会话实现。
