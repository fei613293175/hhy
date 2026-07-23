package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.R11Contracts.TeamLeaderAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class R11PostgresStoreTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void createLockReadAndUpdateTeamLeaderUseRealPostgres() {
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R11PostgresStore(jdbc, new ObjectMapper());
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transactions.executeWithoutResult(transaction -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Long ownerId = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "17" + suffix.substring(0, 9), "R11" + suffix.substring(0, 13));
            assertNotNull(ownerId);
            Long logoId = jdbc.queryForObject("""
                    INSERT INTO hhy.media_objects(owner_id,bucket,object_key,mime,size,sha256,visibility)
                    VALUES (?,'r11-test',?,'image/png',128,?,'PRIVATE') RETURNING id
                    """, Long.class, ownerId, "r11/" + suffix + "/logo.png", "b".repeat(64));
            TeamLeaderAttributes original = new TeamLeaderAttributes("团队一", "负责人", logoId,
                    "个人介绍", "团队介绍", "10-20", "产品", "联合创业", "诚信合作", List.of("案例一"), true);
            long contentId = store.createTeamLeader(ownerId, "团队标题", "摘要", "TEAM", "CN-11", original,
                    "{\"description\":\"说明\",\"categoryCode\":\"TEAM\"}", List.of(logoId), now);

            R11Store.TeamLeaderRow created = store.teamLeader(contentId).orElseThrow();
            assertEquals("TEAM_LEADER", created.type());
            assertEquals("团队一", created.attributes().teamName());
            assertEquals(List.of("案例一"), created.attributes().pastCases());
            assertEquals(contentId, store.lockOwnerTeamLeader(ownerId).orElseThrow());
            assertEquals(contentId, store.lockTeamLeader(contentId).orElseThrow().id());

            TeamLeaderAttributes updated = new TeamLeaderAttributes("团队二", "新负责人", null,
                    null, "新团队介绍", "20-50", "运营", "资源合作", "长期合作", List.of("案例二"), false);
            assertTrue(store.updateTeamLeader(contentId, 0, "新标题", "新摘要", "TEAM", "CN-31", updated,
                    "{\"description\":\"新说明\",\"categoryCode\":\"TEAM\"}", List.of(), true,
                    now.plusSeconds(1), ownerId));
            R11Store.TeamLeaderRow result = store.teamLeader(contentId).orElseThrow();
            assertEquals(1, result.version());
            assertEquals("团队二", result.attributes().teamName());
            assertEquals("CN-31", result.region());
            assertEquals(false, result.attributes().acceptPrivateChat());
            transaction.setRollbackOnly();
        });
    }
}
