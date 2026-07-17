package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSecurityIdempotencyDigestTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final String HMAC_SECRET = "test-only-idempotency-hmac-secret-at-least-32-chars";

    @Mock AdminSecurityStore repository;
    @Mock AdminLoginFactWriter loginFacts;
    @Mock PasswordEncoder passwords;
    @TempDir Path secretDirectory;

    private AdminSecurityService service;

    @BeforeEach
    void setUp() {
        AdminSecurityProperties properties = properties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var objectMapper = new ObjectMapper();
        var secrets = new AdminMfaSecretStore(
                secretDirectory.toString(), "v1", properties.mfaRootSecret(), "");
        service = new AdminSecurityService(
                repository,
                new AdminTokenService(objectMapper, properties, clock),
                new AdminTotpService(properties, clock, secrets),
                loginFacts,
                passwords,
                properties,
                objectMapper,
                clock);
    }

    @Test
    void logoutDigestIsKeyedHmacRatherThanRawSha256() throws Exception {
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(71L, invocation.getArgument(2), null), false));
        when(repository.revokeSession(23L, 4L, NOW)).thenReturn(true);
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of("admin.self.read"));

        service.logout(
                principal, 4L, "security incident", "digest-key-00000001", "request-digest", "203.0.113.8");

        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).claimIdempotency(
                eq("admin.logout:23"), eq("digest-key-00000001"), digest.capture(), expiresAt.capture());
        String canonical = "4:security incident";
        String rawSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));

        assertTrue(digest.getValue().matches("^[0-9a-f]{64}$"));
        assertNotEquals(rawSha256, digest.getValue());
        assertNotEquals(canonical, digest.getValue());
        assertEquals(hmac(canonical), digest.getValue());
        assertEquals(NOW.plus(Duration.ofHours(24)), expiresAt.getValue());
    }

    private static String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static AdminSecurityProperties properties() {
        return new AdminSecurityProperties(
                "test-only-admin-jwt-secret-at-least-32-characters",
                "test-only-admin-mfa-root-secret-at-least-32-characters",
                HMAC_SECRET,
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
