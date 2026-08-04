package cc.orbexa.hhy.commerce;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Request and response shapes for the frozen R19 prop and exposure APIs. */
public final class R19PropContracts {
    private R19PropContracts() { }

    public record PageMeta(
            long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record PropResource(
            String id,
            String propType,
            String name,
            long quantity,
            String status,
            Instant expiresAt,
            Map<String, Object> configuration,
            long version) {
        public PropResource {
            configuration = configuration == null
                    ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        }
    }

    public record PropPage(List<PropResource> items, PageMeta page) {
        public PropPage { items = List.copyOf(items); }
    }

    public record PropOrderRequest(String skuId, Long quantity, String paymentChannel) { }

    public record PropUseRequest(
            String targetContentId, Instant scheduledAt, Long expectedVersion) { }

    public record PropPatchRequest(
            String reason, Long expectedVersion, Map<String, Object> payload) {
        public PropPatchRequest {
            payload = payload == null
                    ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public record PropCreateRequest(
            String propType, String name, Long durationSeconds, String executionType,
            String productCode, String skuCode, Long priceCent, Long memberPriceCent,
            Map<String, Object> scope, String status, String reason) {
        public PropCreateRequest {
            scope = scope == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(scope));
        }
    }

    public record HeadlineSlotRequest(
            String reason, Long expectedVersion, Map<String, Object> payload) {
        public HeadlineSlotRequest {
            payload = payload == null
                    ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

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
