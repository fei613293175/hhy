package cc.orbexa.hhy.boot.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.DeliveryCommitted;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import cc.orbexa.hhy.content.R14RealtimeService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class R14RealtimeTransactionBoundaryTest {
    @Test
    void listenerIsAfterCommitOnlyAndDoesNotFallbackWithoutTransaction() throws Exception {
        Method method = R14RealtimeDeliveryListener.class
                .getMethod("afterCommit", DeliveryCommitted.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
        assertFalse(annotation.fallbackExecution());
    }

    @Test
    void failedPostCommitPushLeavesPersistedDeliveryForRetry() {
        R14WebSocketSessionRegistry sessions = org.mockito.Mockito.mock(R14WebSocketSessionRegistry.class);
        R14RealtimeService realtime = org.mockito.Mockito.mock(R14RealtimeService.class);
        R14RealtimeDeliveryListener listener = new R14RealtimeDeliveryListener(sessions, realtime);
        Delivery delivery = new Delivery(UUID.randomUUID(), 11, 1, "chat.message.new", 42L,
                Scope.CHAT, "{}", true, 0, null, Instant.parse("2026-07-31T04:00:00Z"));
        when(sessions.sendToUser(11, "{}")).thenReturn(0);

        listener.afterCommit(new DeliveryCommitted(delivery));

        verify(realtime, never()).recordDelivered(delivery);
    }
}
