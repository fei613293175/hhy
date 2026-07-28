package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R14PostgresStoreTest {
    @Test
    void directChatLifecycleUsesRealPostgresInvariants() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var r08 = new R08PostgresStore(jdbc);
        var store = new R14PostgresStore(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long sender = user(jdbc, "18" + suffix.substring(0, 9), "R14A" + suffix.substring(0, 12));
            long reader = user(jdbc, "19" + suffix.substring(0, 9), "R14B" + suffix.substring(0, 12));
            R08Store.ConversationRow conversation = r08.createDirectConversation(sender, reader, now);
            r08.outbox(sender, "CONVERSATION", "chat.direct.created.v1",
                    Long.toString(conversation.id()), "ACTIVE", now);

            assertEquals(0, r08.directConversation(sender, reader).orElseThrow().version());
            assertEquals("r14-api", jdbc.queryForObject("""
                    SELECT headers->>'source' FROM hhy.outbox_events WHERE event_type='chat.direct.created.v1'
                    ORDER BY id DESC LIMIT 1
                    """, String.class));
            assertEquals(reader, store.membership(conversation.id(), sender, true).orElseThrow().peerId());
            String maxContact = "联".repeat(256);
            String contactEnvelope = new ContentContactCipher(
                    "r14-postgres-contact-root-secret-at-least-32-characters")
                    .encrypt(conversation.id(), "CHAT_CONTACT:r14-real-contact-1:0", maxContact);
            assertTrue(contactEnvelope.length() > 256);
            R14Store.MessageRow contactMessage = store.insertMessage(
                    conversation.id(), sender, "r14-real-contact-1", "CONTACT_CARD",
                    "{\"fields\":[{\"type\":\"OTHER\",\"value\":\"" + contactEnvelope + "\"}]}",
                    now.plusSeconds(1));
            assertEquals(contactEnvelope, jdbc.queryForObject("""
                    SELECT body_json->'fields'->0->>'value' FROM hhy.chat_messages WHERE id=?
                    """, String.class, contactMessage.id()));

            R14Store.MessageRow message = store.insertMessage(
                    conversation.id(), sender, "r14-real-message-1", "TEXT",
                    "{\"text\":\"真实消息\"}", now.plusSeconds(1));
            store.advanceConversation(conversation.id(), sender, message.id(), now.plusSeconds(1));

            assertEquals(1, r08.directConversation(sender, reader).orElseThrow().version());
            R14Store.ConversationPageRow conversations = store.conversations(
                    new R14Store.ConversationQuery(reader, 1, 20, null, null));
            assertEquals(1, conversations.items().size());
            assertEquals(1, conversations.items().getFirst().unreadCount());
            assertEquals("真实消息", conversations.items().getFirst().lastMessagePreview());
            jdbc.update("UPDATE hhy.user_profiles SET nickname=? WHERE user_id=?",
                    "搜索%下划_感叹!反斜\\联系人", sender);
            for (String literalKeyword : List.of("%", "_", "!", "\\")) {
                assertEquals(1, store.conversations(
                        new R14Store.ConversationQuery(reader, 1, 20, null, literalKeyword)).items().size(),
                        "literal LIKE keyword must remain searchable: " + literalKeyword);
            }
            R14Store.MessagePageRow messages = store.messages(
                    new R14Store.MessageQuery(conversation.id(), 1, 20, null));
            assertEquals(message.id(), messages.items().getFirst().id());
            assertEquals(contactMessage.id(), messages.items().get(1).id());

            store.markRead(conversation.id(), reader, message.id(), now.plusSeconds(2));
            assertEquals("READ", store.message(conversation.id(), message.id()).orElseThrow().status());
            assertEquals(0, jdbc.queryForObject("""
                    SELECT unread_count FROM hhy.conversation_members
                    WHERE conversation_id=? AND user_id=?
                    """, Integer.class, conversation.id(), reader));

            assertTrue(store.reportEvidenceValid(
                    conversation.id(), reader, List.of(message.id()), List.of()));
            long reportId = store.report(reader, sender, conversation.id(), "HARASSMENT", "骚扰消息",
                    List.of(message.id()), List.of(), now.plusSeconds(3));
            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT status FROM hhy.chat_reports WHERE id=?", String.class, reportId));
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
            store.outbox(reader, "CHAT_REPORT", "chat.report.created.v1",
                    Long.toString(reportId), "PENDING", now.plusSeconds(3));
            assertEquals("r14-api", jdbc.queryForObject("""
                    SELECT headers->>'source' FROM hhy.outbox_events WHERE event_type='chat.report.created.v1'
                    ORDER BY id DESC LIMIT 1
                    """, String.class));

            assertTrue(store.block(reader, sender, "骚扰", now.plusSeconds(4)));
            assertFalse(store.block(reader, sender, "骚扰", now.plusSeconds(4)));
            assertTrue(store.unblock(reader, sender));
            assertFalse(store.unblock(reader, sender));

            assertTrue(store.hideConversation(conversation.id(), reader, now.plusSeconds(5)));
            assertFalse(store.hideConversation(conversation.id(), reader, now.plusSeconds(5)));
            assertTrue(store.conversations(
                    new R14Store.ConversationQuery(reader, 1, 20, null, null)).items().isEmpty());
            assertEquals(2, jdbc.queryForObject(
                    "SELECT count(*) FROM hhy.chat_messages WHERE conversation_id=?", Integer.class, conversation.id()));
            status.setRollbackOnly();
        });
    }

    @Test
    void duplicateClientMessageConstraintRollsBackBothRowsInRealPostgres() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long sender = user(jdbc, "16" + suffix.substring(0, 9), "R14C" + suffix.substring(0, 12));
        long peer = user(jdbc, "15" + suffix.substring(0, 9), "R14D" + suffix.substring(0, 12));
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
                """, Long.class, sender, peer);
        var store = new R14PostgresStore(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> transaction.executeWithoutResult(status -> {
                    store.insertMessage(conversationId, sender, "duplicate-client-1", "TEXT",
                            "{\"text\":\"第一条\"}", now);
                    store.insertMessage(conversationId, sender, "duplicate-client-1", "TEXT",
                            "{\"text\":\"第二条\"}", now.plusSeconds(1));
                }));

        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.chat_messages WHERE conversation_id=?",
                Long.class, conversationId));
    }

    private static long user(JdbcTemplate jdbc, String phone, String invite) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code) VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, phone, invite);
        if (id == null) throw new IllegalStateException("R14 test user id missing");
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)", id, "用户" + id);
        return id;
    }
}
