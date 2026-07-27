# 已知陷阱

1. 不得把效果图当功能文档。
2. B01 的第三方登录不实现。
3. B09 的充值和商豆不实现。
4. B11 的多会员身份不实现。
5. 不得重新采用旧 V2 页面编号和业务模型。
6. “效果图不是功能事实源”不等于“效果图可以自由发挥”；页面建模、布局、信息层级、组件形态和视觉样式必须按精确面板还原。
7. 不得使用 `P01-P08`、`TOKENS_ONLY` 或相邻页面参考直接施工；无精确视觉覆盖必须先补批准规格。
8. 过滤效果图中的虚拟功能、无关文字和占位数据时，不得借机重构其余布局；过滤/替换必须写入逐页视觉合同。
9. 框架默认字号、按钮高度、输入框高度和圆角不能替代冻结 Design Token。
10. APK只验证`api.orbexa.cc`平台状态不够；每个版本必须以真实登录态黑盒验证本版新增核心接口，避免客户端版本已前进而公网后端仍停留在旧Release。
11. APK `versionCode` 递增必须原子同步构建配置、应用内发布策略、版本一致性单测和CHANGELOG，不能只改一个入口后再依赖构建失败补漏。
12. 不得使用汉字、Unicode、Emoji或字体字符充当图标；全部客户端图标必须进入各端统一语义图标库。
13. 不得以`mutableState`、枚举切页或写死目标代替真实导航栈；返回必须回到实际来源，并保持底部栏目和页面状态。
14. 不得为基础能力自行构思小众方案；优先官方Stable和大型商业App广泛采用的成熟架构，例外必须ADR与批准CR。
15. PASSWORD/SMS等同级模式只能使用无方向peer motion；REGISTER/RESET等下级页必须进入forward、返回pop backward并恢复真实登录模式，禁止写死PASSWORD。
16. 实名授权说明可读取不代表运行策略或供应商已经激活；APK交付前必须分别验证协议、会话创建、活体令牌与结果查询，不能把创建会话500误归因于第三方Key。
17. 外部实名供应商尚未配置时，真机闭环只能使用默认关闭且与production硬隔离的Staging供应商沙箱；不得写假ACTIVE配置、不得在Android本地伪造通过，也不得把沙箱结果当正式实名认证。
18. 第三方活体H5是供应商完整页面，不得嵌入240dp取景框或在重组时重载初始URL；固定取景区只承载准备、授权、加载、处理中和失败状态，供应商页必须由独立全屏WebView路由承载。实名入口必须先读取服务端总览并恢复活动会话，不能依靠客户端缓存猜测未认证状态。
19. Windows 上脚本能回退到 Codex 捆绑 Git，不代表裸 `git` 或第三方子进程已经具备 PATH。首次接手必须运行 `scripts/configure_windows_git_runtime.ps1` 并用 `-Check` 回读，持久化用户级 `HHY_GIT_BIN` 与 Git `cmd`；旧长驻进程未刷新时使用显式变量继续并择机重启一次，禁止每轮开发重复定位、临时拼 PATH、误报仓库损坏或重建仓库。
20. 新大版本首次候选没有已批准视觉基线时，禁止让同一套完整编译与模拟器因为“基线不存在”确定性失败后再原样跑第二次。唯一允许流程是：一次权威模拟器采集必须先通过仪器、JUnit、日志、页面身份、像素稳定、截图唯一性和跨页差异，仅保留 `BASELINE_REVIEW_REQUIRED`；AI逐图批准原始截图与 SHA-256 后，由轻量 Actions 下载同一 Run 的 APK、报告和截图完成哈希绑定与晋升，晋升阶段禁止重新编译、重新打包或启动模拟器。已有基线的后续候选仍必须在模拟器运行中直接完成像素比较。候选若连续暴露不同根因，不得永久提高全局轮次、重置为attempt1或复用已消费请求；基础设施修复本身也不构成新候选授权。CR-0358 已记录项目所有者对 AI 候选持续决策的站立授权；后续不再逐轮询问项目所有者，但只有上一请求已消费、独立根因已有代码/模块/普通CI证据、AI另建CR并精确绑定Release/连续轮次/唯一request ID/修复Commit/单次运行上限时，独立AI候选授权角色才可批准一次新例外，未知未来轮次不得提前授权。候选容器健康与公网状态200不得拆开自证切流成功；必须由受控脚本备份并精确切换Nginx upstream，保留客户端发送请求号但必须读取公网响应头中的后端实际X-Request-Id，以响应号证明请求进入目标容器日志，缺失或格式非法立即回滚；不得假设Cloudflare等代理会原样保留客户端请求号。Nginx reload后的旧worker排空只允许在固定10次小窗口内使用全新请求号重试，仍未命中目标日志必须回滚，禁止把旧upstream返回的200当作候选通过。OIDC和Staging bootstrap必须早于Java、Android SDK与KVM重型准备并分阶段报告安全HTTP状态；同一Commit一次重跑仍失败时禁止第三次点击重跑。普通提交只改候选YAML、策略、Shell或Python门禁时必须由tooling专项验证，不得触发完整Android编译；只有真实客户端源码、共享合同或领域类型受影响才进入android-module。新增秘密、第三方权限、资金/账本策略和生产激活仍必须由项目所有者决定。
    异步媒体补充：页面声明存在真实 READY 媒体时，候选组件必须按业务实体暴露 `loading/loaded/error` 语义，旅程等待全部预期媒体 `loaded` 后才允许像素稳定采样；任一 `error` 或超时立即失败。固定尺寸的 Lazy 列表媒体还必须给图片请求提供与容器一致的确定像素尺寸，但显式尺寸不能替代列表项进入活动视口：候选若能观察到预组合节点却持续部分success、零error、其余loading，必须在同一总期限内受控滚动激活预期媒体，全部成功后回到冻结截图起点再次复验。Compose LazyColumn不得假设自动向UiAutomator暴露scrollable属性；跨框架通用手势即使被执行也不能证明指定条目进入活动视口。存在唯一业务标题时，应使用Compose UI测试的`performScrollTo`精确命中Lazy条目，再由UiAutomator独立复验业务三态；禁止继续用裸`UiDevice.swipe`或无目标结构化滚动重复猜测激活。禁止通过延长超时、固定等待、全屏固定坐标、减少预期数量或改变截图位置掩盖该状态。固定等待和连续像素相同不能替代业务加载完成，占位图不得批准为真实媒体页面的首版基线。
