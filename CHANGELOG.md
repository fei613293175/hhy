# CHANGELOG

## R07 搜索、发布者与联系方式可观测性 · 2026-07-22

- R07候选隔离夹具使用事务级临时用户上下文传递CI身份，避免在PostgreSQL匿名代码块内误用客户端变量；失败保持零写入，输出继续禁止手机号、联系方式和秘密。
- 新增搜索历史量、有效热词量、活跃发布者量、近 5 分钟联系方式访问/拒绝量和 R07 Outbox 积压六项只读业务 Gauge，不读取搜索关键词、联系方式明文或密文。
- 建立 R07 独立 Compose、Prometheus、Alertmanager、审计接收器、静态门禁及精确 Commit 现场验收脚本；测试 Outbox 事实只按不可变状态机终结，不删除审计记录。
- R07 预发布只验证结构化日志、TraceId、RED、业务告警和同库卷应用镜像回切；模拟器、截图和候选 APK 继续留在 TASK-R07-007 最终候选阶段。

## R07 搜索、发布者与联系方式专项测试 · 2026-07-22

- 将 TASK-R07-005 的旧“9项”描述校正为发布清单冻结的10项权威测试，所有 Catalog 条目升级为 `AUTOMATED` 并绑定真实 Java/Kotlin 方法和中央证据矩阵。
- 新增搜索、发布者公开资料、联系方式高敏访问及清空历史确认的主路径、拒绝、重复、并发、超时、幂等冲突、Outbox 去重和明文清除故障注入；正式 PASS 必须绑定冻结 Commit 的 Java 21、PostgreSQL 17 和 Android Gradle JDK 21 日志及 SHA-256。
- Windows 受控 Git 由临时 PATH 注入升级为用户级一次性配置：仓库脚本可幂等安装和检查 `HHY_GIT_BIN`/PATH，换电脑按 `START_HERE` 自动恢复；旧长驻进程未刷新时继续走统一入口，不再重复排查或误判仓库。

## R07 Android 搜索、发布者主页与联系方式闭环 · 2026-07-22

- Android 新增全局搜索、搜索结果、发布者主页、联系方式面板和清空搜索历史确认五个冻结交互面，绑定 R07 七项唯一合同接口，并从已认证首页提供真实搜索入口。
- 搜索支持热词、账号隔离历史、内容类型筛选、空/错误/离线/权限恢复和分页；结果中的发布者可进入公开主页，主页仅展示最小公开资料及其在线内容。
- 联系方式访问保持同业务意图稳定幂等键，授权明文只存在当前底部面板内存，面板开启期间启用系统防截屏，关闭立即清除；复制必须由用户显式触发且明文不进入日志、埋点、持久化或保存状态。
- H5 与后台在 R07 没有登记页面，按事实源明确 N/A，未新增未冻结字段、按钮或路由。

## R07 搜索、发布者主页与联系方式后端 · 2026-07-22

- R07 后端已实现并冻结 7 个故事接口：全局搜索、热词、搜索历史读取/清空、发布者公开资料、指定发布者的在线内容列表和联系方式访问；客户端页面仍由后续 R07 任务落地。
- 搜索只返回在线内容，历史记录按登录用户隔离并去重；发布者主页只披露公开资料，内容列表强制 `ONLINE` 且支持 `publisherId` 筛选。
- 联系方式访问改为版本化 AES-GCM 安全信封，明文仅在 `no-store` 响应中短时返回，不进入日志、Outbox、幂等引用或客户端持久缓存；查看、复制、拒绝和幂等重放均记录审计。
- V032 将历史联系方式引用无损迁移为可容纳安全信封的文本字段；回滚遇到新密文会原子阻断，空库、升级库、回滚、重放和真实 PostgreSQL 17 存储集成均已验证。

## R06 客户端与内容运营首切片 · 2026-07-20

- R06 五页视觉合同已补齐并全部通过：Android 首页/关于绑定批准补充规格与最终候选截图，管理端内容列表/详情/字典绑定标准模板并生成固定 1440×1100 浏览器截图；截图审查发现并修复原始内容枚举、分类/地区代码、ISO 时间泄漏及详情排版缺口，新增 3 项页面回归后管理端累计 86 项测试通过。
- Release 关闭门禁移除“固定只能有3个operationId”和“APK候选Commit必须等于后续文档关闭Commit”的错误假设，并在Windows解析受控Git路径；R06共10个冻结operationId全部按OpenAPI校验。项目所有者真机反馈继续保持异步PENDING，只阻断正式验收/生产激活，不阻断R07开发。
- R06 最终测试 APK 版本身份递增为 `versionCode 10214`，同步构建配置、应用内发布策略和版本一致性单测；旧 `10213` 候选仅保留视觉与旅程通过证据，不得覆盖 R05 已验收产物或作为 R06 最终交付。
- 新增 R06 内容在线量、待审核量、内容 Outbox 积压和首页启用模块四项只读业务 Gauge；建立独立 Compose/Prometheus/Alertmanager 告警链、静态门禁及同库卷应用回切手册，生产路由、数据库结构、实名认证沙箱和 CI 自动登录均不变。
- R06 隔离 Staging 已在精确 Commit 完成 TraceId/RED/四项业务 Gauge、后端与内容 Outbox 告警 firing/resolved 以及同一 PostgreSQL 容器和卷的应用回切；测试 Outbox 事实按不可变状态机终结并永久保留，禁止删除。
- AI 拒绝将首轮真实页面截图直接登记为基线：修复首页成功空数组时未显示空模块降级卡，以及“我的”页底部五栏目只绘制首尾项的视觉漂移；五栏目现在显式等权、始终显示标签，并在截图前逐项断言可见。
- 修复 GitHub 候选一次性引导码在模拟器冷启动与测试 APK 编译完成前确定性过期：引导码改为 10 分钟硬上限，仍精确绑定仓库、工作流、Commit 与 Run 并单次原子消费；兑换会话保持 15 分钟，生产环境继续硬拒绝。
- Android 候选门禁改为按版本影响清单采集真实业务页面，R06 使用 GitHub OIDC 一次性 Staging 会话进入首页、我的和关于页；AI 按冻结设计、页面身份、加载状态、像素稳定与截图哈希自主验收，桌面 APK 的延后真机反馈不再阻断后续版本编码。
- 修复 Android 候选自动截图在页面过渡期间过早采集、导致密码登录与忘记密码页面错位的问题；新门禁以页面身份、真实加载和连续稳定像素绑定截图，禁止用屏幕外控件或仅语义节点判定页面稳定，视觉基线由 AI 按冻结规格和截图哈希自主验收。
- 新增管理端统一内容列表、内容详情、字典与属性页面，绑定冻结内容 operationId、权限、expectedVersion、幂等写操作及加载/空/离线/无权限/404/冲突恢复状态。
- Android 首页接入 `homeGetHome` 模块化读取并保留局部失败；“我的”新增关于与检查更新真实导航入口，接入版本检查与协议读取。
- 关于页协议读取绑定冻结代码 `PRIVACY_POLICY`，更新按钮仅在服务端返回安全 HTTPS 下载地址时启用，并由单元测试覆盖非法 URL 拒绝。

## R05 单一实名认证首切片 · 2026-07-20

