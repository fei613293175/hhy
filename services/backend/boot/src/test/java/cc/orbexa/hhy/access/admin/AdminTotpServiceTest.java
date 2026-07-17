package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminTotpServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final String SECRET_REF = "derived:v1:enrollment-17";

    private AdminTotpService totp;

    @BeforeEach
    void setUp() {
        totp = new AdminTotpService(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsCurrentAndAdjacentTotpWindowAndRejectsMalformedInput() {
        String currentCode = totp.codeAt(17L, SECRET_REF, NOW);
        String previousCode = totp.codeAt(17L, SECRET_REF, NOW.minusSeconds(30));

        assertTrue(totp.verify(17L, SECRET_REF, currentCode));
        assertTrue(totp.verify(17L, SECRET_REF, previousCode));
        assertFalse(totp.verify(17L, SECRET_REF, "12345"));
        assertFalse(totp.verify(17L, "plaintext-secret", currentCode));
        assertFalse(totp.verify(17L, SECRET_REF, null));
    }

    @Test
    void enrollmentKeepsTheDerivedSecretOutOfTheStoredReferenceAndMaskedKey() {
        var material = totp.enrollment(17L, "root admin", "enrollment-17", NOW);
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
