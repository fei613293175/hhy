# TASK-R08-003 项目发布与详情后端闭环报告

## 结论

- 结果：`PASS`
- 精确验证 Commit：`e312321c8e0664493aec04dd232552eb42ce6e18`
- 环境：`obx-test`、Temurin `21.0.9`、Maven `3.9.11`、PostgreSQL `17.10`
- 产品缺陷：P0 = 0，P1 = 0
- 测试夹具问题：`PROB-0078` 已按 `CR-0216` 修复；生产约束、迁移和业务代码未放宽。

## 已落地能力

- 项目创建、登录态详情、所有者编辑、收藏、分享、直接会话和公开分享预览七项接口已实现。
- 复用 R06 项目列表和 R07 联系方式访问后，R08 页面依赖的九项后端能力全部具备真实实现。
- 发布联系方式使用带渠道结构和 AES-GCM 安全信封；公开预览不返回联系方式明文。
- 收藏、分享、直接会话、幂等、审计和 Outbox 均使用真实 PostgreSQL 存储，不以模拟仓库替代最终 Store 验证。
- V033 项目不变量和 V034 后端配置均支持空库、升级、回滚保护及重放。

## 验证结果

| 门禁 | 结果 | 摘要 |
|---|---|---|
| Python 合同与配置静态回归 | PASS | 7 tests，0 failure |
| V033 PostgreSQL 17.10 | PASS | 空库 200 表；3 约束、2 索引、1 触发器；升级、业务不变量、U033 回滚和重放全部通过 |
| V034 PostgreSQL 17.10 | PASS | 13 项配置、非覆盖、激活配置回滚保护、长文本回滚保护、U034 和重放全部通过 |
| R08 Store 真实数据库定向测试 | PASS | 34 个 Flyway 迁移至 V034；3 tests，0 failures，0 errors，0 skipped；`BUILD SUCCESS` |
| 后端受影响 MODULE | PASS | `mvn -B -pl access,boot -am test`；340 tests，0 failures，0 errors，8 个既有显式条件跳过；`BUILD SUCCESS` |

## 证据与 SHA-256

| 远端证据 | SHA-256 |
|---|---|
| `/tmp/hhy-r08-task003-pg17.log` | `33d92a26e7b7c6da89047b7cfaa1f1773ca41ba46b32313698bd91122abad919` |
| `/tmp/hhy-r08-task003-config-pg17.log` | `5ab6236c9103ba82701763f0ceb5ec22ecb59420c6a2afc4383ab506bb8e7bac` |
| `/tmp/hhy-r08-task003-java21-postgres-e312321c.log` | `5a3877012f85b3371c5c50ff401790d0a3f3b908d410449d2a5bd1979150d3c2` |
| `/tmp/hhy-r08-task003-module-java21-e312321c.log` | `2f4525bdce99a5717f696641002f4db6edcb5cd64614d26bbe03f3d28d24a933` |

精确提交工作树 `/tmp/hhy-r08-task003-current-e312321c` 在验证前后均无源码漂移。成功判定同时核对命令退出码、Surefire 总结和 `BUILD SUCCESS`，不采信包装器末尾的单独 PASS 文本。

## 分层说明

本任务属于 R08 单域正式后端能力，按 `MODULE` 执行受影响验证。Android 模拟器、最终视觉截图和候选 APK 只在 R08 大版本最终候选任务执行；本任务不触发 GitHub 模拟器或桌面 APK 交付。
