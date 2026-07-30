# SCR-HOME-001 · 首页 · R06 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`  
批准变更：`CR-0165`、`CR-0265`（CR-0265 纠正 CR-0165 的首页信息架构删减）
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 1. 页面建模

系统状态栏 → 56dp 平台 Logo/名称与消息入口 → 全宽搜索框 → 可选公告 → 横向 Banner → 项目/App/群聊/团队长四个一级入口 → 按服务端顺序展示“为你推荐、最新发布、精选项目、精选 App、热门群聊、推荐团队长、头条、置顶、活动专题、新手指南”等真实运营模块 → 64dp 五栏目底部导航。

页面建模精确绑定 `B02/P01`、`B02/P02`、`B02/P04`；项目、App、群聊、团队长内容卡分别继承 `B02/P05`、`P06`、`P07`、`P08`。禁止以 Hero 主卡、发布快捷入口或通用文字列表替代上述结构。服务端未配置某个可选运营模块时允许不显示该模块，但客户端必须具备对应模块类型的真实渲染能力。

## 2. 业务过滤

- 页面只展示 `homeGetHome` 返回的真实运营模块、媒体、徽标和目标；四类一级入口来自开发文档固定信息架构，点击能力只绑定已登记真实路由。未落地路由保持不可交互，不伪造成功跳转。
- 效果图中的示例 Banner 文案、金额、人数、头像、图片、浏览量和活动结果不得复制；有真实 `coverUrl` 时加载真实媒体，无媒体时使用中性非业务占位。
- `moduleType`、`layoutType`、`itemType`、`coverUrl`、`badges`、`target` 和 `moreTarget` 必须完整消费；`BANNER`、`GRID/QUICK_ACTIONS`、`HORIZONTAL_LIST`、`VERTICAL_LIST`、`NOTICE` 不得统一降级为文字列表。
- `serverTime`、`featureFlags` 和 `trackingContext` 仅参与客户端逻辑与受控诊断，不作为用户可见技术字段。
- 底部导航固定为首页、红包、发布、消息、我的；图标只使用 `HhyIcons`，当前栏目仅以 Token 色和容器状态强调。

## 3. 冻结视觉参数

- 基线画布：360dp 宽；页面背景 `color.background.page` `#F5F7FA`，表面 `color.background.surface` `#FFFFFF`。
- 页面水平边距：`layout.pageHorizontalPaddingDp` 16dp；区块间距：`layout.sectionGapDp` 20dp；卡片内边距 16dp。
- 顶栏内容高：`sizeDp.topAppBarContentHeight` 56dp；底部导航内容高：`sizeDp.bottomNavigationContentHeight` 64dp。
- 页面标题：`typographySp.pageTitle` 20/28/600；正文：`typographySp.body` 14/22/400；导航文字：`typographySp.navigation` 11/16/500。
- 搜索框使用 52dp 输入高度 Token；Banner 使用 16dp 圆角、横向分页和真实媒体；四入口使用等宽语义图标、11-14sp 标签和 12-14dp 圆角容器，顺序固定为项目、App、群聊、团队长。
- 运营内容卡按 B02/P05-P08 保持媒体、类型标签、标题/摘要、真实徽标与导航层级；不得把所有模块压缩成同一种灰色文字行。
- 空模块卡圆角：`radiusDp.normalCard` 14dp；边框与文字分别使用 `color.border.default`、`color.text.secondary`。
- 页面与栏目转场使用 `motionMs.page` 360ms、`motionMs.standard` 200ms 和 `HhyMotion`，禁止页面私有动画数字。

## 4. 状态与证据

| 状态 | 视觉规则 | 参考证据 |
| --- | --- | --- |
| LOADING | 模块区显示与最终布局同宽的结构骨架，顶栏和底栏尺寸不变化 | 页面规格 + Token |
| CONTENT | 顶部、搜索、Banner、四入口和模块区结构完整；按 `moduleType/layoutType` 与服务端顺序展示真实模块 | `B02/P01`、`P02`、`P04-P08` + 页面规格 + Token |
| EMPTY/PARTIAL_CONTENT | 保留标题、副标题和底栏；模块区显示单张空态或局部错误卡，不扩大为全屏营销页 | `tests/android/visual-baselines/R06/01-home-loaded.png` |
| ERROR/OFFLINE | 保留稳定页面骨架，提供商业化重试提示，不显示接口名、错误码或请求编号 | 页面规格 + Token |

## 5. 验收

- 业务合同：`docs/02-ui/page-specs/android/SCR-HOME-001_首页.md`。
- 历史实现截图和基线仅保留为被纠偏证据，不再证明视觉通过：`artifacts/validation/r09-historical-ui/android/screenshots/26-r06-home.png`、`tests/android/visual-baselines/R06/01-home-loaded.png`。
- 修复后必须在 R10 最终候选采集真实加载截图，对照 V1.2 工程执行强化版 4.3 与 `B02/P01/P02/P04-P08` 逐项复核后，才允许在视觉台账恢复 `PASS`。
- 必须保持 1080×2400 像素稳定、页面身份明确、底栏无遮挡，且自动化登录后的首页真实加载完成。
