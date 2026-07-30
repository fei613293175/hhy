# TASK-R09-001 · P00/R01–R08 历史 UI 全局审计

状态：`COMPLETE · PASS`
批准变更：`CR-0247`、`CR-0248`
审计范围：计划版本不晚于 R08 的全部前端页面
审计原则：效果图强制约束视觉建模，但不作为功能、字段、动作或业务数据事实源。

## 1. 根因结论

1. 主 UI 规则长期要求按效果图施工，但 189 份逐页规格同时使用“效果图仅用于视觉参考”的弱化表述，形成强弱事实冲突。实现者容易把效果图降级成配色参考，只完成字段、状态和 Token 对齐。
2. 旧视觉门禁允许页面长期停留在 `IN_REVIEW`，代码检查与功能测试通过并不代表实际截图达到效果图的丰富度和精致度。
3. 旧版本关闭命令把机器交付、项目所有者异步真机反馈、Release Tag 和生产终态混在一个 `--close-gate` 中；失败提示只能逐项暴露，造成版本末尾反复试错。
4. 测试影响映射曾把连续性索引和门禁配置自身误映射到 Android 全链，导致治理文件变化也触发不相关编译或模拟器工作。

## 2. 已完成的流程修复

- CR-0247 已机械消除 189 份逐页规格的弱化表述，并增加文档回归检查。
- `release-close` 已固定为大版本机器收尾，允许异步真机为 `PENDING`，但仍强制候选证据、视觉结论、APK 身份与 SHA、交付文档和稳定签名链完整。
- 正式生产验收只能显式执行 `--production-close-gate`，继续强制真机 `PASS`、Release Tag 与生产终态。
- 旧 `--close-gate` 直接拒绝，避免不同 AI 猜测关闭语义。
- 普通任务只运行受影响 FAST/MODULE；GitHub 模拟器、完整 APK 和全量门禁保留到大版本最终候选。

## 3. 页面审计结果

总计 45 页：

- 已有并复核通过：19 页。
- R01 本次补齐合同和真实浏览器截图后通过：3 页。
- Android 本次专用离线模拟器取证并由 AI 逐张复核通过：23 页，其中联系方式安全面板依 `FLAG_SECURE` 以源码、语义和仪器报告替代敏感截图。

本次新通过页面：

| 页面 | 结论 | 证据 |
| --- | --- | --- |
| ADM-AUTH-001 | PASS | `artifacts/validation/r09-historical-ui/admin/ADM-AUTH-001-browser-1440x1100.png` |
| ADM-AUTH-002 | PASS | `artifacts/validation/r09-historical-ui/admin/ADM-AUTH-002-browser-1440x1100.png` |
| ADM-SECURITY-001 | PASS | `artifacts/validation/r09-historical-ui/admin/ADM-SECURITY-001-browser-1440x1100.png` |

三页均通过肉眼丰富度、信息层级、组件精致度、真实业务映射、状态完整性与 AI 对照结论六项检查；页面中原有 `ADM-...` 和 `CRITICAL` 技术编号已移除。

## 4. Android 首批返工

- R02：启动状态卡改为 Token 品牌渐变；登录设备页改为品牌说明、设备卡、当前设备标签与局部确认；账号受限页改为风险主卡、账号事实卡和独立申诉卡。
- R04：媒体上传器已有品牌头、类型说明、空态、文件状态卡和受控动作，源码审计通过，等待统一截图。
- R05：实名首页、信息输入、活体容器和结果页已有冻结规格要求的渐变状态卡、准备事项、表单、240dp 取景区和分状态结果；清除字符假图标，统一使用 `HhyIcons`。
- R06：首页重做为真实搜索品牌主卡、三项已实现合作入口、服务端推荐区与完整空态；关于页现有新源码已移除 debug/测试说明并按品牌摘要、版本状态和协议卡组织。
- R07：搜索页增加品牌摘要和组合搜索层级；结果卡增加类型语义块、真实公开标签和发布者层级；发布者资料头使用品牌渐变；联系面板增加渠道标题组和掩码摘要卡，仍保持 `FLAG_SECURE` 且禁止敏感截图。

上述 Android 变更均不增加新 API、字段、入口、统计、图片或业务能力。

## 5. 效果图补充包判断

当前没有必须新生成的页面效果图：

- R02 可复用 B01 与认证安全页面风格；
- R04 可复用项目表单/上传组件风格；
- R05 已有 `design/R05-UI-FROZEN`；
- R06 可复用 B02；
- R07 可复用 B02、B03；
- R01 可复用管理后台 `ADM-AUTH`、`ADM-DETAIL` 标准模板。

仅当后续页面无法映射上述视觉体系时，才在桌面生成效果图补充包，避免无意义重复设计和第二套事实源。

## 6. 视觉取证执行策略

- 23 个 Android 页面已完成证据回填并改为 `PASS`；非敏感页面必须绑定本次真实截图，联系方式安全面板必须绑定 `FLAG_SECURE` 源码语义与仪器报告，仍禁止仅凭源码或 Token 检查通过普通页面。
- 本机 CPU 虚拟化已开启，但未安装 Android Emulator Hypervisor Driver；普通权限下无法启用，强行安装会要求管理员操作并可能重启。远端 `obx-test` 同样没有可用 `/dev/kvm`，因此不把本机/服务器环境改造混进产品返工。
- 既有 `config/android-candidate-request.yaml` 使用唯一组合 `release=HISTORICAL-UI`、`candidate=false`，由已注册的候选请求入口把布尔值原样传给现有权威 `.github/workflows/android-quality-gate.yml`：只运行 `HistoricalVisualAuditTest`，以 `androidTest` 离线夹具渲染生产 Composable，一次生成 22 张非敏感页面截图；不请求 CI 登录、不访问 Staging、不生成候选 APK、不运行发布关闭门禁，也不注册第二套工作流。
- 源码冻结前已通过 Android 全量 `testDebugUnitTest lintDebug`（436 tasks，`BUILD SUCCESSFUL`）、`compileDebugAndroidTestKotlin` 和文档严格门禁；冻结后只执行一次上述视觉专跑，由 AI 逐张完成六项判定，不在每次提交重复模拟器。
- Run `29942343711` 已证明非候选分流和 KVM 启动正确，但 `android-emulator-runner` 将多行 `script` 逐行隔离，导致 `cd apps/android` 不传递、测试前以 `./gradlew: not found` / 127 退出；该轮没有产品截图，不计为视觉判定失败。`PROB-0094` 已将命令收敛为单一仓库脚本。
- Run `29943415539` 的确定性复验成功：候选编译、lint、单测、候选模拟器、候选资格与修复队列全部跳过，仅执行历史 UI 离线视觉批次；18 个仪器测试通过，产出并核对 22 张非敏感截图，GitHub 制品 SHA-256 为 `aa177763656e6b5f45f7424a9ef00685f18021ef90fd740c603cf87406ae5275`。
- 联系方式面板继续使用安全语义、源码和本次仪器报告替代敏感值截图。

## 7. 最终验收结论

- `catalogs/ui_visual_acceptance.csv` 中截至 R08 的 45 页现均为 `PASS`。
- 22 张 Android 截图均逐张通过：肉眼丰富度、信息层级、组件精致度、真实业务映射、状态完整性与 AI 对照结论。
- `python scripts/check_ui_visual_acceptance.py --historical-through R08` 输出 `UI_VISUAL_HISTORICAL_ACCEPTANCE_OK R08 pages=45`。
- 本轮页面均可复用既有冻结效果图或统一视觉体系，不需要生成效果图补充包。
