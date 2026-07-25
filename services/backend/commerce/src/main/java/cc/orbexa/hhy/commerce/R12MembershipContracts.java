package cc.orbexa.hhy.commerce;

import java.time.Instant;
import java.util.List;

public final class R12MembershipContracts {
    private R12MembershipContracts() { }

    public record BenefitResource(
            String benefitCode,
            String name,
            Object value,
            String unit) { }

    public record MembershipResource(
            String id,
            String skuId,
            String name,
            String status,
            Instant startsAt,
            Instant expiresAt,
            List<BenefitResource> benefits,
            Long paidValueCent,
            Long remainingValueCent,
            long version) { }
}
