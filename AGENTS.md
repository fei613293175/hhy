# AGENTS.md — 合伙云 Pro V1.2.3 最高工程与无状态接续约束

## 0. 对话不是事实源

旧对话、聊天摘要和个人记忆不得作为继续开发的必要输入。需求、决策、WIP、测试、阻塞和下一步必须写入仓库。新 AI 接手时不得要求用户重新解释仓库已有需求。

## 1. 冷启动唯一流程

1. 运行 `python3 scripts/continuity.py resume`。
2. 阅读输出指定的 Context Pack、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、Release DoR、Stories 和当前 Session/Checkpoint。
3. 没有活跃会话时，只能执行输出的 `bootstrap` 或 `start` 命令。
4. 存在 `HANDED_OFF` 会话时必须 `takeover`；过期/异常会话必须 `recover`。
5. 禁止跳过唯一任务领取和租约直接修改项目文件。
6. 运行 `python3 scripts/verify_cloud_environment.py --check-android` 验证 `obx-test` 上的既有项目环境；失败必须阻断并报告。

除非项目所有者明确报告云服务器未连接，否则任何 AI 和任何电脑都必须以“Codex 客户端已连接项目云服务器且既有环境可用”为开发前提。不得把换电脑、换 AI、SSH 预检失败或本机缺少 Android SDK 解释为无服务器开发许可；禁止下载、安装或重建本地 Android SDK 作为回退。

## 2. 每次开发强制协议

- 同一时间只允许一个 ACTIVE Session 和一个相同 Task/Story Claim。
- 项目所有者已提供长期仓库级授权：主控在每个 Task 开始及范围变化时必须评估并行机会；存在边界清晰、路径互斥且可安全并行的工作包时，应自行委托 1 至 3 个执行代理，不得再次请求逐次用户确认。
- 主控必须按实际复杂度选择执行模型并留下可审计记录：复杂/高风险使用 Sol，中等使用 Terra，轻量/机械/只读使用 Luna。目标模型不可用时只允许按 `config/DEVELOPMENT_RUNTIME.yaml` 降级并记录实际模型；不得虚构目标模型已被使用。Luna 不可用时默认降级为 Terra low。
- 未委托时必须在 Checkpoint 的 `parallel_execution` 中记录 `NO_SAFE_PARALLEL`、`CAPABILITY_UNAVAILABLE` 或 `USER_SERIAL_OVERRIDE` 及具体原因；不具备代理能力的 AI 必须记录能力限制，不得伪造代理或并行测试证据。
- 用户可以在具体请求中临时要求串行；长期授权只消除“是否启用代理”的逐次确认，不扩大任务范围、外部权限、生产权限或秘密访问权限。
- 每个执行代理必须使用独立 scratch worktree 和互不重叠的路径租约；总模型固定为 1 个事实主控加最多 3 个执行代理，不是 4 个子代理，也不是 4 个并行事实分支。
- 执行代理不是独立事实源：不得修改 `.continuity/**`、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、`releases/**`，不得提交、推送、合并或发布；只能返回补丁和模块测试证据。
- 主控必须先验证执行代理的修改范围与测试证据，再把补丁应用到事实分支；只有进入事实分支并完成 Checkpoint 的变化才算项目进度。
- 资金、安全、状态机、并发、破坏性迁移、签名、生产变更和最终集成必须由主控串行复核。
- 首次产生项目变化、每60分钟、关键测试后、提交/推送/交接/切换AI前必须 `checkpoint`。
- 项目变化时检查点必须记录测试；不能用聊天中的“测过了”替代证据。
- 检查点后再次修改文件必须重新检查点，旧检查点不能提交。
- 每个 Commit 必须绑定 Task、Story（存在时）、Session、Checkpoint、Tests、CR。
- Git Hooks 和 CI 对每个非合并 Commit 重验，禁止通过 `--no-verify` 绕过远程门禁。
- 交接必须生成 Handoff Bundle；另一个 AI 仅凭仓库/交接包恢复，不读取旧对话。
- Git 远程和 upstream 只能按 `config/REPOSITORY_TRANSPORT.yaml` 及受控脚本恢复。推送前必须执行 transport/push preflight；禁止 force push、含凭据 URL、错误 remote/upstream、behind/diverged 推送，以及从无 `.git` 且无已验证 Bundle 的纯源码伪造历史。

### 2.1 分层验证

- `FAST`：每次本地变化与提交，校验 Session、Scope、Secret、CR、Fingerprint、Trailer 和受影响的轻量测试。
- `MODULE`：执行代理交付补丁前，运行其修改模块的单元、类型、契约或静态检查。
- `INTEGRATION`：每天一次或纵向切片汇合时，运行后端、Web、数据库、Android 和连续性全量集成。
- `RELEASE`：版本封板前运行全量集成、Staging/E2E、APK 四方哈希与用户真机验收。
- 完整文档 Doctor 仅在其事实输入变化、手动 Doctor、集成或发布阶段运行；不得因此跳过任何连续性核心校验。

## 3. 变更控制

冻结需求、页面、API、数据库、配置、状态机、资金或架构发生变化前必须：

`cr-create → cr-amend → 不同Actor cr-approve → checkpoint → commit`

申请人不得自审；空壳 CR 不得审批。Bug 修复同时更新 Problem Registry 和回归测试。

## 4. 产品与工程事实源

1. V1.2.2 产品/页面施工主文档和已批准 CR/ADR。
2. OpenAPI、WebSocket、数据库、状态机和配置契约。
3. 页面字段/状态/动作/后台运营规格和追踪矩阵。
4. Release DoR、Stories、Tasks、Acceptance。
5. `.continuity/` 会话、检查点、事件哈希链和 Context Pack。
6. UI 参考图只提供视觉参考，不能新增业务。

## 5. 资金、安全、配置和发布

沿用 V1.2.2 全部硬规则：整数分、不可变账本、幂等、红包不超发、秘密只存 SecretRef、生产变更双人复核、Android 产物绑定 Commit/签名/SHA/测试。

测试 APK 必须使用跨版本稳定的测试签名 SecretRef 和单调递增的 `versionCode`；仓库副本、桌面副本、服务器文件和公网下载必须四方 SHA-256 一致。机器交付通过与项目所有者真机验收是两个独立状态，未经项目所有者明确反馈不得把真机状态写为 PASS。

若当前任务仅等待项目所有者真机验收，不得把它伪标为完成。项目所有者明确要求继续开发时，可将该任务保留为 `BLOCKED`，并仅切换到依赖图中全部声明依赖已经 `DONE` 的独立 Release 首个 `READY` 任务；必须记录原任务、阻断原因和恢复条件，收到真机反馈后仍须恢复其验收与关闭。

## 6. 会话结束

完成实现 Commit 后执行 `continuity.py close`，生成关闭检查点和关闭元数据 Commit；更新 Release、Task、Changelog、Context Pack、CURRENT_STATUS 和 NEXT_TASK。未形成可验证仓库记录的工作不算完成。
