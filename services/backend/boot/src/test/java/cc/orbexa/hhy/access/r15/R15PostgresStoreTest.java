package cc.orbexa.hhy.access.r15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R15PostgresStoreTest {
    @Test
    void publicDocumentsAndSupportAttachmentsUseAuthoritativeTables() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R15PostgresStore(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            long userId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "17" + suffix.substring(0, 9), "R15" + suffix.substring(0, 16));
            long otherUserId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "16" + suffix.substring(0, 9), "R15O" + suffix.substring(0, 15));
            long mediaId = jdbc.queryForObject("""
                    INSERT INTO hhy.media_objects(owner_id,bucket,object_key,visibility)
                    VALUES (?,'r15-test',?,'PRIVATE') RETURNING id
                    """, Long.class, userId, "support/" + suffix);
            long ticketId = jdbc.queryForObject("""
                    INSERT INTO hhy.support_tickets(ticket_no,user_id,type,biz_type,status)
                    VALUES (?,?,? ,?,'OPEN') RETURNING id
                    """, Long.class, "R15-" + suffix, userId, "QUESTION", "附件问题");

            assertTrue(store.attachmentsAvailable(List.of(mediaId), userId));
            assertFalse(store.attachmentsAvailable(List.of(mediaId), otherUserId));
            store.appendSupportMessage(ticketId, "USER", userId, "附件正文",
                    List.of(mediaId), userId, Instant.now());
            assertEquals(1L, jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.ticket_attachments
                    WHERE ticket_id=? AND media_id=?
                    """, Long.class, ticketId, mediaId));

            long articleId = jdbc.queryForObject("""
                    INSERT INTO hhy.cms_articles(type,title,content,status,published_at)
                    VALUES ('HELP','安装帮助','帮助正文','PUBLISHED',clock_timestamp()) RETURNING id
                    """, Long.class);
            assertEquals("帮助正文", store.publicHelpArticle(articleId).orElseThrow().body());

            long agreementId = jdbc.queryForObject("""
                    INSERT INTO hhy.agreements(code,current_version_id)
                    VALUES (?,0) RETURNING id
                    """, Long.class, "R15_" + suffix.substring(0, 8));
            long agreementVersionId = jdbc.queryForObject("""
                    INSERT INTO hhy.agreement_versions(agreement_id,version,content,effective_at)
                    VALUES (?,1,'协议正文',clock_timestamp()) RETURNING id
                    """, Long.class, agreementId);
            jdbc.update("UPDATE hhy.agreements SET current_version_id=? WHERE id=?",
                    agreementVersionId, agreementId);
            String agreementCode = jdbc.queryForObject(
                    "SELECT code FROM hhy.agreements WHERE id=?", String.class, agreementId);
            assertEquals("协议正文", store.publicAgreement(agreementCode).orElseThrow().body());
            status.setRollbackOnly();
        });
    }
}
