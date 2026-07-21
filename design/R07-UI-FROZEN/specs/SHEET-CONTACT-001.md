# SHEET-CONTACT-001 · 联系方式面板 · R07 精确视觉规格

状态：`FROZEN_VISUAL_SPEC_SECURE`
批准变更：`CR-0202`
Token：`design/tokens/hhy_design_tokens_v1.2.2.json`

## 面板建模

Material 3 模态底部面板 → 24dp 顶部圆角与拖拽柄 → “联系方式”标题 → 内容标题与渠道掩码摘要 → 获取状态或授权结果卡 → 全宽获取/复制动作 → 全宽关闭动作。面板水平内边距 20dp、垂直 16dp、区块间距 12dp。

## 高敏业务过滤

- 打开面板立即启用 `FLAG_SECURE`；关闭时清除本次新增的安全标记。
- 授权前只显示渠道和掩码值；授权后实际值只存在于当前面板内存，可复制但不得写入日志、埋点、Outbox 或持久化缓存。
- 获取动作必须走权限、幂等和不可变访问审计；关闭后清除显示状态。

## 冻结视觉参数

- 授权结果卡使用 `HhyColors.SoftBlue`、14dp 圆角和 16dp 内边距；敏感值使用正文层级，不用夸张金额或营销样式。
- 主次动作高度至少 48dp；提交期间锁定关闭和重复提交并显示“正在获取”。
- 错误态保留面板结构和安全标记，只显示商业恢复提示。

## 安全验收替代证据

本面板禁止生成包含联系方式实际值的截图。实现证据使用 `artifacts/validation/r07-task007-android/candidate-report.json` 与 `artifacts/validation/r07-task007-android/APPROVAL.yaml`：自动化已验证面板标题、唯一语义资源、获取按钮、关闭按钮和 `FLAG_SECURE`，并确认截图目录不存在敏感面板图像。
