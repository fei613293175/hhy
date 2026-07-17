package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminTotpServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    @TempDir Path secretDirectory;
    private AdminTotpService totp;
    private String secretRef;

    @BeforeEach
    void setUp() {
        AdminSecurityProperties properties = properties();
        var secrets = new AdminMfaSecretStore(
                secretDirectory.toString(), "v1", properties.mfaRootSecret(), "");
        totp = new AdminTotpService(properties, Clock.fixed(NOW, ZoneOffset.UTC), secrets);
        secretRef = totp.createEnrollment(17L, "root admin", "enrollment-17", NOW).secretRef();
    }

    @Test
    void acceptsCurrentAndAdjacentTotpWindowAndRejectsMalformedInput() {
        String currentCode = totp.codeAt(17L, secretRef, NOW);
        String previousCode = totp.codeAt(17L, secretRef, NOW.minusSeconds(30));

        assertTrue(totp.verify(17L, secretRef, currentCode));
        assertTrue(totp.verify(17L, secretRef, previousCode));
        assertEquals(
                NOW.getEpochSecond() / 30,
                totp.matchingStep(17L, secretRef, currentCode).orElseThrow());
        assertFalse(totp.verify(17L, secretRef, "12345"));
        assertFalse(totp.verify(17L, "plaintext-secret", currentCode));
        assertFalse(totp.verify(17L, secretRef, null));
    }

    @Test
    void enrollmentKeepsTheDerivedSecretOutOfTheStoredReferenceAndMaskedKey() {
        var material = totp.enrollmentFromReference(17L, "root admin", secretRef, NOW);
        String secret = material.qrCodeUrl().split("secret=", 2)[1].split("&", 2)[0];

        assertTrue(material.qrCodeUrl().startsWith("otpauth://totp/"));
        assertTrue(material.qrCodeUrl().contains("root%20admin"));
        assertTrue(material.manualKeyMasked().contains("****"));
        assertFalse(material.secretRef().contains(secret));
        assertFalse(material.manualKeyMasked().contains(secret));
        assertNotEquals(secret, material.manualKeyMasked());
    }

    private static AdminSecurityProperties properties() {
        return new AdminSecurityProperties(
                "test-only-admin-jwt-secret-at-least-32-characters",
                "test-only-admin-mfa-root-secret-at-least-32-characters",
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
