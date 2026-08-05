# R20 六页视觉绑定矩阵

## 视觉合同

- `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `619f43b70ef7892b00ac985b771efd71111fe7f6c50d77504926f80f303ddb2c`。
- `design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `521138189fbd7dc5a5cf055a0593cec7b558654f3a7b6710266fd304d86f86b0`。
- `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`，Manifest SHA-256 `7b0f226942dd4188a8fa12ad73d64f9789d14823d6b7cf1573a63da2f92faba7`。
- 效果图只约束布局、层级、密度、色彩和组件形态；字段、身份、状态、金额、按钮、权限和接口只来自 R20 产品目录及 OpenAPI。
- B08/P05、B03/P01、B03/P08、B09/P08 已实际查看；后台两页原目录为 `TOKENS_ONLY`，以补充规格、ADM-LIST/ADM-REVIEW 模板及后台 Design Token 冻结施工。

## 绑定矩阵

| Page ID | 平台/路由 | 精确视觉合同 | 目标视口 | 运行截图证据 |
| --- | --- | --- | --- | --- |
| SCR-RP-ADV-001 | Android `/me/red-packet-campaigns` | B08/P05；`specs/SCR-RP-ADV-001.md` | Pixel 7 / API 35 | `artifacts/reports/R20/visual/android/SCR-RP-ADV-001.png` |
| SCR-RP-ADV-002 | Android `/me/red-packet-campaigns/create` | B03/P01 内容详情层级 + B09/P08 红包金额摘要；`specs/SCR-RP-ADV-002.md` | Pixel 7 / API 35 | `artifacts/reports/R20/visual/android/SCR-RP-ADV-002.png` |
| SCR-RP-ADV-003 | Android `/me/red-packet-campaigns/{id}/review` | B03/P08；`specs/SCR-RP-ADV-003.md` | Pixel 7 / API 35 | `artifacts/reports/R20/visual/android/SCR-RP-ADV-003.png` |
| SCR-RP-ADV-004 | Android `/me/red-packet-campaigns/{id}/quote` | B09/P08；`specs/SCR-RP-ADV-004.md` | Pixel 7 / API 35 | `artifacts/reports/R20/visual/android/SCR-RP-ADV-004.png` |
| ADM-RP-001 | Admin `/red-packets` | `TOKENS_ONLY` + ADM-LIST；`specs/ADM-RP-001.md` | 1440x900 | `artifacts/reports/R20/visual/admin/ADM-RP-001.png` |
| ADM-RP-003 | Admin `/red-packets/review` | `TOKENS_ONLY` + ADM-REVIEW；`specs/ADM-RP-003.md` | 1440x900 | `artifacts/reports/R20/visual/admin/ADM-RP-003.png` |

## 验收记录

- 视觉合同已冻结：本文件及 6 个逐页补充规格。
- 逐页运行截图已对照：待 R20 真实运行证据生成。
- 页面已通过视觉验收：待逐页对照通过后回填，不以编译或单元测试替代。
