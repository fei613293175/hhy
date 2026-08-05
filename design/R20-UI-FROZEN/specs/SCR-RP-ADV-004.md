# SCR-RP-ADV-004 红包报价确认视觉施工规格

## 绑定

- 精确面板：B09/P08「红包余额与明细」，复用橙色红包金额摘要、分段金额信息和底部固定操作层级。
- 原图：`design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`。目标为 Pixel 7 / API 35。

## 保留结构

1. 顶部返回、标题「红包报价确认」和活动上下文。
2. 橙色金额摘要仅展示真实本金、服务费和最终应付金额；下方为报价有效期、数量、单个金额和不可退款提示。
3. 底部固定「确认并创建订单」主按钮与返回/重新报价次操作；提交、支付处理中和未知结果均原位反馈。

## 产品映射

- 报价调用 `redPacketPostRedPacketCampaignsByIdQuote`，下单调用 `redPacketPostRedPacketCampaignsByIdOrders`；请求严格使用 `totalCount`、`amountPerClaimCent`、`expectedVersion`、`quoteId`、`paymentChannel` 和 `X-Idempotency-Key`。
- QUOTE_READY、SUBMITTING、PAYMENT_PENDING、SUCCESS、FAILED、UNKNOWN、EXPIRED、OFFLINE 均保留金额上下文；服务端报价是金额唯一事实源。

## 禁止

- 不复制 B09/P08 的余额数字、用户资产、提现入口或示例明细，不在客户端推导服务费和最终金额。
