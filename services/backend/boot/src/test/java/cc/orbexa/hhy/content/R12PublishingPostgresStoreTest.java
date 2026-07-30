package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class R12PublishingPostgresStoreTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void submitCopyAndDeleteCommitWithV039HistorySnapshotOutboxAndReencryptedContact() {
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var shared = new R08PostgresStore(jdbc);
        var publishingStore = new R12PublishingPostgresStore(jdbc);
        var contentStore = new ContentPostgresStore(jdbc);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var content = new ContentService(contentStore, mapper, Clock.systemUTC());
            var cipher = new ContentContactCipher(
                    "r12-publishing-postgres-root-secret-at-least-32-characters");
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transactions.executeWithoutResult(transaction -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Long owner = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "16" + suffix.substring(0, 9), "R12" + suffix.substring(0, 13));
            jdbc.update("""
                    INSERT INTO hhy.user_profiles(user_id,nickname)
                    VALUES (?,?)
                    """, owner, "R12发布者");
            jdbc.update("""
                    INSERT INTO hhy.identity_profiles(
                      user_id,name_cipher,id_no_cipher,id_hash,status,verified_at
                    ) VALUES (?,?,?,?,'VERIFIED',?)
                    """, owner, "name-cipher", "id-no-cipher", "c".repeat(64), Timestamp.from(now));
            long sourceId = shared.createProject(owner, "R12项目", "摘要", "详细说明", "COOP", "CN-11",
                    "实名认证", null,
                    "{\"description\":\"详细说明\",\"categoryCode\":\"COOP\",\"regionCode\":\"CN-11\"}",
                    List.of(), now);
            String sourceEnvelope = cipher.encrypt(sourceId, "WECHAT", "owner-contact");
            shared.replaceContacts(sourceId, List.of(
                    new R08Store.ContactWrite("WECHAT", sourceEnvelope, "ow***ct", 0)), now);
            Clock fixed = Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC);
            var service = new R12PublishingService(
                    publishingStore, shared, content, cipher, mapper, fixed);

            CommandResult submitted = service.submit(owner, Long.toString(sourceId),
                    new StatusRequest(0L, "资料完整"), "r12-postgres-submit-key");
            assertEquals("PENDING_REVIEW", submitted.status());
            assertEquals(1, submitted.version());

            CommandResult copied = service.copy(owner, Long.toString(sourceId),
                    new StatusRequest(1L, "复制继续编辑"), "r12-postgres-copy-key-01");
            long targetId = Long.parseLong(copied.resourceId());
            assertEquals("DRAFT", copied.status());
            assertEquals("PENDING_REVIEW", jdbc.queryForObject(
                    "SELECT status FROM hhy.content_posts WHERE id=?", String.class, sourceId));
            assertEquals("DRAFT", jdbc.queryForObject(
                    "SELECT status FROM hhy.content_posts WHERE id=?", String.class, targetId));
            String copiedEnvelope = jdbc.queryForObject("""
                    SELECT value_cipher FROM hhy.content_contacts
                    WHERE content_id=? AND removed_at IS NULL
                    """, String.class, targetId);
            assertNotEquals(sourceEnvelope, copiedEnvelope);
            assertEquals("owner-contact", cipher.decrypt(targetId, "WECHAT", copiedEnvelope));

            CommandResult deleted = service.delete(
                    owner, Long.toString(targetId), 0, "r12-postgres-delete-key");
            assertEquals("DELETED", deleted.status());
            assertEquals(1, deleted.version());

            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.content_versions
                    WHERE content_id=? AND version_no='1'
                    """, Integer.class, sourceId));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.content_status_logs
                    WHERE content_id=? AND transition_version=1 AND to_status='PENDING_REVIEW'
                    """, Integer.class, sourceId));
            assertEquals(3, jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.outbox_events
                    WHERE event_type IN ('content.submitted.v1','content.copied.v1','content.deleted.v1')
                      AND aggregate_id IN (?,?)
                    """, Integer.class, Long.toString(sourceId), Long.toString(targetId)));
            assertTrue(publishingStore.approvedSnapshotIsCurrent(sourceId) == false);
            transaction.setRollbackOnly();
        });
    }
}
