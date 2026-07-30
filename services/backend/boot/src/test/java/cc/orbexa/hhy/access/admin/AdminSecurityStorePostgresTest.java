package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AdminSecurityStorePostgresTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void claimFindAndAtomicSnapshotUseTheMigratedPostgresTable() {
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new AdminSecurityStore(jdbc);
        String suffix = UUID.randomUUID().toString();
        String scope = "r01.store.integration:" + suffix;
        String key = "r01-store-" + suffix;
        String digest = "a".repeat(64);

        try {
            AdminSecurityStore.IdempotencyClaim claim = store.claimIdempotency(
                    scope, key, digest, Instant.now().plusSeconds(3600));
            assertFalse(claim.replay());
            assertEquals(digest, claim.row().requestHash());

            store.completeIdempotencySnapshot(
                    claim.row().id(), "session:23", "r01.command-result.v1",
                    "hhy-idem-v1.A256GCM.v1.integration-nonce.integration-ciphertext");

            var found = store.findIdempotency(scope, key);
            assertTrue(found.isPresent());
            assertEquals("session:23", found.orElseThrow().responseRef());
            assertEquals("r01.command-result.v1", found.orElseThrow().responseType());
        } finally {
            jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND idem_key=?", scope, key);
        }
    }
}
