# AGENTS.md — 合伙云 Pro V1.2.3 最高工程与无状态接续约束

## 0. 对话不是事实源

旧对话、聊天摘要和个人记忆不得作为继续开发的必要输入。需求、决策、WIP、测试、阻塞和下一步必须写入仓库。新 AI 接手时不得要求用户重新解释仓库已有需求。

## 1. 冷启动唯一流程

1. 运行 `python3 scripts/continuity.py resume`。
2. 阅读输出指定的 Context Pack、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、Release DoR、Stories 和当前 Session/Checkpoint。
3. 没有活跃会话时，只能执行输出的 `bootstrap` 或 `start` 命令。
4. 存在 `HANDED_OFF` 会话时必须 `takeover`；过期/异常会话必须 `recover`。
5. 禁止跳过唯一任务领取和租约直接修改项目文件。

## 2. 每次开发强制协议

- 同一时间只允许一个 ACTIVE Session 和一个相同 Task/Story Claim。
- 允许该 ACTIVE Session 的主控委托最多 3 个执行代理并行工作，但每个代理必须使用独立 scratch worktree 和互不重叠的路径租约。
- 执行代理不是独立事实源：不得修改 `.continuity/**`、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、`releases/**`，不得提交、推送、合并或发布；只能返回补丁和模块测试证据。
- 主控必须先验证执行代理的修改范围与测试证据，再把补丁应用到事实分支；只有进入事实分支并完成 Checkpoint 的变化才算项目进度。
- 首次产生项目变化、每60分钟、关键测试后、提交/推送/交接/切换AI前必须 `checkpoint`。
- 项目变化时检查点必须记录测试；不能用聊天中的“测过了”替代证据。
- 检查点后再次修改文件必须重新检查点，旧检查点不能提交。
- 每个 Commit 必须绑定 Task、Story（存在时）、Session、Checkpoint、Tests、CR。
- Git Hooks 和 CI 对每个非合并 Commit 重验，禁止通过 `--no-verify` 绕过远程门禁。
- 交接必须生成 Handoff Bundle；另一个 AI 仅凭仓库/交接包恢复，不读取旧对话。

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

## 6. 会话结束

完成实现 Commit 后执行 `continuity.py close`，生成关闭检查点和关闭元数据 Commit；更新 Release、Task、Changelog、Context Pack、CURRENT_STATUS 和 NEXT_TASK。未形成可验证仓库记录的工作不算完成。
