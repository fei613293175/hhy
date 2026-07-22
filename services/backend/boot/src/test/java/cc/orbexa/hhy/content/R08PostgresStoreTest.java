package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class R08PostgresStoreTest {
    @Mock private JdbcTemplate jdbc;

    @Test
    void activeConfigurationIsReadFromGlobalRegistryWithoutCodeDefault() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("content.limit.normal.drafts")))
                .thenReturn(10);
        R08PostgresStore store = new R08PostgresStore(jdbc);
        assertEquals(10, store.integerConfig("content.limit.normal.drafts"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), eq("content.limit.normal.drafts"));
        assertTrue(sql.getValue().contains("scope='GLOBAL'"));
    }

    @Test
    void blockLookupChecksBothDirections() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(11L), eq(7L), eq(7L), eq(11L)))
                .thenReturn(true);
        R08PostgresStore store = new R08PostgresStore(jdbc);
        assertTrue(store.blockedEitherWay(11, 7));
    }

    @Test
    void projectFavoriteShareConversationIdempotencyAndOutboxUseRealPostgres() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var realJdbc = new JdbcTemplate(dataSource);
        var realStore = new R08PostgresStore(realJdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long owner = user(realJdbc, "18" + suffix.substring(0, 9), "OWN" + suffix.substring(0, 13));
            long viewer = user(realJdbc, "19" + suffix.substring(0, 9), "VIEW" + suffix.substring(0, 12));
            long peer = user(realJdbc, "15" + suffix.substring(0, 9), "PEER" + suffix.substring(0, 12));
            realJdbc.update("INSERT INTO hhy.identity_profiles(user_id,name_cipher,status) VALUES (?,?,'VERIFIED')",
                    owner, "cipher");
            assertTrue(realStore.identityVerified(owner));
            assertEquals(10, realStore.integerConfig("content.limit.normal.drafts"));
            assertEquals("h5.orbexa.cc", realStore.textConfig("domain.h5.host"));

            long contentId = realStore.createProject(owner, "R08真实项目", "真实摘要", "真实说明",
                    "COOP", "CN-11", "实名", "https://example.invalid",
                    "{\"description\":\"真实说明\",\"categoryCode\":\"COOP\",\"regionCode\":\"CN-11\"}",
                    List.of(), now);
            realStore.replaceContacts(contentId, List.of(
                    new R08Store.ContactWrite("EMAIL", "encrypted-only", "o***@example.com", 0)), now);
            var draft = realStore.project(contentId).orElseThrow();
            assertEquals(owner, draft.ownerId());
            assertEquals("DRAFT", draft.status());
            assertTrue(realStore.updateProject(contentId, 0, "R08真实项目2", "摘要2", "说明2",
                    "COOP2", "CN-31", null, null,
                    "{\"description\":\"说明2\",\"categoryCode\":\"COOP2\",\"regionCode\":\"CN-31\"}",
                    List.of(), false, now.plusSeconds(1), owner));
            realJdbc.update("UPDATE hhy.content_posts SET status='ONLINE' WHERE id=?", contentId);

            assertTrue(realStore.favorite(viewer, contentId, now));
            assertFalse(realStore.favorite(viewer, contentId, now));
            realStore.share(viewer, contentId, "COPY_LINK", now);
            assertEquals("1", realJdbc.queryForObject(
                    "SELECT favorites FROM hhy.content_stats WHERE content_id=?", String.class, contentId));
            assertEquals(1, realJdbc.queryForObject("SELECT count(*) FROM hhy.content_view_logs"
                    + " WHERE user_id=? AND content_id=? AND traffic_type='SHARE'", Integer.class, viewer, contentId));

            realStore.lockDirectPair(viewer, peer);
            assertTrue(realStore.directConversation(viewer, peer).isEmpty());
            var created = realStore.createDirectConversation(viewer, peer, now);
            assertEquals(created.id(), realStore.directConversation(viewer, peer).orElseThrow().id());
            assertEquals(1, realStore.directConversationCountToday(viewer, now));

            String scope = "r08-real:" + suffix;
            String key = "r08-real-key-" + suffix;
            var claim = realStore.claim(scope, key, "a".repeat(64), now.plusSeconds(3600));
            assertFalse(claim.replay());
            realStore.complete(claim.id(), "r08.test:ok", "r08.test", "encrypted-snapshot");
            assertTrue(realStore.claim(scope, key, "a".repeat(64), now.plusSeconds(3600)).replay());
            realStore.outbox(viewer, "CONTENT", "content.r08.test." + suffix,
                    Long.toString(contentId), "ONLINE", now);
            String payload = realJdbc.queryForObject(
                    "SELECT payload::text FROM hhy.outbox_events WHERE event_type=?",
                    String.class, "content.r08.test." + suffix);
            assertNotNull(payload);
            assertFalse(payload.contains("真实说明"));
            assertFalse(payload.contains("encrypted-only"));
            status.setRollbackOnly();
        });
    }

    private static long user(JdbcTemplate jdbc, String phone, String invite) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code) VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, phone, invite);
        if (id == null) throw new IllegalStateException("test user id missing");
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)", id, "用户" + id);
        return id;
    }
}
