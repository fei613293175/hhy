# SCR-WD-002 申请提现

- 视觉合同：B09/P04；保留可提现/待结算摘要、支付宝方式、金额输入、报价摘要和固定提交按钮。
- 目标视口：Android 1080x2400；运行截图：`artifacts/reports/R24/ui/SCR-WD-002.png`。
- 仅调用 `withdrawalPostWithdrawalsQuote` 与 `withdrawalPostWithdrawals`；金额、手续费、到账、quoteId 和幂等键不得自造。
- 状态：LOADING、QUOTE_READY、SUBMITTING、PAYMENT_PENDING、SUCCESS、FAILED、UNKNOWN、ERROR、OFFLINE。

