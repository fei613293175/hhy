# SCR-HOME-001 · 首页 · R06 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`  
批准变更：`CR-0165`  
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 1. 页面建模

系统状态栏 → 56dp 品牌顶栏 → 页面标题与副标题 → 已实现真实能力组合卡（搜索、浏览项目、发布项目）→ 服务端模块区 → 64dp 五栏目底部导航。模块为空时保留一张有标题、说明和条件式重新加载动作的空模块卡，不增加 Hero、营销插画、虚构推荐或未实现入口。

## 2. 业务过滤

- 页面只展示 `homeGetHome` 返回的真实模块；搜索和项目操作只绑定 R07/R08 已实现路由；空模块状态不伪造项目、金额、人数或轮播数据。
- `serverTime`、`featureFlags` 和 `trackingContext` 仅参与客户端逻辑与受控诊断，不作为用户可见技术字段。
- 底部导航固定为首页、红包、发布、消息、我的；图标只使用 `HhyIcons`，当前栏目仅以 Token 色和容器状态强调。

## 3. 冻结视觉参数

- 基线画布：360dp 宽；页面背景 `color.background.page` `#F5F7FA`，表面 `color.background.surface` `#FFFFFF`。
- 页面水平边距：`layout.pageHorizontalPaddingDp` 16dp；区块间距：`layout.sectionGapDp` 20dp；卡片内边距 16dp。
- 顶栏内容高：`sizeDp.topAppBarContentHeight` 56dp；底部导航内容高：`sizeDp.bottomNavigationContentHeight` 64dp。
- 页面标题：`typographySp.pageTitle` 20/28/600；正文：`typographySp.body` 14/22/400；导航文字：`typographySp.navigation` 11/16/500。
- 空模块卡圆角：`radiusDp.normalCard` 14dp；边框与文字分别使用 `color.border.default`、`color.text.secondary`。
- 页面与栏目转场使用 `motionMs.page` 360ms、`motionMs.standard` 200ms 和 `HhyMotion`，禁止页面私有动画数字。

## 4. 状态与证据

| 状态 | 视觉规则 | 参考证据 |
| --- | --- | --- |
| LOADING | 模块区显示与最终布局同宽的结构骨架，顶栏和底栏尺寸不变化 | 页面规格 + Token |
| CONTENT | 按服务端顺序展示模块，模块卡统一使用 Token 边距、圆角和文字层级 | 页面规格 + Token |
| EMPTY/PARTIAL_CONTENT | 保留标题、副标题和底栏；模块区显示单张空态或局部错误卡，不扩大为全屏营销页 | `tests/android/visual-baselines/R06/01-home-loaded.png` |
| ERROR/OFFLINE | 保留稳定页面骨架，提供商业化重试提示，不显示接口名、错误码或请求编号 | 页面规格 + Token |

## 5. 验收

- 业务合同：`docs/02-ui/page-specs/android/SCR-HOME-001_首页.md`。
- 实现截图：`artifacts/validation/r06-task007-android/screenshots/01-home-loaded.png`。
- 已批准基线：`tests/android/visual-baselines/R06/01-home-loaded.png`。
- 必须保持 1080×2400 像素稳定、页面身份明确、底栏无遮挡，且自动化登录后的首页真实加载完成。
