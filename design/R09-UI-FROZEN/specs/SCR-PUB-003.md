# SCR-PUB-003 App发布/编辑冻结视觉规格

- 权威页面合同：`docs/02-ui/page-specs/android/SCR-PUB-003_App发布_编辑.md`
- 精确效果图：`design/effect-previews/B04/HHY_B04_8PAGE_UI_REFERENCE.png` 的 `P04`
- Token：`design/tokens/hhy_design_tokens_v1.2.2.json`
- 批准来源：`CR-0251`
- 状态：`EXACT`

## 固定结构

1. 56dp 返回顶栏；新建标题“发布App推广”，编辑标题“编辑App推广”。
2. 页面背景 `#F5F7FA`，左右边距 16dp；分组卡圆角 14dp、内边距 16dp、卡间距 12dp。
3. 顶部蓝色说明卡保持 P04 的品牌视觉与清晰层级，并明确“只填写真实资料，不上传 APK 安装包”。
4. 字段按 P04 与冻结合同组织为：基础信息、应用介绍、平台与版本、访问方式、应用图片、联系方式。仅提交 `title/summary/description/categoryCode/mediaIds/contacts/attributes` 中已冻结字段。
5. `attributes` 只允许 `appName/platform/versionText/downloadUrl/website`；下载和官网必须是安全 HTTPS。媒体上传仅接受图片，不接受 APK 或其他安装包。
6. 底部固定主按钮高度不低于 48dp；实名不足、加载中、提交中、版本冲突时按合同禁用。提交前必须确认服务端仍会复核权限、版本和链接。

## 肉眼验收

- 必须达到 P04 的品牌头图、分组卡、帮助说明、状态与固定操作丰富度，不得退化为裸输入框长列表。
- 不显示虚构 App 图标、截图、下载量、评分、审核结果或示例联系方式。
- 最终候选截图由 AI 与 P04 逐项对照后才能登记 PASS。
