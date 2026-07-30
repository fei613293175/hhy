# SCR-ID-003 · 活体检测容器 · R05 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`  
参考：`B12/P04 顶栏与卡片语言 + 批准补充规格`  
Token：`hhy_design_tokens_v1.2.2.json` + `TOKEN_ADDITIONS_PROPOSAL.json`

## 1. 页面建模

标准顶栏 → 步骤标签 → 240dp 活体取景区 → 状态提示/检测前说明 → 主操作。

## 2. 业务过滤

不展示供应商名称、AppKey、Token、活体URL、轮询次数和技术错误；相机说明只描述用户下一步。

## 3. 通用视觉参数

- 画布：360dp × 800dp，导出 1080 × 2400px；状态栏 24dp；顶部导航内容高 56dp。
- 页面底色：`color.background.page` `#F5F7FA`；表面：`color.background.surface`；主色：`color.brand.primary` `#1677FF`。
- 页面左右边距：`layout.pageHorizontalPaddingDp` 16dp；模块间距：`layout.sectionGapDp` 20dp；卡片间距 12dp；卡片内边距 16dp。
- 卡片圆角：`radiusDp.largeCard` 16dp；输入框/按钮圆角 12dp；标准卡片阴影 `shadow.card`。
- 顶部标题：`typographySp.pageTitle` 20/28/600；卡片标题 16/24/600；正文 14/22/400；说明 12/18/400；按钮 15/22/600。
- 主按钮高：`sizeDp.primaryButtonHeight` 48dp；输入框高 52dp；最小触控区 48dp。
- 动画：页面状态切换 `motionMs.standard` 200ms；按钮反馈 120ms；结果页强调 300ms；Snackbar 4000ms。

## 4. 状态图片

| 序号 | 状态 | 图片 | 尺寸 |
| --- | --- | --- | --- |
| 01 | 准备 | `images/SCR-ID-003_01_准备.png` | 1080×2400 |
| 02 | 相机授权 | `images/SCR-ID-003_02_相机授权.png` | 1080×2400 |
| 03 | 加载 | `images/SCR-ID-003_03_加载.png` | 1080×2400 |
| 04 | 处理中 | `images/SCR-ID-003_04_处理中.png` | 1080×2400 |
| 05 | 失败重试 | `images/SCR-ID-003_05_失败重试.png` | 1080×2400 |

总览：`images/SCR-ID-003_OVERVIEW.png`

## 5. 状态差异规则

- **准备**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。
- **相机授权**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。
- **加载**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。
- **处理中**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。
- **失败重试**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。

## 6. 验收要求

- 页面区域顺序、主次层级、卡片和按钮位置必须按本稿还原。
- 原始 dp/sp/hex 不得直接写入页面代码；应合并 Token 后引用。
- 正式 UI 不得出现 requestId、traceId、state、sessionId、供应商代码、AppKey、Token、接口名或异常栈。
- 敏感字段严格按页面规格脱敏，管理端原图必须经过用途授权和访问审计。
- 实现后提供同尺寸真机/浏览器截图并进入 `ui_visual_acceptance.csv` 比对；未通过不得关闭 R05。