- 修复模拟器测试结束后应用专属外部目录随卸载被清理、导致页面截图收集为零的问题：UIAutomator先在应用缓存生成截图，再通过Android官方MediaStore发布到系统Pictures专用测试目录，主机在测试完成后统一拉取，测试前会清空该专用目录，避免旧截图误判通过；JUnit失败断言同时进入GitHub Annotation，后续可直接定位而无需二次下载日志。
- 建立 R06-R32 长期 Android 候选自动门禁：GitHub Actions 固定执行编译、Lint、单测、APK/测试 APK 打包、Pixel 7 模拟器安装、登录注册旅程、页面截图、视觉基线比较、logcat/崩溃/ANR 检查及候选报告；任何失败进入可跨电脑、跨 AI 接续的有界修复队列，全部 PASS 前禁止标记完成或要求项目所有者真机测试，最终候选才允许复制到桌面并进行一次真机终验。
- 首轮云端自修复把 Android 编译目标从预览 API 37 收敛到正式稳定 API 36，并补齐 Linux 中文路径、Git Hook 可执行位和第三方原始证据逐字节冻结规则；自动门禁必须在干净 Linux 克隆中复现通过，不能再依赖某台 Windows 电脑的换行或文件模式。
- 第二轮云端自修复补齐 Android Gradle Wrapper 的 Linux 执行位，工具与契约任务显式固定 Node 24，并在成功或失败时上传完整诊断日志；后续 Runner 环境差异可直接定位，不再重复环境摸底。
- 第三轮云端自修复确认 Emulator Runner 会逐行创建独立 `sh` 进程，原内嵌多行脚本因此丢失目录、变量和续行并退出 127；现改为调用仓库内单一可执行 Bash 门禁脚本，补齐模拟器作业稳定 SDK，并把关键失败摘要同步为 GitHub Annotation 供后续 AI 无鉴权接续定位。
- 自动化系统自测补齐两项跨平台边界：Android工作流、策略、脚本、测试或影响映射自身变更必须实际触发Android全链；动态委托Worktree的禁止提交/推送Hook在POSIX系统强制赋予执行权限，避免Linux忽略0644 Hook而放行提交。
- 修复真机活体页被240dp容器裁切、已认证仍可重复进入和中途退出后状态卡死：新增服务端实名总览与活动会话恢复，Android按未认证/认证中/已认证加载真实状态，第三方完整H5进入独立全屏WebView，240dp冻结区域只承载准备、授权、加载、处理中和失败状态；409/422优先展示安全商业原因。修复测试包递增为`versionCode 10213`。
- 修复10211测试包打开即显示“暂时无法连接”：构建误注入`owner-test`渠道，而公网仅发布`official + STAGING`版本策略，版本检查因此返回404；修复包递增为`versionCode 10212`，并新增BuildConfig渠道与环境单测，非`official + STAGING`构建会在交付前失败。
- 登录导航修复与实名认证沙箱闭环真机测试包版本身份递增为`versionCode 10211`；继续使用`1.2.2-debug`、固定Staging测试签名和`https://api.orbexa.cc`，禁止复用已测试的10210产物。
- 新增生产硬隔离的实名认证Staging供应商沙箱：默认关闭，仅`staging`可显式启用，任何`prod/production`组合误开都会拒绝启动；真机仍真实申请相机权限、打开HTTPS活体页、由服务端数据库事务写入结果并通过原轮询进入结果页，Android不伪造状态，正式环境继续要求后台激活真实供应商与SecretRef。
- 修复认证入口导航来源与转场语义：密码/验证码登录改为无方向同级淡入淡出，注册/忘记密码改为真实Navigation Compose子目的地，页内、系统键和手势pop均使用反向转场并恢复进入前登录模式；静态门禁禁止再次写死PASSWORD或用forwardContent切同级Tab。
- 记录versionCode 10210部分真机验收：实名认证首页与资料输入页通过；活体前会话创建因公网Staging没有任何ACTIVE identity配置返回HTTP 500，确认发生在第三方AppCode调用之前，不把其冒充为外部供应商正常失败或整版通过。
- 实名结果状态补齐后端`VERIFIED`成功映射，避免真实或沙箱供应商完成后仍停留“正在确认认证结果”。
- 全项目新增“大型商业 App 成熟方案优先”硬边界：基础能力只采用官方 Stable、行业主流且有长期维护路径的方案；禁止小众、预发布或自行构思的导航、返回栈、图标与动效框架，例外必须 ADR 与批准 CR。
- Android 全部既有页面迁移到稳定 Jetpack Navigation Compose 真实返回栈；顶栏、系统键和手势返回同源，实名认证从“我的”进入后返回仍恢复“我的”，登录注册及账号安全子页不再以页面枚举模拟导航。
- 新增 `HhyIcons` 官方矢量语义图标注册表与 `HhyMotion` 统一转场 Token；底部五栏目、实名认证准备项/状态、认证结果、登录注册和账号安全页已移除汉字、Unicode 与临时字符图标，并补齐前进/返回原生方向动效。
- 新增 Android UI 基础设施静态门禁、单元测试和 Navigation Test：阻止字符占位图标、自研返回栈、写死返回首页、页面私有动效数字与预发布导航依赖回流；本轮真机测试包递增为 `versionCode 10210`。
- 采纳并校验R05冻结视觉补充包：Android实名认证首页、身份信息、活体检测和结果页按逐状态效果图与Design Token重构，H5回跳页改为深蓝品牌头与悬浮业务状态卡；管理后台按项目原始ADM标准模板验收，不误扩张为专属效果图要求。正式页面继续过滤示例身份数据、供应商字段、state、接口名、错误码和请求编号。
- 修复R05客户端与公网后端版本错位：保留现有测试账号并完成数据库可恢复备份后，将`api.orbexa.cc`从R03滚动到R05候选服务；真实注册令牌读取实名认证授权说明返回HTTP 200，旧认证安全验证回归继续通过，第三方实名密钥仍只影响后续活体检测。
- R05 冻结视觉真机测试包版本身份递增为 `versionCode 10209`，继续使用 `1.2.2-debug`、固定 Staging 测试签名和 `https://api.orbexa.cc`，并绑定桌面、仓库、服务器与公网四方哈希门禁。
- 构建配置、应用内发布策略与版本一致性单测统一锁定 `versionCode 10209`，任一来源漂移都会在 APK 构建前失败。
- 八项核心实名专项测试已由占位路径升级为可执行 Adapter，并绑定真实 Java 测试方法；新增供应商超时、订单错配、私有证据摘要错配、处理中重复幂等请求和16路并发重复回跳故障注入，失败路径均保持稳定商业错误、零重复结果和秘密用后清零。
- 七个实名页面/交互面补齐可恢复状态验收：Android 覆盖业务状态与限流/字段错误，H5覆盖处理中、人工复核、未通过、回跳失效、离线和服务不可用，管理后台覆盖加载、空列表、无权限、记录不存在、乐观锁写入和成功导航；所有页面继续禁止展示供应商代码、内部失败码与请求编号。
- Cloudflare R2 与阿里云 OSS 均已接入真实对象存储端口：按业务作用域和精确激活配置路由，使用SecretRef短时解析凭据、签名上传/私有读取、HEAD大小/SHA/ETag校验和幂等对象键；本地开发回退与伪造成功继续禁止。
- 供应商结果新增乐观锁事务编排：待处理结果保持可重试，活体拒绝、人脸通过、人工复核与拒绝只通过受控状态机推进；原始供应商响应按用户与字段域加密，照片必须先取得 `private_kyc` 内部媒体ID才能更新会话和身份档案，PostgreSQL 17 集成验证已通过。
- 修复公开活体回跳控制器因 `final` 与参数校验代理冲突导致的全应用启动失败；保留原路由与响应契约，Spring Boot 应用上下文回归通过。
- 已购阿里云市场实名接口不再处于字段猜测状态：以用户提供原始资料哈希冻结活体结果0/1/2和人脸比对1001—1004契约，适配器补齐真实表单请求、响应大小/HTTPS/订单一致性校验、激活结果码配置和APPCODE用后清零；私有照片与事务状态机完成前仍禁止把供应商回包直接标记为实名通过。
- 活体照片新增服务端安全下载边界：不跟随重定向，域名解析必须全部为公网地址，正文限制100KiB并校验JPEG/PNG魔数与SHA-256；图片只在内存中短时存在，不落临时文件、不记录第三方地址。
- 实名授权协议改为服务端受控读取：Android进入信息表单自动加载当前生效正文，用户可展开阅读并明确同意，技术版本ID只在内存传递；创建会话会再次校验当前版本，APK不再硬编码协议版本，V027种子及有签署拒绝回滚均纳入PostgreSQL 17门禁。
- H5活体回跳改为一次性state原子消费：进入页面后立即清理URL，只返回最小业务状态，重复、过期或篡改state统一失效；V026新增消费时间并纳入PostgreSQL回滚重放门禁。
- Android 新增实名认证首页、本人信息输入、活体检测容器和结果页，接入四个冻结身份接口；身份证号仅在当前输入内存中存在，提交成功即清除，活体页面限制为 HTTPS 并按相机权限受控加载。
- 登录后“我的”新增实名认证入口，并移除原 Shell 中开发基线、API 地址、契约版本和 READY 等技术性展示；业务错误统一转换为用户可处理的中文提示。
- 管理后台新增实名认证列表与详情页，接入列表、详情、短时敏感资料、人工复核和双人冻结五个冻结接口；签名地址、供应商代码、内部失败码和请求编号不进入页面。
- 第三方原始响应加密、事务状态机和真实 `private_kyc` 对象写入适配器已实现；剩余门禁是注入正式环境SecretRef与激活配置并完成端到端验收，在此之前存储不可用会保持可重试且禁止伪造实名通过。

## R04 媒体与多对象存储 · 2026-07-19

