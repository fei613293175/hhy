package cc.orbexa.hhy.commerce;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java shapes matching the frozen R17 payment contracts. */
public final class R17PaymentContracts {
    private R17PaymentContracts() { }

    public record PaymentResource(
            String id,
            String orderNo,
            String gateway,
            String gatewayTradeNo,
            String status,
            long amountCent,
            String currency,
            Instant paidAt,
            String reconciliationStatus,
            long version) { }

    public record PageMeta(
            long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record PaymentPage(List<PaymentResource> items, PageMeta page) {
        public PaymentPage {
            items = List.copyOf(items);
        }
    }

    public record CreatePaymentRequest(String gateway, String returnUrl) { }

    public record ProviderNotification(
            String notificationId,
            String eventType,
            String orderNo,
            Long amountCent,
            String currency,
            String status,
            Map<String, Object> rawPayload) {
        public ProviderNotification {
            rawPayload = rawPayload == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(rawPayload));
        }
    }

    public record ProviderCallbackContext(
            String gateway,
            String timestamp,
            String nonce,
            String signature,
            String idempotencyKey,
            String rawBody) { }

    public record AdminPaymentQueryRequest(
            String reason, Long expectedVersion, Map<String, Object> payload) {
        public AdminPaymentQueryRequest {
            payload = payload == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public record ResolvePaymentExceptionRequest(
            String resolution, String reason, long expectedVersion) { }

    public record ReconciliationRequest(String parameters, Boolean dryRun) { }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            Long version,
            Instant acceptedAt) { }

    public record UserCommandContext(
            long userId, String operationId, String requestId, String idempotencyKey) { }

    public record AdminCommandContext(
            long adminId,
            long sessionId,
            String username,
            String operationId,
            String requestId,
            String ip,
            String idempotencyKey) { }
}
