package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class R09PostgresStoreTest {
    @Test
    void createReadAndUpdateAppUseRealPostgres() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R09PostgresStore(jdbc);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Long owner = jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code) VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, "16" + suffix.substring(0, 9), "R09" + suffix.substring(0, 13));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            long id = store.createApp(owner, "真实App", "摘要", "真实App", "ANDROID", "1.0.0",
                    "https://download.example.invalid/app", "https://app.example.invalid",
                    "{\"appName\":\"真实App\",\"description\":\"介绍\",\"categoryCode\":\"TOOLS\"}",
                    List.of(), now);
            var created = store.app(id).orElseThrow();
            assertEquals("APP", created.type());
            assertEquals("真实App", created.appName());
            assertTrue(store.updateApp(id, 0, "真实App 2", "摘要2", "真实App 2", "ANDROID",
                    "1.1.0", "https://download.example.invalid/app2", "https://app.example.invalid",
                    "{\"appName\":\"真实App 2\",\"description\":\"介绍2\",\"categoryCode\":\"TOOLS\"}",
                    List.of(), false, now.plusSeconds(1), owner));
            assertEquals("1.1.0", store.lockApp(id).orElseThrow().versionText());
            status.setRollbackOnly();
        });
    }
}
