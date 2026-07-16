package cc.orbexa.hhy.shared.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    String aggregateType();
    String aggregateId();
    String eventType();
    int eventVersion();
    Instant occurredAt();
}
