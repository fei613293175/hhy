# SCR-SEARCH-001 · 全局搜索 · R07 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`
批准变更：`CR-0202`
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 页面建模

系统状态栏 → 56dp 返回顶栏 → 单行搜索输入 → 内容类型筛选 → 48dp 主搜索按钮 → 最近搜索与热词区。页面使用 360dp 基线、16dp 水平边距、12dp 卡片间距和 `color.background.page`；不得增加营销 Hero、虚构数量或临时入口。

## 业务过滤

- 搜索历史只显示当前账号事实，热词只显示服务端启用项；两者为空时显示单卡空状态。
- 类型顺序固定为全部、项目、应用、群聊、团长；输入限制 1–100 字符。
- 页面不展示接口名、TraceId、内部枚举或联系方式明文。

## 冻结视觉参数

- 顶栏 56dp，主按钮 48dp，最小触控区 48dp；输入框使用标准 Material 3 单行字段。
- 页面内容间距使用 `HhySpacing.Lg/Md/Sm`；空态卡圆角使用 `HhyRadius.NormalCard` 14dp。
- 页面标题 20/28/600，卡片标题 16/24/600，正文 14/22/400，次要文字使用 `color.text.secondary`。
- 页面背景、表面、品牌色、边框和状态色只允许使用 `HhyColors` 与冻结 Token。

## 状态与验收

LOADING 保持输入、筛选和按钮骨架；CONTENT 按历史后热词顺序展示；EMPTY 使用商业空态；ERROR/OFFLINE 保留安全输入并提供重试。实现截图为 `artifacts/validation/r07-task007-android/screenshots/01-search-landing.png`，批准基线为 `tests/android/visual-baselines/R07/01-search-landing.png`。
