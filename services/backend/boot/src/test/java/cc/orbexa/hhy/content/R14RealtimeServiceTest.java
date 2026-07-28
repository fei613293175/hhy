package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R08Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R14Contracts.ChatMessage;
import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.GapWatermark;
import cc.orbexa.hhy.content.R14RealtimeContracts.ResumeMode;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import cc.orbexa.hhy.content.R14RealtimeContracts.SequenceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class R14RealtimeServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");
    @Mock private R14RealtimeStore store;
    @Mock private ApplicationEventPublisher publisher;
    private R14RealtimeService service;

    @BeforeEach
    void setUp() {
        service = new R14RealtimeService(store, publisher,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void messageCreatesSenderAckAndPeerDeliveryWithIndependentUserSequences() {
        when(store.nextSequence(11, NOW)).thenReturn(4L);
        when(store.nextSequence(12, NOW)).thenReturn(9L);
        when(store.insert(any(), eq(11L), eq(4L), eq("chat.message.ack"), eq(42L),
                eq(Scope.CHAT), any(), eq(false), eq(NOW.plusSeconds(72 * 3600)), eq(NOW)))
                .thenAnswer(invocation -> delivery(invocation.getArgument(0), 11, 4,
                        "chat.message.ack", false, invocation.getArgument(6)));
        when(store.insert(any(), eq(12L), eq(9L), eq("chat.message.new"), eq(42L),
                eq(Scope.CHAT), any(), eq(true), eq(NOW.plusSeconds(72 * 3600)), eq(NOW)))
                .thenAnswer(invocation -> delivery(invocation.getArgument(0), 12, 9,
                        "chat.message.new", true, invocation.getArgument(6)));
        ChatMessage message = new ChatMessage("101", "42",
                new PublisherSummary("11", "甲", null, null, false, null, null),
                "client-1", "TEXT", Map.of("text", "你好"), "SENT", null, NOW, null);

        service.messagePersisted(11, 12, message);

        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(any(Object.class));
        verify(store).insert(any(), eq(12L), eq(9L), eq("chat.message.new"), eq(42L),
                eq(Scope.CHAT), org.mockito.ArgumentMatchers.contains("\"serverSequence\":9"),
                eq(true), any(), eq(NOW));
    }

    @Test
    void expiredCursorRequiresOnlyRecordedRestScopesAndSafeWatermark() {
        when(store.highWatermark(11)).thenReturn(20L);
        when(store.gapWatermark(11)).thenReturn(Optional.of(
                new GapWatermark(7, Set.of(Scope.CHAT))));

        var plan = service.resume(11, 6);

        assertEquals(ResumeMode.REST_GAP_FILL, plan.mode());
        assertEquals(20, plan.resumeFromServerSequence());
        assertEquals(Set.of(Scope.CHAT), plan.affectedScopes());
        assertTrue(plan.replay().isEmpty());
        verify(store).expireDeliveries(11, NOW);
    }

    @Test
    void futureCursorAndMismatchedAckAreRejected() {
        when(store.highWatermark(11)).thenReturn(3L);
        assertThrows(SequenceException.class, () -> service.resume(11, 4));
        UUID eventId = UUID.randomUUID();
        when(store.acknowledge(11, eventId, 3, NOW)).thenReturn(false);
        assertEquals(false, service.acknowledge(11, eventId, 3));
    }

    private static Delivery delivery(
            UUID eventId, long userId, long sequence, String type,
            boolean ackRequired, String envelope) {
        return new Delivery(eventId, userId, sequence, type, 42L, Scope.CHAT,
                envelope, ackRequired, 0, null, NOW.plusSeconds(72 * 3600));
    }
}
