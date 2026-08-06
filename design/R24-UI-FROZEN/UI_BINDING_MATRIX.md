# R24 UI 绑定矩阵

视觉原图：`design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`；Manifest：`design/effect-previews/B09/HHY_B09_MANIFEST.json`；Android 目标视口：1080x2400；管理端目标视口：1440x900。

| Page ID | 精确面板/补充规格 | 业务过滤 | 截图证据 |
|---|---|---|---|
| SCR-REWARD-001 | B09/P01 + `specs/SCR-REWARD-001.md` | 仅奖励账户四类余额；过滤充值、商豆、红包示例 | `artifacts/reports/R24/ui/SCR-REWARD-001.png` |
| SCR-REWARD-002 | B09/P03 + `specs/SCR-REWARD-002.md` | 仅真实 reward ledger；不显示虚构来源 | `artifacts/reports/R24/ui/SCR-REWARD-002.png` |
| SCR-WD-001 | B09/P04 + `specs/SCR-WD-001.md` | 仅支付宝账户真实脱敏字段 | `artifacts/reports/R24/ui/SCR-WD-001.png` |
| SCR-WD-002 | B09/P04 + `specs/SCR-WD-002.md` | 仅报价、手续费、到账金额和实名校验 | `artifacts/reports/R24/ui/SCR-WD-002.png` |
| SCR-WD-003 | B09/P05-P07 + `specs/SCR-WD-003.md` | 仅提现状态机与真实订单字段 | `artifacts/reports/R24/ui/SCR-WD-003.png` |
| ADM-REWARD-001 | `specs/ADM-REWARD-001.md` | B09/P01 密度；后台字段按合同 | `artifacts/reports/R24/ui/ADM-REWARD-001.png` |
| ADM-REWARD-002 | `specs/ADM-REWARD-002.md` | B09/P03 列表层级；后台字段按合同 | `artifacts/reports/R24/ui/ADM-REWARD-002.png` |
| ADM-WD-001 | `specs/ADM-WD-001.md` | B09/P04-P07 状态筛选；不展示示例金额 | `artifacts/reports/R24/ui/ADM-WD-001.png` |
| ADM-WD-002 | `specs/ADM-WD-002.md` | B09/P05-P07 详情与固定操作栏 | `artifacts/reports/R24/ui/ADM-WD-002.png` |
| ADM-ACCOUNTING-001 | `specs/ADM-ACCOUNTING-001.md` | 复式交易/分录字段按 OpenAPI | `artifacts/reports/R24/ui/ADM-ACCOUNTING-001.png` |
| ADM-ACCOUNTING-002 | `specs/ADM-ACCOUNTING-002.md` | 交易详情与冲正审批，不显示技术诊断 | `artifacts/reports/R24/ui/ADM-ACCOUNTING-002.png` |

所有页面复用 `design/tokens/*design-tokens.v1.2.2*`；原图示例用户、金额、商豆、红包和未登记入口均为视觉结构参考，不进入产品事实。
