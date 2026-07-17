package cc.orbexa.hhy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P00FlywayMigrationTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void emptyUpgradeRepeatAndDevelopmentRollbackCycle() throws Exception {
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");

        resetDatabase();
        flyway("9").migrate();
        assertEquals(9, appliedMigrationCount());

        Flyway latest = flyway(null);
        latest.migrate();
        assertTrue(appliedMigrationCount() >= 10);
        assertTrue(isMigrationApplied("10"));
        int migrationCount = appliedMigrationCount();
        latest.migrate();
        assertEquals(migrationCount, appliedMigrationCount(), "repeat migrate must be a no-op");
        assertP00Baseline();

        resetDatabase();
        latest.migrate();
        assertTrue(appliedMigrationCount() >= 10);
        assertTrue(isMigrationApplied("10"));
        assertP00Baseline();

        Path root = findProjectRoot();
        String rollback = Files.readString(
                root.resolve("database/rollback/U010__p00_event_ledger_invariants.sql"),
                StandardCharsets.UTF_8);
        String forward = Files.readString(
                root.resolve("database/migrations/V010__p00_event_ledger_invariants.sql"),
                StandardCharsets.UTF_8);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(rollback);
            statement.execute(forward);
        }
        assertTrue(isMigrationApplied("10"));
        assertP00Baseline();
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }

    private void resetDatabase() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS hhy CASCADE");
            statement.execute("DROP TABLE IF EXISTS public.flyway_schema_history");
        }
    }

    private int appliedMigrationCount() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT count(*) FROM public.flyway_schema_history WHERE success")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private boolean isMigrationApplied(String version) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM public.flyway_schema_history "
                             + "WHERE success AND CAST(version AS integer)=?")) {
            statement.setInt(1, Integer.parseInt(version));
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) == 1;
            }
        }
    }

    private void assertP00Baseline() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.tables "
                            + "WHERE table_schema='hhy' AND table_type='BASE TABLE'")) {
                assertTrue(rows.next());
                assertEquals(198, rows.getInt(1));
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT count(*) FROM pg_trigger "
                            + "WHERE tgname IN ('trg_outbox_events_guard',"
                            + "'trg_balance_snapshots_guard','trg_reconciliation_runs_guard') "
                            + "AND NOT tgisinternal")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }

    private Path findProjectRoot() {
        String configuredRoot = System.getProperty("hhy.project.root");
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot).toAbsolutePath().normalize();
        }
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve(
                    "database/rollback/U010__p00_event_ledger_invariants.sql"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("project root not found");
    }
}
