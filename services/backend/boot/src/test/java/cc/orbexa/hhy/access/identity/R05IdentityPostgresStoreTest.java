package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.IdentityService.LivenessTicket;
import cc.orbexa.hhy.access.identity.IdentityService.ProtectedIdentity;
import cc.orbexa.hhy.access.identity.IdentityService.SessionDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R05IdentityPostgresStoreTest {
    private final String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
    private final String user = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", "");
    private final String password = System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", "");

    @Test
    void createAttachReadAndRetryBindUtcTimestampsAndEncryptProviderPayloadOnPostgres() {
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var cipher = new IdentitySensitiveCipher("integration-root-secret-" + "x".repeat(32),
                new SecureRandom());
        var store = new R05IdentityPostgresStore(
                jdbc, transactions, new ObjectMapper().findAndRegisterModules(), cipher);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long userId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status) VALUES (?,'ACTIVE') RETURNING id
                """, Long.class, "18" + suffix.substring(0, 9));
        assertNotNull(userId);
        try {
            Instant now = Instant.now();
            var created = store.create(new SessionDraft(
                    userId,
                    new ProtectedIdentity("cipher-name", "cipher-id", hex(suffix)),
                    "consent-v1", "ALIYUN_MARKET_FACE", "create-" + suffix,
                    now.plusSeconds(600), now));
            assertFalse(created.state().isBlank());
            assertTrue(Duration.between(now.plusSeconds(600), created.expiresAt()).abs().toMillis() <= 1,
                    "PostgreSQL must preserve the UTC session expiry bound by the store");
            assertEquals(created.state(), store.find(created.id(), userId).orElseThrow().state());

            URI providerUrl = URI.create(
                    "https://provider.example/liveness/start?token=" + suffix);
            var attached = store.attachLiveness(created,
                    new LivenessTicket("order-" + suffix, providerUrl),
                    "liveness-" + suffix, now.plusSeconds(1));
            assertEquals(providerUrl, attached.livenessUrl());
            assertEquals(providerUrl,
                    store.find(created.id(), userId).orElseThrow().livenessUrl());
            String stored = jdbc.queryForObject("""
                    SELECT response_cipher FROM hhy.identity_provider_requests
                    WHERE session_id=? AND request_type='LIVENESS_TOKEN'
                    """, String.class, created.id());
            assertNotNull(stored);
            assertNotEquals(providerUrl.toString(), stored);
            assertTrue(stored.startsWith("hhy-id-v1."));

            jdbc.update("""
                    UPDATE hhy.identity_verification_sessions
                    SET status='REJECTED',completed_at=?,failure_code='TEST_REJECTED'
                    WHERE id=?
                    """, OffsetDateTime.ofInstant(now.plusSeconds(2), ZoneOffset.UTC), created.id());
            var rejected = store.find(created.id(), userId).orElseThrow();
            var retried = store.retry(rejected, "ALIYUN_MARKET_FACE",
                    "retry-" + suffix, now.plusSeconds(900), now.plusSeconds(3));
            assertEquals(2, retried.attemptNo());
            assertFalse(retried.state().isBlank());
            assertNotEquals(created.state(), retried.state());
        } finally {
            jdbc.update("DELETE FROM hhy.identity_provider_requests WHERE session_id IN "
                    + "(SELECT id FROM hhy.identity_verification_sessions WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM hhy.identity_verification_sessions WHERE user_id=?", userId);
            jdbc.update("DELETE FROM hhy.identity_profiles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM hhy.users WHERE id=?", userId);
        }
    }

    private static String hex(String source) {
        String value = source.replaceAll("[^0-9a-fA-F]", "a").toLowerCase();
        return (value + "a".repeat(64)).substring(0, 64);
    }
}
