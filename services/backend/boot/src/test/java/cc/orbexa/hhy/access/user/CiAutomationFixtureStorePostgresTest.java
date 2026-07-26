package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class CiAutomationFixtureStorePostgresTest {
    private static volatile Harness sharedHarness;
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void consumedTargetIsRebuiltAsOneFreshCompleteDraftWithoutReviewHistory() {
        Harness harness = harness();
        Seed seed = seed(harness, "PENDING_REVIEW", true);

        var first = harness.tx().execute(status -> harness.store().prepareR12SubmitTarget(seed.userId()));
        var second = harness.tx().execute(status -> harness.store().prepareR12SubmitTarget(seed.userId()));

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.contentId(), second.contentId());
        assertFreshTarget(harness.jdbc(), seed.userId(), first.contentId());
        assertEquals(0L, count(harness.jdbc(),
                "SELECT count(*) FROM hhy.content_review_records WHERE content_id=?", first.contentId()));
        assertEquals(1L, count(harness.jdbc(),
                "SELECT count(*) FROM hhy.content_review_records WHERE content_id=?", seed.templateId()));
        assertEquals("DELETED", harness.jdbc().queryForObject(
                "SELECT status FROM hhy.content_posts WHERE id=?", String.class, seed.templateId()));
    }

    @Test
    void concurrentPreparationUsesCrossInstanceTransactionLockAndReturnsSameDraft() throws Exception {
        Harness harness = harness();
        Seed seed = seed(harness, "PENDING_REVIEW", true);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return harness.tx().execute(status -> harness.store().prepareR12SubmitTarget(seed.userId()));
            });
            var second = executor.submit(() -> {
                start.await();
                return harness.tx().execute(status -> harness.store().prepareR12SubmitTarget(seed.userId()));
            });
            start.countDown();
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(results.get(0).contentId(), results.get(1).contentId());
            assertEquals(1L, results.stream().filter(CiAutomationFixtureStore.PreparedTarget::created).count());
            assertFreshTarget(harness.jdbc(), seed.userId(), results.get(0).contentId());
        }
    }

    @Test
    void incompleteTemplateRollsBackWithoutRetiringSourceOrCreatingDraft() {
        Harness harness = harness();
        Seed seed = seed(harness, "PENDING_REVIEW", false);

        assertThrows(IllegalStateException.class, () -> harness.tx().execute(
                status -> harness.store().prepareR12SubmitTarget(seed.userId())));

        assertEquals("PENDING_REVIEW", harness.jdbc().queryForObject(
                "SELECT status FROM hhy.content_posts WHERE id=?", String.class, seed.templateId()));
        assertEquals(0L, count(harness.jdbc(), """
                SELECT count(*) FROM hhy.content_posts
                WHERE owner_id=? AND title=? AND status='DRAFT'
                """, seed.userId(), CiAutomationFixtureStore.R12_TARGET_TITLE));
    }

    @Test
    void duplicateDraftsAndBannedTargetsFailClosed() {
        Harness duplicateHarness = harness();
        Seed duplicate = seed(duplicateHarness, "DRAFT", true);
        duplicateHarness.tx().executeWithoutResult(status -> createContent(
                duplicateHarness.jdbc(), duplicate.userId(), duplicate.mediaId(), "DRAFT", true));
        assertThrows(IllegalStateException.class, () -> duplicateHarness.tx().execute(
                status -> duplicateHarness.store().prepareR12SubmitTarget(duplicate.userId())));

        Harness bannedHarness = harness();
        Seed banned = seed(bannedHarness, "BANNED", true);
        assertThrows(IllegalStateException.class, () -> bannedHarness.tx().execute(
                status -> bannedHarness.store().prepareR12SubmitTarget(banned.userId())));
    }

    @Test
    void missingTemplateFailsClosed() {
        Harness harness = harness();
        long owner = createOwner(harness.jdbc());
        assertThrows(IllegalStateException.class, () -> harness.tx().execute(
                status -> harness.store().prepareR12SubmitTarget(owner)));
    }

    private Harness harness() {
        Assumptions.assumeTrue(
                url != null && !url.isBlank() && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        synchronized (CiAutomationFixtureStorePostgresTest.class) {
            if (sharedHarness == null) {
                var dataSource = new DriverManagerDataSource(url, user, password);
                Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
                var jdbc = new JdbcTemplate(dataSource);
                var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                sharedHarness = new Harness(jdbc, tx, new CiAutomationFixtureStore(jdbc));
            }
            return sharedHarness;
        }
    }

    private Seed seed(Harness harness, String status, boolean complete) {
        return harness.tx().execute(transaction -> {
            harness.jdbc().queryForObject("SELECT pg_advisory_xact_lock(709013)", (rs, row) -> Boolean.TRUE);
            long owner = createOwner(harness.jdbc());
            long media = createReadyMedia(harness.jdbc(), owner);
            long content = createContent(harness.jdbc(), owner, media, status, complete);
            return new Seed(owner, content, media);
        });
    }

    private static long createOwner(JdbcTemplate jdbc) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String phone = "19" + String.format("%09d", Math.floorMod(suffix.hashCode(), 1_000_000_000));
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, phone, ("T" + suffix.substring(0, 11)).toUpperCase());
        return id == null ? 0L : id;
    }

    private static long createReadyMedia(JdbcTemplate jdbc, long owner) {
        Long binding = jdbc.query("""
                SELECT id FROM hhy.storage_scope_bindings
                WHERE scope_code='public_media' AND status='ACTIVE' ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong("id")).stream().findFirst().orElse(null);
        if (binding == null) {
            Long config = jdbc.query("""
                    SELECT id FROM hhy.provider_config_versions
                    WHERE provider_code='storage' AND status='ACTIVE' ORDER BY id LIMIT 1
                    """, (rs, row) -> rs.getLong("id")).stream().findFirst().orElse(null);
            if (config == null) {
                config = jdbc.queryForObject("""
                        INSERT INTO hhy.provider_config_versions(
                          provider_code,version_no,status,created_by,activated_at,environment,
                          values_json,secret_refs_json,remark,connection_successful,masked_test_result,tested_at
                        ) VALUES ('storage',?,'ACTIVE','ci-test',clock_timestamp(),'STAGING',
                          '{}'::jsonb,'{}'::jsonb,'ci fixture',TRUE,'ok',clock_timestamp())
                        RETURNING id
                        """, Long.class, "ci-" + UUID.randomUUID());
            }
            binding = jdbc.queryForObject("""
                    INSERT INTO hhy.storage_scope_bindings(
                      scope_code,provider_code,config_version_id,bucket,public_domain,status
                    ) VALUES ('public_media','CLOUDFLARE_R2',?,'ci-public-media',
                      'https://download.orbexa.cc/ci/','ACTIVE') RETURNING id
                    """, Long.class, config);
        }
        String key = "ci/r12/" + UUID.randomUUID() + ".png";
        Long media = jdbc.queryForObject("""
                INSERT INTO hhy.media_objects(
                  owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
                  storage_scope,storage_binding_id,status
                ) VALUES (?,'ci-public-media',?,'image/png',1,repeat('a',64),'PUBLIC',
                  'CONTENT_PROJECT','public_media',?,'READY') RETURNING id
                """, Long.class, owner, key, binding);
        return media == null ? 0L : media;
    }

    private static long createContent(JdbcTemplate jdbc, long owner, long media,
                                      String finalStatus, boolean complete) {
        Long content = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(
                  owner_id,type,title,summary,status,review_status,refresh_times,version
                ) VALUES (?,'PROJECT',?,'候选事务重建测试','DRAFT',NULL,0,0) RETURNING id
                """, Long.class, owner, CiAutomationFixtureStore.R12_TARGET_TITLE);
        jdbc.update("""
                INSERT INTO hhy.project_details(content_id,cooperation,conditions,region,website)
                VALUES (?,'真实合作详情','双方责任清晰','上海','https://h5.orbexa.cc')
                """, content);
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
                VALUES (?,'0','{"description":"真实合作详情"}'::jsonb,'ci-test')
                """, content);
        if (complete) {
            jdbc.update("""
                    INSERT INTO hhy.content_stats(
                      content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts
                    ) VALUES (?,'10','0','0','2','1','1')
                    """, content);
        }
        jdbc.update("""
                INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order)
                VALUES (?,?,'image/png',0)
                """, content, media);
        if (!"DRAFT".equals(finalStatus)) {
            String reviewStatus = "PENDING_REVIEW".equals(finalStatus) ? "PENDING" : null;
            jdbc.update("""
                    UPDATE hhy.content_posts
                    SET status=?,review_status=?,version=version+1,updated_at=clock_timestamp()
                    WHERE id=? AND status='DRAFT' AND version=0
                    """, finalStatus, reviewStatus, content);
            if ("PENDING_REVIEW".equals(finalStatus)) {
                jdbc.update("""
                        INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
                        VALUES (?,'1','{"description":"真实合作详情"}'::jsonb,'ci-submit')
                        """, content);
            }
            jdbc.update("""
                    INSERT INTO hhy.content_status_logs(
                      content_id,from_status,to_status,operator,transition_version
                    ) VALUES (?,'DRAFT',?,'ci-test',1)
                    """, content, finalStatus);
            if ("PENDING_REVIEW".equals(finalStatus)) {
                jdbc.update("""
                        INSERT INTO hhy.content_review_records(
                          content_id,version_no,decision,reason,snapshot_version_id,command_id
                        )
                        SELECT ?,'1','CLAIM',NULL,id,?
                        FROM hhy.content_versions WHERE content_id=? AND version_no='1'
                        """, content, "ci-source-review-" + content, content);
            }
        }
        return content == null ? 0L : content;
    }

    private static void assertFreshTarget(JdbcTemplate jdbc, long owner, long target) {
        assertEquals(1L, count(jdbc, """
                SELECT count(*) FROM hhy.content_posts
                WHERE owner_id=? AND title=? AND status='DRAFT' AND version=0 AND review_status IS NULL
                """, owner, CiAutomationFixtureStore.R12_TARGET_TITLE));
        assertEquals(1L, count(jdbc, "SELECT count(*) FROM hhy.project_details WHERE content_id=?", target));
        assertEquals(1L, count(jdbc,
                "SELECT count(*) FROM hhy.content_versions WHERE content_id=? AND version_no='0'", target));
        assertEquals(1L, count(jdbc, "SELECT count(*) FROM hhy.content_stats WHERE content_id=?", target));
        assertEquals(1L, count(jdbc,
                "SELECT count(*) FROM hhy.content_media WHERE content_id=? AND removed_at IS NULL", target));
    }

    private static long count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }

    private record Harness(JdbcTemplate jdbc, TransactionTemplate tx, CiAutomationFixtureStore store) { }
    private record Seed(long userId, long templateId, long mediaId) { }
}
