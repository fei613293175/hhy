# SCR-SEARCH-002 · 搜索结果 · R07 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`
批准变更：`CR-0202`
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 页面建模

系统状态栏 → 56dp“搜索结果”返回顶栏 → 已提交搜索组合卡（输入 → 类型筛选 → 主搜索按钮）→ 结果卡列表 → 条件式加载更多。页面使用 360dp 基线、16dp 水平边距、12dp 卡片间距和 14dp 卡片圆角。每张结果卡依次展示真实商业类型标签、标题、最多三行摘要、分隔线和真实发布者动作，形成与 B02 内容卡一致的多层信息结构；无字段时直接省略，不使用虚构图片或统计填空。

## 业务过滤

- 只展示搜索接口返回的标题、商业类型、摘要和公开发布者；发布者入口必须进入真实主页。
- 子页面返回后保留关键词、类型和已提交结果；筛选或重新搜索重置游标，分页去重追加。
- 不展示内容内部状态、技术类型值、接口名、请求正文或联系方式明文。

## 冻结视觉参数

- 结果卡使用 `HhyColors.Surface`、`HhySpacing.Lg` 内边距和 `HhySpacing.Sm` 字段间距。
- 标题使用卡片标题层级且最多按业务组件截断；类型使用品牌主色；摘要使用次要文字色并限制三行。
- 加载更多为全宽次按钮；LOADING、EMPTY、ERROR 和 OFFLINE 不改变顶栏及搜索区尺寸。

## 状态与验收

实现截图为 `artifacts/validation/r07-task007-android/screenshots/02-search-results.png`，批准基线为 `tests/android/visual-baselines/R07/02-search-results.png`。AI 已核对页面身份、真实结果夹具、返回栈状态、文字边界和无技术字段泄漏。
