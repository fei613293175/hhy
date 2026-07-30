# R06 专项测试与故障注入报告

- Task：`TASK-R06-005`
- Story：`STORY-R06-005`
- 环境：`obx-test`，`maven:3.9.11-eclipse-temurin-21`
- 模块：`services/backend/boot`
- 结果：`PASS`

## 权威测试 ID

| 测试 ID | 覆盖证据 | 结论 |
|---|---|---|
| `TST-CONTENT_001-HAPPY` | 合法上架写入状态历史、审计、单条 Outbox 和幂等结果 | PASS |
| `TST-CONTENT_001-IDEMPOTENT` | 相同请求重放已存结果，不再加锁、变更状态或重复写 Outbox | PASS |
| `TST-CONTENT_001-REJECT` | 同键异请求、处理中重复请求、陈旧版本和存储超时均在副作用前拒绝或传播 | PASS |
| `TST-HOME_001-HAPPY` | 只返回冻结排期内模块，服务端时间与追踪信息稳定 | PASS |
| `TST-HOME_001-IDEMPOTENT` | 相同请求重复读取结果一致，不产生 Outbox 或写副作用 | PASS |
| `TST-HOME_001-REJECT` | 非法 CMS 排期与 CMS 提供方超时不返回部分首页数据 | PASS |

## 故障覆盖

- 重复请求：命中已完成幂等记录时复用结果。
- 并发：处理中幂等记录和陈旧 `expectedVersion` 均返回冲突，不越过写门禁。
- 超时：存储变更超时在审计、Outbox 和幂等完成前终止；事务由 Spring 回滚。
- 消息重复：重放路径明确断言不再写 Outbox。
- 供应方异常：CMS 配置非法或提供方超时均失败关闭，不输出部分业务数据。

## 执行结果

命令：

```text
mvn -pl boot -am -Dtest=ContentServiceTest,AdminContentControllerContractTest,HomeControllerContractTest,R06FrozenOperationCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`15 tests, 0 failures, 0 errors, BUILD SUCCESS`，总耗时 `8.153s`。其中 `ContentServiceTest` 12 项，冻结 operationId、首页控制器和管理端内容控制器契约各 1 项。

首次仅指定 `-pl boot` 未包含 reactor 依赖，Maven 在测试开始前因本仓库 SNAPSHOT 依赖未构建而退出；修正为 `-am` 后执行成功。该命令问题不计为产品缺陷或测试失败。

## 缺陷结论

- P0/P1 缺陷：0
- 生产 TODO/Mock：未发现新增项
- 阻断项：0
