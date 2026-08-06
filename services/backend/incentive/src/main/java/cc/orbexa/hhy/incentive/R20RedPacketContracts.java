package cc.orbexa.hhy.incentive;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen R20 request and resource shapes shared by HTTP and application code. */
public final class R20RedPacketContracts {
    private R20RedPacketContracts() { }

    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record CampaignResource(
            String id,
            String contentId,
            String ownerUserId,
            String status,
            long totalCount,
            long remainingCount,
            long amountPerClaimCent,
            long principalCent,
            long serviceFeeCent,
            Instant startAt,
            Instant endAt,
            long version) { }

    public record CampaignPage(List<CampaignResource> items, PageMeta page) {
        public CampaignPage { items = List.copyOf(items); }
    }

    public record CreateRequest(
            String contentId,
            Long totalCount,
            Long amountPerClaimCent,
            Instant startAt,
            Instant endAt,
            Map<String, Object> targeting) {
        public CreateRequest {
            targeting = copy(targeting);
        }
    }

    public record PatchRequest(
            Long totalCount,
            Long amountPerClaimCent,
            Instant startAt,
            Instant endAt,
            Map<String, Object> targeting,
            Long expectedVersion) {
        public PatchRequest {
            targeting = targeting == null ? null : copy(targeting);
        }
    }

    public record SubmitReviewRequest(Long expectedVersion, String remark) { }

    public record QuoteRequest(Long totalCount, Long amountPerClaimCent, Long expectedVersion) { }

    public record OrderRequest(String quoteId, String paymentChannel, Long expectedVersion) { }

    /** R21 owner lifecycle command. The reason is retained for audit only. */
    public record LifecycleRequest(String reason, Long expectedVersion) { }

    public record IncreaseQuoteRequest(Long newAmountPerClaimCent, Long expectedVersion) { }

    public record IncreaseOrderRequest(String quoteId, String paymentChannel, Long expectedVersion) { }

    /** R22 user-side browsing task commands. The server owns elapsed time. */
    public record ViewSessionRequest(String clientNonce, String deviceContext) { }

    public record HeartbeatRequest(long clientSequence, long elapsedSeconds, boolean pageVisible) { }

    public record ClaimRequest(String clientNonce, long finalHeartbeatSequence) { }

    public record CancelRequest(String reason) { }

    public record AdminReviewRequest(
            String decision, String reason, Long expectedVersion, List<String> evidenceIds) {
        public AdminReviewRequest {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            long version,
            Instant acceptedAt,
            Long principalCent,
            Long serviceFeeCent,
            Long payableCent,
            Long totalCount,
            Long amountPerClaimCent,
            String quoteType,
            Instant expiresAt,
            Long requiredSeconds,
            Long accumulatedSeconds,
            Long lastHeartbeatSequence,
            Instant lastServerTime) {
        public CommandResultResource(
                String resourceId,
                String businessNo,
                String status,
                long version,
                Instant acceptedAt,
                Long principalCent,
                Long serviceFeeCent,
                Long payableCent,
                Long totalCount,
                Long amountPerClaimCent,
                String quoteType,
                Instant expiresAt) {
            this(resourceId, businessNo, status, version, acceptedAt,
                    principalCent, serviceFeeCent, payableCent, totalCount,
                    amountPerClaimCent, quoteType, expiresAt,
                    null, null, null, null);
        }

        public CommandResultResource(
                String resourceId,
                String businessNo,
                String status,
                long version,
                Instant acceptedAt) {
            this(resourceId, businessNo, status, version, acceptedAt,
                    null, null, null, null, null, null, null,
                    null, null, null, null);
        }
    }

    public record UserCommand(long userId, String operationId, String idempotencyKey, String requestId) { }

    public record AdminCommand(
            long adminId,
            long sessionId,
            String username,
            String operationId,
            String requestId,
            String ip,
            String idempotencyKey) { }

    public interface Codec {
        byte[] canonicalBytes(Object value);

        String json(Object value);

        Map<String, Object> object(String json);
    }

    public static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
