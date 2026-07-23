# TASK-R10-005 群聊推广专项测试与故障注入报告

- 执行日期：2026-07-23
- Session：`SES-20260723T114316Z-44EBE3C1`
- Task / Story：`TASK-R10-005` / `STORY-R10-004`
- 结论：`PASS`

## 冻结测试逐项结果

| 测试 | 结果 | 主要证据 |
| --- | --- | --- |
| `TST-GROUP_001-HAPPY` | PASS | R10 创建、详情、编辑、公开分享；独立 `JOIN_PASSWORD` 密文与固定掩码；PostgreSQL17 空库/升级/运行时不变量 |
| `TST-GROUP_001-REJECT` | PASS | 非法/重复联系渠道、不安全群链接、未拥有二维码、权限拒绝、陈旧/并发版本冲突均零业务副作用 |
| `TST-GROUP_001-IDEMPOTENT` | PASS | 首次响应丢失后同键同体重试返回加密快照，群记录和 Outbox 各一次；同键换请求体返回 `COMMON-409-IDEMPOTENCY_CONFLICT` 且零写入 |

## 自动化结果

- Python 合同与数据库静态矩阵：9 tests，PASS，1 项真实数据库场景按环境开关跳过后由下列 PostgreSQL17 容器矩阵实际执行。
- Maven 精确 R10 测试：`R10ServiceTest`、`R10PostgresStoreTest`、`R10ControllerContractTest` 共 9 tests，0 failures，0 errors；Postgres Store 的环境测试由独立真实矩阵覆盖。
- PostgreSQL `17.10-alpine` 隔离容器：升级前置失败关闭、空库至 V036、V035 有效升级、群推广不变量、父行锁并发写偏斜、U036 回滚、V036 重放全部 PASS。

## 故障注入判定

- 重复请求：同键同体只产生一个群记录、一次联系人替换、一次 Outbox 和一次幂等完成。
- 超时重试：以“业务已完成但首次响应丢失”模拟网络超时，重试只解密冻结响应快照，不重复副作用。
- 并发：陈旧 `expectedVersion` 和锁后版本变化均返回稳定冲突；数据库父行锁阻止详情/联系人双写偏斜。
- 消息重复：R10 群聊推广不消费消息队列或供应商回调；唯一异步副作用是事务内 Outbox 单次写入，已由幂等测试断言。判定 `N/A_WITH_EVIDENCE`。
- 供应商异常：R10 无供应商 SDK、支付、短信或外部回调依赖；HTTPS 群链接仅作为受控业务字段，不由服务端调用。判定 `N/A_WITH_EVIDENCE`。

## 缺陷结果

- 新增回归补齐“同一幂等键复用不同请求体”的显式冲突与零副作用断言。
- 本轮未发现生产代码缺陷；关键缺陷为 0。
- 模拟器、截图和候选 APK 按 R10 执行计划仅在 `TASK-R10-007` 运行，本任务未触发 GitHub 候选门禁。
