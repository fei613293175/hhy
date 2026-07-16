# AGENTS.md — 合伙云 Pro V1.2 最高工程约束

## 0. 接手顺序

收到“请继续开发这个项目”后必须：

1. 读取 `START_HERE.md`、主开发文档、`CURRENT_STATUS.yaml`、`NEXT_TASK.yaml`。
2. 运行 `./scripts/resume-project.sh`。
3. 只执行 `NEXT_TASK.yaml` 和对应 `releases/<版本>/TASKS.yaml` 中已READY任务。
4. 开发前查询 ADR、CR、Problem Registry、Pitfalls 和 Reusable Patterns。
5. 不得要求用户重新解释已经写入仓库的需求。

## 1. 唯一事实源与优先级

1. 主开发文档、已批准CR/ADR
2. 冻结的OpenAPI/DB/WebSocket/Config契约
3. Design Token与组件目录
4. 页面—前后端追踪矩阵
5. 12批UI参考图
6. AI或开发者推测

效果图只提供布局、样式和信息层级。不得从图中新增商豆、充值、多会员身份、CRM、第三方登录或其他未定义功能。

## 2. 禁止开发漂移

- 不得静默扩大范围、改变业务规则或恢复旧V2方案。
- 用户提出修改时，先运行 `scripts/create_change_request.py` 创建CR，更新主文档、追踪矩阵、版本任务和测试，再编码。
- API开发前必须把相应契约从 `CATALOG_BOUND` 提升为 `FROZEN`，补齐DTO、错误码和示例。
- 数据库只能通过版本化迁移修改；不得手工改生产Schema。
- 页面不得自定义接口字段、金额计算、状态机或权限。

## 3. UI强制规则

- 所有字号、行高、间距、圆角、颜色、边框、阴影、透明度、动画、图片比例和断点必须引用 `design/tokens/`。
- 页面代码禁止出现未登记原始dp/sp/px/Hex；CI Token Lint必须通过。
- Android/H5/后台优先使用 `design/component-catalog.*` 中成熟组件。
- 不为文字容器写死高度；测试360/390/412/430dp和字体1.0/1.15/1.30。
- 每个页面覆盖加载、空、错误、权限、禁用和业务状态。

## 4. 资金、红包和奖励

- 金额统一整数分；禁止浮点金额。
- 支付、红包、佣金、任务、奖励、提现各自独立账本，管理员不得直接改余额。
- 资金、库存和状态变更接口必须幂等；红包不得超发；支付回调不得重复履约。
- 红包本金不参与佣金；广告主不充值通用钱包；虚拟服务不提供正常退款。

## 5. 后台配置和密钥

- 阿里云短信、Cloudflare R2、阿里云OSS、实名、彩虹易支付、支付宝出款、域名和App构建均经配置中心管理。
- Secret、私钥、证书不得提交Git、打印日志或返回前端；保存后只显示已配置、指纹、末四位或有效期。
- 正式配置激活、生产签名和生产发布必须双人复核。

## 6. App构建与APK

- 自助构建中心仅构建合伙云Pro官方Android App，不允许任意Shell、第三方APK或白标工厂。
- `release_plan.csv` 标记YES的版本未产生测试APK不得关闭。
- APK必须绑定唯一Commit、版本号、签名指纹、SHA256、测试报告和Artifact Manifest。
- 正式签名只在受保护CI使用，不得放入普通Codex工作区。

## 7. Git与环境

- 用户自有私有仓库；禁止直接推送main。
- 使用task分支、PR、CI、Staging验收和Release Tag。
- Codex不得直接修改生产目录或读取生产数据库/密钥。
- 域名根为 `orbexa.cc`；需要DNS解析时生成明确待办提醒用户。

## 8. 每次结束硬规则

必须提交并推送：代码、迁移、契约、测试、文档、Session Log、状态、下一任务、Changelog、Problem Registry/Reusable Patterns/Pitfalls（如适用）和追踪矩阵。未推送远程的工作不算完成。Bug修复必须有根因和回归测试。
