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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        flyway("10").migrate();
        assertTrue(isMigrationApplied("10"));
        exerciseP00DevelopmentRollback();
        assertP00EventLedgerInvariants();

        flyway("54").migrate();
        assertTrue(isMigrationApplied("54"));
        assertFalse(isMigrationApplied("55"));
        assertFalse(columnExists("red_packet_view_sessions", "client_nonce"));

        Flyway latest = flyway(null);
        latest.migrate();
        assertTrue(appliedMigrationCount() >= 10);
        assertTrue(isMigrationApplied("10"));
        assertTrue(isMigrationApplied("55"));
        int migrationCount = appliedMigrationCount();
        latest.migrate();
        assertEquals(migrationCount, appliedMigrationCount(), "repeat migrate must be a no-op");
        assertP00Baseline();
        assertR22Baseline();

        resetDatabase();
        latest.migrate();
        assertTrue(appliedMigrationCount() >= 10);
        assertTrue(isMigrationApplied("10"));
        assertTrue(isMigrationApplied("55"));
        assertP00Baseline();
        assertR22Baseline();
    }

    private void exerciseP00DevelopmentRollback() throws Exception {
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
                assertEquals(203, rows.getInt(1), "latest Flyway chain must match the schema catalog");
            }
        }
        assertP00EventLedgerInvariants();
    }

    private void assertP00EventLedgerInvariants() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
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

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM information_schema.columns "
                             + "WHERE table_schema='hhy' AND table_name=? AND column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) == 1;
            }
        }
    }

    private void assertR22Baseline() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='hhy' AND (
                      (table_name='red_packet_view_sessions' AND column_name IN (
                        'client_nonce','device_context','accumulated_seconds',
                        'last_heartbeat_sequence','last_server_time','claimed_at'))
                      OR (table_name='red_packet_reservations' AND column_name IN ('released_at','claim_id'))
                      OR (table_name='red_packet_claims' AND column_name IN (
                        'session_id','client_nonce','final_heartbeat_sequence','request_hash')))
                    """)) {
                assertTrue(rows.next());
                assertEquals(12, rows.getInt(1));
            }
            try (ResultSet rows = statement.executeQuery("""
                    SELECT count(*) FROM hhy.system_configs
                    WHERE scope='GLOBAL' AND (
                      (key='red_packet.default_view_seconds' AND value_json #>> '{}'='20') OR
                      (key='red_packet.reservation_min_seconds' AND value_json #>> '{}'='90'))
                    """)) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
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
