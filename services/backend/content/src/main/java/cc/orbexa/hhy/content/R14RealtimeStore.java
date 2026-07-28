package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.GapWatermark;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface R14RealtimeStore {
    long nextSequence(long userId, Instant now);

    Delivery insert(
            UUID eventId, long userId, long serverSequence, String eventType,
            Long conversationId, Scope scope, String envelopeJson,
            boolean ackRequired, Instant expiresAt, Instant now);

    long highWatermark(long userId);
    List<Delivery> deliveriesAfter(long userId, long sequence, long through, Instant now);
    Optional<GapWatermark> gapWatermark(long userId);
    void expireDeliveries(long userId, Instant now);
    boolean acknowledge(long userId, UUID eventId, long serverSequence, Instant now);
    boolean recordDeliveryAttempt(long userId, UUID eventId, long serverSequence, Instant now);
    List<Delivery> dueRedeliveries(Instant dueBefore, Instant now, int limit);
}
