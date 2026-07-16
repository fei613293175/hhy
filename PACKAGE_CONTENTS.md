# V1.2.3 最终开发包目录

## 正式入口

1. `V1.2.3_持续开发无状态接续强制门禁发布说明.md`
2. `START_HERE.md`
3. `AGENTS.md`
4. `artifacts/context/CURRENT_CONTEXT_PACK.md`
5. `CURRENT_STATUS.yaml`
6. `NEXT_TASK.yaml`
7. `releases/P00/DEFINITION_OF_READY.yaml`
8. `releases/P00/STORIES.yaml`

## 产品与施工规格

- V1.2.2 主文档、189 个页面/交互面、315 个 REST API、10 个 WebSocket 事件、314 个配置项、198 张表和 P00—R32 计划保持完整。
- 页面、字段、状态、动作、后台运营、配置和追踪事实源位于 `catalogs/`、`contracts/`、`database/` 和 `docs/02-ui/`。

## V1.2.3 连续性控制面

| 入口 | 用途 |
|---|---|
| `.continuity/CONTINUITY_POLICY.yaml` | 唯一机器策略入口 |
| `scripts/continuity.py` | Bootstrap、Start、Checkpoint、CR、Handoff、Takeover、Recover、Close、Export |
| `scripts/continuity_gate.py` | pre-commit、commit-msg、pre-push 和 CI 逐 Commit 重验 |
| `.githooks/` | 本地强制门禁 |
| `.github/workflows/continuity-gate.yml` | 远程不可绕过重验 |
| `.continuity/sessions/` | 机器会话记录 |
| `.continuity/checkpoints/` | 项目文件指纹与测试证据 |
| `.continuity/change-requests/` | 完整 CR 合同和职责分离审批 |
| `artifacts/handoffs/` | 基线交接证据 |
| `artifacts/context/` | 仓库唯一上下文包 |
| `catalogs/continuity_*` | 42 条门禁、16 类事件、20 项必需记录 |

## 最终验证

- 文档严格校验：PASS
- 连续性 Doctor：PASS
- 联合 V1.2.3 门禁：PASS
- 真实生命周期：14/14 PASS
- 可移植重建：9/9 PASS
- EVENT_LOG 与 Handoff 篡改检测：PASS

## 冷启动

```bash
python3 scripts/continuity.py resume
```

对话上下文禁止作为事实源。
