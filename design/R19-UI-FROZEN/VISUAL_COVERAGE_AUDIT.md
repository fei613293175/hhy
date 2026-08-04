# R19 六页视觉覆盖审计

- 效果图：`design/effect-previews/B11/HHY_B11_8PAGE_UI_REFERENCE.png`
- 效果图 Manifest：`design/effect-previews/B11/HHY_B11_MANIFEST.json`
- 效果图 SHA-256：`16c492cd9fc46c5be435da3979126b8eb67063a641ec9b575074751f67c96778`
- 产品边界：只实现登记的四种道具与曝光权益；B11 中的示例名称、价格、数量、图片、日期和成功文案不是产品事实。
- 后台边界：后台原目录为 `TOKENS_ONLY`，以下补充规格以 `ADM-LIST`、`CMP-ADM-001/002/004/005`、后台 Design Token、字段/状态/动作目录和后台运营规格冻结施工结构。
- 视觉状态：`SPEC_FROZEN_SCREENSHOT_PENDING`。真实运行截图完成逐页对照前，不得声明页面视觉验收通过或交付 APK。

| 页面 | 精确来源/补充规格 | 目标视口 | 截图证据 |
| --- | --- | --- | --- |
| SCR-PROP-001 | B11/P04；`specs/SCR-PROP-001.md` | Pixel 7 / API 35 | `artifacts/reports/R19/visual/android/SCR-PROP-001.png` |
| SCR-PROP-002 | B11/P06；`specs/SCR-PROP-002.md` | Pixel 7 / API 35 | `artifacts/reports/R19/visual/android/SCR-PROP-002.png` |
| SCR-PROP-003 | B11/P07；`specs/SCR-PROP-003.md` | Pixel 7 / API 35 | `artifacts/reports/R19/visual/android/SCR-PROP-003.png` |
| ADM-PROP-001 | `specs/ADM-PROP-001.md`；后台 Design Token + ADM-LIST | 1440x900 | `artifacts/reports/R19/visual/admin/ADM-PROP-001.png` |
| ADM-PROP-002 | `specs/ADM-PROP-002.md`；后台 Design Token + ADM-LIST | 1440x900 | `artifacts/reports/R19/visual/admin/ADM-PROP-002.png` |
| ADM-PROP-003 | `specs/ADM-PROP-003.md`；后台 Design Token + ADM-LIST | 1440x900 | `artifacts/reports/R19/visual/admin/ADM-PROP-003.png` |

字段、状态、动作、权限、接口、金额、排期和审计事实只来自 R19 产品目录、OpenAPI、数据库合同与服务端响应。
