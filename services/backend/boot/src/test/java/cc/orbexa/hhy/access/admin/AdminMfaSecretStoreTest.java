package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminMfaSecretStoreTest {
    private static final String ROOT_V1 = "test-mfa-root-secret-v1-at-least-32-characters";
    private static final String ROOT_V2 = "test-mfa-root-secret-v2-at-least-32-characters";

    @TempDir Path secretDirectory;

    @Test
    void createsIndependentRandomBase32Seeds() {
        var store = store("v1", ROOT_V1, "");

        var first = store.create(17L, "enrollment-a");
        var second = store.create(17L, "enrollment-b");

        assertTrue(first.secret().matches("^[A-Z2-7]{32}$"));
        assertTrue(second.secret().matches("^[A-Z2-7]{32}$"));
        assertNotEquals(first.secret(), second.secret());
        assertNotEquals(first.reference(), second.reference());
    }

    @Test
    void revokingOldReferenceMakesItUnresolvableAfterRebinding() {
        var store = store("v1", ROOT_V1, "");
        var oldSecret = store.create(17L, "enrollment-old");

        store.revoke(17L, oldSecret.reference());
        var rebound = store.create(17L, "enrollment-new");

        assertThrows(IllegalStateException.class,
                () -> store.resolve(17L, oldSecret.reference()));
        assertEquals(rebound.secret(), store.resolve(17L, rebound.reference()).secret());
    }

    @Test
    void unavailableRootVersionFailsClosedWithoutTotpFallback() {
        var firstVersion = store("v1", ROOT_V1, "");
        var stored = firstVersion.create(17L, "enrollment-v1");
        var currentOnly = store("v2", ROOT_V2, "");
        var totp = new AdminTotpService(
                properties(ROOT_V2), Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC),
                currentOnly);

        assertThrows(IllegalStateException.class,
                () -> currentOnly.resolve(17L, stored.reference()));
        assertFalse(totp.verify(17L, stored.reference(), "000000"));
    }

    @Test
    void rotatedStoreReadsPreviousVersionButCreatesOnlyCurrentVersionReferences() {
        var firstVersion = store("v1", ROOT_V1, "");
        var old = firstVersion.create(17L, "enrollment-v1");
        var rotated = store("v2", ROOT_V2, "v1=" + ROOT_V1);

        assertEquals(old.secret(), rotated.resolve(17L, old.reference()).secret());
        var current = rotated.create(17L, "enrollment-v2");
        assertTrue(current.reference().startsWith("secret-file:v2:"));
        assertEquals(current.secret(), rotated.resolve(17L, current.reference()).secret());
    }

    @Test
    void referenceAndEncryptedFileNeverContainPlainSeed() throws Exception {
        var store = store("v1", ROOT_V1, "");
        var stored = store.create(17L, "enrollment-private");
        List<Path> files;
        try (var paths = Files.list(secretDirectory)) {
            files = paths.toList();
        }

        assertEquals(1, files.size());
        String encrypted = Files.readString(files.getFirst());
        assertFalse(stored.reference().contains(stored.secret()));
        assertFalse(encrypted.contains(stored.secret()));
        assertFalse(files.getFirst().getFileName().toString().contains(stored.secret()));
    }

    private AdminMfaSecretStore store(String version, String currentSecret, String previous) {
        return new AdminMfaSecretStore(secretDirectory.toString(), version, currentSecret, previous);
    }

    private static AdminSecurityProperties properties(String mfaSecret) {
        return new AdminSecurityProperties(
                "test-only-admin-jwt-secret-at-least-32-characters",
                mfaSecret,
                "test-only-idempotency-hmac-secret-at-least-32-chars",
                "test/admin",
                Duration.ofHours(8),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                8,
                72,
                5,
                900);
    }
}
