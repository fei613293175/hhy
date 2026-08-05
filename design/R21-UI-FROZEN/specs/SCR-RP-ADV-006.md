# SCR-RP-ADV-006 提高红包金额视觉施工规格

## 绑定

- 精确结构合同：B03/P01「项目详情页」的顶部媒体/标题与主操作层级；B09/P01「钱包首页」与 B09/P08「红包余额与明细页」的金额摘要、费用明细和底部主操作密度。
- 原图：`design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`、`design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`；目标为 Pixel 7 / API 35（1080x2400 capture）。
- 产品事实：`docs/02-ui/page-specs/android/SCR-RP-ADV-006_提高红包金额.md`、`catalogs/ui_page_fields.csv`、`catalogs/ui_page_states.csv`、`catalogs/ui_action_matrix.csv`、`contracts/openapi.yaml`。

## 保留结构

1. 顶部返回、页面标题「提高红包金额」和活动上下文；首屏直接展示服务端报价状态，不以 URL 或本地输入作为金额事实。
2. 主体按「商品/金额摘要 | 权益/费用 | 协议确认 | 支付方式 | 结果查询」顺序组织：金额摘要采用高对比主数值和清晰的本金/服务费/最终金额分行；协议与支付方式使用可读、可访问的选择控件。
3. 固定主操作区保持最终金额可见，主按钮只在 QUOTE_READY 且本地字段、权限、版本均有效时启用；提交、支付处理中、未知结果原位反馈。
4. LOADING、QUOTE_READY、SUBMITTING、PAYMENT_PENDING、SUCCESS、FAILED、UNKNOWN、INELIGIBLE、ERROR、OFFLINE 等状态保持同一结构骨架；敏感输入成功后清除。

## 产品映射

- 报价使用 `redPacketPostRedPacketCampaignsByIdIncreaseQuotes`，下单使用 `redPacketPostRedPacketCampaignsByIdIncreaseOrders`。
- 字段只使用 `id`、`newAmountPerClaimCent`、`expectedVersion`、`quoteVersion`、`quoteId`、`paymentChannel`、`X-Idempotency-Key` 和服务端响应中的金额/费用/过期时间；最终金额、服务费和报价版本由服务端返回。
- 同一业务意图保持幂等键与请求体稳定；报价过期或 409 版本冲突时重新报价，不重复创建订单或扣款。

## 禁止

- 不复制 B03/B09 的示例用户、钱包余额、提现入口、商豆、虚构订单号或示例金额。
- 不在客户端推导服务费、最终金额、订单状态或支付成功；不展示 R21 未登记的充值钱包和提现动作。
