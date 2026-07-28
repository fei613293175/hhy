package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class R14RealtimeContracts {
    private R14RealtimeContracts() { }

    public enum Scope { CHAT, NOTIFICATIONS }
    public enum ResumeMode { REPLAY_COMPLETE, REST_GAP_FILL }

    public record Delivery(
            UUID eventId,
            long userId,
            long serverSequence,
            String eventType,
            Long conversationId,
            Scope affectedScope,
            String envelopeJson,
            boolean ackRequired,
            int deliveryAttempts,
            Instant lastDeliveredAt,
            Instant expiresAt) { }

    public record GapWatermark(long expiredThroughSequence, Set<Scope> affectedScopes) { }

    public record ResumePlan(
            ResumeMode mode,
            long requestedLastServerSequence,
            long serverHighWatermark,
            long resumeFromServerSequence,
            Set<Scope> affectedScopes,
            List<Delivery> replay) { }

    public record DeliveryCommitted(Delivery delivery) { }

    public static final class SequenceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
