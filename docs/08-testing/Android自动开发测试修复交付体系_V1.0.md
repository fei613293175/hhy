# 合伙云 Pro Android 自动开发、测试、修复与交付体系 V1.0

状态：`APPROVED · CR-0135 + CR-0150 + CR-0152`
生效范围：`R06-R32` 及 R06 起所有产生 APK 的 Bug 修复候选
迁移基线：`R05 versionCode 10213` 已由项目所有者于 2026-07-20 确认整体真机体验通过

## 1. 不可绕过的结果边界

每个 Android 版本只有在 GitHub Actions 自动门禁全部为 `PASS` 后，才允许生成最终候选 APK 并复制到桌面。项目所有者真机体验改为异步反馈：未测试或尚未反馈时保留 `owner_physical_test=PENDING`，不伪造正式 Release 关闭或生产验收，但通过既有外部门禁延期机制立即继续后续版本编码和桌面候选累积交付。编译成功不等于候选合格，APK 生成不等于自动证据通过。

以下任一状态均禁止标记自动候选完成或把 APK 放到项目所有者桌面：

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
4. **功能旅程**：GitHub OIDC 向仅 Staging 启用的内部端点申请一次性短时引导码，模拟器兑换正常用户会话后执行 `tests/android/visual-manifests/<Release>.yaml` 登记的本版本关键旅程。引导码固定 10 分钟硬上限，绑定 repository、workflow_ref、Commit 与 Run 并只允许原子消费一次；它只覆盖 GitHub 模拟器冷启动与测试 APK 编译，兑换后的会话仍固定 15 分钟，生产环境必须拒绝启用。认证专项只在认证影响或定时全量回归时运行，不得固定附加到所有版本。
5. **视觉门禁**：每张截图绑定页面 TestTag、加载状态、文件名和 SHA-256；像素必须连续稳定，不同页面不得出现重复哈希或低于阈值的像素差异。AI 实现代理依据冻结效果图、Design Token、页面规格和上述机器证据自主判定并登记基线，不得暂停等待项目所有者逐轮核实。
6. **运行门禁**：采集目标进程 `logcat`、Android 退出原因、JUnit XML，扫描崩溃、ANR、异常退出和技术性用户提示。
7. **候选门禁**：`scripts/android_ci_gate.py finalize` 只有在构建和运行报告均通过时才生成 PASS 报告并上传候选 APK。
8. **候选落地**：接续 AI 下载 PASS Actions 的候选 artifact，复核 Commit、API 地址和 SHA-256 后，才把 APK 与该版本完整测试说明复制到项目所有者桌面；Actions 中间 APK 不得冒充候选。
9. **真机反馈**：项目所有者有时间时逐一安装桌面最终候选并反馈；问题进入当前或后续版本修复队列，不阻断后续版本编码。未反馈版本保持延期待验，正式关闭和生产激活仍要求项目所有者 PASS。

### 2.1 三层执行和候选请求协议

1. **FAST/MODULE 普通提交**：受影响模块的静态策略、正式 API、编译、单测、Lint 和打包；目标是快速发现确定性源码问题，结果不能授权真机测试。
2. **CONTINUITY 核心门禁**：每次提交校验身份、范围、检查点、CR、Trailer 和 Context Pack；只有连续性核心事实变化才执行完整临时 Git 生命周期。
3. **RELEASE CANDIDATE**：候选请求验证通过后运行完整 Android 构建、模拟器、旅程、截图、视觉、日志、候选报告与 APK 上传。

`config/android-candidate-request.yaml` 必须包含：`schema_version: 1`、`status: REQUESTED`、`R06-R32` Release、`candidate: true`、普通范围 1 至 3 的 `remediation_attempt`、以及每次候选唯一的 `request_id`。全局 `max_ai_attempts` 必须始终为 3；只有 `config/android-automation.yaml` 中经过批准且精确绑定 Release、轮次、request ID、CR、根因修复 Commit 和单次运行上限的例外，才允许相应请求超过普通范围。例外不得改写全局上限、伪造为新 attempt 1、扩展到其他 Release 或产生 attempt 5；候选运行报告必须同时记录全局上限与本次有效上限。普通 CI 的 build artifact 不是候选，任何人或 AI 都不得把它复制到桌面冒充候选。

## 3. 自动修复闭环

失败时 Actions 必须保留构建报告、JUnit、截图、`logcat`、退出原因和机器可读运行报告，并创建或更新 `[Android CI] <Release> 自动门禁失败，禁止真机验收` 修复项。

接续 AI 必须遵循：

1. 从失败修复项和 Actions artifacts 下载证据；
2. 先归类为编译、测试、运行、视觉、接口/环境或基础设施故障；
3. 对确定性故障修改最小必要代码和回归测试；
4. 提交后由同一工作流重新编译、打包、安装和测试；
5. 原 Commit 只有在业务测试开始前且日志明确证明为 GitHub、网络、镜像或 Staging 接口瞬态故障（包括 HTTP 5xx）时，才允许只重跑失败作业及其依赖一次；已通过的编译、Lint、单测和打包必须复用，不得修改业务代码或重跑全部作业；同一瞬态再次出现必须停止重跑并检查 Staging 健康、服务日志和基础设施；
6. 同一根因最多三轮 AI 修复。若同一 Release 的前三轮分别暴露不同根因，且最后一个根因已由新 Commit 修复并通过受影响 MODULE，必须通过独立批准 CR 建立全字段精确、最多运行一次的候选例外；校验器必须在 Android 环境安装和模拟器启动前拒绝任何字段漂移。测试实现缺陷由 AI 重构后以准确递增的候选轮次继续，不得重置或伪造历史，不得停下等待项目所有者判断截图；只有确需新增外部秘密、第三方权限或商业决策时才允许请求介入。失败候选仍不得交付。

所谓“自动修复”不是让 Actions 无审查改写生产代码。Actions 负责确定性验证和耐久失败队列；当前或接续 AI 负责依据证据修改、提交，随后 Actions 自动重跑。这避免无限循环和不可审计的机器人提交。

项目所有者不承担逐版本即时反馈义务。桌面候选可按版本累积，项目所有者在任意时间安装并反馈；未反馈版本保持 `owner_physical_test=PENDING`，只阻断对应 Release 的正式验收和生产激活，不得阻断后续依赖已满足版本的编码、机器候选和桌面 APK 交付。后续反馈的问题进入当前适用版本或批准热修队列，不得要求项目所有者先补齐所有旧版本结论。

## 4. 视觉基线规则

- 每个候选页面截图必须来自固定模拟器、固定语言、固定方向、固定字体比例和关闭系统动画的环境。
- 基线只允许来自冻结效果图施工完成且页面身份、真实加载、像素稳定、跨页面差异、敏感信息过滤和截图哈希全部通过的模拟器截图；AI 实现代理负责逐图审查、接受和登记，不得仅凭文件数量自动晋升。
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

若修复项为 `REMEDIATION_REQUIRED`，唯一合法动作是继续定位、修改和重跑；不得把失败候选交付桌面。自动候选完成后必须直接继续下一版本，不得因等待截图确认、桌面真机测试或大小版本节点暂停。
