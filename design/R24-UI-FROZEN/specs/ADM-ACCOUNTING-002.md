# ADM-ACCOUNTING-002 会计详情与冲正

- 视觉合同：复用 B09/P05-P07 的详情状态和固定操作区；管理端目标视口 1440x900。
- 运行截图：`artifacts/reports/R24/ui/ADM-ACCOUNTING-002.png`。
- 详情只调用 `adminAccountingGetTransactionById`；冲正只调用 `adminAccountingPostReversal`，原因、证据、审批和 expectedVersion 严格按契约。
- 状态：LOADING、CONTENT、EMPTY、RECONCILING、PENDING_APPROVAL、CONFLICT、ERROR、OFFLINE、NO_PERMISSION。

