# AGENTS.md — 合伙云 Pro V1.2.3 最高工程与无状态接续约束

## 0. 对话不是事实源

旧对话、聊天摘要和个人记忆不得作为继续开发的必要输入。需求、决策、WIP、测试、阻塞和下一步必须写入仓库。新 AI 接手时不得要求用户重新解释仓库已有需求。

## 1. 冷启动唯一流程

1. 运行 `python3 scripts/continuity.py resume`。
2. 阅读输出指定的 Context Pack、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`、Release DoR、Stories 和当前 Session/Checkpoint；Context Pack 的 `rule_readiness.status` 必须为 `PASS`，并且其全部 `required_sources` 都必须存在于带 SHA-256 的 `source_manifest`。
3. 没有活跃会话时，只能执行输出的 `bootstrap` 或 `start` 命令。
4. 存在 `HANDED_OFF` 会话时必须 `takeover`；过期/异常会话必须 `recover`。
5. 禁止跳过唯一任务领取和租约直接修改项目文件。
6. 运行 `python3 scripts/verify_cloud_environment.py --check-android` 验证 `obx-test` 上的既有项目环境；失败必须阻断并报告。

项目所有者仅说“继续开发”时，新 AI 或新电脑不得要求其重新讲解规则、需求或进度；必须通过上述唯一流程恢复。禁止用“我已完全理解”作为准入证据，缺失、未入清单或哈希过期的规则来源必须由门禁阻断开发。

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
- 项目所有者已长期授权开发所需终端、PowerShell、SSH 和仓库工具执行；AI 必须静默复用已允许前缀、受控运行时或等价工作区通道，禁止弹出终端确认框、Git Credential Manager 或权限选择要求项目所有者操作。可能触发新交互授权的命令必须在执行前改写为无弹窗通道；只读 GitHub 构件不得调用凭据管理器。

### 2.1 分层验证

- `FAST`：每次本地变化与提交，校验 Session、Scope、Secret、CR、Fingerprint、Trailer 和受影响的轻量测试。
- `MODULE`：执行代理交付补丁前，运行其修改模块的单元、类型、契约或静态检查。
- `INTEGRATION`：每天一次或纵向切片汇合时，运行后端、Web、数据库、Android 和连续性全量集成。
- `RELEASE`：大版本开发交付阶段在 `obx-test` 固定工具链对冻结 Commit 运行一次正式 API、编译、单测、Lint、APK 构建、稳定签名与四方哈希；项目所有者真机验收保持异步且不得阻断下一版本。R14 起每个 Android 大版本机器完成前必须在 GitHub 固定模拟器运行一次该版本专属的真实交互候选，逐步执行点击、输入、长按、返回、重进和关键业务状态断言，并同时审核截图、目标进程日志和 JUnit 报告；只看截图不构成功能验收。候选失败阻断该版机器完成和生产自动证据，但不阻断独立合格的 `TEST_APK` 交付、项目所有者异步测试或下一版本独立开发。`TASK-xx-008` 不得在本机安装 Java/Android SDK 或重复已绑定同一源码 Commit 的重型门禁。
- 完整文档 Doctor 仅在其事实输入变化、手动 Doctor、集成或发布阶段运行；不得因此跳过任何连续性核心校验。
- GitHub 普通提交不得运行 Android 模拟器、截图、候选报告或候选 APK；`candidate=false` 只执行受影响的源码策略、正式 API、编译、单测、Lint 与打包。完整 Android 自动化仅由已验证的 `config/android-candidate-request.yaml` 或默认分支注册后的 `workflow_dispatch(candidate=true)` 触发。
- Continuity Gate 每次提交都必须执行 Commit/Session/Checkpoint/CR/Scope/Trailer 和严格 Doctor；只有连续性核心策略、脚本、Hook、运行时/传输描述或连续性工作流变化时，才运行临时 Git 仓库的完整交接、恢复、关闭和篡改生命周期演练。不得用跳过核心门禁换取速度。
- 候选请求、门禁分层和最近一次结果必须写入仓库及 Context Pack。换电脑、换 AI 时只允许从仓库恢复；聊天中的“已经测过”或“可以少跑”没有效力。
- R12 起每个大版本必须在最终候选完成后、机器关闭前执行一次六项全局漂移审计：开发文档、硬门禁执行、开发进度、可复用模式、Problem Registry、踩坑记录。审计证据必须绑定最终候选源码 Commit 并记录 SHA-256；发现漂移必须先修订原权威事实源和回归，不得带着漂移关闭版本或进入下一版。
- 机器关闭的视觉验收是累计门禁：除当前版本页面外，还必须验证截至当前版本的全部既有前端页面。后续 CR 将历史页面重新标为 `IN_REVIEW/BLOCKED` 时，旧 `PASS` 立即失效并阻断当前版本机器关闭；CR-0242 已记录的 R08 历史关闭事实不得追溯改写。

### 2.2 任务复杂度必须与流程相称

- 每项开发先运行 `python scripts/hhy_workflow.py plan --intent <意图>`，以 `config/development-workflow.yaml` 自动选择 `SIMPLE`、`STANDARD`、`CROSS_LAYER`、`TEST_APK` 或 `RELEASE_CLOSE`。
- 单模块、低风险、小范围Bug默认走 `SIMPLE`，只运行受影响模块回归；不得无理由运行全量集成、拆分多个CR、重建APK或执行版本关闭，禁止“用大炮打蚊子”。
- 数据库、冻结契约、安全、资金、生产基础设施、APK身份和版本关闭必须自动升级；升级输出必须列出具体触发路径，不得只用“为了保险”解释。
- `TEST_APK` 与 `RELEASE_CLOSE` 是不同状态。测试包通过固定环境质量、正式 API、稳定签名、身份和 SHA 门禁后即可交付并继续下一版本，但不得冒充自动候选、正式验收或生产关闭；R14 起机器完成还必须复用同一冻结 Commit 的版本专属 GitHub 真实交互候选 PASS，不得重复已经绑定同一源码 Commit 且 PASS 的重型门禁。
- 统一流程和例外规则以 `docs/09-development/统一开发与交付效率规范.md` 为事实源，聊天中的临时做法不得覆盖。

## 3. 变更控制

用户提出新增或强化规则时，必须先检索 `.continuity/CONTINUITY_POLICY.yaml`、现有事实源、CR、踩坑记录和经验复用记录。存在相同或相似规则时，只能修订原权威规则及其既有投影视图，禁止并列建立第二套硬规则；只有确认不存在等价规则并在 CR 中记录独立缺口后，才允许新增规则。

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

## 5.1 正式商业系统全局硬边界

- 开发前必须读取 `docs/00-baseline/正式商业系统全局硬性开发边界.md`。
- 正式前端不得展示请求编号、追踪编号、挑战编号、令牌、指纹、接口名、异常栈或其他技术字段；诊断信息只保留在受控日志和网络层。
- 页面必须按冻结效果图、页面规格和 Design Token 的字号、行高、间距、宽高、圆角、颜色、阴影、遮罩、动画与状态逐项施工；不允许凭经验简化。
- 效果图是页面建模、区域顺序、信息层级、布局、对齐、组件形态和视觉样式的强制来源，不是可自由发挥的灵感图；业务文档是功能、字段、动作和正式文案的唯一来源。效果图中的虚拟功能、无关文字和占位数据必须过滤，但过滤不得被用来重构其余布局。
- 每个页面必须在 `catalogs/ui_visual_acceptance.csv` 绑定精确面板或批准的补充视觉规格。`P01-P08`、`TOKENS_ONLY`、相邻页面参考等粗粒度绑定不得作为施工依据；无精确覆盖时必须先补视觉规格，禁止自行构思 UI。
- 页面或版本只有在视觉合同、Token 静态检查、参考图对照和真机/浏览器截图证据全部为 `PASS` 后才可关闭；单版检查使用 `scripts/check_ui_visual_acceptance.py --release <版本>`，R12 起机器关闭必须执行累计 `--historical-through <版本>` 语义。
- 每个版本必须逐项核对开发文档和 Acceptance；任何遗漏都阻止版本关闭。
- 每个大版本关闭前必须把正式 APK 和完整“功能清单 + 文档核对 + 详细测试 + 反馈方式”TXT/MD 成对放到项目所有者桌面；R14 起两者必须绑定同一 Release 和源码 Commit，测试说明使用 `hhy-<release>-<commit7>-test-guide.md` 规范名，并由 Manifest、Evidence、仓库源文件与桌面副本的路径、大小和 SHA-256 硬门禁证明，任一缺失或漂移均不得标记交付 PASS。
- 万能邀请码等测试能力只允许在 DEV/TEST/STAGING 显式启用，PROD 必须硬拒绝。
- 全项目基础能力必须优先使用大型商业 App 广泛验证的官方 Stable 主流方案；禁止自行构思导航框架、返回栈、图标系统或引入预发布/小众基础库，例外必须 ADR + 批准 CR。
- Android 全部既有和后续页面统一使用稳定 Jetpack Navigation Compose 真实返回栈；顶栏、系统键、手势返回必须同源并返回实际来源，底部栏目和页面状态必须保存恢复，禁止 `mutableState`/枚举切页和写死返回首页。
- Android 图标必须从 `HhyIcons` 引用矢量资源，页面转场必须从 `HhyMotion` 引用冻结 Token；任何文字、汉字、Unicode、Emoji占位图标或页面私有动画数字阻断提交与APK。
- Android 改动必须通过 `python scripts/check_android_ui_foundation.py`；该门禁同时检查所有已开发页面，不得只检查当前版本新增页。
- R06 起每个 Android 大版本必须交付 `TEST_APK`：冻结 Commit 在 `obx-test` 固定工具链通过正式 API、编译、单测、Lint、打包后，继续通过稳定测试签名、版本身份、四方 SHA 和桌面说明门禁；项目所有者按自己的时间真机测试，`PENDING` 不得阻断下一版本。GitHub 模拟器不是 `TEST_APK` 或继续开发的前置条件，但 R14 起它是对应大版本机器完成的前置条件。
- R14 起版本专属交互候选失败时，当前或接续 AI 必须读取 Actions 的逐步交互、JUnit、截图和日志证据并记录问题；最小修复后按准确递增轮次重测，同一根因最多三轮且禁止无新证据重跑。失败不阻断固定环境 APK 交付或下一版本独立开发，但该版在 PASS 前不得标记机器完成或冒充候选；真实外部秘密/权限/商业决策才可升级。
- Android 自动化唯一事实源为 `config/android-automation.yaml` 和 `docs/08-testing/Android自动开发测试修复交付体系_V1.0.md`；跨电脑、跨 AI 接手不得绕过或另起一次性流程。
- 开发分支尚未在 GitHub 默认分支注册候选工作流时，必须通过 `.github/workflows/android-candidate-request.yml` 监听机器可读候选请求，并调用同一 `.github/workflows/android-quality-gate.yml`；禁止人工复制普通 CI 中间 APK 冒充候选，也禁止为了触发候选把未审查变更直接推入默认分支。

## 6. 会话结束

完成实现 Commit 后执行 `continuity.py close`，生成关闭检查点和关闭元数据 Commit；更新 Release、Task、Changelog、Context Pack、CURRENT_STATUS 和 NEXT_TASK。未形成可验证仓库记录的工作不算完成。