- R04 真机测试包版本身份递增为 `versionCode 10207`，继续绑定固定 Staging 签名、`https://api.orbexa.cc` 和桌面/仓库/服务器/公网四方哈希门禁。
- Android 新增可复用媒体上传底部面板与管理器：选择文件后按后台配置的并发上限自动校验和上传，逐文件展示进度，支持取消、失败文件单独重试、完成回传、受信任私有预览和未绑定媒体删除确认；离线及 401/403/404/409/422/429/500 均提供可处理的商业提示。
- 媒体创建、完成和删除使用冻结网络契约与稳定幂等键；上传地址、私有读取地址、访问凭据、请求编号和幂等键仅在内部传递，不展示或记录到商业前端。
- 媒体上传面板的按钮高度统一使用冻结设计系统尺寸，视觉尺寸保持不变并通过全项目 UI Token 门禁。

## R03 供应商配置首切片 · 2026-07-18

- 热修复快速通道补齐35分钟时间盒、逐检查输入指纹复用、冻结Commit后的最多2路模块并行和便携Git入口；测试APK支持验证成功后自动归档并原子换版，失败不再破坏或手工移动当前交付状态。
- 修复注册专用提示未生效：Android补齐统一错误信封固定字段，已注册手机号不再退化为通用注册失败；邀请码预校验改用无锁只读查询，不存在的邀请码不再触发服务异常。公网已分别验证409和422专用场景码，回归APK版本递增为`versionCode 10206`。
- 注册体验修复后端已通过 V020 迁移和公网认证黑盒门禁并切换至新健康实例；真机回归 APK 版本身份递增为 `versionCode 10205`，继续绑定固定测试签名和正式 API 地址。
- 注册页新增持续可见的8–20位字母数字组合密码规则和确认密码不一致提示；密码、短信、注册、忘记密码之间切换会清空各自输入，不再串用手机号或敏感字段。
- 注册接口新增手机号已注册、邀请码无效和密码策略不符专用错误码，Android与H5展示可直接处理的商业文案；安全验证码仍默认120秒，后台配置与运行时范围收紧为60–300秒。
- 验证码误分类修复真机回归包递增为 `versionCode 10204`，沿用固定测试签名与 `https://api.orbexa.cc`，支持覆盖安装上一版 10203。
- 修复连续开发接续死锁：仅允许关闭流程显式生成、Release任务与NEXT_TASK均为同一BLOCKED任务且接续命令精确匹配时恢复；普通阻断仍拒绝，恢复不会伪造验收通过或任务完成。
- R02安全验证码模态交互与视觉设计登记为非阻断外部回传；新增只读回包门禁检查必需规格、八状态PNG、占位内容、参考资料哈希和敏感文件，设计返回后再经独立CR实现、测试并重新交付APK，不阻断R03及后续版本推进。
- R03 Android 回归测试包使用单调 `versionCode 10203`；构建配置、应用内发布策略和版本测试统一更新，并继续强制固定 Staging 签名、真实 API 地址与四方哈希门禁。
- 新增外部激活与域名实时证据：缺少真实 SecretRef/证书的供应商保持 BLOCKED 并绑定负责人和截止版本，12 个域名分别记录 DNS、TLS 指纹与服务健康，禁止用模拟成功替代外部验收。
- `ADM-CONFIG-005/006` 从目录占位升级为支付网关与支付宝企业付款真实配置页，沿用冻结供应商版本、SecretRef、只读连接测试、双人审批、激活和回滚门禁。
- 支付与出款连接测试新增专用只读探针：彩虹支付只执行签名账户查询，支付宝只执行证书认证查询，连接测试不会创建支付单或企业出款；带凭据、内网、回环及非 HTTPS 地址在传输前被拒绝。
- 新增冻结类型驱动的供应商证书客户端和支付宝证书管理面板；上传后只展示 SHA-256 指纹、有效期、状态和版本，原始证书/私钥不可回读，轮换必须携带乐观锁版本与双人审批单，写请求结果不确定时不会自动重放。
- 新增短信、对象存储与实名认证供应商配置字段白名单和 SecretRef 安全边界；明文秘密、未知字段及非 Vault/KMS 引用会在进入持久化前被拒绝。
- 固化供应商配置不可变版本状态机：只有字段校验和真实连接测试成功后才能申请审批，审批申请人与复核人必须不同，旧版本并发请求、未审批激活和无审批回滚均被阻断。
- 回滚改为绑定本次独立双人审批：当前运行版本原子标记为已回滚，只有曾真实连接测试成功的历史版本才能恢复激活，自审回滚和未验证历史版本均被阻断。
- 新增供应商配置版本应用服务与事务存储端口，把字段校验、连接探测、审批激活、旧版本替代、成对回滚和安全审计组合为可原子持久化的纵向流程；共享数据库迁移和 HTTP 控制器仍由 R03 汇合任务统一接入。
- 新增短信、R2/OSS 与实名认证的连接测试编排及专用适配层：按激活供应商校验必填端点和 SecretRef，统一阻断 HTTP、URL 凭据、内网与回环探测，解析后的秘密仅在连接器调用期间存在并立即清零；异常响应只保存安全分类，不记录供应商原文或异常内容。
- 管理后台新增冻结类型驱动的供应商配置客户端，接通列表、详情、创建版本、连接测试、激活和回滚请求路径及幂等头；写请求发生不确定网络失败时不会自动重放。
- `ADM-CONFIG-002/003/004/007` 从目录占位页升级为真实配置页面，覆盖供应商概览、环境与版本、连接状态、离线/403/冲突恢复及仅脱敏 SecretRef 元数据展示；创建、连接测试、审批激活和回滚表单按状态机启用，提交前阻断疑似明文秘密和非法 SecretRef。
- 新增 orbexa.cc 受控域名计划校验，固定 12 个生产/预发布主机名及环境映射，拒绝伪造子域、外部域名、带协议/端口/凭据的歧义输入。
- 域名验证拆分为 DNS、TLS/证书和业务服务健康三层串行门禁；仅 DNS 解析或 HTTPS 握手成功不会标记域名可用，前层失败将阻断后续探测。
- 域名配置应用服务接入 expectedVersion、稳定幂等键、DNS 待办负责人/截止条件和安全审计；更新主机计划会重置旧健康结果，重复 verify 不会重复执行网络探测。
- 管理端新增冻结生成类型驱动的域名客户端，接通域名列表、DNS 待办、更新与分层验证路径；写请求不在不确定网络失败后自动重放。
- `ADM-CONFIG-008` 从目录占位页升级为域名与环境真实配置页，展示受控域名、DNS 待办、三层门禁和服务端版本；离线、权限收回和 409 冲突时禁止继续写入，且不会把 DNS/HTTPS 通过误显示为业务健康。

## R02 认证启动首切片 · 2026-07-18

