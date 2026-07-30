# R04 TASK-003 V022 媒体上传生命周期数据库证据

- 执行日期：2026-07-19
- 数据库：一次性 `postgres:17.10-alpine` 容器，未发布主机端口
- 容器：`hhy-r04-dbtest-v022`（测试结束已删除）
- 临时目录：`/tmp/hhy-r04-dbtest-v022`（测试结束已删除）
- 影响范围：仅隔离容器和临时只读测试副本；现有服务器容器、数据库和 Staging 未修改

## 结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| 空库顺序升级 | PASS | V001 至 V022 顺序应用，`hhy` 基础表数量为 200 |
| 上传生命周期正反不变量 | PASS | 完整上传意图通过；短 SHA、缺失完成事实、无删除时间戳均被拒绝 |
| U022 + U021 回滚 | PASS | R04 约束与索引剩余计数 `0|0` |
| V021 + V022 重放 | PASS | 回滚后迁移重放并再次执行两组 R04 不变量 |
| 静态镜像及回滚测试 | PASS | 7 tests，0 failures；运行时 Flyway 与源迁移一致 |

## 可复现命令

```bash
HHY_R04_DB_CONTAINER=hhy-r04-<unique-suffix> \
  bash scripts/run_r04_disposable_postgres_container.sh
```

脚本仅接受 `hhy-r04-*` 容器名，不映射数据库端口，结束或失败时均清理独立容器。
