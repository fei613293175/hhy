# ADM-WD-002 提现详情

- 视觉合同：B09/P05-P07 的详情卡、状态提示和固定操作栏；管理端目标视口 1440x900。
- 运行截图：`artifacts/reports/R24/ui/ADM-WD-002.png`。
- 操作仅允许 `GetById/RiskReview/FinanceReview/Payout/Query`，权限、expectedVersion、幂等和审计由服务端合同决定。
- 状态：LOADING、CONTENT、EMPTY、RECONCILING、PENDING_APPROVAL、CONFLICT、ERROR、OFFLINE、NO_PERMISSION。

