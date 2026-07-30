# Governance V5.0 Supervisor 兼容性审计

审计结论：`PASS_WITH_COMPATIBILITY_NOTES`。

## 权威控制面

- 项目为 `hhy-pro-platform`，仓库 HEAD 为 `bdc1f6661126ec5d545bcd863230bdd46dab0338`；审计时工作区已有未提交的 Supervisor 草稿文件，未将其误报为干净基线。
- `governance/DEVELOPMENT_CONSTITUTION.yaml` 为 `ENFORCED`，状态 Schema 为 `hhy.governance-state/v5.0`。
- `governance/STATE.yaml` Revision 为 `6`，当前唯一任务为 `TASK-R14-RECOVERY-002`，状态 `READY`，Attempt `0/3`。
- 唯一状态写入者仍是现有 Orchestrator；Supervisor 不写 `STATE.yaml`、不选择 Task、不增加 Attempt。

## 控制器接口

`run-loop` 已经在 Orchestrator 内部调用 `codex exec --ephemeral` 启动单 Task、单 Attempt 的 Worker，并返回 `hhy.run-loop/v5.0` 结构化 JSON。Supervisor 只调用：

```text
doctor --ci
active-task --format json
run-loop --max-runs <bounded value>
```

Supervisor 不调用 Codex CLI，不恢复聊天，不模拟 Codex App 输入，也不建立第二套任务状态机。

现有控制器没有直接输出 `MAX_RUNS_REACHED`、`NEXT_TASK_READY` 两个文字状态。监督层只允许依据结构化结果、批次运行数和重新读取的权威状态进行兼容映射；未知状态一律停止为 `UNKNOWN_STATUS`。`CANDIDATE_EVIDENCE_REQUIRED` 映射为 `CANDIDATE_REQUIRED`，原始结果进入批次日志和停止报告。

## 状态边界

自动继续仅限 `MAX_RUNS_REACHED` 与 `NEXT_TASK_READY`，并须同时满足 Doctor、状态一致性、工作区安全、单实例锁、预算和无停止报告条件。Candidate、External/Infrastructure Blocked、Failed Bounded、Policy Violation、Owner、Program Complete、认证、冲突和未知状态均立即停止。

R14 Candidate、APK、截图、Gate 和 Machine Close 仍由现有 Orchestrator/Gate 管理；Supervisor 只负责调用、观察、限流、停止和报告。

## 运行文件

运行文件仅位于 `governance/runtime/supervisor/`，包括锁、PID、heartbeat、runtime state、批次/事件日志和停止报告。它们不是事实源，不能替代 `governance/STATE.yaml`。

## 审计边界

V5.0 已正确安装、Doctor 通过、状态 Schema 有效、仓库为 hhy。当前没有活动 Worker 或 Supervisor。可以进入独立 Supervisor 实现阶段；本审计没有启动产品开发，也没有修改 R14 产品代码。
