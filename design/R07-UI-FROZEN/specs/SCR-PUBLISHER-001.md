# SCR-PUBLISHER-001 · 发布者主页 · R07 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`
批准变更：`CR-0202`
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 页面建模

系统状态栏 → 56dp“发布者主页”返回顶栏 → 发布者资料卡 → “公开内容”区标题 → 内容卡列表 → 条件式加载更多。资料卡上部使用 SoftBlue 品牌底、白色圆形头像占位、昵称及真实认证/会员标签，下部仅在服务端返回简介时展示“个人简介”；内容卡依次展示真实类型标签、标题、摘要、分隔线和可用联系动作。不得增加关注数、虚构图片或社交指标。

## 业务过滤

- 只显示公开资料和已公开内容；认证、会员标识、简介及内容均以服务端事实为准。
- 内容卡只显示标题、商业类型、摘要和可用的掩码联系方式入口。
- 联系方式实际值不得在主页、截图、日志或持久化缓存中出现。

## 冻结视觉参数

- 页面使用 360dp 基线、16dp 水平边距、12dp 列表间距和 `color.background.page`。
- 资料卡和内容卡使用 `HhyColors.Surface`、16dp 内边距、14dp 圆角；头像占位使用 `HhyColors.SoftBlue` 与圆形裁切。
- 标题、正文、状态色和品牌色均来自冻结 Typography 与 `HhyColors`；所有动作满足 48dp 最小触控区。

## 状态与验收

LOADING 保留顶栏；EMPTY 显示“暂无公开内容”单卡；ERROR/OFFLINE 保留已加载公开资料并提供局部重试。实现截图为 `artifacts/validation/r07-task007-android/screenshots/03-publisher.png`，批准基线为 `tests/android/visual-baselines/R07/03-publisher.png`。
