package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminSecurityMfaSecretIsolationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

    @Mock AdminSecurityStore repository;
    @Mock AdminLoginFactWriter loginFacts;
    @Mock PasswordEncoder passwords;
    @TempDir Path secretDirectory;

    private AdminMfaSecretStore secretStore;
    private AdminTotpService totp;
    private AdminSecurityService service;

    @BeforeEach
    void setUp() {
        AdminSecurityProperties properties = properties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var objectMapper = new ObjectMapper();
        secretStore = new AdminMfaSecretStore(
                secretDirectory.toString(), "v1", properties.mfaRootSecret(), "");
        totp = new AdminTotpService(properties, clock, secretStore);
        service = new AdminSecurityService(
                repository,
                new AdminTokenService(objectMapper, properties, clock),
                totp,
                loginFacts,
                passwords,
                properties,
                objectMapper,
                clock);
    }

    @Test
    void rebindingRevokesOldReferenceAndPersistsOnlyOpaqueReferenceAndSafeAudit() {
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of("admin.self.security"));
        var old = totp.createEnrollment(17L, "root", "enrollment-old", NOW.minusSeconds(1800));
        var existing = new AdminSecurityStore.MfaMethodRow(
                31L, 17L, "TOTP", old.secretRef(), "DISABLED", 3L,
                NOW.minusSeconds(3600), NOW.minusSeconds(1800));
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(81L, invocation.getArgument(2), null), false));
        when(repository.mfaMethod(17L)).thenReturn(Optional.of(existing));
        when(repository.beginMfaEnrollment(eq(17L), anyString(), eq(NOW))).thenReturn(31L);

        TransactionSynchronizationManager.initSynchronization();
        AdminSecurityContracts.MfaEnrollmentResource response;
        try {
            response = service.enrollMfa(
                    principal, "mfa-enroll-key-0001", "request-enroll", "203.0.113.8");
            assertEquals(old.secretRef(), secretStore.resolve(17L, old.secretRef()).reference());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        String seed = response.secretQrCodeUrl().split("secret=", 2)[1].split("&", 2)[0];
        ArgumentCaptor<String> storedReference = ArgumentCaptor.forClass(String.class);
        verify(repository).beginMfaEnrollment(eq(17L), storedReference.capture(), eq(NOW));
        assertTrue(storedReference.getValue().startsWith("secret-file:v1:"));
        assertFalse(storedReference.getValue().contains(seed));
        assertEquals(seed, secretStore.resolve(17L, storedReference.getValue()).secret());
        assertNotEquals("enrollment-old", response.enrollmentId());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), response.expiresAt());
        assertThrows(IllegalStateException.class,
                () -> secretStore.resolve(17L, old.secretRef()));
        verify(repository).lockRateLimitBuckets(List.of("admin-mfa-enrollment:17"));

        ArgumentCaptor<String> auditJson = ArgumentCaptor.forClass(String.class);
        verify(repository).operationLog(
                eq(17L), eq("ADMIN_MFA_ENROLL_STARTED"), eq("admin_mfa_method"), eq(31L),
                isNull(), auditJson.capture(), eq("203.0.113.8"));
        assertFalse(auditJson.getValue().contains(seed));
        assertFalse(auditJson.getValue().contains(response.secretQrCodeUrl()));
        assertFalse(auditJson.getValue().contains(response.manualKeyMasked()));
    }

    @Test
    void expiredPendingEnrollmentGetsFreshReferenceEnrollmentIdAndTtl() {
        var principal = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of("admin.self.security"));
        var old = totp.createEnrollment(17L, "root", "pending-old", NOW.minusSeconds(1200));
        var existing = new AdminSecurityStore.MfaMethodRow(
                31L, 17L, "TOTP", old.secretRef(), "PENDING", 7L,
                NOW.minusSeconds(1200), NOW.minusSeconds(601));
        when(repository.claimIdempotency(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(82L, invocation.getArgument(2), null), false));
        when(repository.mfaMethod(17L)).thenReturn(Optional.of(existing));
        when(repository.beginMfaEnrollment(eq(17L), anyString(), eq(NOW))).thenReturn(31L);

        var response = service.enrollMfa(
                principal, "mfa-enroll-key-0002", "request-pending-rebind", "203.0.113.8");

        ArgumentCaptor<String> newReference = ArgumentCaptor.forClass(String.class);
        verify(repository).beginMfaEnrollment(eq(17L), newReference.capture(), eq(NOW));
        verify(repository).lockRateLimitBuckets(List.of("admin-mfa-enrollment:17"));
        assertNotEquals(old.secretRef(), newReference.getValue());
        assertNotEquals("pending-old", response.enrollmentId());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), response.expiresAt());
        assertThrows(IllegalStateException.class,
                () -> secretStore.resolve(17L, old.secretRef()));
        assertEquals(response.enrollmentId(), secretStore.enrollmentId(newReference.getValue()));
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
