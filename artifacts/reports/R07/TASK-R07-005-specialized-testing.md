# TASK-R07-005 十项专项测试报告

## 结论

- 结果：`PASS`
- 冻结源码 Commit：`015178b3d45c5d0ff0d0e458eb92a166d8d5026a`
- 权威库存：R07 发布清单中的 10 个测试 ID，10/10 已自动化并通过。
- 产品缺陷：P0 = 0，P1 = 0。
- 执行范围：受影响 MODULE；本任务不触发大版本最终模拟器、候选 APK 或全量发布门禁。

## 环境与模块结果

| 门禁 | 环境 | 结果 | 摘要 | 证据 SHA-256 |
|---|---|---|---|---|
| 后端受影响 MODULE | obx-test 精确 Commit；Maven 3.9.11；Temurin 21.0.9 | PASS | 332 tests，0 failures，0 errors，7 个既有环境条件跳过 | `d837c1c7bbed61c011274a526036684c2baf761a4fbded6164b1fdd2da2864d3` |
| R07 真实数据库集成 | PostgreSQL 17.10；一次性 `hhy_r07` 数据库 | PASS | Flyway 到 V032；2 tests，0 failures，0 errors，0 skips | `bc8098c657fb2bac47b773914bbc1e3d6bdf10d29d7b3e994d05b5e5e07e3fa7` |
| Android 受影响 MODULE | Windows；Temurin 21.0.11；Gradle 9.4.1 | PASS | `testDebugUnitTest lintDebug`；387 tasks；BUILD SUCCESSFUL | `1adafd833542d28584999428c8f0c38e72d5c5a04baf651410b15102d4519451` |

## 十项矩阵

| 测试 ID | 主验证内容 | 故障与边界覆盖 | 结果 |
|---|---|---|---|
| `TST-CONTACT_001-HAPPY` | 解密、审计、Outbox、客户端明文清理 | 禁止缓存、最小披露 | PASS |
| `TST-CONTACT_001-IDEMPOTENT` | 重复与并发只有一个所有者 | 响应超时重试、同键不同意图 | PASS |
| `TST-CONTACT_001-REJECT` | 非法渠道、旧密文、存储超时 | 零明文泄露、零消息副作用 | PASS |
| `TST-PUBLISHER_001-HAPPY` | 公开主页与内容投影 | 不读取、不暴露联系方式 | PASS |
| `TST-PUBLISHER_001-IDEMPOTENT` | 重复读取稳定 | 零写入、零 Outbox | PASS |
| `TST-PUBLISHER_001-REJECT` | 非法 ID 与不可用发布者 | 隐私保护型未找到 | PASS |
| `TST-SEARCH_001-HAPPY` | 关键词与必要筛选、历史记录 | 真实 PostgreSQL 投影 | PASS |
| `TST-SEARCH_001-IDEMPOTENT` | 重复搜索稳定、历史去重 | 零 Outbox | PASS |
| `TST-SEARCH_001-REJECT` | 非法排序、不稳定游标 | 存储超时不返回部分结果 | PASS |
| `TST-V122-004` | 清空历史显式确认与账号隔离 | 幂等重试、重复提交、单消息 | PASS |

## 远端环境失败与复用结论

首次后端命令把精确 Commit 源码目录以只读方式挂载给 Maven。Maven 在编译阶段需要创建各模块的 `target` 目录，因此出现 `Error while writing new mojo status`，未进入产品测试。这不是代码失败。

纠正方式是保持精确 Commit 克隆不变，将工作副本以可写方式挂载到临时容器；纠正后同一 Commit 的后端受影响 MODULE 332 项通过。原始环境失败日志已单独保存为 `backend-java21-readonly-mount.failure.log`，SHA-256 为 `74c569e03ea503004279d1628d1f7ee2ae7efcc45959b9b7dc35688f99899f10`。

后续规则：远端构建工作副本必须允许构建工具写入派生目录，或把所有构建输出显式重定向到可写卷；挂载、权限、工具链错误单列为环境失败，不计入产品失败，但必须同时保留纠正后的最终产品门禁证据。

## 证据文件

- `artifacts/validation/r07-test-evidence/r07-specialized.evidence.json`
- `artifacts/validation/r07-test-evidence/backend-java21.log`
- `artifacts/validation/r07-test-evidence/backend-java21-readonly-mount.failure.log`
- `artifacts/validation/r07-test-evidence/postgresql17.log`
- `artifacts/validation/r07-test-evidence/android-module.log`

机器校验入口：

```text
python scripts/run_r07_specialized_matrix.py --check --evidence artifacts/validation/r07-test-evidence/r07-specialized.evidence.json
```
