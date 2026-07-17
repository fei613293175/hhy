package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.PasswordChangeRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSecurityServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

    @Mock AdminSecurityStore repository;
    @Mock AdminLoginFactWriter loginFacts;
    @Mock PasswordEncoder passwords;

    private ObjectMapper objectMapper;
    private AdminTotpService totp;
    private AdminSecurityService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AdminSecurityProperties properties = properties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var tokens = new AdminTokenService(objectMapper, properties, clock);
        totp = new AdminTotpService(properties, clock);
        service = new AdminSecurityService(
                repository,
                tokens,
                totp,
                loginFacts,
                passwords,
                properties,
                objectMapper,
                clock);
    }

    @Test
    void sameIdempotencyKeyWithDifferentLogoutIntentRejectsBeforeAnySideEffect() {
        var principal = principal();
        when(repository.claimIdempotency(anyString(), eq("idem-key-00000001"), anyString(), any(Instant.class)))
                .thenReturn(new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(91L, "different-request-digest", "session:23"), true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.logout(
                        principal, 4L, "security incident", "idem-key-00000001", "request-1", "203.0.113.8"));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        assertEquals(409, error.httpStatus());
        assertFalse(error.retryable());
        verify(repository, never()).revokeSession(anyLong(), anyLong(), any(Instant.class));
        verify(repository, never()).operationLog(
                anyLong(), anyString(), anyString(), anyLong(), any(), anyString(), anyString());
        verify(repository, never()).completeIdempotency(anyLong(), anyString());
    }

    @Test
    void passwordChangeWritesOnlyTheHashAndKeepsSecretsOutOfAuditAndResponse() {
        var request = new PasswordChangeRequest("Current!234", "New!56789", "123456");
        var principal = principal();
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(92L, invocation.getArgument(2), null), false));
        when(repository.findAdmin(17L)).thenReturn(Optional.of(
                new AdminSecurityStore.AdminAccount(17L, "root", "stored-password-hash", "ACTIVE", 6L, true)));
        when(passwords.matches("Current!234", "stored-password-hash")).thenReturn(true);
        when(passwords.encode("New!56789")).thenReturn("bcrypt-password-hash");
        when(repository.mfaMethod(17L)).thenReturn(Optional.of(
                new AdminSecurityStore.MfaMethodRow(
                        31L, 17L, "TOTP", "derived:v1:enrollment-17", "ACTIVE", 2L, NOW.minusSeconds(60))));
        String validMfaCode = totp.codeAt(17L, "derived:v1:enrollment-17", NOW);
        request = new PasswordChangeRequest("Current!234", "New!56789", validMfaCode);
        when(repository.updatePassword(17L, 6L, "bcrypt-password-hash")).thenReturn(true);

        var response = service.changePassword(
                principal, request, "idem-key-00000002", "request-sensitive-1", "203.0.113.8");

        assertEquals("PASSWORD_CHANGED", response.status());
        assertEquals(7L, response.version());
        verify(repository).updatePassword(17L, 6L, "bcrypt-password-hash");
        verify(repository, never()).updatePassword(17L, 6L, "New!56789");

        ArgumentCaptor<String> auditJson = ArgumentCaptor.forClass(String.class);
        verify(repository).operationLog(
                eq(17L), eq("ADMIN_PASSWORD_CHANGED"), eq("admin_user"), eq(17L),
                isNull(), auditJson.capture(), eq("203.0.113.8"));
        String persistedAudit = auditJson.getValue();
        assertTrue(persistedAudit.contains("request-sensitive-1"));
        assertFalse(persistedAudit.contains("Current!234"));
        assertFalse(persistedAudit.contains("New!56789"));
        assertFalse(persistedAudit.contains(validMfaCode));
        assertFalse(response.toString().contains("Current!234"));
        assertFalse(response.toString().contains("New!56789"));
        assertFalse(response.toString().contains(validMfaCode));
    }

    private static AdminPrincipal principal() {
        return new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root",
                Set.of("admin.self.read", "admin.self.security"));
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
