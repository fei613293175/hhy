package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request and page shapes for the frozen R18 membership API. */
public final class R18MembershipContracts {
    private R18MembershipContracts() { }

    public record PageMeta(
            long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record MembershipPage(List<MembershipResource> items, PageMeta page) {
        public MembershipPage {
            items = List.copyOf(items);
        }
    }

    public record MembershipOrderRequest(String skuId, String paymentChannel) { }

    public record MembershipUpgradeQuoteRequest(String targetSkuId) { }

    public record MembershipUpgradeOrderRequest(String quoteId, String paymentChannel) { }

    public record MembershipSkuPatchRequest(
            String name,
            Long priceCent,
            Long durationDays,
            Map<String, Object> benefits,
            String status,
            Long expectedVersion) {
        public MembershipSkuPatchRequest {
            benefits = benefits == null ? null
                    : Collections.unmodifiableMap(new LinkedHashMap<>(benefits));
        }
    }

    public record MembershipBenefitsPutRequest(
            String name,
            Long priceCent,
            Long durationDays,
            Map<String, Object> benefits,
            String status,
            Long expectedVersion) {
        public MembershipBenefitsPutRequest {
            benefits = benefits == null ? null
                    : Collections.unmodifiableMap(new LinkedHashMap<>(benefits));
        }
    }

    public record MembershipGrantRequest(
            String userId, String skuId, Long durationDays, String reason) { }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            long version,
            Instant acceptedAt) { }

    public record AdminActorContext(
            long adminId,
            long sessionId,
            String username,
            String operationId,
            String requestId,
            String ip) { }
}