- 修复真机验证码结果误分类：后端新增仅供图片安全验证失败、过期或已使用的专用错误码；Android 与 H5 不再把账号密码、邀请码或短信服务异常误报为验证码错误、认证失效或网络中断，冻结 UI 布局和交互参数保持不变。
- 接入2026-07-19冻结安全验证码设计回传：初始页不展示验证码，登录、发送短信或注册等业务按钮通过本地校验后自动打开328dp模态弹层；覆盖加载、就绪、输错换图、过期换图、服务端限流倒计时、网络恢复、成功360ms自动续办及小屏键盘适配。
- 注册改为手机号、登录密码、重复密码和邀请码四项用户输入；注册不发送短信验证码，图片安全验证成功后直接续办注册。测试万能邀请码仅允许显式配置在dev/test/staging，任何prod/production组合都会强制失效。
- 正式商业前端全局移除请求编号、请求标识、挑战字段和“敏感信息不会写入日志”等技术性说明；新增Android/H5/管理端静态门禁，并将严格按效果图及dp/sp/圆角参数施工、版本逐项核对和桌面测试说明写入跨AI连续性规则。
- 图形安全验证改为160x56真实PNG，服务单测和公网门禁均验证PNG签名与尺寸，修复后端返回SVG而Android位图解码失败导致真机看不到验证码的问题。
- 登录、短信登录、注册、忘记密码和账号注销不再展示“创建安全验证”技术按钮；用户点击登录或发送验证码等业务按钮时自动加载图形验证，完成后沿原按钮继续操作。
- Android 认证页按 B01 八页参考图和冻结设计令牌重构：接入 20/18/16/15/14/13/12/11sp 字体层级、48dp 主按钮、52dp 输入框、64dp 品牌标识及 12/14/16dp 圆角；登录仅保留两个等宽模式页签，注册与忘记密码回到辅助入口，修复窄屏按钮纵向折行。
- `api.orbexa.cc` 后端从旧 P00 旁路升级到 R02：数据库备份后完成 Flyway V012—V018，使用受控 staging 安全密钥启动候选服务，匿名注册配置、安全验证和平台状态均验证 HTTP 200 后切换 Nginx，并保留旧服务与配置回滚点。
- 新增 R02 公网匿名认证门禁并补齐 staging 三项用户安全密钥的必填编排，后续 APK 不再只凭平台状态 200 判断认证服务可用。
- APK HTTPS 校验增加 CDN 强制回源头与明确 HTTP 状态诊断，修复新 Commit 精确下载路径首次 404 被 Cloudflare 负缓存后持续误判的问题。
- R02 Android 测试包启用单调 `versionCode 10202`，构建配置与应用内发布策略使用同一版本身份，并与固定签名、真实 API 地址和四方哈希交付门禁绑定，避免旧包覆盖或误交付。
- R02 首轮全量集成消除受限刷新静态断言、H5 原始色值、端点计数和滚动总计划漂移；本版本精确端点数按已批准注册协议配置接口同步为 20，完整 Python 工具链 95 项测试通过。
- H5 邀请注册页同步无短信注册和自动安全验证：页面只保留手机号、密码、确认密码及锁定邀请码，安全验证通过后直接续办注册；写请求不自动重试，敏感表单只保留在内存。
- Android APK 打包现在强制校验 `HHY_API_BASE_URL`；未注入、`.invalid` 占位域名、非 HTTPS 或携带凭据的地址会直接使构建失败，防止再交付必然显示“暂时无法连接”的 APK。
- 管理后台用户列表与详情已接入真实分页、筛选和脱敏数据，并补齐限制、解除限制、双人复核冻结、解冻与强制下线的权限化确认流程。
- 新增 `user_restrictions` 持久化、细粒度用户管控权限、幂等加密快照和限制到期自动恢复；数据库门禁升级为 V001—V018、199 表完整迁移与历史回滚重放。
- 新增账号注销申请后端闭环：敏感操作短信验证、数据版本乐观锁、状态历史与全会话失效在同一事务中完成。
- 补齐冻结账号受限自助通道：受限会话只可读取本人脱敏资料和提交幂等申诉工单，不能访问其他登录后 API。
- 已登录用户新增登录设备列表、远端设备二次确认下线和修改登录密码入口；修改密码成功后清除本机刷新凭据并强制重新登录。
- 用户 Bearer 令牌现在必须同时通过签名与数据库活跃会话校验；改密、设备下线或会话版本变化会立即使旧访问令牌失效。
- 登录设备接口改用不含 accessToken/refreshToken 的安全会话摘要，Android 页面与生成客户端类型同步阻止凭据回显。
- Android 新增密码登录、短信登录、注册、忘记密码与图形安全验证入口；启动完成后未认证用户进入认证页，安全验证图片在端内渲染。
- 后端新增认证挑战、密码/短信登录、邀请码校验、注册、重置密码和刷新令牌接口，并补齐密码失败锁定、幂等快照、验证码尝试限制和 V017 数据库不变量。
- `api.orbexa.cc` 已作为受控开发 API 入口接入服务器 loopback 后端；测试 APK 默认使用已发布的 `official/STAGING` 版本策略，修复真机“暂时无法连接”启动兜底。
- Android 认证请求已统一使用网络层冻结契约模型，并使用随机安装指纹建立最小设备会话上下文；不再采集 Android 硬件标识。
- 认证页现在按冻结错误码展示面向用户的校验失败、账号受限和限流恢复提示；服务端请求编号仅保留在内部诊断链路，不在商业页面渲染。
- 登录后的刷新令牌现通过 Android Keystore 加密保存；应用重启会用冻结 refresh 接口恢复会话，访问令牌不落盘，恢复失败会安全回到登录页。
- 注册流程现在要求邀请码通过服务端校验；修改邀请码会立即使之前的校验失效，防止未经验证的注册提交。
- 注册页不再把协议版本加载或确认作为账号创建字段；手机号、两次密码和邀请码通过本地规则后进入安全验证并直接续办注册。
- 认证请求提交期间现在冻结路由和输入字段；邀请码校验结果只绑定发起校验时的原始值，避免异步返回造成前端误显示为已验证。
- 启动门禁补齐强制更新下载内容暂不可用时仍阻断进入、以及版本检查失败保留请求编号的回归保障。
- 启动更新下载现在仅接受受信的 HTTPS APK 域名；异常协议、伪造主机、携带凭据、非默认端口或片段的地址会被拒绝且不会打开系统浏览器。
- 补齐认证挑战、短信发送、邀请码校验、密码重置和短信登录的 HTTP 成功契约回归，冻结请求字段与幂等头均有云端 Maven 验证。
- 补齐公开认证接口的拒绝契约：缺失或过短幂等键、畸形注册体及非法邀请码请求均返回安全校验信封，且不会调用认证服务。
- Android 认证失败提示不展示服务端错误码、请求编号或挑战内部字段，只保留可理解、可操作的用户文案。
- 未配置短信供应商时服务端显式失败且不伪造验证码；阿里云短信 SecretRef、模板、连接测试和真实回执继续由 R03 配置生命周期完成。

## 跨电脑 Git、模型分级与云端既有环境门禁 · 2026-07-18

- 跟踪不含秘密的仓库 transport descriptor，支持在新电脑、完整仓库或已验证 Git Bundle 中恢复 `origin`/upstream，并拒绝 force、错误 remote、behind/diverged 和含凭据 URL 的推送。
- 固化任务模型路由：复杂/高风险使用 Sol，中等使用 Terra，轻量/机械/只读使用 Luna；目标模型不可用时必须记录实际回退且不得伪称。
- 固化 Codex 默认已连接 `obx-test` 和项目既有环境的前提；启动预检失败必须阻断，禁止按无服务器状态开发或重建本地 Android SDK，Android 继续复用固定镜像和 Gradle 缓存。

## 跨 AI / 跨设备默认多代理授权 · 2026-07-18

- 将项目所有者长期授权写入权威连续性策略、AGENTS、START_HERE、导出模板、Context Pack、R02—R32 总计划和 R02—R04 并行计划；后续无需逐 Task 再确认是否启用执行代理。
- 固定调度模型为 1 个事实主控加最多 3 个隔离执行代理；未委托必须在 Checkpoint 记录原因，不支持代理的 AI 禁止伪造并行证据。
- 新增已批准 CR 精确范围应用、Checkpoint 结构化并行决策、第 4 个代理拒绝、路径租约与模板/Context Pack 防漂移门禁。

## R02 并行加速开发基线 · 2026-07-18

- 保留唯一主控 Session，引入最多 3 个受托执行代理、独立 scratch worktree、互斥路径租约和主控统一集成，避免事件哈希链及 Release 事实冲突。
- 将 R02 后续实施改为 4 个可并行纵向 Story 批次，再统一进入专项测试、预发布、APK 和封板流程。
- 建立 FAST、MODULE、INTEGRATION、RELEASE 四级门禁、受影响测试映射、确定性生成漂移检查和每日一次全量集成。
- APK 交付基线要求稳定测试签名、单调 versionCode、桌面/仓库/服务器/公网四方哈希，并把机器交付与用户真机验收分开记录。

## R01 Android 测试 APK 交付准备 · 2026-07-18

- Debug 测试包使用独立包名、测试签名，并启用 AGP 9 的资源覆盖功能，使应用名称明确显示“合伙云 Pro 测试”，避免与正式包混淆。
- 新增基于 Java 21、Android SDK Platform 37.0 与 Build Tools 36.0.0 的可复现 Docker 工具链，供后续测试 APK 复用。

## R01 管理员认证与个人安全前端 · 2026-07-17

