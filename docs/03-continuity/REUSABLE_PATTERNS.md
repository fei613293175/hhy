# 可复用模式

## PATTERN-CONTRACT-001 契约先行
OpenAPI、WebSocket和数据库字典先冻结，前端生成/校验DTO，后端实现不得先于契约。

## PATTERN-CONFIG-001 供应商配置版本
Secret不可读回，连接测试后双人激活，历史版本可回滚，业务保存规则快照。

## PATTERN-STORAGE-001 Scope化对象存储
业务只使用Storage Scope，R2/OSS由绑定配置决定，支持SHA校验和迁移回滚。

## PATTERN-LEDGER-001 不可变账本
余额由流水汇总，任何修正产生冲正记录，不允许管理员直接修改金额字段。

## PATTERN-APK-001 可追溯APK
APK绑定Commit、versionName、versionCode、签名指纹、SHA256、测试报告和下载地址。历史版本回归必须读取该版本已归档的候选报告、构建证据和截图哈希验证不可变身份；当前构建身份只验证严格大于历史versionCode，不得把全局当前构建配置、候选请求或共享模拟器旅程永久锁死为旧Release。候选后端公网命中证明必须以成功响应头中的后端实际X-Request-Id关联目标容器结构化日志，客户端发送号只作诊断，不得假设Cloudflare等代理会原样透传；Nginx reload后只允许在固定小窗口以全新请求号重试等待旧worker排空，超时仍须回滚。

## PATTERN-CONTINUITY-001 无状态接续
CURRENT_STATUS + NEXT_TASK + Release Manifest + Context Pack + Session Log构成AI接手上下文。总执行计划必须由CURRENT_STATUS的活动版本确定性生成，不能由生成器和检查器共享硬编码旧窗口。只承担导航作用的文档指针也必须由严格文档门禁验证目标存在且与唯一事实源文件名完全一致，不能只校验主文档本身。普通CI的影响映射必须把历史候选测试、视觉归档、视觉验收台账与合同、严格Doctor和治理知识源精确关联到tooling；本地PASS不能替代远端Job被正确选择，相关Job显示skipped即视为覆盖缺口。R12起每个大版本最终候选后复用同一Doctor、累计视觉目录和六项治理审计，报告绑定候选Commit与SHA后才允许机器关闭和进入下一版。后续版本满足历史债务的延迟恢复条件时，必须在同一CR中以不可变Run、Commit、证据SHA和批准记录原子同步历史台账、Problem Registry与回归测试，避免证据已完成但治理状态继续漂移。

## PATTERN-CONTINUITY-002 外部门禁不扩大为全项目停工
APK机器交付完成但项目所有者真机验收尚未返回时，原任务保持BLOCKED且绝不标记DONE。只有项目所有者明确要求继续、目标Release从首个READY任务开始、并且其声明依赖全部DONE时，才使用受控blocked-and-advance转换继续独立依赖图分支；NEXT_TASK必须携带deferred_task及恢复条件。

## PATTERN-CONTINUITY-003 BLOCKED任务精确接续

关闭会话时若为同一任务写入了标准 `resume_command`，后续只可在 Release 任务与 `NEXT_TASK` 均为该任务的 `BLOCKED`、命令逐字匹配且当前无活动会话时恢复。普通 `BLOCKED` 任务仍拒绝启动；恢复只表示继续解决或补证，不得改变验收、APK或任务完成状态。

## PATTERN-EXTERNAL-DESIGN-001 非阻断设计回传

外部设计尚未回传时，把确定的源包、源提交、回传清单和接入步骤登记进仓库，但不得凭空实现最终视觉，也不得阻断无依赖的版本工作。回包先经过只读结构、占位符、效果图、哈希、参考资料不变性和敏感文件门禁，再通过独立 CR 合入、实现、测试并重新交付 APK。

## PATTERN-CLIENT-FOUNDATION-001 成熟客户端统一基础设施

Android 以稳定 Jetpack Navigation Compose 维护真实来源返回栈，顶栏、系统键和手势共用 `popBackStack`，底部栏目用保存/恢复状态保留来源；所有图标经 `HhyIcons` 语义注册，转场经 `HhyMotion` 与冻结 Token。旧页面和新页面运行同一静态门禁，禁止字符图标、自研返回栈、写死返回目标和页面私有动画数字。

## PATTERN-ANDROID-REMOTE-001 固定容器受影响模块验证

候选涉及 `AsyncImage` 等异步真实媒体时，组件必须按业务实体暴露 `loading/loaded/error` 语义；跨Compose与UiAutomator边界时不能只依赖`testTag`，还必须把三种终态映射为业务可读的动态content description。旅程分别计数成功、失败和加载中，先等待全部预期媒体 `loaded`，任一 `error` 或超时立即失败并输出三类数量，再执行像素稳定采样。固定尺寸 Lazy 列表中的图片请求必须显式使用与容器一致的确定像素尺寸，使已预组合但尚未测量的节点也能启动请求；出现部分成功、零失败、其余持续加载时，先检查约束尺寸解析，禁止靠增加等待、固定sleep或减少expectedCount放行。固定等待和连续像素相同都不能替代业务加载完成，视觉拒绝的同 Run 产物不得晋升、重跑或交付。

普通 Android 开发任务只在既有 `obx-test` 固定镜像中运行受影响 MODULE。容器统一使用 `hhy-r01-android-gradle-cache:/root/.gradle` 和 `hhy-android-sdk-platform-36:/opt/android-sdk/platforms/android-36`，以精确 Commit 的干净克隆作为源码输入；禁止把宿主 `/root/.gradle` 当成等价缓存、用命名卷覆盖整个 Android SDK，或为单次验证重建镜像。启动前由单次 SSH 预检固定镜像、Gradle 缓存、API 36 平台卷及 `android.jar`、持久 Swap、可用内存和 `/var/lock/hhy-android-build.lock`，构建必须串行持锁并使用 CPU/内存/PID 上限、Gradle 单 worker 与受控 JVM 堆，退出后 `--rm` 清理临时容器。平台卷缺失时只用后台受限容器预填 `platforms;android-36`；确认 `android.jar` 存在后再更新包装器，并以模块编译日志不含 `Install Android SDK Platform 36` 作为复用证据。候选后端滚动切流必须复用受控可回滚脚本，以目标容器本机200、Nginx精确upstream、`nginx -t`、公网唯一requestId命中目标容器四类证据形成同一原子结果；不得把新容器健康与旧容器返回的公网200拼成假通过。GitHub的OIDC与Staging bootstrap前置门禁必须先于Android重型环境准备。候选治理文件走`android-governance-unit` tooling，真实客户端源码、共享合同或领域类型才走`android-module`，避免规则提交重复编译APK。完整打包、模拟器、截图及候选 APK 仍只在大版本最终候选阶段执行。CR-0358之后，AI依据项目所有者站立授权自行创建和审批精确的连续候选CR，不再向项目所有者逐轮提问；仍必须绑定已消费上一请求、独立根因、修复Commit、全局唯一request ID和单次运行上限，并按每个Release独立从attempt4连续编号，禁止预授权未知未来轮次。Compose关键候选动作优先暴露稳定资源标识；必须按文字定位时只点击首个enabled且clickable祖先，不能直接点击Text语义子节点。候选视觉采样必须等待至少四次连续相同像素，并由AI逐图核对系统安全区、顶栏、返回键、退出层残影、业务时间和品牌底按钮对比度；HhyMotion退出页完全离开视口，TopAppBar交由WindowInsets确定总高度，技术SUCCESS但视觉拒绝的同Run产物不得晋升或交付。
