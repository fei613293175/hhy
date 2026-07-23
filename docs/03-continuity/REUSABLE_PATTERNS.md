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
APK绑定Commit、versionName、versionCode、签名指纹、SHA256、测试报告和下载地址。

## PATTERN-CONTINUITY-001 无状态接续
CURRENT_STATUS + NEXT_TASK + Release Manifest + Context Pack + Session Log构成AI接手上下文。

## PATTERN-CONTINUITY-002 外部门禁不扩大为全项目停工
APK机器交付完成但项目所有者真机验收尚未返回时，原任务保持BLOCKED且绝不标记DONE。只有项目所有者明确要求继续、目标Release从首个READY任务开始、并且其声明依赖全部DONE时，才使用受控blocked-and-advance转换继续独立依赖图分支；NEXT_TASK必须携带deferred_task及恢复条件。

## PATTERN-CONTINUITY-003 BLOCKED任务精确接续

关闭会话时若为同一任务写入了标准 `resume_command`，后续只可在 Release 任务与 `NEXT_TASK` 均为该任务的 `BLOCKED`、命令逐字匹配且当前无活动会话时恢复。普通 `BLOCKED` 任务仍拒绝启动；恢复只表示继续解决或补证，不得改变验收、APK或任务完成状态。

## PATTERN-EXTERNAL-DESIGN-001 非阻断设计回传

外部设计尚未回传时，把确定的源包、源提交、回传清单和接入步骤登记进仓库，但不得凭空实现最终视觉，也不得阻断无依赖的版本工作。回包先经过只读结构、占位符、效果图、哈希、参考资料不变性和敏感文件门禁，再通过独立 CR 合入、实现、测试并重新交付 APK。

## PATTERN-CLIENT-FOUNDATION-001 成熟客户端统一基础设施

Android 以稳定 Jetpack Navigation Compose 维护真实来源返回栈，顶栏、系统键和手势共用 `popBackStack`，底部栏目用保存/恢复状态保留来源；所有图标经 `HhyIcons` 语义注册，转场经 `HhyMotion` 与冻结 Token。旧页面和新页面运行同一静态门禁，禁止字符图标、自研返回栈、写死返回目标和页面私有动画数字。

## PATTERN-ANDROID-REMOTE-001 固定容器受影响模块验证

普通 Android 开发任务只在既有 `obx-test` 固定镜像中运行受影响 MODULE。容器统一使用 `hhy-r01-android-gradle-cache:/root/.gradle`，以精确 Commit 的干净克隆作为源码输入；禁止把宿主 `/root/.gradle` 当成等价缓存，也不得为单次验证重建镜像。启动前由单次 SSH 预检固定镜像、缓存、持久 Swap、可用内存和 `/var/lock/hhy-android-build.lock`，构建必须串行持锁并使用 CPU/内存/PID 上限、Gradle 单 worker 与受控 JVM 堆，退出后 `--rm` 清理临时容器。遇到稳定 API 36 自动安装或短时无输出，先联合检查容器运行状态、CPU 与增量日志；确认进程真正退出或无活动后才诊断失败，避免重复容器、重复下载和无意义重跑。完整打包、模拟器、截图及候选 APK 仍只在大版本最终候选阶段执行。