- 管理后台新增真实登录、MFA 二次验证与管理员安全设置三页面，联通 R01 冻结的 8 个后台安全接口。
- 管理员访问令牌、MFA 票据和绑定信息仅保存在页面内存；刷新、退出、改密、权限撤销和弹窗关闭均执行敏感信息清理。
- 补齐请求内容感知幂等、结构化字段错误、限流倒计时、受限账号、无权限、离线和网络瞬断重试状态。
- 所有后台目录路由统一纳入认证守卫，高风险弹窗补齐键盘焦点圈定、Escape 关闭与关闭还焦。
- 新增同源本地交互验证服务及管理端路由、页面、服务、HTML 入口回归测试。
- 管理端写操作统一处理超时、429 冷却、离线清理、冲突刷新与重复提交，补齐故障注入回归。
- 7 个管理员安全 POST 持久化加密保存首次响应；改密、退出或 MFA 状态变化后仍可凭原幂等键安全重放。
- 新增 27 项 R01 权威测试矩阵、设计 Token 防漂移、配置注册表与 PostgreSQL 迁移不变量门禁。
- R01 隔离预发布栈接入 Prometheus、Alertmanager 与隐私最小化告警回执，错误率/P95 阈值与冻结配置统一为 2%/500ms。
- 新增管理员活跃会话、五分钟认证失败、MFA 启用、幂等快照不变量及 Gauge 查询失败指标和告警。
- 修正 4xx/5xx 错误体把 RequestId 冒充 TraceId 的问题，安全拒绝路径现在端到端回传真实 TraceId；未知业务端口资源按 404 返回，不再污染 5xx RED 指标。
- 不受支持的请求媒体类型统一返回冻结的 400 校验错误，不再落入 500 并污染服务端故障率。
- 加密幂等快照损坏测试改为确定性修改有效密文字节，消除 Base64 尾位导致的概率性假阴性。

## P00 工程基线修复 · 2026-07-17

- 产品版本保持 V1.2.2，修正 Android 内残留的 V1.2.1 展示和重复版本常量校验。
- 后台页面生成/测试同步为 74 页，Android 页面生成同步为 102 页，保留冻结的页面富化规格。
- 后端运行时同步完整的 198 表 V001—V009 迁移、184 个后台 API 及 10 个 WebSocket 事件契约。
- 修复 pnpm 锁文件中不存在的 `source-map-js@1.2.2`，并为递归 JSON 类型增加确定性 TypeScript 后处理。
- 修复首次 Git Bootstrap 与预领取 P00 会话之间的连续性死锁，并将其接入 CI 回归。

## V1.2.2 · 页面与运营规格冻结版 · 2026-07-16

- 新增 189 个页面/交互面施工合同、5159 个字段、1633 个状态和 606 个动作规格。
- 管理后台从 60 页扩展至 74 页，补齐认证、举报申诉、工单详情、会计、审批、风控规则、通知、导出和管理员安全。
- 315 个 REST 接口和 10 个 WebSocket 事件全部明确 UI 或系统所有者。
- 核心 OpenAPI/WS 资源类型化，移除文字型 `additionalProperties: true`。
- 314 个配置项补齐控件、范围、角色、审批、依赖和回滚；新增 58 条跨字段规则。
- 全部需求显式追踪到配置或 N/A；新增 162 个故事和 396 项逐版本 DoR。
- 风险重新分类；P00—R32 实现不再被误写为文档缺口。
- 新增 `scripts/check_v122_documentation.py` 文档防漂移门禁。

# Changelog

## 2026-07-22 — R07 最终候选真实页面旅程

- 将 Android 候选模拟器从 R06 通用首页旅程切换到 R07 搜索落地、真实搜索结果、发布者主页和清空历史确认框四图验证。
- 联系方式弹层保持 `FLAG_SECURE`，明确禁止截图并由自动化验证页面身份、动作和安全窗口标记。
- 新增仅限专用 Staging CI 账号的幂等 R07 数据夹具；真实手机号、Secret 和联系方式明文均不进入仓库或证据。

## 2026-07-20 · 全项目UI视觉零漂移门禁

- CR-0113：效果图成为页面建模、布局、信息层级、组件形态和视觉样式的强制来源；功能、字段、动作和正式文案仍以开发文档和合同为准，过滤虚拟/无关内容时不得自行重构布局。
- 新增逐页视觉验收目录与Release关闭检查；粗粒度面板范围、`TOKENS_ONLY`、相邻页面参考和缺少截图证据均不能通过关闭门禁。
- 当前R05七个客户端页面按真实状态登记为待补视觉规格或待按图重构，禁止把现有APK误标为UI验收通过。

- 2026-07-19：CR-0081落地统一轻重分级开发入口，简单Bug按受影响模块验证，正式功能、测试APK和版本关闭分流；补充跨电脑Git/pnpm发现与APK精确Nginx路由构建前预检，防止局部修复被发布流程放大。

- R02 后台用户读取闭环：实现用户列表、筛选、分页、服务端手机号脱敏与详情查询，`ADM-USER-001/002` 替换占位页并覆盖加载、空、离线、无权限、404、重试和详情导航。

## V1.2.2 — 2026-07-16

- 修复 H5 邀请注册错误关联 R27、设计 Token 引用、后台路由 TBD 和状态命名冲突；
- OpenAPI 升级为类型化冻结契约，增加启动维护/公开版本检查/公开帮助接口；
- 页面映射升级为动作级精确契约，新增 operationId、权限、幂等、错误状态、埋点和测试绑定；
- 数据字典补齐技术字段，新增 Flyway 迁移、索引约束、验证和回滚脚本；
- 新增统一复式记账、Outbox/Inbox、余额投影与统一对账模型；
- 配置注册表增加生命周期、激活门禁、SecretRef、重启/热更和回滚策略；
- 发布顺序改为依赖图，基础 RBAC、Provider 框架、版本检查前置；
- 测试计划扩展为场景级并加入语义门禁；
- 新增后端、Android、Admin、H5、基础设施、CI 和代码生成骨架；
- Project Doctor 升级为严格语义检查。

## V1.2 — 2026-07-15

原工程执行强化基线，保存在 `docs/99-archive/` 供追溯。

## V1.2.2 正式开发基线补丁 — 2026-07-16

- 修复 GET/DELETE 请求体泄漏和 Parameters Schema 缺失；
- 消除客户端/后台 operationId 冲突；
- 统一配置生命周期、SecretRef 词汇和生产签名安全策略；
- 补齐 R03 外部激活任务与阻断验收；
- 优化 Project Doctor 性能并消除语义误报；
- 增加 UTF-8 构建环境、PostgreSQL 17.10 CI smoke 和开发风险登记；
- 复验后端 8 模块/5 测试、Web 类型/测试/构建、8 个数据库迁移及关键会计不变量。

## TASK-P00-001 · COMPLETED · 2026-07-17T02:26:52Z

- Task close: TASK-P00-001 / SES-20260716T232809Z-B4A980AF
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：完成P00开发就绪、故事领取、冻结契约纠正、工程基线与无状态接续门禁
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260716T232809Z-B4A980AF.md`

## TASK-P00-002 · COMPLETED · 2026-07-17T02:33:56Z

- Task close: TASK-P00-002 / SES-20260717T023226Z-06841AFC
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：PostgreSQL 17.10空库/升级、并发不变量、Flyway重复迁移与U010/U011回滚重放全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023226Z-06841AFC.md`

## TASK-P00-003 · COMPLETED · 2026-07-17T02:37:21Z

- Task close: TASK-P00-003 / SES-20260717T023511Z-C6513BB0
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：三个P00 operationId、权限/错误码/审计、模块边界与真实数据库版本策略全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023511Z-C6513BB0.md`

## TASK-P00-004 · COMPLETED · 2026-07-17T02:42:30Z

- Task close: TASK-P00-004 / SES-20260717T023848Z-9F352CA9
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00零页面N/A边界、两个故事、生成API类型、H5/Admin与Android客户端契约全部验证通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T023848Z-9F352CA9.md`

## TASK-P00-005 · COMPLETED · 2026-07-17T05:31:12Z

- Task close: TASK-P00-005 / SES-20260717T024407Z-B03C9375
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00精确59项测试矩阵全部通过并归档10套可验证证据，关键缺陷清零
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T024407Z-B03C9375.md`

## TASK-P00-006 · COMPLETED · 2026-07-17T05:37:35Z

- Task close: TASK-P00-006 / SES-20260717T053626Z-25451510
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00可观测性、预发布环境、告警故障注入和回滚验收全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T053626Z-25451510.md`

## TASK-P00-007 · COMPLETED · 2026-07-17T05:40:19Z

- Task close: TASK-P00-007 / SES-20260717T053909Z-3A8B6A51
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00 Android APK可下载、清洁构建、签名、SHA256和版本信息完整，项目所有者真机安装启动验收通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T053909Z-3A8B6A51.md`

## TASK-P00-008 · COMPLETED · 2026-07-17T05:51:00Z

- Task close: TASK-P00-008 / SES-20260717T054147Z-7AE51A86
- Release：`P00`
- Story：`STORY-P00-001`
- Actor：`codex-root`
- 摘要：P00八项任务、六项验收、59项测试、Staging可观测性、APK真机验收及无状态交接全部完成；按项目所有者要求封板后暂停
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T054147Z-7AE51A86.md`

