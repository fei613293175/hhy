package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class R10PostgresStoreTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void createReadLockAndUpdateGroupUseRealPostgresAndRollback() {
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R10PostgresStore(jdbc);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transactions.executeWithoutResult(transaction -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Long ownerId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "18" + suffix.substring(0, 9), "R10" + suffix.substring(0, 13));
            assertNotNull(ownerId);
            Long qrMediaId = jdbc.queryForObject("""
                    INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
                    VALUES (?,'r10-test',?,'image/png',128,?,'PRIVATE') RETURNING id
                    """, Long.class, ownerId, "r10/" + suffix + "/qr.png", "a".repeat(64));
            assertNotNull(qrMediaId);

            long contentId = store.createGroup(ownerId, "群聊标题", "摘要", "微信", "100-200",
                    "实名行业人士", qrMediaId, "https://group.example.invalid/join", "998877",
                    "{\"platform\":\"微信\",\"description\":\"说明\",\"categoryCode\":\"COMMUNITY\"}",
                    List.of(qrMediaId), now);
            R10Store.GroupRow created = store.group(contentId).orElseThrow();
            assertEquals("GROUP", created.type());
            assertEquals("微信", created.platform());
            assertEquals(qrMediaId, created.qrMediaId());
            assertEquals(contentId, store.lockGroup(contentId).orElseThrow().id());

            assertTrue(store.updateGroup(contentId, 0, "群聊新标题", "新摘要", "QQ", "200-500",
                    "审批加入", null, "https://group.example.invalid/new", "112233",
                    "{\"platform\":\"QQ\",\"description\":\"新说明\",\"categoryCode\":\"COMMUNITY\"}",
                    List.of(), true, now.plusSeconds(1), ownerId));
            R10Store.GroupRow updated = store.group(contentId).orElseThrow();
            assertEquals(1, updated.version());
            assertEquals("群聊新标题", updated.title());
            assertEquals("QQ", updated.platform());
            assertEquals("112233", updated.groupNo());
            assertTrue(jdbc.queryForObject(
                    "SELECT count(*)=0 FROM hhy.content_media WHERE content_id=?", Boolean.class, contentId));
            transaction.setRollbackOnly();
        });
    }
}
