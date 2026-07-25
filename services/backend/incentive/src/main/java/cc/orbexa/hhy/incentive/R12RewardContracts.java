package cc.orbexa.hhy.incentive;

import java.time.Instant;

public final class R12RewardContracts {
    private R12RewardContracts() { }

    public record RewardAccountResource(
            String userId,
            long pendingCent,
            long availableCent,
            long frozenCent,
            long withdrawnCent,
            long version,
            Instant updatedAt) { }
}