## TASK-R01-001 · COMPLETED · 2026-07-17T08:03:00Z

- Task close: TASK-R01-001 / SES-20260717T074317Z-C575A687
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：完成R01开发就绪、三故事领取、验证器语义修复、NEXT_TASK命令集和8接口/27测试任务合同对齐
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T074317Z-C575A687.md`

## TASK-R01-002 · COMPLETED · 2026-07-17T08:41:19Z

- Task close: TASK-R01-002 / SES-20260717T080633Z-7A2C9226
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01管理员认证V012数据迁移、状态机、领域不变量和PostgreSQL17空库升级回滚并发验证全部完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T080633Z-7A2C9226.md`

## TASK-R01-003 · COMPLETED · 2026-07-17T11:11:58Z

- Task close: TASK-R01-003 / SES-20260717T084524Z-9FE47D9F
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01管理员认证安全8接口完成，独立审计ACCEPT，PG17/API/代理/卷/重启/文档与连续性门禁全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T084524Z-9FE47D9F.md`

## TASK-R01-004 · COMPLETED · 2026-07-17T12:20:19Z

- Task close: TASK-R01-004 / SES-20260717T111928Z-C383F7A2
- Release：`R01`
- Story：`STORY-R01-001`
- Actor：`codex-root`
- 摘要：R01 管理端登录、MFA 与管理员自身安全设置前端已完成；类型化接口、内存会话、安全错误态、离线与权限收敛、自动化及浏览器验收均通过。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T111928Z-C383F7A2.md`

## TASK-R01-005 · COMPLETED · 2026-07-17T13:55:41Z

- Task close: TASK-R01-005 / SES-20260717T122518Z-91E6F4D6
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：TASK-R01-005完成：权威27项测试27/27 PASS，Java21、PostgreSQL17和隔离真实API证据归档，关键缺陷清零，CR-0016关闭。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T122518Z-91E6F4D6.md`

## TASK-R01-006 · COMPLETED · 2026-07-17T15:20:29Z

- Task close: TASK-R01-006 / SES-20260717T141717Z-A01412D7
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01独立预发布栈、结构化日志与TraceId、RED和九类业务监控序列、双告警firing/resolved、日志脱敏、最终镜像回滚及AC-R01-004现场证据全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T141717Z-A01412D7.md`

## TASK-R01-007 · COMPLETED · 2026-07-17T17:06:16Z

- Task close: TASK-R01-007 / SES-20260717T152721Z-016DB4B2
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01 Android测试APK已按固定工具链构建、签名和归档；项目所有者于2026-07-18提供真机截图确认安装、启动、首页和五项导航均通过。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T152721Z-016DB4B2.md`

## TASK-R01-008 · COMPLETED · 2026-07-17T17:45:20Z

- Task close: TASK-R01-008 / SES-20260717T171412Z-7CD86701
- Release：`R01`
- Story：`STORY-R01-003`
- Actor：`codex-root`
- 摘要：R01全部验收证据、真机APK、HTTPS下载、发布标签和无状态交接均已完成。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T171412Z-7CD86701.md`

## TASK-R02-001 · COMPLETED · 2026-07-17T20:02:10Z

- Task close: TASK-R02-001 / SES-20260717T183459Z-D64E7407
- Release：`R02`
- Story：`STORY-R02-009`
- Actor：`codex-master`
- 摘要：完成R02开发就绪与并行加速基线：主控与隔离执行机制、纵向切片任务图、分层测试、确定性代码生成、APK持续交付和CI全量集成已落地并验证
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T183459Z-D64E7407.md`

## TASK-R02-002 · COMPLETED · 2026-07-18T05:31:33Z

- Task close: TASK-R02-002 / SES-20260717T210927Z-13B07A7D
- Release：`R02`
- Story：`STORY-R02-003`
- Actor：`codex-root`
- 摘要：TASK-R02-002 completed: startup, password/SMS authentication, invite registration, controlled agreement versions, source/runtime contract synchronization, cloud backend test, fixed-image Android tests, WIP APK, and trace records delivered.
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260717T210927Z-13B07A7D.md`

## TASK-R02-003 · COMPLETED · 2026-07-18T07:05:22Z

- Task close: TASK-R02-003 / SES-20260718T053331Z-F0ED92BF
- Release：`R02`
- Story：`STORY-R02-005`
- Actor：`codex-root`
- 摘要：完成R02账户安全批次：受限账户申诉、账户注销与短信敏感操作闭环；修复测试APK因遗漏API地址导致暂时无法连接的问题，并建立打包阻断与自动回归测试。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T053331Z-F0ED92BF.md`

## TASK-R02-004 · COMPLETED · 2026-07-18T08:14:54Z

- Task close: TASK-R02-004 / SES-20260718T070836Z-25E39817
- Release：`R02`
- Story：`STORY-R02-001`
- Actor：`codex-root`
- 摘要：完成TASK-R02-004：用户列表、详情、限制、双人冻结、解冻、强制下线、到期恢复、V018迁移和全部模块门禁真实闭环。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T070836Z-25E39817.md`

## TASK-R02-005 · COMPLETED · 2026-07-18T08:44:58Z

- Task close: TASK-R02-005 / SES-20260718T081744Z-71EAAA84
- Release：`R02`
- Story：`STORY-R02-008`
- Actor：`codex-root`
- 摘要：完成TASK-R02-005：公开邀请注册配置、H5四端点安全调用链、MOB-AUTH-FORM页面、加载/失效/离线/重试/限流/成功导航与幂等自动化全部闭环。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T081744Z-71EAAA84.md`

## TASK-R02-006 · COMPLETED · 2026-07-18T10:48:30Z

- Task close: TASK-R02-006 / SES-20260718T084729Z-BD53B7C4
- Release：`R02`
- Story：`STORY-R02-009`
- Actor：`codex-root`
- 摘要：TASK-R02-006完成：四批次全量集成、25/25权威矩阵、131项Maven、真实PostgreSQL 17迁移、Android/H5/Admin回归、R02预发布可观测性与告警故障注入全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T084729Z-BD53B7C4.md`

## TASK-R02-007 · BLOCKED · 2026-07-18T13:23:29Z

- Task close: TASK-R02-007 / SES-20260718T105025Z-0B8DE284
- Release：`R02`
- Story：`STORY-R02-009`
- Actor：`codex-root`
- 摘要：R02机器交付与公网PNG门禁均PASS，等待项目所有者对hhy-r02-7b425c4-debug.apk完成真机验证码与登录路径验收
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T105025Z-0B8DE284.md`

## TASK-R03-001 · COMPLETED · 2026-07-18T13:30:28Z

- Task close: TASK-R03-001 / SES-20260718T132650Z-C5038104
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：R03开发就绪核验完成：6项需求、7个页面、13个接口、4个Story、18项测试、26张表及三切片共享边界全部冻结，严格文档与云端Android预检通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T132650Z-C5038104.md`

## TASK-R03-002 · COMPLETED · 2026-07-18T14:21:44Z

- Task close: TASK-R03-002 / SES-20260718T133151Z-12DB5949
- Release：`R03`
- Story：`STORY-R03-001`
- Actor：`codex-root`
- 摘要：TASK-R03-002短信、R2/OSS与实名供应商配置纵向闭环完成，Admin与后端MODULE门禁通过，等待TASK-R03-005共享API和数据库汇合
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T133151Z-12DB5949.md`

## TASK-R03-003 · COMPLETED · 2026-07-18T14:52:05Z

- Task close: TASK-R03-003 / SES-20260718T142638Z-EF4C3723
- Release：`R03`
- Story：`STORY-R03-002`
- Actor：`codex-root`
- 摘要：TASK-R03-003完成orbexa.cc域名计划、DNS待办、DNS/TLS/服务健康分层验证、expectedVersion和幂等阻断、ADM-CONFIG-008页面及69项Admin与14项后端测试闭环
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T142638Z-EF4C3723.md`

## TASK-R03-004 · COMPLETED · 2026-07-18T15:18:31Z

- Task close: TASK-R03-004 / SES-20260718T145327Z-DEA562CB
- Release：`R03`
- Story：`STORY-R03-003`
- Actor：`codex-root`
- 摘要：支付、企业出款、证书生命周期、只读连接测试和ADM-CONFIG-005/006后台闭环完成，全量测试与文档门禁通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T145327Z-DEA562CB.md`

## TASK-R03-005 · COMPLETED · 2026-07-18T16:21:43Z

- Task close: TASK-R03-005 / SES-20260718T152013Z-8B704646
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：TASK-R03-005完成：13个冻结API、共享V019迁移、审批资源绑定、真实PostgreSQL17.10、193项后端测试、73项管理端测试、生产构建、文档与生成物门禁全绿
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T152013Z-8B704646.md`

