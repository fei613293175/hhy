package cc.orbexa.hhy.boot.realtime;

import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.DeliveryCommitted;
import cc.orbexa.hhy.content.R14RealtimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class R14RealtimeDeliveryListener {
    private final R14WebSocketSessionRegistry sessions;
    private final R14RealtimeService realtime;

    public R14RealtimeDeliveryListener(
            R14WebSocketSessionRegistry sessions, R14RealtimeService realtime) {
        this.sessions = sessions;
        this.realtime = realtime;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(DeliveryCommitted event) {
        deliver(event.delivery());
    }

    @Scheduled(fixedDelay = 1000L)
    public void redeliverDueEvents() {
        realtime.dueRedeliveries(200).forEach(this::deliver);
    }

    private void deliver(Delivery delivery) {
        if (sessions.sendToUser(delivery.userId(), delivery.envelopeJson()) > 0) {
            realtime.recordDelivered(delivery);
        }
    }
}
