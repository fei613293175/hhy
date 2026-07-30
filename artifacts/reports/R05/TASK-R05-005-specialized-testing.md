# TASK-R05-005 · 单一实名认证专项测试与故障注入报告

- Session：`SES-20260719T224052Z-2C69767F`
- Story：`STORY-R05-001`
- 被测冻结源码：`3ec3ba2fdcb2063c658c467710e294b1f9b92dbb`
- 结论：八项核心实名测试全部自动化并通过；关键缺陷为 0

## 权威八项矩阵

| 测试 ID | 覆盖重点 | 结果 |
| --- | --- | --- |
| TST-ID_001-HAPPY | 姓名身份证、H5活体、人证比对、私有照片、原子完成 | PASS |
| TST-ID_001-IDEMPOTENT | 首次响应重放、处理中冲突、16路并发重复回跳 | PASS |
| TST-ID_001-REJECT | 过期协议、订单错配、非法供应商结果零副作用 | PASS |
| TST-ID_001-SECURITY | AEAD绑定、摘要篡改、超时、Secret用后清零 | PASS |
| TST-ID_002-HAPPY | 短时原图、水印、原因、敏感访问审计 | PASS |
| TST-ID_002-IDEMPOTENT | 复核快照、重复消息、不同负载冲突 | PASS |
| TST-ID_002-REJECT | 乐观锁、双人冻结、拒绝前零副作用 | PASS |
| TST-ID_002-SECURITY | `private_kyc`、授权访问、不可变审计 | PASS |

机器可读结果：`artifacts/validation/r05-test-evidence/r05-specialized.evidence.json`。

## 真实环境结果

| 证据 | 结果 | SHA-256 |
| --- | --- | --- |
| `maven-java21.log` | 全后端 284 项，0 failure，0 error；5 个环境条件测试按普通模式跳过 | `52290208BCB6A59380B47E8963B364346F16B2A9BC1CFA4143A391A7BCC57CAE` |
| `maven-postgresql17.log` | R05 Store 与私有证据 5/5，0 failure，0 error，0 skipped；真实应用 V001–V028 | `3C3556079A0D7AEB4E92C9E283A223B4D5F34AB76303EADB157ADCB18292AFE4` |
| `postgresql17-invariants.log` | 空库200表、32约束、14索引、回滚、重放和实名不变量全部 PASS | `A68E9F05F5AB7ABBC34BB87662405F95ECD9B8227BE5754F83034F2AAB493BFE` |
| `maven-postgresql17-missing-user.failure.log` | 首次运行缺测试数据库用户名的环境失败，未进入业务断言；补齐 `postgres` 后消除 | `086E0D2BAF8A941F1F8867320C6E0BEE57474B7CDDA197D1FABE5FE6C92BC04D` |

## 故障注入结论

- 供应商连接超时统一转为可重试商业错误，APPCODE 在异常路径仍被清零。
- 供应商订单号错配和私有证据 SHA-256 错配均在状态推进前阻断，不产生完成记录。
- 同一回跳 state 的 16 路并发消费只有 1 路获得业务结果，其余稳定返回失效，不重复写入。
- 首个幂等请求仍处理中时，重复请求不执行第二次业务动作；首次响应完成后可加密重放，不同负载稳定冲突。
- 私有媒体、人工复核记录和敏感访问审计均受 PostgreSQL 约束、唯一键和不可变触发器保护。

## 失败证据与关键缺陷

第一次 PostgreSQL Java 测试因命令未传 `HHY_DB_MIGRATION_TEST_USER`，连接在业务测试前被数据库拒绝。该问题属于可复现的测试环境配置遗漏，已通过显式传入 `postgres` 修正，并保留原始失败日志。最终业务测试、数据库测试和矩阵门禁均通过，未发现待修复的 P0/P1 产品缺陷。

## 验收结论

- 无 TODO、生产 Mock 或用户可见技术字段被引入。
- 八项 Catalog 状态已从 `READY_TO_AUTOMATE` 更新为 `AUTOMATED`，每项均绑定真实 Java 方法。
- 代码、文档、测试、追踪和失败证据同步完成。
- AC-R05-002 与 AC-R05-003 满足 PASS；TASK-R05-005 可进入关闭门禁。
