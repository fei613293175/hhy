# START HERE · V1.2.3

## 1. 本包是什么

V1.2.2 仍是产品、页面和运营施工基线；V1.2.3 在其上增加持续开发无状态接续强制门禁。项目可以不依赖任何旧对话，由完整仓库恢复任务、WIP、测试、CR、检查点和下一步。

## 2. 新 AI / 新开发者第一条命令

```bash
python3 scripts/continuity.py resume
```

严格执行输出的唯一下一命令。不要先修改文件，也不要让用户重新解释需求。

随后必须验证项目云端既有环境：

```bash
python3 scripts/verify_cloud_environment.py --check-android
python3 scripts/restore_git_transport.py status
```

除非项目所有者明确报告未连接，否则默认 Codex 已连接 `obx-test` 且项目环境已存在。预检失败时停止并报告，不得进入“无服务器开发”或重建本地 Android SDK。

## 3. 默认多代理规则

项目所有者已经提供跨 AI、跨电脑的长期授权。主控在每个 Task 开始和范围变化时必须评估并行机会；有安全、边界清晰且路径互斥的工作包时，自动启用 1 至 3 个隔离执行代理，不再逐次询问用户。始终只有 1 个 ACTIVE Session、1 个 Claim、1 个事实分支和 1 个提交/推送/发布者。

未启用代理时，Checkpoint 必须记录不可安全拆分、运行环境不支持或用户本次要求串行等原因。不支持代理的 AI 可以由主控单线程继续，但不得伪造并行证据。资金、安全、状态机、并发、破坏性迁移与最终集成仍由主控串行复核。

模型按任务复杂度自动选择：复杂/高风险使用 Sol，中等使用 Terra，轻量/机械/只读使用 Luna；不可用时按 `config/DEVELOPMENT_RUNTIME.yaml` 记录目标、实际模型与降级原因。当前若无 Luna，只能记录为 Terra low 回退，不能声称使用了 Luna。

## 4. 首次仓库初始化

```bash
python3 scripts/continuity.py bootstrap   --actor <ACTOR_ID> --init-git --initial-commit   --task TASK-P00-001 --branch task/TASK-P00-001
python3 scripts/continuity.py start   --actor <ACTOR_ID> --task TASK-P00-001   --story <STORY_ID> --goal '<精确目标>'
```

## 5. 开发中

```bash
python3 scripts/continuity.py checkpoint   --summary '<完成内容>' --next-step '<精确下一步>'   --test 'name|PASS|evidence|note'   --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'
```

冻结事实变化使用 `cr-create`、`cr-amend` 和不同 Actor 的 `cr-approve`。切换 AI 前使用 `handoff`；异常中断使用 `recover`；干净项目归档使用 `export-clean`。

跨电脑或交接包恢复 Git 时只使用 `config/REPOSITORY_TRANSPORT.yaml` 和 `scripts/restore_git_transport.py`；认证凭据始终由当前电脑的 Credential Manager/SSH Agent 提供，不得写入仓库或远程 URL。推送前必须运行受控 preflight，禁止 force push。

## 6. 门禁

```bash
python3 scripts/check_v123_continuity.py --strict
python3 scripts/continuity_gate.py --mode doctor --strict
```

代码编译、数据库、供应商、DNS、签名、压测和上线合规仍在对应开发/发布门禁完成；它们不是文档或接续协议缺口。
