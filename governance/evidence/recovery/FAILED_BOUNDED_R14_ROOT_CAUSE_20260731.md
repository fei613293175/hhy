# R14 FAILED_BOUNDED 根因报告

## 状态边界

- 原任务：`TASK-R14-RECOVERY-001`
- 当前状态：`FAILED_BOUNDED`
- Attempt：`3/3`
- 原任务状态、Attempt 计数和失败提交永久保留；没有创建 Attempt 4。
- 当前 main 基线：`b789d3528c4047ae1528771e08cf9fe034f848fa`
- 旧 Candidate `89ccaf45` 仅作为历史证据，不作为新任务源码或 PASS 依据。

## 三次 Attempt

| Attempt | 状态提交 | 可确认的直接原因 | 产品代码修改 | Gate/Reviewer |
| --- | --- | --- | --- | --- |
| 1 | `804d30ca` | Worker CLI/Windows 运行环境未产生 Worker result | 无 | 没有候选，因此没有任务 Gate 或 Reviewer result |
| 2 | `171a90a9` | Codex Worker 上游 stream 在 5 次重试后断开 | 无 | 没有候选，因此没有任务 Gate 或 Reviewer result |
| 3 | `9f4a6bd7` | 同类 Worker execution failure/no worker-result | 无 | 没有候选，因此没有任务 Gate 或 Reviewer result |

三次提交的 diff 只包含 `CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、`governance/STATE.yaml` 和生成视图；没有 `apps/`、`services/`、`database/`、`contracts/`、`packages/` 或其他产品代码改动。

## 机器证据

- GitHub migration Gate 日志：`.scratch/run-30571669039`、`.scratch/run-30571669044`、`.scratch/run-30574520565`。
- GitHub main 收口日志：`.scratch/run-30575499521`、`.scratch/run-30575499537`。
- 可复核的 GitHub 检查包括 core/governance；它们不是三次 Worker 的候选 Gate 或 Reviewer 结果。
- Worker 原始结果、Reviewer result、每次停止报告和逐次任务 Gate JSON 没有进入仓库权威证据目录，不能补写或推断。

## 分类结论

- 产品代码问题：未证实；三次 Attempt 均没有产品代码 diff。
- UI/视觉问题：未证实；没有候选 APK、截图或视觉验收结果。
- 测试/验收合同问题：原任务要求同时完成源码审计、APK、模拟器、视觉、候选和机器关闭，范围很宽；但没有 Worker result 证明某个产品测试失败。
- Android/模拟器/GitHub 基础设施：不是三次失败的直接证据。第一次受 Windows CLI/runtime 影响，第二、三次为上游 Worker stream/no-result；GitHub 控制面问题另有治理日志。
- 治理控制器缺陷：`FAILED_BOUNDED` 后旧治理测试仍强制要求 active recovery task，已在 `16263a92` 修正为验证终态；这不是 Worker 三次失败原因。
- 缺少外部证据/权限/密钥/证书：没有证据表明这是三次失败原因；缺失的是 Worker/Reviewer/停止报告持久化。

## 最终根因

主要根因是三次 Worker 会话都没有产生可验证的 Worker result，前两次分别受到本机 CLI/runtime 和上游 stream 断开影响，第三次仍是同类 no-result；因此 Orchestrator 按规则耗尽 3 次并进入 `FAILED_BOUNDED`。次要治理问题是原 recovery 任务范围过宽，以及失败会话的机器证据没有随任务提交持久化。

## 接替决策

项目所有者授权仅保留 `89ccaf45` 之后已确认有效的修复，以当前 main 为基线建立 `TASK-R14-RECOVERY-002`。新任务不得继承旧 Candidate、APK、截图或 PASS，最大 Attempt 固定为 3，且只处理本报告确认的根因。
