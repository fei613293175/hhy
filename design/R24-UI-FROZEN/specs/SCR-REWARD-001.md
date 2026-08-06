# SCR-REWARD-001 奖励账户

- 视觉合同：B09/P01；保留顶部标题、蓝色余额摘要、四项余额分栏、资产明细和底部导航层级。
- 目标视口：Android 1080x2400；运行截图：`artifacts/reports/R24/ui/SCR-REWARD-001.png`。
- 仅映射 `rewardGetMeRewardAccount` 的 `userId/pendingCent/availableCent/frozenCent/withdrawnCent/version/updatedAt`；不实现充值、商豆、红包示例。
- 状态：LOADING、CONTENT、EMPTY、REFRESHING、RISK_FROZEN、ERROR、OFFLINE；写入口按合同权限和状态机计算。

