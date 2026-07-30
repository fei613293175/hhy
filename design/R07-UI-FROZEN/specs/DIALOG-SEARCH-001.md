# DIALOG-SEARCH-001 · 清空搜索历史确认 · R07 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`
批准变更：`CR-0202`
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 对话框建模

Material 3 居中确认框 → 20dp 对话框圆角 → “清空搜索历史？”标题 → 不可恢复与当前账号范围说明 → 取消次动作 → 确认清空主动作。背景使用冻结遮罩色，不改变后方搜索页布局和状态。

## 业务过滤

- 文案必须明确“永久清空当前账号”和“其他账号不受影响”，不得弱化不可恢复性。
- 取消不修改历史；确认成功关闭对话框并清空当前账号列表；失败保留可恢复页面状态。
- 提交期间按钮锁定，确认文案切换为“正在清空”，不得重复发送等价意图。

## 冻结视觉参数

- 标题使用页面或卡片标题层级，正文 14/22/400；主次动作使用 15/22/600 和至少 48dp 触控区。
- 对话框使用 `radiusDp.dialog` 20dp、`color.background.surface` 和 `color.overlay.scrim`。
- 所有颜色、间距、圆角、文字和动效从冻结 Token 或 Material 3 主题映射取得。

## 状态与验收

实现截图为 `artifacts/validation/r07-task007-android/screenshots/04-clear-history-dialog.png`，批准基线为 `tests/android/visual-baselines/R07/04-clear-history-dialog.png`。AI 已核对遮罩、标题、风险说明、主次动作、页面上下文和无敏感或技术字段泄漏。
