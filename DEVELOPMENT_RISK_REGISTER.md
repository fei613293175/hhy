# 开发、外部激活与生产治理登记 · V1.2.2

## 解释

- `DOCUMENT_GAP`：开发前文档缺口。V1.2.2 中全部已关闭。
- `IMPLEMENTATION_GATE`：编码、构建、数据库、集成、并发或环境验证，在对应版本开发/退出时完成。
- `EXTERNAL_ACTIVATION`：需要项目所有者、供应商或基础设施资料的激活事项。
- `PRODUCTION_GOVERNANCE`：上线前安全、隐私、法务和财税治理。
- P00—R32 的业务实现是项目计划，不是风险，另见 `PROJECT_EXECUTION_PLAN.md`。

## 当前统计

| DOCUMENT_GAP关闭 | IMPLEMENTATION_GATE | EXTERNAL_ACTIVATION | PRODUCTION_GOVERNANCE | 当前阻止正式开发 |
| --- | --- | --- | --- | --- |
| 7 | 4 | 8 | 1 | 0 |

## 登记项

| risk_id | classification | status | severity | title | affected_release | owner | trigger | mitigation | acceptance_evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RISK-ANDROID-BUILD | IMPLEMENTATION_GATE | OPEN_RECORDED | HIGH | Android SDK/Gradle 实际编译尚未在当前环境执行 | P00 | android_owner | 首个Android变更合并前及P00退出 | 在具备 Android SDK 37 的CI执行 lintDebug、testDebugUnitTest、assembleDebug；失败即阻断P00关闭 | CI日志与可安装debug APK |
| RISK-POSTGRES-RUNTIME | IMPLEMENTATION_GATE | OPEN_RECORDED | HIGH | 真实 PostgreSQL 17.10 空库/升级库执行仍需CI确认 | P00/R01 | data_owner | 首次数据库变更合并前及每次迁移PR | CI使用postgres:17.10运行 scripts/run_postgres_migration_smoke.sh；后续补充升级库快照测试 | PostgreSQL 17.10 smoke日志和升级库报告 |
| RISK-COMPOSE-RUNTIME | IMPLEMENTATION_GATE | OPEN_RECORDED | MEDIUM | 本地 Docker Compose 全栈启动未在当前环境执行 | P00 | platform_owner | P00环境验收 | 开发机或CI执行 make infra-up、健康检查和 make infra-down | 服务健康检查、启动/关闭日志 |
| RISK-PRIVATE-REPOSITORY | EXTERNAL_ACTIVATION | OPEN_RECORDED | MEDIUM | 用户私有Git仓库、分支保护和权限尚未初始化 | P00 | project_owner | 正式提交首个开发变更前 | 导入用户自有私有仓库，启用main保护、必需检查和最小权限 | 仓库设置截图或审计导出 |
| RISK-SMS-SANDBOX | EXTERNAL_ACTIVATION | OPEN_RECORDED | HIGH | 短信测试/生产签名、模板和SecretRef待提供 | R02/R03 | sms_owner | R02集成测试及生产发布 | 先接Mock/沙箱Provider；R02联调前提供测试资料，生产上线前提供正式资料 | 连接测试报告、模板审核记录、SecretRef标识 |
| RISK-STORAGE-SANDBOX | EXTERNAL_ACTIVATION | OPEN_RECORDED | HIGH | 对象存储测试桶、跨域、生命周期和SecretRef待激活 | R03/R04 | storage_owner | R04媒体上传联调前 | 使用隔离测试桶联调上传/回收/迁移；验证CORS、生命周期、最小权限 | 连接测试、桶策略、生命周期和恢复演练 |
| RISK-IDENTITY-SANDBOX | EXTERNAL_ACTIVATION | OPEN_RECORDED | CRITICAL | 实名认证/活体供应商沙箱、回调白名单和合规资料待提供 | R05 | identity_owner | R05集成测试前 | R05前完成沙箱开通、验签、重复/乱序回调和敏感数据审计 | 沙箱测试报告、回调验签证据、合规确认 |
| RISK-PAYMENT-SANDBOX | EXTERNAL_ACTIVATION | OPEN_RECORDED | CRITICAL | 支付商户、证书、回调域名和对账文件待提供 | R16/R32 | payment_owner | R16支付集成及R32生产发布 | 先实现沙箱适配器和契约测试；R16前完成支付闭环，R32前完成生产演练 | 支付沙箱报告、验签/重放测试、日对账结果 |
| RISK-PAYOUT-SANDBOX | EXTERNAL_ACTIVATION | OPEN_RECORDED | CRITICAL | 支付宝企业付款/出款商户和证书待提供 | R22/R32 | withdrawal_owner | R22提现集成及R32生产发布 | R22前完成沙箱出款、失败退回、乱序回调和双人审批联调 | 出款沙箱报告、回调/对账/冲正证据 |
| RISK-DNS-CERTIFICATE | EXTERNAL_ACTIVATION | OPEN_RECORDED | HIGH | orbexa.cc DNS写权限和生产证书激活待用户完成 | R03 | domain_owner | R03外部激活门禁 | R03 TASK-R03-007登记每个记录、责任人、TTL、证书指纹和验收状态 | DNS解析、TLS扫描和证书指纹证据 |
| RISK-ANDROID-SIGNING | EXTERNAL_ACTIVATION | OPEN_RECORDED | CRITICAL | 生产Android签名密钥和受保护CI Profile待创建 | R29 | app_build_owner | 首个正式签名包前 | 由项目所有者/受保护CI生成和托管；只向系统暴露引用和证书指纹 | 签名指纹、受保护变量审计和安装验证 |
| RISK-LOAD-CONCURRENCY | IMPLEMENTATION_GATE | OPEN_RECORDED | CRITICAL | 支付、红包、奖励、提现的真实并发/故障注入尚未执行 | R16/R19/R22/R32 | qa_owner | 对应高风险版本退出及上线前 | 按版本执行库存1并发、回调重放、MQ重复、Redis故障、账务重建和容量压测 | 压测报告、故障注入报告、零账务差异 |
| RISK-SECURITY-COMPLIANCE | PRODUCTION_GOVERNANCE | OPEN_RECORDED | CRITICAL | 渗透测试、隐私合规、财税和激励模式法律审查尚未完成 | R32 | security_owner | 生产发布候选前 | 上线前完成独立安全测试、隐私影响评估、协议审查和整改闭环 | 渗透报告、整改记录、合规/法务签字 |
| RISK-MOCKITO-AGENT | TOOLCHAIN_OBSERVATION | MONITOR | LOW | Mockito/Byte Buddy 在新JDK上的动态Agent警告 | P00 | backend_owner | 升级JDK或测试框架时 | 在测试JVM显式配置Mockito agent或升级测试栈，JDK升级前清零警告 | 测试日志无动态自附加警告 |
| DOC-GAP-PAGE-SPEC | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 页面字段、状态、动作、导航和错误恢复不够精确 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-ADMIN-OPS | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 后台筛选、表格列、批量/行操作、导出、脱敏和审批不完整 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-API-OWNERSHIP | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 部分接口没有明确页面或系统所有者 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-OPEN-SCHEMA | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 首页、CMS、驾驶舱等核心资源仍使用自由对象 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-CONFIG-UX | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 配置项缺少运营表单、角色和跨字段规则 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-TRACEABILITY | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 需求到配置、页面施工规格和故事未完全闭合 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |
| DOC-GAP-RELEASE-DOR | DOCUMENT_GAP | CLOSED | BLOCKING_BEFORE_V1.2.2 | 版本任务偏模板化且缺少页面级开发就绪门禁 | P00/R01-R32 | product_architecture_owner | 任何相关事实源变更 | 已由V1.2.2权威目录和逐页施工文档闭环 | scripts/check_v122_documentation.py 全部门禁通过 |