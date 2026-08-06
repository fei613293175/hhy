# R22 UI Binding Matrix

R22 页面视觉合同冻结在实现前建立。视觉原图只约束布局、层级、密度和组件形态；字段、状态、动作、文案和身份只来自正式页面规格、OpenAPI、配置目录和故事合同。

| Page ID | Target route | Exact visual source and panel | Frozen supplement | Target viewport | Runtime screenshot evidence | Status |
| --- | --- | --- | --- | --- | --- | --- |
| SCR-RP-001 | `/red-packet` | `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png` B03/P01 top content hierarchy; `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png` B09/P01 account summary and B09/P08 red-packet amount/list density | `design/R22-UI-FROZEN/specs/SCR-RP-001.md` | Pixel 7 / API 35, 1080x2400 | `artifacts/reports/R22/visual/SCR-RP-001.png` | PASS — runtime screenshot compared; no overlap |
| SCR-RP-002 | `/red-packet/{id}/eligibility` | B03/P01 detail header and fixed primary action; B09/P08 amount summary | `design/R22-UI-FROZEN/specs/SCR-RP-002.md` | Pixel 7 / API 35, 1080x2400 | `artifacts/reports/R22/visual/SCR-RP-002.png` | PASS — runtime screenshot compared; no overlap |
| SCR-RP-003 | `/red-packet/{id}/view` | B03/P01 detail header/content hierarchy; B09/P08 amount/detail hierarchy and fixed action | `design/R22-UI-FROZEN/specs/SCR-RP-003.md` | Pixel 7 / API 35, 1080x2400 | `artifacts/reports/R22/visual/SCR-RP-003.png` | PASS — runtime screenshot compared; no overlap |
| SHEET-RP-001 | `sheet://red-packet-claim` | B03/P06 standard bottom-sheet structure; B09/P08 monetary summary density | `design/R22-UI-FROZEN/specs/SHEET-RP-001.md` | Pixel 7 / API 35, 1080x2400 | `artifacts/reports/R22/visual/SHEET-RP-001.png` | PASS — runtime screenshot compared; no overlap |

H5 and Admin are explicitly N/A for R22. The four PASS entries are backed by the runtime screenshots and comparison record in `artifacts/reports/R22/visual/SCREENSHOT_COMPARISON.md`.
