# ADM-REWARD-001 奖励账户

- 视觉合同：B09/P01 的蓝色资产摘要与紧凑分栏结构；管理端目标视口 1440x900。
- 运行截图：`artifacts/reports/R24/ui/ADM-REWARD-001.png`。
- 字段与操作严格来自 `adminRewardsGetRewardAccounts`、`adminRewardsGetRewardAccountsByUseridLedger`、`adminRewardsPostRewardAdjustments`；敏感字段脱敏，高敏读取/写入审计。
- 状态：LOADING、CONTENT、EMPTY、RECONCILING、PENDING_APPROVAL、CONFLICT、ERROR、OFFLINE、NO_PERMISSION。

