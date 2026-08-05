# R21 两页视觉绑定矩阵

## 视觉合同

- `design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `521138189fbd7dc5a5cf055a0593cec7b558654f3a7b6710266fd304d86f86b0`；已实际查看 B08/P05「红包活动管理页」。
- `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `619f43b70ef7892b00ac985b771efd71111fe7f6c50d77504926f80f303ddb2c`；已实际查看 B03/P01「项目详情页」。
- `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `7b0f226942dd4188a8fa12ad73d64f9789d14823d6b7cf1573a63da2f92faba7`；已实际查看 B09/P01「钱包首页」与 B09/P08「红包余额与明细页」。
- 效果图只约束布局、层级、密度、色彩和组件形态；字段、身份、状态、金额、按钮、权限和接口只来自 R21 产品目录、页面规格及 OpenAPI。

## 绑定矩阵

| Page ID | 平台/路由 | 精确视觉合同 | 目标视口 | 运行截图证据 |
| --- | --- | --- | --- | --- |
| SCR-RP-ADV-005 | Android `/me/red-packet-campaigns/{id}` | B08/P05；`specs/SCR-RP-ADV-005.md` | Pixel 7 / API 35（1080x2400 capture） | `artifacts/reports/R21/visual/SCR-RP-ADV-005.png` |
| SCR-RP-ADV-006 | Android `/me/red-packet-campaigns/{id}/increase` | B03/P01 的媒体/标题层级 + B09/P01、B09/P08 的金额摘要/明细层级；`specs/SCR-RP-ADV-006.md` | Pixel 7 / API 35（1080x2400 capture） | `artifacts/reports/R21/visual/SCR-RP-ADV-006.png` |

## 验收记录

- 视觉合同已冻结：本文件及两个逐页补充规格。
- 逐页运行截图已对照：待 R21 远程 Android 测试生成并逐页归档。
- 页面已通过视觉验收：待运行截图与绑定原图/冻结规格对照后回填 PASS。
