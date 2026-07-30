package cc.orbexa.hhy.boot.realtime;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.R14Contracts.ReadRequest;
import cc.orbexa.hhy.content.R14Contracts.SendMessageRequest;
import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.ResumeMode;
import cc.orbexa.hhy.content.R14RealtimeContracts.ResumePlan;
import cc.orbexa.hhy.content.R14RealtimeContracts.SequenceException;
import cc.orbexa.hhy.content.R14RealtimeService;
import cc.orbexa.hhy.content.R14Service;
import cc.orbexa.hhy.content.R14Store;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class R14WebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private static final CloseStatus BAD_ENVELOPE = new CloseStatus(4400, "Invalid event");
    private static final CloseStatus UNAUTHENTICATED = new CloseStatus(4401, "Session unavailable");
    private static final CloseStatus FORBIDDEN = new CloseStatus(4403, "Action unavailable");
    private static final CloseStatus SEQUENCE_GAP = new CloseStatus(4409, "Sequence unavailable");
    private static final CloseStatus RATE_LIMITED = new CloseStatus(4429, "Please retry later");

    private final R14RealtimeService realtime;
    private final R14Service chat;
    private final R14Store chatStore;
    private final R14WebSocketSessionRegistry sessions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R14WebSocketHandler(
            R14RealtimeService realtime, R14Service chat, R14Store chatStore,
            R14WebSocketSessionRegistry sessions, ObjectMapper mapper, Clock clock) {
        this.realtime = realtime;
        this.chat = chat;
        this.chatStore = chatStore;
        this.sessions = sessions;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<String> getSubProtocols() { return List.of("hhy.v1"); }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UserPrincipal principal = principal(session);
        if (principal == null) {
            session.close(UNAUTHENTICATED);
            return;
        }
        long requested = requestedSequence(session);
        try {
            ResumePlan plan = realtime.resume(principal.userId(), requested);
            for (Delivery delivery : plan.replay()) {
                if (!sessions.send(session, delivery.envelopeJson())) {
                    session.close(new CloseStatus(1011, "Delivery unavailable"));
                    return;
                }
                realtime.recordDelivered(delivery);
            }
            if (!sessions.send(session, resumeEnvelope(plan))) {
                session.close(new CloseStatus(1011, "Resume unavailable"));
                return;
            }
            sessions.register(principal.userId(), session);
            ResumePlan catchUp = realtime.resume(principal.userId(), plan.serverHighWatermark());
            if (catchUp.mode() == ResumeMode.REPLAY_COMPLETE) {
                for (Delivery delivery : catchUp.replay()) {
                    if (sessions.send(session, delivery.envelopeJson())) realtime.recordDelivered(delivery);
                }
            } else {
                sessions.send(session, resumeEnvelope(catchUp));
            }
        } catch (SequenceException exception) {
            session.close(SEQUENCE_GAP);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserPrincipal principal = principal(session);
        if (principal == null) {
            session.close(UNAUTHENTICATED);
            return;
        }
        try {
            JsonNode envelope = mapper.readTree(message.getPayload());
            UUID commandId = UUID.fromString(requiredText(envelope, "eventId"));
            Instant.parse(requiredText(envelope, "occurredAt"));
            String type = requiredText(envelope, "eventType");
            JsonNode payload = envelope.path("payload");
            if (!payload.isObject()) throw new IllegalArgumentException();
            switch (type) {
                case "system.delivery.ack" -> acknowledge(principal.userId(), payload);
                case "system.ping" -> pong(session, payload);
                case "chat.message.send" -> sendMessage(principal.userId(), commandId, payload);
                case "chat.message.read" -> markRead(principal.userId(), commandId, payload);
                case "chat.typing" -> typing(principal.userId(), payload);
                default -> throw new IllegalArgumentException();
            }
        } catch (BusinessException exception) {
            session.close(closeFor(exception));
        } catch (SequenceException exception) {
            session.close(SEQUENCE_GAP);
        } catch (RuntimeException exception) {
            session.close(BAD_ENVELOPE);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        UserPrincipal principal = principal(session);
        if (principal != null) sessions.unregister(principal.userId(), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UserPrincipal principal = principal(session);
        if (principal != null) sessions.unregister(principal.userId(), session);
        if (session.isOpen()) session.close(new CloseStatus(1011, "Transport unavailable"));
    }

    private void acknowledge(long userId, JsonNode payload) {
        UUID eventId = UUID.fromString(requiredText(payload, "eventId"));
        long sequence = requiredPositiveLong(payload, "serverSequence");
        if (!realtime.acknowledge(userId, eventId, sequence)) throw new SequenceException();
    }

    private void pong(WebSocketSession session, JsonNode payload) {
        Instant.parse(requiredText(payload, "clientTime"));
        sendEphemeral(session, "system.pong", Map.of("serverTime", Instant.now(clock)));
    }

    private void sendMessage(long userId, UUID commandId, JsonNode payload) {
        String conversationId = requiredId(payload, "conversationId");
        String clientMessageId = requiredText(payload, "clientMessageId");
        String messageType = requiredText(payload, "messageType");
        Map<String, Object> body = mapper.convertValue(
                payload.path("payload"), new TypeReference<Map<String, Object>>() { });
        chat.send(userId, conversationId,
                new SendMessageRequest(clientMessageId, messageType, body), "ws:" + commandId);
    }

    private void markRead(long userId, UUID commandId, JsonNode payload) {
        String conversationId = requiredId(payload, "conversationId");
        String lastRead = requiredId(payload, "lastReadMessageId");
        requiredText(payload, "clientMessageId");
        chat.read(userId, conversationId, new ReadRequest(lastRead), "ws:" + commandId);
    }

    private void typing(long userId, JsonNode payload) {
        long conversationId = Long.parseLong(requiredId(payload, "conversationId"));
        if (!Long.toString(userId).equals(requiredId(payload, "userId"))) {
            throw new BusinessException("COMMON-403-FORBIDDEN", "Action unavailable", 403, false);
        }
        if (!payload.path("typing").isBoolean()) throw new IllegalArgumentException();
        boolean typing = payload.path("typing").booleanValue();
        Instant.parse(requiredText(payload, "expiresAt"));
        R14Store.MembershipRow membership = chatStore.membership(conversationId, userId, false)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-403-FORBIDDEN", "Action unavailable", 403, false));
        sendEphemeral(membership.peerId(), "chat.typing", Map.of(
                "conversationId", Long.toString(conversationId),
                "userId", Long.toString(userId),
                "typing", typing,
                "expiresAt", Instant.now(clock).plusSeconds(5)));
    }

    private String resumeEnvelope(ResumePlan plan) {
        return json(envelope("system.resume", Map.of(
                "mode", plan.mode().name(),
                "requestedLastServerSequence", plan.requestedLastServerSequence(),
                "serverHighWatermark", plan.serverHighWatermark(),
                "resumeFromServerSequence", plan.resumeFromServerSequence(),
                "affectedScopes", plan.affectedScopes().stream().map(Enum::name).sorted().toList())));
    }

    private void sendEphemeral(WebSocketSession session, String type, Map<String, Object> payload) {
        if (!sessions.send(session, json(envelope(type, payload)))) throw new IllegalStateException();
    }

    private void sendEphemeral(long userId, String type, Map<String, Object> payload) {
        sessions.sendToUser(userId, json(envelope(type, payload)));
    }

    private Map<String, Object> envelope(String type, Map<String, Object> payload) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", UUID.randomUUID().toString());
        result.put("eventType", type);
        result.put("occurredAt", Instant.now(clock));
        result.put("ackRequired", false);
        result.put("payload", payload);
        return result;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static UserPrincipal principal(WebSocketSession session) {
        Object value = session.getAttributes().get(R14WebSocketHandshakeInterceptor.USER_ATTRIBUTE);
        return value instanceof UserPrincipal principal ? principal : null;
    }

    private static long requestedSequence(WebSocketSession session) {
        Object value = session.getAttributes().get(R14WebSocketHandshakeInterceptor.SEQUENCE_ATTRIBUTE);
        return value instanceof Long sequence ? sequence : 0;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException();
        }
        return value.textValue();
    }

    private static String requiredId(JsonNode node, String field) {
        String value = requiredText(node, field);
        if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
        return value;
    }

    private static long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() < 1) {
            throw new IllegalArgumentException();
        }
        return value.longValue();
    }

    private static CloseStatus closeFor(BusinessException exception) {
        return switch (exception.httpStatus()) {
            case 401 -> UNAUTHENTICATED;
            case 403, 404 -> FORBIDDEN;
            case 429 -> RATE_LIMITED;
            default -> BAD_ENVELOPE;
        };
    }
}
