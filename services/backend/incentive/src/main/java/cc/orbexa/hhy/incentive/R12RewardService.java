package cc.orbexa.hhy.incentive;

import cc.orbexa.hhy.incentive.R12RewardContracts.RewardAccountResource;
import cc.orbexa.hhy.shared.api.BusinessException;

public class R12RewardService {
    private final R12RewardStore store;

    public R12RewardService(R12RewardStore store) {
        this.store = store;
    }

    public RewardAccountResource current(long userId) {
        if (userId < 1) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
        R12RewardStore.RewardLookup lookup = store.find(userId)
                .orElseThrow(R12RewardService::notFound);
        if (!"VERIFIED".equals(lookup.identityStatus())) {
            throw new BusinessException(
                    "IDENTITY-422-NOT_VERIFIED", "请先完成实名认证", 422, false);
        }
        if (lookup.riskFrozen()) {
            throw new BusinessException(
                    "REWARD-423-RISK_FROZEN", "奖励账户因风险审核被冻结", 423, false);
        }
        R12RewardStore.RewardAccountRow row = lookup.account();
        if (row == null) throw notFound();
        if (row.pendingCent() < 0 || row.availableCent() < 0 || row.frozenCent() < 0
                || row.withdrawnCent() < 0 || row.version() < 0) {
            throw new IllegalStateException("Reward authority returned a negative balance or version");
        }
        return new RewardAccountResource(
                Long.toString(lookup.userId()), row.pendingCent(), row.availableCent(),
                row.frozenCent(), row.withdrawnCent(), row.version(), row.updatedAt());
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "COMMON-404-NOT_FOUND", "奖励账户不存在", 404, false);
    }
}
