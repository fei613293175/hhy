# 合伙云 Pro Android 自动开发、测试、修复与交付体系 V1.0

状态：`APPROVED · CR-0135 + CR-0150`
生效范围：`R06-R32` 及 R06 起所有产生 APK 的 Bug 修复候选
迁移基线：`R05 versionCode 10213` 已由项目所有者于 2026-07-20 确认整体真机体验通过

## 1. 不可绕过的结果边界

每个 Android 版本只有在 GitHub Actions 自动门禁全部为 `PASS` 后，才允许生成“最终候选 APK”并邀请项目所有者做一次真机体验验收。编译成功不等于版本完成，APK 生成不等于候选合格，模拟器测试通过也不等于项目所有者真机验收通过。

以下任一状态均禁止标记版本完成、禁止把 APK 放到项目所有者桌面并要求提前测试：

- 编译、Lint、单元测试或打包失败；
- 模拟器未成功启动、APK 未安装或 App 未冷启动；
- 功能旅程、系统返回、导航恢复或页面状态测试失败；
- 页面截图缺失、未绑定批准基线或视觉差异超阈值；
- `logcat`、退出原因中存在目标 App 崩溃、ANR 或进程异常退出；
- APK 的 Commit、API 地址、签名、版本号、SHA-256 或证据不完整；
- 机器可读 `candidate-report.json` 不是 `PASS` 或 `owner_test_allowed` 不是 `true`。

## 2. 固定流水线

唯一可复用质量入口为 `.github/workflows/android-quality-gate.yml`。普通 CI 通过 `.github/workflows/ci.yml` 以 `candidate=false` 调用，只执行第 1、2 项快速门禁；不得启动模拟器、生成候选报告或上传候选 APK。版本候选通过以下任一受控入口以 `candidate=true` 调用同一质量入口：

- 开发分支提交经 `scripts/android_candidate_request.py` 校验的 `config/android-candidate-request.yaml`，由 `.github/workflows/android-candidate-request.yml` 自动触发；
- 工作流已进入 GitHub 默认分支后，通过 `workflow_dispatch` 指定 Release 和修复轮次触发。

两条候选入口必须调用同一个可复用工作流，不得维护两套编译、模拟器、截图、日志或候选判断逻辑。

1. **源码与策略检查**：校验 `config/android-automation.yaml`、UI 基础设施、Design Token 和正式 API 地址。
2. **编译与单元门禁**：运行 `verifyApiBaseUrl`、`lintDebug`、`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`。
3. **模拟器门禁**：在固定 Pixel 7 / Android API 35 / 中文 / 竖屏环境安装并运行 APK。
4. **功能旅程**：使用稳定 UI Automator 执行冷启动、密码登录页、短信登录页、注册返回和忘记密码返回；每个版本在此基础上增加该版本关键旅程。
5. **视觉门禁**：采集确定性截图；R06 起必须与 `tests/android/visual-baselines/<Release>/` 中批准基线同名比较。失败运行不得自动更新基线。
6. **运行门禁**：采集目标进程 `logcat`、Android 退出原因、JUnit XML，扫描崩溃、ANR、异常退出和技术性用户提示。
7. **候选门禁**：`scripts/android_ci_gate.py finalize` 只有在构建和运行报告均通过时才生成 PASS 报告并上传候选 APK。
8. **候选落地**：接续 AI 下载 PASS Actions 的候选 artifact，复核 Commit、API 地址和 SHA-256 后，才把 APK 与该版本完整测试说明复制到项目所有者桌面；Actions 中间 APK 不得冒充候选。
9. **真机终验**：项目所有者仅安装最终候选 APK 做一次体验验收；反馈 PASS 后才允许关闭 Release。

### 2.1 三层执行和候选请求协议

1. **FAST/MODULE 普通提交**：受影响模块的静态策略、正式 API、编译、单测、Lint 和打包；目标是快速发现确定性源码问题，结果不能授权真机测试。
2. **CONTINUITY 核心门禁**：每次提交校验身份、范围、检查点、CR、Trailer 和 Context Pack；只有连续性核心事实变化才执行完整临时 Git 生命周期。
3. **RELEASE CANDIDATE**：候选请求验证通过后运行完整 Android 构建、模拟器、旅程、截图、视觉、日志、候选报告与 APK 上传。

`config/android-candidate-request.yaml` 必须包含：`schema_version: 1`、`status: REQUESTED`、`R06-R32` Release、`candidate: true`、1 至 3 的 `remediation_attempt`、以及每次候选唯一的 `request_id`。普通 CI 的 build artifact 不是候选，任何人或 AI 都不得把它复制到桌面冒充候选。

## 3. 自动修复闭环

失败时 Actions 必须保留构建报告、JUnit、截图、`logcat`、退出原因和机器可读运行报告，并创建或更新 `[Android CI] <Release> 自动门禁失败，禁止真机验收` 修复项。

接续 AI 必须遵循：

1. 从失败修复项和 Actions artifacts 下载证据；
2. 先归类为编译、测试、运行、视觉、接口/环境或基础设施故障；
3. 对确定性故障修改最小必要代码和回归测试；
4. 提交后由同一工作流重新编译、打包、安装和测试；
5. 原 Commit 只允许因 GitHub/网络/镜像瞬态故障重跑一次；业务失败不得靠重复运行碰运气；
6. 同一根因最多三轮 AI 修复。三轮后仍失败，或确需新增项目所有者秘密、第三方权限和商业决策时，才允许升级；失败版本仍不得交付。

所谓“自动修复”不是让 Actions 无审查改写生产代码。Actions 负责确定性验证和耐久失败队列；当前或接续 AI 负责依据证据修改、提交，随后 Actions 自动重跑。这避免无限循环和不可审计的机器人提交。

## 4. 视觉基线规则

- 每个候选页面截图必须来自固定模拟器、固定语言、固定方向、固定字体比例和关闭系统动画的环境。
- 基线只允许来自冻结效果图施工完成后的人工批准模拟器截图；不能把当前输出自动晋升为基线。
- 效果图决定建模、区域顺序、层级、布局、字号、间距、圆角、颜色与组件形态；效果图中的虚拟业务文字和无关功能仍须按全局 UI 规则过滤。
- 像素比较负责发现明显漂移，Token 与页面规格门禁负责精确参数；两者必须同时通过。

## 5. 第三方能力边界

第三方活体、真实相机、人脸、短信等可能拒绝模拟器的能力采用三层验证：

1. 本地/CI 的供应商适配器模拟与错误场景自动回归；
2. Staging 的真实供应商沙箱合同和回调测试；
3. 最终候选由项目所有者真机做一次体验验收。

不得因为模拟器无法完成真实活体而跳过前两层，也不得把第三方真实服务不可用误判为 UI 已通过。

## 6. 跨电脑、跨 AI 接续

任何接手者运行 `python scripts/continuity.py resume` 后，还必须读取：

- `config/android-automation.yaml`；
- `config/android-candidate-request.yaml` 及其 `request_id`、Release 和修复轮次；
- 最近一次 Android Actions 运行及其 artifacts；
- 当前 Release Manifest 的 `android_automation` 和 `android_delivery`；
- 尚未关闭的 `[Android CI]` 修复项。

若修复项为 `REMEDIATION_REQUIRED`，唯一合法动作是继续定位、修改和重跑；不得绕过它推进版本完成或邀请项目所有者测试。
