# 合伙云 Pro V1.2.3 持续开发无状态接续强制门禁版

本包由两层基线组成：

- **V1.2.2 产品与页面施工基线**：189 个页面/交互面、315 个 REST API、314 个配置项和 P00—R32 计划。
- **V1.2.3 持续开发协议**：唯一 Session/Task Claim、租约、检查点、Commit Trailer、完整 CR、Handoff Bundle、Context Pack、事件哈希链、本地 Hooks 和逐 Commit CI。

## 冷启动

```bash
python3 scripts/continuity.py resume
```

仓库是唯一上下文，对话不是事实源。任何 AI 只要取得完整仓库或受验证的 Handoff/Clean Export，即可读取当前任务、进度、修改、测试、决策和下一步。

除非项目所有者明确报告未连接，否则冷启动必须假定 Codex 已连接 `obx-test` 且既有项目环境可用，并运行 `python3 scripts/verify_cloud_environment.py --check-android` 验证。失败只允许阻断并报告，禁止按无服务器状态继续或重建本地 Android SDK。

跨电脑 Git 恢复以 `config/REPOSITORY_TRANSPORT.yaml` 为非秘密事实源，由 `scripts/restore_git_transport.py` 校验/恢复 remote 和 upstream 并执行推送预检。模型按 `config/DEVELOPMENT_RUNTIME.yaml` 分级：复杂/高风险用 Sol，中等用 Terra，轻量/机械/只读用 Luna；不可用时记录实际回退，禁止虚构模型使用记录。

## 关键入口

- `.continuity/CONTINUITY_POLICY.yaml`
- `docs/03-continuity/持续开发无状态接续强制门禁_V1.2.3.md`
- `docs/03-continuity/无状态接续运行手册_V1.2.3.md`
- `CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`
- `artifacts/context/CURRENT_CONTEXT_PACK.md`
- `scripts/continuity.py`
- `scripts/check_v123_continuity.py`
- `config/REPOSITORY_TRANSPORT.yaml`
- `config/DEVELOPMENT_RUNTIME.yaml`
- `scripts/verify_cloud_environment.py`
- `scripts/restore_git_transport.py`

## 状态

`READY_FOR_FORMAL_DEVELOPMENT_WITH_ENFORCED_STATELESS_CONTINUITY`
