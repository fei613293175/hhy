# SCR-ABOUT-001 · 关于与检查更新 · R06 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`  
批准变更：`CR-0165`  
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 1. 页面建模

系统状态栏 → 56dp 带真实返回箭头的顶栏 → SoftBlue 品牌与当前版本摘要卡 → 更新策略卡（标题、加载/失败/版本状态、更新说明、条件式下载动作）→ 条件式协议卡。禁止增加居中 Hero、营销装饰、开发说明区或服务端未返回的版本事实。

## 2. 业务过滤

- 当前版本、最新版本、更新类型、发布说明和下载动作只来自版本合同；不展示 SHA-256、渠道、环境、接口名或请求编号。
- 无更新时保留更新策略卡并禁用更新按钮；不得伪造可下载版本。
- 返回按钮、系统返回和手势返回操作同一个 Navigation Compose 返回栈，不写死跳转目标。

## 3. 冻结视觉参数

- 基线画布：360dp 宽；页面背景 `color.background.page` `#F5F7FA`，顶栏表面 `color.background.surface` `#FFFFFF`。
- 页面水平边距：`layout.pageHorizontalPaddingDp` 16dp；区块间距：`layout.sectionGapDp` 20dp；卡片内边距 16dp。
- 顶栏内容高：`sizeDp.topAppBarContentHeight` 56dp；返回图标：`sizeDp.standardIcon` 24dp；最小触控区 48dp。
- 页面标题：`typographySp.pageTitle` 20/28/600；卡片标题：`typographySp.cardTitle` 16/24/600；正文：14/22/400；按钮：15/22/600。
- 更新卡圆角：`radiusDp.normalCard` 14dp；按钮圆角：`radiusDp.button` 12dp；禁用透明度：`opacity.disabled` 0.38。
- 页面转场使用 `motionMs.page` 360ms，反馈使用 `motionMs.fast` 120ms；全部从 `HhyMotion` 引用。

## 4. 状态与证据

| 状态 | 视觉规则 | 参考证据 |
| --- | --- | --- |
| LOADING | 版本摘要与更新卡使用结构骨架，顶栏保持稳定 | 页面规格 + Token |
| NO_UPDATE | 展示当前/最新版本与业务发布说明，更新按钮禁用 | `tests/android/visual-baselines/R06/03-about-loaded.png` |
| OPTIONAL/FORCED_UPDATE | 保持同一布局，仅按合同改变说明、按钮可用性和强制返回限制 | 页面规格 + Token |
| ERROR/OFFLINE | 保留当前版本；更新区域显示商业化恢复提示，不展示技术字段 | 页面规格 + Token |

## 5. 验收

- 业务合同：`docs/02-ui/page-specs/android/SCR-ABOUT-001_关于与检查更新.md`。
- 实现截图：`artifacts/validation/r06-task007-android/screenshots/03-about-loaded.png`。
- 已批准基线：`tests/android/visual-baselines/R06/03-about-loaded.png`。
- 必须保持 1080×2400 像素稳定、标题和按钮无截断、返回入口清晰，且页面由“我的”真实导航进入。