## TASK-R03-006 · COMPLETED · 2026-07-18T16:56:21Z

- Task close: TASK-R03-006 / SES-20260718T162320Z-23C14331
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：R03可观测性、隔离Staging、两组业务告警和同库卷应用回切演练全部通过，AC-R03-004已签PASS
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T162320Z-23C14331.md`

## TASK-R03-007 · BLOCKED · 2026-07-18T17:24:49Z

- Task close: TASK-R03-007 / SES-20260718T165842Z-356A8138
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：R03机器实现、测试、外部清单和固定签名APK四方交付均PASS；等待项目所有者对hhy-r03-3a913c9-debug.apk完成真机安装、启动与自动验证码路径验收
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T165842Z-356A8138.md`

## TASK-R03-007 · COMPLETED · 2026-07-19T08:16:56Z

- Task close: TASK-R03-007 / SES-20260718T200607Z-3569D212
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：R03外部激活证据、10206固定签名APK四方交付和项目所有者真机验收全部PASS
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260718T200607Z-3569D212.md`

## TASK-R03-008 · COMPLETED · 2026-07-19T08:31:20Z

- Task close: TASK-R03-008 / SES-20260719T081942Z-8D5C4241
- Release：`R03`
- Story：`STORY-R03-004`
- Actor：`codex-root`
- 摘要：R03版本关闭完成：13个冻结API、V019迁移、193项后端测试、73项管理端测试、Staging可观测与回滚、10206固定签名APK四方交付及项目所有者真机验收全部PASS，终态Manifest、Tag和桌面测试说明已归档
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T081942Z-8D5C4241.md`

## TASK-R04-001 · COMPLETED · 2026-07-19T08:52:59Z

- Task close: TASK-R04-001 / SES-20260719T083704Z-6E4CE28F
- Release：`R04`
- Story：`STORY-R04-002`
- Actor：`codex-root`
- 摘要：R04开发就绪核验完成：2项需求、3个冻结媒体API、12张相关表、1个Android交互面、2个故事和6项测试均无TBD；存储外部阻断边界、10207 APK基线与last_green自动回填防复发修复全部落地
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T083704Z-6E4CE28F.md`

## TASK-R04-002 · COMPLETED · 2026-07-19T11:33:01Z

- Task close: TASK-R04-002 / SES-20260719T085423Z-0385FEE0
- Release：`R04`
- Story：`STORY-R04-002`
- Actor：`codex-root`
- 摘要：TASK-R04-002完成：存储Scope与活动桶隔离、R2/OSS端口、私有短期URL、跨供应商迁移游标恢复、访问审计、PostgreSQL空库/回滚重放及MODULE门禁全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T085423Z-0385FEE0.md`

## TASK-R04-003 · COMPLETED · 2026-07-19T12:08:04Z

- Task close: TASK-R04-003 / SES-20260719T113522Z-6B27AD4B
- Release：`R04`
- Story：`STORY-R04-001`
- Actor：`codex-root`
- 摘要：TASK-R04-003完成：V022、三个冻结媒体API、用途/Scope策略、加密幂等、owner/过期/SHA/重复完成删除、Outbox与MODULE门禁全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T113522Z-6B27AD4B.md`

## TASK-R04-004 · COMPLETED · 2026-07-19T13:04:23Z

- Task close: TASK-R04-004 / SES-20260719T122607Z-04FDBE70
- Release：`R04`
- Story：`STORY-R04-001`
- Actor：`codex-root`
- 摘要：TASK-R04-004完成：冻结Media DTO/API、配置驱动并发上传、进度/取消/重试、受信任私有预览、删除确认、商业错误恢复及Android MODULE全部通过
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T122607Z-04FDBE70.md`

## TASK-R04-005 · COMPLETED · 2026-07-19T13:57:40Z

- Task close: TASK-R04-005 / SES-20260719T130824Z-06DC3492
- Release：`R04`
- Story：`STORY-R04-002`
- Actor：`codex-root`
- 摘要：TASK-R04-005完成：三分区汇合、六项权威测试6/6、Java21后端216项、PostgreSQL17迁移回滚重放、Android331任务、Python144项、连续性11项、Contracts与Web全部通过，生成物漂移和关键缺陷清零
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T130824Z-06DC3492.md`

## TASK-R04-006 · COMPLETED · 2026-07-19T14:41:52Z

- Task close: TASK-R04-006 / SES-20260719T135908Z-32D952EC
- Release：`R04`
- Story：`STORY-R04-002`
- Actor：`codex-root`
- 摘要：R04可观测性、隔离Staging、两组业务告警、日志脱敏和同库卷应用回切全部通过，AC-R04-004已签PASS
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T135908Z-32D952EC.md`

## TASK-R04-007 · COMPLETED · 2026-07-19T16:00:22Z

- Task close: TASK-R04-007 / SES-20260719T144443Z-BF11796E
- Release：`R04`
- Story：`STORY-R04-001`
- Actor：`codex-root`
- 摘要：R04测试APK versionCode 10207固定签名构建、真实API、桌面/仓库/服务器/公网四方同哈希和项目所有者真机验收全部PASS
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T144443Z-BF11796E.md`

## TASK-R04-008 · COMPLETED · 2026-07-19T16:10:28Z

- Task close: TASK-R04-008 / SES-20260719T160438Z-BF4F2F1D
- Release：`R04`
- Story：`STORY-R04-002`
- Actor：`codex-root`
- 摘要：R04媒体与多对象存储六项验收、Staging告警回滚、固定签名APK、四方同哈希、项目所有者真机PASS、Release Tag和无状态交接全部完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T160438Z-BF4F2F1D.md`

## TASK-R05-001 · COMPLETED · 2026-07-19T16:22:45Z

- Task close: TASK-R05-001 / SES-20260719T161434Z-47C1FAA4
- Release：`R05`
- Story：`STORY-R05-008`
- Actor：`codex-root`
- 摘要：R05开发就绪完成：2项需求、9个新增operationId、10张表、7个页面、8个故事、10项测试与R02外部真机发布门禁边界均已冻结并通过文档门禁
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T161434Z-47C1FAA4.md`

## TASK-R05-002 · COMPLETED · 2026-07-19T16:52:15Z

- Task close: TASK-R05-002 / SES-20260719T162332Z-9372DEF2
- Release：`R05`
- Story：`STORY-R05-008`
- Actor：`codex-root`
- 摘要：TASK-R05-002完成：V023迁移、回滚、幂等唯一性、状态历史、私有媒体、人工复核与敏感访问审计均通过PostgreSQL17.10真实验证和MODULE测试
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T162332Z-9372DEF2.md`

## TASK-R05-003 · COMPLETED · 2026-07-19T18:30:04Z

- Task close: TASK-R05-003 / SES-20260719T165401Z-12791729
- Release：`R05`
- Story：`STORY-R05-008`
- Actor：`codex-root`
- 摘要：R05-003九个冻结实名认证接口、生产活体供应商适配、权限幂等审计Outbox、V023-V025迁移回滚及PostgreSQL17真实存储集成门禁全部完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T165401Z-12791729.md`

## TASK-R05-004 · COMPLETED · 2026-07-19T22:38:18Z

- Task close: TASK-R05-004 / SES-20260719T183335Z-535311E4
- Release：`R05`
- Story：`STORY-R05-001`
- Actor：`codex-root`
- 摘要：R05-004 七端页面恢复态与无技术字段验证完成：Android 单元测试、lint、固定 API 地址校验和 debug 构建成功；H5/Admin 全量测试与生产构建通过；补齐空态、403、404、处理中、人工复核、拒绝、离线和服务不可用测试。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T183335Z-535311E4.md`

## TASK-R05-005 · COMPLETED · 2026-07-19T23:04:16Z

- Task close: TASK-R05-005 / SES-20260719T224052Z-2C69767F
- Release：`R05`
- Story：`STORY-R05-001`
- Actor：`codex-root`
- 摘要：R05-005八项实名认证专项测试全部自动化并通过：Java21全后端284项0失败，PostgreSQL17真实集成5项0失败0跳过，空库迁移/回滚/不变量门禁通过，供应商超时、并发回跳、重复消息、订单与摘要错配均有故障证据，关键产品缺陷为0。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T224052Z-2C69767F.md`

## TASK-R05-006 · COMPLETED · 2026-07-19T23:41:58Z

- Task close: TASK-R05-006 / SES-20260719T230526Z-55ABC07F
- Release：`R05`
- Story：`STORY-R05-001`
- Actor：`codex-root`
- 摘要：R05-006已完成四项实名认证业务指标、七条告警、结构化日志与Trace脱敏、隔离Staging、两类告警触发恢复、同库卷回切和AC-R05-004证据归档。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T230526Z-55ABC07F.md`

