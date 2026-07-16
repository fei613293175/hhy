# 合伙云 Pro V1.2.3 持续开发无状态接续强制门禁版

本包由两层基线组成：

- **V1.2.2 产品与页面施工基线**：189 个页面/交互面、315 个 REST API、314 个配置项和 P00—R32 计划。
- **V1.2.3 持续开发协议**：唯一 Session/Task Claim、租约、检查点、Commit Trailer、完整 CR、Handoff Bundle、Context Pack、事件哈希链、本地 Hooks 和逐 Commit CI。

## 冷启动

```bash
python3 scripts/continuity.py resume
```

仓库是唯一上下文，对话不是事实源。任何 AI 只要取得完整仓库或受验证的 Handoff/Clean Export，即可读取当前任务、进度、修改、测试、决策和下一步。

## 关键入口

- `.continuity/CONTINUITY_POLICY.yaml`
- `docs/03-continuity/持续开发无状态接续强制门禁_V1.2.3.md`
- `docs/03-continuity/无状态接续运行手册_V1.2.3.md`
- `CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`
- `artifacts/context/CURRENT_CONTEXT_PACK.md`
- `scripts/continuity.py`
- `scripts/check_v123_continuity.py`

## 状态

`READY_FOR_FORMAL_DEVELOPMENT_WITH_ENFORCED_STATELESS_CONTINUITY`