21. 异步真机反馈可能跨越多个大版本，跨Release依赖不能只看历史 `TASKS.yaml` 是否全为 `DONE`。只有当历史Release Manifest同时证明机器交付PASS、Owner真机PENDING、正式验收与生产激活仍阻断、后续开发ALLOWED时，才可把其最后一个 `BLOCKED` 外部门禁任务视为开发依赖GREEN；任何更早未完成任务、缺失Manifest事实或生产已放行的异常组合仍必须阻断。该豁免只允许继续开发，不得改写历史真机状态。
22. Context Pack等跨电脑清单的来源哈希必须由生成端和校验端共同使用 `portable_source_record` 的规范化换行语义；不得一端记录LF规范化SHA、另一端比较Windows CRLF原始字节SHA。修复时必须保留真实内容变化负向阻断，禁止靠批量改写CSV换行掩盖算法不一致。
23. 固定 Android 服务器容器验证必须把既有命名卷 `hhy-r01-android-gradle-cache` 挂载到 `/root/.gradle`，不得改用宿主 `/root/.gradle`；后者不会复用项目固定缓存，会造成依赖重复下载和耗时漂移。稳定 API 36 必须复用 `hhy-android-sdk-platform-36:/opt/android-sdk/platforms/android-36`，只允许挂载该平台目录，禁止用空卷覆盖整个 `/opt/android-sdk`；否则短生命周期容器会每轮重复下载约 146 MB 平台包并额外消耗 1 至 2 分钟。所有 Android 构建必须先获取服务器唯一锁 `/var/lock/hhy-android-build.lock`，容器必须限制 CPU、内存和 PID，Gradle 固定单 worker 与受控 JVM 堆，结束后自动删除临时容器；禁止多个 AI、Story 或门禁并行争抢同一服务器。平台缓存缺失或首次预填时必须使用后台受限容器并检查容器状态、CPU 和增量日志；只要仍有安装或编译活动，就不得误判卡死、并行重启验证或重建固定镜像。服务器预检必须同时验证固定镜像、Gradle 缓存、API 36 平台卷及 `android.jar`、持久 Swap、最低可用内存和构建锁；SSH banner 超时时先区分未认证槽限流与资源饱和，禁止盲目提高 `MaxStartups` 或并发重连。
24. 项目所有者授权使用 PowerShell/终端不等于允许 AI 把确认动作转交给项目所有者。未复用已批准前缀的 `ProcessStartInfo`、Git Credential Manager、复合复制/解压命令都可能让 Codex 客户端弹出“允许运行此命令”；执行前必须选择 `hhy_workflow.py git`、受控运行时、已批准 SSH、工作区编辑或等价静默通道。发生一次弹窗后不得换一种相似命令继续试探；客户端授权机制不是项目外部阻断。
25. “效果图不得新增虚构功能”不能解释成删除主开发文档明确功能。开发任何页面必须先逐项核对主开发文档章节、需求、页面合同和版本Story，再用精确效果图决定这些真实功能的布局；补充视觉规格不得把Banner、分类入口、内容模块等合同能力替换为自创Hero或快捷入口。聚合页在后续分类版本完成时必须同步回接真实入口、卡片和导航，视觉验收必须对照上游文档与效果图，禁止拿实现自身生成的基线循环自证。
26. 首页模型和Compose组件存在不等于首页功能完成。必须实时核验目标环境`home_modules`不为空、模块排期有效、dataSource只读取ONLINE真实内容、同请求contentId去重、moreTarget可达、媒体HTTPS返回200，并用真实登录态读取`GET /api/v1/home`；Staging夹具必须显式拒绝非STAGING且幂等，未来版本模块不得提前写假数据占位。
27. `scp` 出现 `Connection reset` 或 `Broken pipe` 时不得先归因于带宽、APK损坏或密钥失效。先读取 `sshd -T`、最近45分钟 `sshd` 日志、Fail2ban和防火墙规则；公网扫描可能在默认 `LoginGraceTime 120` 内占满 `MaxStartups 10:30:100` 的未认证连接槽，使正常自动化被随机丢弃。只有日志证明该根因时，才可在备份 `/etc/ssh/sshd_config`、`sshd -t` 和保持现有会话可回滚的前提下把 `LoginGraceTime` 调为 `30`、`MaxStartups` 调为 `30:50:100`，reload后必须由新连接回读并完成至少12次连续连接验证。不得借机关闭密码/root登录或修改端口；APK仍必须由 `deliver_android_test_apk.py` 的分块重试、远端合并SHA和四方交付门禁负责。
28. 生成器与检查器使用同一个硬编码旧版本，会让过期滚动计划稳定地产生假PASS；只检查当前Release页面，也会让后续CR重开的历史视觉债务绕过关闭。滚动窗口必须与CURRENT_STATUS交叉验证，R12起机器关闭必须累计检查截至当前版本的全部页面，并要求六项治理审计绑定最终候选Commit和证据SHA。Context Pack哈希只能证明规则文件未被漏读，不能替代Problem Registry闭环字段、复用模式唯一编号和踩坑连续编号的结构校验。后续候选已经满足历史债务恢复条件时，历史台账、问题状态和回归断言必须在同一CR中绑定Run、Commit、证据SHA与批准记录同步更新；证据已完成但状态长期停留在IN_REVIEW/REMEDIATING同样属于漂移。
29. 主文档本身存在且文档合同PASS，不代表面向新AI的定位指针仍有效。任何只承担导航作用的Markdown指针都必须在既有严格文档门禁中同时校验指针存在、目标存在和唯一文件名一致；重命名权威文件时必须原位更新指针与回归，禁止留下指向旧文件的“第二入口”。
30. 历史候选回归不得断言全局当前`versionCode`、Release、候选请求或共享模拟器旅程永远等于旧版本；旧版本身份、页面集合和截图哈希必须从已归档候选报告与构建证据读取，当前身份只做严格单调递增校验。Linux CI通过`./gradlew`或`./mvnw`执行的Wrapper必须以Git `100755`模式提交，并由严格Doctor机检；不得依赖Windows本机可运行、CI临时`chmod`或失败后人工补救掩盖索引模式漂移。候选历史测试、视觉归档、严格Doctor或治理知识源发生变化时，普通CI必须由既有影响映射选择tooling；本地全量PASS而远端Job为skipped仍是假绿，必须修复映射而不是改写验收为“无需运行”。
31. 候选工作流SUCCESS和两次相同像素不能替代AI逐图验收。自动处理WindowInsets的TopAppBar不得再设置只覆盖内容区的固定总高度；HhyMotion退出层必须完全离开视口，不能只移动八分之一或四分之一后依赖透明度隐藏；候选截图至少四次连续相同才可采信，并继续核对状态栏、顶栏、返回键和前页残层。服务端ISO时间必须转换为业务时区和商业格式，品牌蓝底上的轮廓按钮必须使用反色文字、图标和边框。任何一张真实截图不合格时，同Run APK不得晋升或交付，候选轮次仍保持已消费。
32. Compose自动化按文字找到的通常是Text语义子节点，不保证该节点本身可点击；`testTag`导出的UiAutomator资源节点同样不保证自身承载Compose click action。候选关键按钮必须使用稳定资源标识，并从命中的资源节点向上选择首个enabled且clickable祖先；文字回退执行同一祖先规则，禁止直接点击语义子节点。点击后只等待`content`的布尔失败不能证明是点击、API还是页面状态根因；存在`loading/content/empty/error`等稳定导出phase时，必须先短窗口观察任一目标phase，只有未出现且源页仍在才重新解析当前祖先并重试一次，随后在原总期限内只放行`content`，失败输出phase、源页、资源节点和祖先诊断。异步媒体候选状态不得由`AsyncImage onSuccess/onError`复制到独立`mutableState`，同URL并发项可能出现回调镜像与真实painter状态漂移；必须由各自`rememberAsyncImagePainter`的`AsyncImagePainter.State`直接派生语义。`approved_attempt_exceptions`的连续性必须按每个Release独立从attempt4计算，不能让R12历史attempt4至7把R13首个例外推成attempt8；但CR、request ID、修复Commit仍须全局唯一，单个Release内仍禁止跳号、倒序、重复或预授权下一未知轮次。
33. Compose `testTag`适合Compose测试，不保证嵌套图片节点会以同名resource-id稳定进入UiAutomator无障碍树。跨框架候选若必须等待异步媒体终态，组件还要暴露业务可读且随`loading/loaded/error`变化的content description；UiAutomator分别计数成功、失败和加载中，失败立即拒绝，超时输出三类数量。公共媒体HTTP 200且logcat无网络异常时，不得靠延长等待掩盖选择器不可观测。
