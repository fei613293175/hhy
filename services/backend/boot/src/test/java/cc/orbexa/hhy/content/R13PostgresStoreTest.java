package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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

class R13PostgresStoreTest {
    @Test
    void favoriteHistoryUnfavoriteAndFeedbackUseRealPostgresContracts() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        R08PostgresStore r08 = new R08PostgresStore(jdbc);
        R13PostgresStore store = new R13PostgresStore(jdbc);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long owner = user(jdbc, "18" + suffix.substring(0, 9), "R13O" + suffix.substring(0, 11));
            long viewer = user(jdbc, "19" + suffix.substring(0, 9), "R13V" + suffix.substring(0, 11));
            long first = project(r08, jdbc, owner, "第一个项目", suffix, now);
            long second = project(r08, jdbc, owner, "第二个项目", suffix.substring(1) + "0", now.plusSeconds(1));

            assertTrue(r08.favorite(viewer, first, now));
            assertTrue(r08.favorite(viewer, second, now.plusSeconds(1)));
            R13Store.ActivityPage firstPage = store.favorites(new R13Store.ActivityQuery(
                    viewer, 1, 1, null, "ONLINE", null, R13Store.SortDirection.DESC));
            assertEquals(2, firstPage.total());
            assertTrue(firstPage.hasMore());
            assertEquals(second, firstPage.items().getFirst().contentId());
            R13Store.ActivityRow last = firstPage.items().getLast();
            R13Store.ActivityPage nextPage = store.favorites(new R13Store.ActivityQuery(
                    viewer, 1, 1, new R13Store.ActivityCursor(last.occurredAt(), last.activityId()),
                    "ONLINE", null, R13Store.SortDirection.DESC));
            assertEquals(first, nextPage.items().getFirst().contentId());

            view(jdbc, viewer, first, now.plusSeconds(2), "ORGANIC_TRAFFIC");
            view(jdbc, viewer, first, now.plusSeconds(3), "ORGANIC_TRAFFIC");
            view(jdbc, viewer, second, now.plusSeconds(4), "ORGANIC_TRAFFIC");
            view(jdbc, viewer, second, now.plusSeconds(5), "SHARE");
            R13Store.ActivityPage history = store.history(new R13Store.ActivityQuery(
                    viewer, 1, 20, null, null, "项目", R13Store.SortDirection.DESC));
            assertEquals(2, history.total());
            assertEquals(List.of(second, first),
                    history.items().stream().map(R13Store.ActivityRow::contentId).toList());

            assertTrue(store.unfavorite(viewer, second, now.plusSeconds(6)));
            assertFalse(store.unfavorite(viewer, second, now.plusSeconds(7)));
            assertEquals("0", jdbc.queryForObject(
                    "SELECT favorites FROM hhy.content_stats WHERE content_id=?", String.class, second));

            long reportId = store.invalidFeedback(
                    viewer, first, "QR_EXPIRED", "二维码已经失效", now.plusSeconds(8));
            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT status FROM hhy.content_reports WHERE id=?", String.class, reportId));
            assertEquals(viewer, jdbc.queryForObject(
                    "SELECT reporter_id FROM hhy.content_reports WHERE id=?", Long.class, reportId));
            status.setRollbackOnly();
        });
    }

    private static long project(
            R08PostgresStore store, JdbcTemplate jdbc, long owner,
            String title, String suffix, Instant now) {
        long id = store.createProject(owner, title, "项目摘要", "项目说明", "COOP", "CN-11",
                null, null, "{\"description\":\"项目说明\",\"categoryCode\":\"COOP\"}",
                List.of(), now);
        assertEquals(4, R12PostgresTestFixtures.publish(jdbc, id, 0, suffix));
        return id;
    }

    private static void view(
            JdbcTemplate jdbc, long userId, long contentId, Instant at, String trafficType) {
        jdbc.update("""
                INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source,created_at)
                VALUES (?,?,?,0,?,?)
                """, userId, contentId, trafficType,
                "SHARE".equals(trafficType) ? "COPY_LINK" : "DETAIL", Timestamp.from(at));
    }

    private static long user(JdbcTemplate jdbc, String phone, String invite) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code) VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, phone, invite);
        if (id == null) throw new IllegalStateException("R13 test user id missing");
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)", id, "用户" + id);
        return id;
    }
}
