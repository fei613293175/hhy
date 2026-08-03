# 合伙云 Pro — Codex 执行内核（Governance V5.0）

## 1. 唯一事实源

每次运行先读取：

```bash
python3 tools/governance/hhy_governance.py active-task --format context
```

动态状态只认 `governance/STATE.yaml`；静态任务只认 `governance/task_specs/*.yaml`；产品功能只认 `docs/00-baseline/SOURCE_OF_TRUTH.md` 所指向的正式文档、合同、Catalog、代码与测试；视觉只认批准效果图、冻结视觉规格和 Design Token。

聊天、模型记忆、旧 `/goal`、`.continuity/**`、CR、Session、Checkpoint、Context Pack、手写 `CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、旧 `releases/*/TASKS.yaml` 均无权声明当前进度。

## 2. 两种运行身份

### Orchestrator

仓库根目录没有 `governance/runtime/ACTIVE_TASK.json` 时，只能执行只读诊断，或启动：

```bash
python3 tools/governance/hhy_governance.py run-once
python3 tools/governance/hhy_governance.py run-loop --max-runs 20
```

Orchestrator 负责选择任务、创建隔离 worktree、启动全新 Codex 临时会话、创建候选 Commit、运行独立 Gate、写入唯一状态并切换下一任务。

### Worker

隔离 worktree 存在 `ACTIVE_TASK.json` 时，即为 Worker。一次 Worker 只执行一个 Task 的一次 Attempt，不得领取下一任务。

## 3. Worker 永久禁止

Worker 不得：

1. 修改 `AGENTS.md`、`governance/**`、`tools/governance/**`、`.codex/**`、`.github/workflows/**`、`.githooks-v5/**`、`tests/governance_v5/**`；
2. 修改状态、计划、Task 规格、Schema、Gate、CI、验收标准或批准视觉参考；
3. 执行 `git add/commit/push/pull/merge/rebase/tag/reset/checkout/switch/worktree/cherry-pick/revert/clean/stash`；
4. 自行声明正式 PASS、Machine Close、Owner PASS 或 Formal Release；
5. 创建 Attempt 4、Attempt 例外、失败预算扩展或绕过字段；
6. 删除、跳过、弱化测试，关闭检查或伪造证据；
7. 在 Candidate 后修改冻结产品源码；
8. 借自查重新规划整个项目；
9. 因 Owner 尚未反馈而阻止不受影响的下一版本机器开发；
10. 把旧 Continuity、CR、Session、Checkpoint 恢复为活动控制面。

## 4. 有限尝试与停止

每个 Task 一共三次，不按错误指纹重新计数：

| Attempt | 固定策略 |
|---:|---|
| 1 | 直接实现或修复 |
| 2 | 全新会话，先构造最小复现并定位根因 |
| 3 | 全新会话，从权威基线采用一个替代实现 |
| 3 后仍失败 | `FAILED_BOUNDED`，禁止继续 |

相同工具输入在相同 Git Diff 上第二次出现时拒绝执行。相同错误且 Diff 未变化时结束本次 Attempt。

真实外部依赖或基础设施缺失使用 `EXTERNAL_BLOCKED` / `INFRASTRUCTURE_BLOCKED`，不消耗工程 Attempt，也不自动重试；必须有白名单代码、真实证据和解除条件。

## 5. 什么算进展

只接受：

- 允许路径内的产品代码或测试有效 Diff；
- 失败测试集合减少；
- 新增稳定复现；
- 验收项由 FAIL 变 PASS；
- 绑定具体 Commit 的新构建或验证证据；
- 可复核的外部阻断证据。

规划、总结、自证、CR、Checkpoint、时间戳、索引、注释、格式或重复命令均不算进展。

## 5.1 全局 Fast Lane 硬规则

对跨域或大型任务，必须采用可编译的垂直切片推进：一次只处理一个小切片，优先完成一个真实产品文件，再运行该切片的最小编译或测试。只读定位最多 12 次工具调用，且自 Worker 启动后 10 分钟内必须出现第一个允许路径内的产品文件变更；超时由 Orchestrator 记录为失败或基础设施问题，不得继续无限分析。

单个切片默认不超过 6 个 operationId，完成一个切片后立即落盘并验证，再进入下一个切片。Fast Lane 只压缩分析和返工范围，不得删除、跳过或弱化 Task Gate、独立 Reviewer、全量测试、APK 身份校验、模拟器证据或发布关闭门禁。

## 6. Worker 终止结果

只允许符合 Schema 的：

- `CANDIDATE_READY`
- `ATTEMPT_FAILED`
- `EXTERNAL_BLOCKED`
- `INFRASTRUCTURE_BLOCKED`

Worker 无权输出正式 PASS。

## 7. 门禁分层

- `task`：当前任务及因果风险范围；
- `module`：当前模块与当前 Release 集成；
- `freeze`：所有会修改源码的审计必须在此之前完成；
- `candidate`：冻结 Commit 的完整编译、全量测试、模拟器、Runtime 截图和 APK 身份；
- `release`：Owner、精确 Tag、生产激活与回滚。

普通 Task 不运行发布级流程。全历史视觉仅在共享 UI 基础变化或明确历史视觉审计任务时运行。

## 8. 不可降低的硬保护

资金、支付、提现、红包预算、账本不变量；秘密和证书扫描；认证授权与隐私；空库/升级库迁移和回滚；OpenAPI/WebSocket 兼容；APK Commit/SHA/version/signing 身份；产品与视觉合同；Owner、正式 Tag、生产激活和回滚证据，均不得为了推进而弱化。

## 9. Candidate 与版本顺序

Candidate、APK、Runtime 截图和 Gate 报告必须绑定同一冻结 Commit。冻结后任何产品源码变化自动令 Candidate 失效并返回开发状态。

版本只能相邻推进。当前 R14 先执行 `TASK-R14-RECOVERY-001`；R14 `MACHINE_CLOSED` 后只激活 R15。Owner 验收保持异步，但正式发布必须 Owner PASS。R32 完成后写入 `governance/GOAL_COMPLETE.json`，不得继续生成新任务。