## TASK-R05-007 · COMPLETED · 2026-07-20T09:53:15Z

- Task close: TASK-R05-007 / SES-20260719T234639Z-1D7D7A00
- Release：`R05`
- Story：`STORY-R05-001`
- Actor：`codex-root`
- 摘要：R05 versionCode 10213 项目所有者于2026-07-20明确确认整体真机体验通过；TASK-R05-007 APK构建、安装、追溯和真机验收门禁完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260719T234639Z-1D7D7A00.md`

## TASK-R05-008 · COMPLETED · 2026-07-20T13:30:59Z

- Task close: TASK-R05-008 / SES-20260720T095830Z-752E5121
- Release：`R05`
- Story：`STORY-R05-008`
- Actor：`codex-root`
- 摘要：R05真机体验已由项目所有者通过；Android长期自动构建、APK、模拟器旅程、四张截图、日志、回归、自修复与候选资格体系由GitHub CI #244完整验证；R06顺序依赖已补齐
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260720T095830Z-752E5121.md`

## R05 关闭门禁跨环境修复 · 2026-07-20

- 修复 Context Pack 将本机历史 APK 计入仓库树指纹、导致 GitHub 干净检出失败的问题。
- 修复版本关闭后 `active_session=null` 时并行计划回归测试崩溃的问题。
- 关闭态改由 `CURRENT_STATUS` / `NEXT_TASK` 恢复 Release 上下文；R06 仍仅为 READY，未启动开发。

## TASK-R06-001 · COMPLETED · 2026-07-20T16:03:58Z

- Task close: TASK-R06-001 / SES-20260720T155546Z-F13C645A
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：R06开发就绪核验完成：DoR、5个Story、Release产物、API契约、R05顺序依赖和R06至R08连续交付授权均已登记并通过门禁
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260720T155546Z-F13C645A.md`

## TASK-R06-002 · COMPLETED · 2026-07-20T16:22:57Z

- Task close: TASK-R06-002 / SES-20260720T160653Z-1DDCDB22
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：R06内容与首页数据迁移、领域不变量、空库升级回滚验证全部完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260720T160653Z-1DDCDB22.md`

## TASK-R06-003 · COMPLETED · 2026-07-20T17:21:13Z

- Task close: TASK-R06-003 / SES-20260720T163244Z-D1F3CEE8
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：统一内容基础、首页与CMS后端服务、10条冻结operationId、幂等审计Outbox、V030权限迁移和实库回滚重放完成
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260720T163244Z-D1F3CEE8.md`

## TASK-R06-004 · COMPLETED · 2026-07-21T01:02:55Z

- Task close: TASK-R06-004 / SES-20260720T173038Z-35648A77
- Release：`R06`
- Story：`STORY-R06-001`
- Actor：`codex-root`
- 摘要：完成TASK-R06-004：管理端内容三页、Android首页/我的/关于页、自动认证、三张AI批准视觉基线及最终GitHub候选门禁全部通过；按项目所有者指令在TASK-R06-004后暂停
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260720T173038Z-35648A77.md`

## TASK-R06-005 · COMPLETED · 2026-07-21T02:00:11Z

- Task close: TASK-R06-005 / SES-20260721T012656Z-E067730A
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：完成TASK-R06-005：六个权威测试ID、重复请求、并发、存储超时、Outbox去重、非法CMS配置与CMS提供方超时专项测试全部通过，P0/P1缺陷为0
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T012656Z-E067730A.md`

## TASK-R06-006 · COMPLETED · 2026-07-21T04:23:38Z

- Task close: TASK-R06-006 / SES-20260721T020225Z-397AF410
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：TASK-R06-006完成：精确Commit 0e528d17的四项内容业务Gauge、TraceId/RED、六条告警规则、两组firing-resolved、V030隔离Staging、不可变Outbox合法终结和同库卷应用回切全部PASS，报告及机器证据已推送
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T020225Z-397AF410.md`

## TASK-R06-007 · COMPLETED · 2026-07-21T05:50:37Z

- Task close: TASK-R06-007 / SES-20260721T043434Z-868F3619
- Release：`R06`
- Story：`STORY-R06-001`
- Actor：`codex-root`
- 摘要：TASK-R06-007完成：GitHub Run 29803223373一次通过，f5cf1e4/10214固定签名APK完成模拟器旅程、AI审图、日志、四方SHA与桌面交付，owner_physical_test按异步规则保持PENDING
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T043434Z-868F3619.md`

## TASK-R06-008 · BLOCKED · 2026-07-21T08:13:30Z

- Task close: TASK-R06-008 / SES-20260721T055708Z-741C3D49
- Release：`R06`
- Story：`STORY-R06-005`
- Actor：`codex-root`
- 摘要：R06机器开发、候选APK、自动化与视觉验收均PASS；仅项目所有者异步真机验收保持PENDING，正式验收和生产激活继续阻断，按批准规则启动独立R07
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T055708Z-741C3D49.md`

## TASK-R07-001 · COMPLETED · 2026-07-21T08:56:02Z

- Task close: TASK-R07-001 / SES-20260721T082026Z-E6763DFE
- Release：`R07`
- Story：`STORY-R07-005`
- Actor：`codex-root`
- 摘要：R07开发就绪核验、五个故事领取、实施入口、并行计划与连续性证据全部PASS，允许进入数据迁移与领域不变量
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T082026Z-E6763DFE.md`

## TASK-R07-002 · COMPLETED · 2026-07-21T17:06:37Z

- Task close: TASK-R07-002 / SES-20260721T130339Z-785E85BE
- Release：`R07`
- Story：`STORY-R07-005`
- Actor：`codex-root-r07`
- 摘要：R07搜索数据迁移与领域不变量完成：V031前向、U031回滚、空库/升级库/重放、PostgreSQL不变量及后端311项零失败；ModuleBoundary既有误报已按PROB-0068和CR-0177修复。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T130339Z-785E85BE.md`

## TASK-R07-003 · COMPLETED · 2026-07-21T18:56:28Z

- Task close: TASK-R07-003 / SES-20260721T171025Z-A3718A1C
- Release：`R07`
- Story：`STORY-R07-005`
- Actor：`codex-root-r07-003`
- 摘要：R07-003七项冻结后端接口、V032联系方式安全迁移、权限幂等错误码审计、真实PostgreSQL集成和全部受影响MODULE门禁已完成；实现提交83d58c6a与CR关闭提交301a3012均已安全推送。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T171025Z-A3718A1C.md`

## TASK-R07-004 · COMPLETED · 2026-07-21T19:27:02Z

- Task close: TASK-R07-004 / SES-20260721T190306Z-32CB66BF
- Release：`R07`
- Story：`STORY-R07-001`
- Actor：`codex-root-r07-004`
- 摘要：TASK-R07-004已完成：五个冻结Android交互面绑定七项R07接口，搜索到发布者及联系方式闭环可操作；高敏明文仅内存、防截屏、关闭清除；合同与Android MODULE全通过。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T190306Z-32CB66BF.md`

## TASK-R07-005 · COMPLETED · 2026-07-21T20:16:15Z

- Task close: TASK-R07-005 / SES-20260721T193434Z-0C5F0BFA
- Release：`R07`
- Story：`STORY-R07-005`
- Actor：`codex-root-r07-005`
- 摘要：完成R07搜索、发布者主页与联系方式保护十项专项测试及故障注入：冻结Commit后端Java21 332项通过，PostgreSQL17 R07集成2项零跳过通过，Android单测与lint通过，10/10机器矩阵PASS，P0/P1缺陷为0；同时永久固化Windows Git用户环境与跨机恢复规则。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T193434Z-0C5F0BFA.md`

## TASK-R07-006 · COMPLETED · 2026-07-21T20:59:41Z

- Task close: TASK-R07-006 / SES-20260721T201747Z-CD80A1EE
- Release：`R07`
- Story：`STORY-R07-005`
- Actor：`codex-root-r07-006`
- 摘要：R07六项业务指标、七条告警、隔离Staging、日志脱敏、告警触发恢复和同库同卷回滚全部通过；AC-R07-004已签署，Git永久化跨机模板与连续性报告同步闭环。
- 记录：`docs/03-continuity/sessions/2026-07/SES-20260721T201747Z-CD80A1EE.md`
