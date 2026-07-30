package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaVerifyRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSecurityRateLimitTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final String IP = "203.0.113.8";
    private static final String DEVICE = "device-fingerprint-17";

    @Mock AdminSecurityStore repository;
    @Mock AdminLoginFactWriter loginFacts;
    @Mock PasswordEncoder passwords;
    @TempDir Path secretDirectory;

    private AdminTokenService tokens;
    private AdminSecurityService service;

    @BeforeEach
    void setUp() {
        AdminSecurityProperties properties = properties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var secrets = new AdminMfaSecretStore(
                secretDirectory.toString(), "v1", properties.mfaRootSecret(), "");
        tokens = new AdminTokenService(objectMapper, properties, clock);
        var totp = new AdminTotpService(properties, clock, secrets);
        service = new AdminSecurityService(
                repository, tokens, totp, loginFacts, passwords, properties, objectMapper, clock);
    }

    @Test
    void unknownAccountIsRateLimitedByIpAndBucketsContainNoRawIdentifiers() {
        when(repository.findIdempotency(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findAdminByUsername("ghost@example.test")).thenReturn(Optional.empty());
        when(repository.recentLoginFailuresByIp(eq(IP), any(Instant.class))).thenReturn(5L);
        when(repository.oldestLoginFailureByIp(eq(IP), any(Instant.class)))
                .thenReturn(NOW.minusSeconds(100));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(
                        new LoginRequest("ghost@example.test", "Current!234", null),
                        "rate-limit-key-0001", "request-rate-ip", IP, DEVICE));

        assertRateLimit(error, 800L);
        ArgumentCaptor<List<String>> buckets = listCaptor();
        verify(repository).lockRateLimitBuckets(buckets.capture());
        assertEquals(3, buckets.getValue().size());
        String joined = String.join("|", buckets.getValue());
        assertFalse(joined.contains("ghost@example.test"));
        assertFalse(joined.contains(IP));
        assertFalse(joined.contains(DEVICE));
        verify(loginFacts, never()).failure(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void knownAccountDimensionUsesFullWindowWhenOldestFactIsUnavailable() {
        var admin = new AdminSecurityStore.AdminAccount(
                17L, "root", "stored-hash", "ACTIVE", 2L, false);
        when(repository.findIdempotency(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findAdminByUsername("root")).thenReturn(Optional.of(admin));
        when(repository.recentPasswordFailures(eq(17L), any(Instant.class))).thenReturn(5L);
        when(repository.oldestPasswordFailure(eq(17L), any(Instant.class))).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(
                        new LoginRequest("root", "Current!234", null),
                        "rate-limit-key-0002", "request-rate-account", IP, DEVICE));

        assertRateLimit(error, 900L);
        verify(passwords, never()).matches(anyString(), anyString());
    }

    @Test
    void deviceWindowAtExpiryBoundaryStillReturnsMinimumOneSecond() {
        var admin = new AdminSecurityStore.AdminAccount(
                17L, "root", "stored-hash", "ACTIVE", 2L, false);
        when(repository.findIdempotency(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findAdminByUsername("root")).thenReturn(Optional.of(admin));
        when(repository.recentLoginFailuresByDevice(eq(DEVICE), any(Instant.class))).thenReturn(5L);
        when(repository.oldestLoginFailureByDevice(eq(DEVICE), any(Instant.class)))
                .thenReturn(NOW.minusSeconds(900));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(
                        new LoginRequest("root", "Current!234", null),
                        "rate-limit-key-0003", "request-rate-device", IP, DEVICE));

        assertRateLimit(error, 1L);
    }

    @Test
    void mfaUsesAdminSessionIpAndDeviceBucketsAndLongestRemainingWindow() {
        String ticket = tokens.issueMfaTicket(
                17L, 23L, 4L, "access-jti-17", NOW.plusSeconds(300)).value();
        when(repository.findIdempotency(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.verifyMfaTicket(any(AdminTokenService.TokenClaims.class), eq(NOW))).thenReturn(true);
        when(repository.recentMfaFailures(eq(17L), any(Instant.class))).thenReturn(5L);
        when(repository.recentMfaFailuresByIp(eq(IP), any(Instant.class))).thenReturn(5L);
        when(repository.recentMfaFailuresByDevice(eq(DEVICE), any(Instant.class))).thenReturn(5L);
        when(repository.oldestMfaFailure(eq(17L), any(Instant.class))).thenReturn(NOW.minusSeconds(800));
        when(repository.oldestMfaFailureByIp(eq(IP), any(Instant.class))).thenReturn(NOW.minusSeconds(100));
        when(repository.oldestMfaFailureByDevice(eq(DEVICE), any(Instant.class)))
                .thenReturn(NOW.minusSeconds(450));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyMfa(
                        new MfaVerifyRequest(ticket, "123456"), ticket,
                        "rate-limit-key-0004", "request-rate-mfa", IP, DEVICE));

        assertRateLimit(error, 800L);
        ArgumentCaptor<List<String>> buckets = listCaptor();
        verify(repository).lockRateLimitBuckets(buckets.capture());
        assertEquals(4, buckets.getValue().size());
        String joined = String.join("|", buckets.getValue());
        assertFalse(joined.contains(IP));
        assertFalse(joined.contains(DEVICE));
        verify(repository, never()).mfaMethod(17L);
    }

    private static void assertRateLimit(BusinessException error, long retryAfter) {
        assertEquals("COMMON-429-RATE_LIMITED", error.code());
        assertEquals(429, error.httpStatus());
        assertEquals(retryAfter, error.retryAfterSeconds());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<String>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
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
