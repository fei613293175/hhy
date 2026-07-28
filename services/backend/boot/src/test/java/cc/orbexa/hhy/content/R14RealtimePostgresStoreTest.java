package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R14Contracts.SendMessageRequest;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class R14RealtimePostgresStoreTest {
    @Test
    void realPostgresAllocatesConcurrentUserSequencesAndPersistsGapWatermark() throws Exception {
        var dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        R14RealtimePostgresStore store = new R14RealtimePostgresStore(jdbc);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long userId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, "17" + suffix.substring(0, 9), "WS" + suffix.substring(0, 12));

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> store.nextSequence(userId, Instant.now())));
            }
            List<Long> values = new ArrayList<>();
            for (Future<Long> future : futures) values.add(future.get());
            Collections.sort(values);
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), values);
        } finally {
            executor.shutdownNow();
        }

        Instant created = Instant.parse("2026-07-20T00:00:00Z");
        long sequence = store.nextSequence(userId, created);
        UUID eventId = UUID.randomUUID();
        var delivery = store.insert(eventId, userId, sequence, "chat.message.new", null,
                Scope.CHAT, "{\"eventType\":\"chat.message.new\"}", true,
                created.plusSeconds(72 * 3600), created);
        assertTrue(store.recordDeliveryAttempt(userId, eventId, sequence, created.plusSeconds(1)));
        assertTrue(!store.acknowledge(userId, eventId, sequence + 1, created.plusSeconds(2)));
        assertTrue(!store.acknowledge(userId + 1, eventId, sequence, created.plusSeconds(2)));
        assertTrue(store.acknowledge(userId, eventId, sequence, created.plusSeconds(2)));
        assertTrue(store.acknowledge(userId, eventId, sequence, created.plusSeconds(9)));
        assertEquals(created.plusSeconds(2), jdbc.queryForObject(
                "SELECT acked_at FROM hhy.websocket_deliveries WHERE event_id=?",
                OffsetDateTime.class, eventId).toInstant());
        assertTrue(store.dueRedeliveries(
                created.plusSeconds(20), created.plusSeconds(20), 10).stream()
                .noneMatch(item -> item.eventId().equals(eventId)));

        long retrySequence = store.nextSequence(userId, created.plusSeconds(3));
        UUID retryEvent = UUID.randomUUID();
        store.insert(retryEvent, userId, retrySequence, "chat.message.new", null,
                Scope.CHAT, "{\"eventType\":\"chat.message.new\"}", true,
                created.plusSeconds(72 * 3600), created);
        for (int attempt = 1; attempt <= 6; attempt++) {
            assertTrue(store.recordDeliveryAttempt(
                    userId, retryEvent, retrySequence, created.plusSeconds(attempt)));
        }
        assertTrue(!store.recordDeliveryAttempt(
                userId, retryEvent, retrySequence, created.plusSeconds(7)));
        assertTrue(store.dueRedeliveries(
                created.plusSeconds(20), created.plusSeconds(20), 10).stream()
                .noneMatch(item -> item.eventId().equals(retryEvent)));

        store.expireDeliveries(userId, created.plusSeconds(72 * 3600 + 1));
        var gap = store.gapWatermark(userId).orElseThrow();
        assertEquals(retrySequence, gap.expiredThroughSequence());
        assertEquals(java.util.Set.of(Scope.CHAT), gap.affectedScopes());
        assertTrue(store.deliveriesAfter(
                userId, 0, retrySequence, created.plusSeconds(72 * 3600 + 1)).isEmpty());
    }

    @Test
    void messageRollbackAlsoRollsBackOutboxSequenceAndRealtimeDeliveries() {
        var dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long senderId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, "18" + suffix.substring(0, 9), "WA" + suffix.substring(0, 12));
        long peerId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, "19" + suffix.substring(0, 9), "WB" + suffix.substring(0, 12));
        long conversationId = jdbc.queryForObject("""
                WITH conversation AS (
                  INSERT INTO hhy.conversations(type) VALUES ('DIRECT') RETURNING id
                ), members AS (
                  INSERT INTO hhy.conversation_members(conversation_id,user_id)
                  SELECT id,? FROM conversation
                  UNION ALL
                  SELECT id,? FROM conversation
                  RETURNING conversation_id
                )
                SELECT max(conversation_id) FROM members
                """, Long.class, senderId, peerId);

        R08Store shared = mock(R08Store.class);
        when(shared.activeUser(senderId)).thenReturn(true);
        when(shared.blockedEitherWay(senderId, peerId)).thenReturn(false);
        when(shared.integerConfig("chat.message.per_minute_limit")).thenReturn(100);
        when(shared.claim(anyString(), anyString(), anyString(), any()))
                .thenReturn(new R08Store.IdempotencyClaim(
                        1, "request", null, null, null, false));
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        R14RealtimeService realtime = new R14RealtimeService(
                new R14RealtimePostgresStore(jdbc), mock(org.springframework.context.ApplicationEventPublisher.class),
                mapper, clock);
        R14Service service = new R14Service(
                new R14PostgresStore(jdbc), shared, mock(R08Service.class), realtime,
                new ContentContactCipher("r14-transaction-test-root-secret-at-least-32-characters"),
                mapper, clock);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThrows(RollbackProbe.class, () -> transaction.executeWithoutResult(status -> {
            service.send(senderId, Long.toString(conversationId),
                    new SendMessageRequest("rollback-client-1", "TEXT", Map.of("text", "rollback")),
                    "r14-rollback-key-0001");
            throw new RollbackProbe();
        }));

        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.chat_messages WHERE conversation_id=?", Long.class, conversationId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.outbox_events WHERE aggregate_type='CHAT_MESSAGE'"
                        + " AND payload->>'actorId'=?", Long.class, Long.toString(senderId)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.websocket_deliveries WHERE user_id IN (?,?)",
                Long.class, senderId, peerId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.websocket_user_sequences WHERE user_id IN (?,?)",
                Long.class, senderId, peerId));
    }

    private static DriverManagerDataSource dataSource() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        return new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
    }

    private static final class RollbackProbe extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
