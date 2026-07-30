package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R07PostgresStoreTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void searchPublisherContactIdempotencyAuditAndOutboxUseRealPostgres() {
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R07PostgresStore(jdbc);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactions.executeWithoutResult(transaction -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            String searchKeyword = "协作" + suffix.substring(0, 8);
            String historyKeyword = "历史" + suffix.substring(0, 8);
            String hotKeyword = "热搜" + suffix.substring(0, 8);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Long viewerId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "16" + suffix.substring(0, 9), "VIEW" + suffix.substring(0, 12));
            Long publisherId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "17" + suffix.substring(0, 9), "PUB" + suffix.substring(0, 13));
            assertNotNull(viewerId);
            assertNotNull(publisherId);
            jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname,avatar,bio) VALUES (?,?,?,?)",
                    publisherId, "真实发布者", "https://example.invalid/avatar.png", "公开简介");
            Long contentId = jdbc.queryForObject("""
                    INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,version)
                    VALUES (?,'PROJECT',?,?,'DRAFT',0) RETURNING id
                    """, Long.class, publisherId, searchKeyword + " 项目", "可靠的真实数据库搜索摘要");
            assertNotNull(contentId);
            jdbc.update("INSERT INTO hhy.project_details(content_id,cooperation,region) VALUES (?,?,?)",
                    contentId, "深度协作", "CN");
            jdbc.update("""
                    INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
                    VALUES (?,'0',CAST(? AS jsonb),'test')
                    """, contentId,
                    "{\"description\":\"深度协作\",\"categoryCode\":\"TECH\",\"regionCode\":\"CN\"}");
            String contactEnvelope = "hhy-contact-v1.test-nonce." + suffix;
            jdbc.update("""
                    INSERT INTO hhy.content_contacts(
                      content_id,channel,value_cipher,display_mask,sort_order)
                    VALUES (?,'EMAIL',?,'t***@example.com',1)
                    """, contentId, contactEnvelope);
            assertEquals(4, R12PostgresTestFixtures.publish(jdbc, contentId, 0, suffix));

            var search = store.search(new R07Store.SearchQuery(
                    viewerId, searchKeyword, "PROJECT", "TECH", "CN",
                    1, 20, null, "p.id DESC"));
            assertEquals(1, search.total());
            assertEquals(contentId.longValue(), search.items().getFirst().id());
            assertEquals("真实发布者", search.items().getFirst().nickname());
            assertFalse(search.items().getFirst().followed());

            store.recordSearch(viewerId, historyKeyword, now.minusSeconds(1));
            store.recordSearch(viewerId, historyKeyword.toLowerCase(), now);
            var history = store.history(viewerId,
                    new R07Store.TermQuery(1, 20, null, null, "created_at DESC,id DESC"));
            assertEquals(1, history.total());
            assertEquals(1, history.items().size());
            assertEquals(historyKeyword.toLowerCase(), history.items().getFirst().keyword());

            jdbc.update("""
                    INSERT INTO hhy.hot_search_terms(keyword,weight,enabled,starts_at,ends_at)
                    VALUES (?,10,true,?,?)
                    """, hotKeyword, OffsetDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(now.plusSeconds(60), ZoneOffset.UTC));
            var hot = store.hotTerms(new R07Store.TermQuery(
                    1, 20, null, hotKeyword, "weight DESC,id DESC"), now);
            assertEquals(1, hot.total());
            assertEquals(hotKeyword, hot.items().getFirst().keyword());

            var publisher = store.publisher(publisherId, viewerId, now).orElseThrow();
            assertEquals("真实发布者", publisher.nickname());
            assertFalse(publisher.verified());
            assertNull(publisher.memberBadge());
            assertEquals(contactEnvelope,
                    store.contact(contentId, "EMAIL").orElseThrow().valueCipher());

            String scope = "r07-test:" + suffix;
            String key = "r07-postgres-key-" + suffix;
            String requestHash = "a".repeat(64);
            var firstClaim = store.claim(scope, key, requestHash, now.plusSeconds(3600));
            assertFalse(firstClaim.replay());
            store.complete(firstClaim.id(), "r07.test:ok", "r07.test", "encrypted-snapshot");
            var replay = store.claim(scope, key, requestHash, now.plusSeconds(3600));
            assertTrue(replay.replay());
            assertEquals("r07.test", replay.responseType());
            assertEquals("encrypted-snapshot", replay.responsePayloadCiphertext());

            store.contactAudit(viewerId, contentId, "EMAIL", "VIEW", now);
            store.outbox(viewerId, "CONTENT", "content.contact.accessed.test." + suffix,
                    Long.toString(contentId), "VIEW", now);
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.content_contact_access_logs
                    WHERE user_id=? AND content_id=? AND channel='EMAIL' AND action='VIEW'
                    """, Integer.class, viewerId, contentId));
            String payload = jdbc.queryForObject("""
                    SELECT payload::text FROM hhy.outbox_events WHERE event_type=?
                    """, String.class, "content.contact.accessed.test." + suffix);
            assertNotNull(payload);
            assertFalse(payload.contains(contactEnvelope));
            transaction.setRollbackOnly();
        });
    }

    @Test
    void concurrentIdenticalClaimsHaveExactlyOneOwnerInRealPostgres() throws Exception {
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R07PostgresStore(jdbc);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String scope = "r07-concurrent-test:" + suffix;
        String key = "r07-concurrent-key-" + suffix;
        String requestHash = "b".repeat(64);
        int workers = 8;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        List<Future<R07Store.IdempotencyClaim>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return store.claim(scope, key, requestHash, Instant.now().plusSeconds(3600));
                }));
            }
            start.countDown();
            List<R07Store.IdempotencyClaim> claims = new ArrayList<>();
            for (Future<R07Store.IdempotencyClaim> future : futures) {
                claims.add(future.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, claims.stream().filter(claim -> !claim.replay()).count());
            assertEquals(workers - 1L, claims.stream().filter(R07Store.IdempotencyClaim::replay).count());
            assertEquals(1, claims.stream().map(R07Store.IdempotencyClaim::id).distinct().count());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM hhy.idempotency_records WHERE scope=? AND idem_key=?",
                    Integer.class, scope, key));
        } finally {
            jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND idem_key=?", scope, key);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
