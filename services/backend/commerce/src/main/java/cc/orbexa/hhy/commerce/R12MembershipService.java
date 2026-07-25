package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.shared.api.BusinessException;

public class R12MembershipService {
    private final R12MembershipStore store;

    public R12MembershipService(R12MembershipStore store) {
        this.store = store;
    }

    public MembershipResource current(long userId) {
        if (userId < 1) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
        R12MembershipStore.MembershipRow row = store.findCurrent(userId)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-404-NOT_FOUND", "当前账号没有会员记录", 404, false));
        if (row.status() == null || row.status().isBlank() || row.version() < 0) {
            throw new IllegalStateException("Membership authority returned an invalid status or version");
        }
        return new MembershipResource(
                Long.toString(row.id()), row.skuId(), row.name(), row.status(),
                row.startsAt(), row.expiresAt(), row.benefits(),
                row.paidValueCent(), row.remainingValueCent(), row.version());
    }
}
