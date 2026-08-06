# ADM-WD-001 提现列表

- 视觉合同：B09/P04-P07 的状态和金额层级；管理端目标视口 1440x900。
- 运行截图：`artifacts/reports/R24/ui/ADM-WD-001.png`。
- 严格调用 `adminWithdrawalsGetWithdrawals`；筛选、分页、状态和金额字段来自契约，敏感账户脱敏。
- 状态：LOADING、CONTENT、EMPTY、RECONCILING、PENDING_APPROVAL、CONFLICT、ERROR、OFFLINE、NO_PERMISSION。

