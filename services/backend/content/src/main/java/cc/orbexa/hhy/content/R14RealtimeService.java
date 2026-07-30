package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R14Contracts.ChatMessage;
import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.DeliveryCommitted;
import cc.orbexa.hhy.content.R14RealtimeContracts.GapWatermark;
import cc.orbexa.hhy.content.R14RealtimeContracts.ResumeMode;
import cc.orbexa.hhy.content.R14RealtimeContracts.ResumePlan;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import cc.orbexa.hhy.content.R14RealtimeContracts.SequenceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R14RealtimeService {
    private static final Duration RETENTION = Duration.ofHours(72);
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);

    private final R14RealtimeStore store;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R14RealtimeService(
            R14RealtimeStore store, ApplicationEventPublisher publisher,
            ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void messagePersisted(long senderId, long peerId, ChatMessage message) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("conversationId", message.conversationId());
        ack.put("clientMessageId", message.clientMessageId());
        ack.put("messageId", message.id());
        ack.put("sentAt", message.createdAt());
        create(senderId, "chat.message.ack", Long.valueOf(message.conversationId()), Scope.CHAT, ack, false);

        Map<String, Object> fresh = new LinkedHashMap<>();
        fresh.put("conversationId", message.conversationId());
        fresh.put("messageId", message.id());
        fresh.put("senderId", Long.toString(senderId));
        fresh.put("messageType", message.messageType());
        fresh.put("payload", message.payload());
        fresh.put("sentAt", message.createdAt());
        create(peerId, "chat.message.new", Long.valueOf(message.conversationId()), Scope.CHAT, fresh, true);
    }

    public void readPersisted(
            long readerId, long peerId, long conversationId, long lastReadMessageId) {
        Map<String, Object> payload = Map.of(
                "conversationId", Long.toString(conversationId),
                "userId", Long.toString(readerId),
                "lastReadMessageId", Long.toString(lastReadMessageId));
        create(readerId, "chat.read.updated", conversationId, Scope.CHAT, payload, false);
        create(peerId, "chat.read.updated", conversationId, Scope.CHAT, payload, false);
    }

    @Transactional
    public ResumePlan resume(long userId, long requestedSequence) {
        if (requestedSequence < 0) throw new SequenceException();
        Instant now = Instant.now(clock);
        store.expireDeliveries(userId, now);
        long high = store.highWatermark(userId);
        if (requestedSequence > high) throw new SequenceException();
        GapWatermark gap = store.gapWatermark(userId).orElse(new GapWatermark(0, Set.of()));
        if (requestedSequence < gap.expiredThroughSequence()) {
            return new ResumePlan(ResumeMode.REST_GAP_FILL, requestedSequence, high, high,
                    gap.affectedScopes(), List.of());
        }
        return new ResumePlan(ResumeMode.REPLAY_COMPLETE, requestedSequence, high, high,
                Set.of(), store.deliveriesAfter(userId, requestedSequence, high, now));
    }

    @Transactional
    public boolean acknowledge(long userId, UUID eventId, long serverSequence) {
        if (serverSequence < 1) return false;
        return store.acknowledge(userId, eventId, serverSequence, Instant.now(clock));
    }

    @Transactional
    public boolean recordDelivered(Delivery delivery) {
        return store.recordDeliveryAttempt(delivery.userId(), delivery.eventId(),
                delivery.serverSequence(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<Delivery> dueRedeliveries(int limit) {
        Instant now = Instant.now(clock);
        return store.dueRedeliveries(now.minus(ACK_TIMEOUT), now, limit);
    }

    private Delivery create(
            long userId, String eventType, Long conversationId, Scope scope,
            Map<String, Object> rawPayload, boolean ackRequired) {
        Instant now = Instant.now(clock);
        long sequence = store.nextSequence(userId, now);
        UUID eventId = UUID.randomUUID();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(rawPayload);
        payload.put("serverSequence", sequence);
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", now);
        envelope.put("serverSequence", sequence);
        if (conversationId != null) envelope.put("conversationId", Long.toString(conversationId));
        envelope.put("ackRequired", ackRequired);
        envelope.put("payload", payload);
        Delivery delivery = store.insert(eventId, userId, sequence, eventType, conversationId,
                scope, json(envelope), ackRequired, now.plus(RETENTION), now);
        publisher.publishEvent(new DeliveryCommitted(delivery));
        return delivery;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize frozen WebSocket envelope", exception);
        }
    }
}
