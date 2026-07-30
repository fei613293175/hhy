# R04 TASK-002 PostgreSQL 与模块门禁证据

- 执行日期：2026-07-19
- 数据库：一次性 `postgres:17.10-alpine` 容器，未发布主机端口
- 影响范围：仅独立容器与只读挂载的临时仓库副本；既有 R01/R02/R03/Staging 容器未修改
- 清理结果：`hhy-r04-dbtest-0520113` 容器和 `/tmp/hhy-r04-dbtest-0520113` 均已删除

## 结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| 空库顺序升级 | PASS | V001 至 V021 顺序应用，`hhy` 基础表数量为 200 |
| R04 数据不变量 | PASS | Scope、活动桶隔离、私有域名、媒体 SHA/大小、上传过期、令牌时效、迁移计数拒绝场景通过 |
| U021 回滚 | PASS | R04 约束与索引剩余计数 `0|0` |
| V021 重放 | PASS | 回滚后重新应用 V021，并再次执行全部 R04 不变量 |
| MODULE | PASS | `backend-module`：208 tests，0 failures，0 errors，2 skipped；Reactor BUILD SUCCESS |
| 影响测试运行器 | PASS | Windows Maven/Gradle wrapper 改为仓库绝对路径，11 项 Python 回归测试通过 |

## 可复现命令

在已连接且具有固定 PostgreSQL 17 镜像的隔离开发机执行：

```bash
HHY_R04_DB_CONTAINER=hhy-r04-<unique-suffix> \
  bash scripts/run_r04_disposable_postgres_container.sh
```

该脚本不映射数据库端口，结束或失败时都会删除独立容器。
