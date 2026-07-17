package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.CommandResultResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSecurityIdempotencySnapshotServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final String KEY = "idem-replay-logout-000001";
    private static final String SCOPE = "admin.logout:23";
    private static final String TYPE = "r01.command-result.v1";

    @Mock AdminSecurityStore repository;
    @Mock AdminTokenService tokens;
    @Mock AdminTotpService totp;
    @Mock AdminLoginFactWriter loginFacts;
    @Mock PasswordEncoder passwords;

    private ObjectMapper objectMapper;
    private AdminSecurityProperties properties;
    private AdminIdempotencySnapshotCipher cipher;
    private AdminSecurityService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        properties = properties();
        cipher = new AdminIdempotencySnapshotCipher(
                "v2", properties.mfaRootSecret(), "v1=test-only-previous-root-secret-at-least-32-characters");
        service = new AdminSecurityService(
                repository, tokens, totp, loginFacts, passwords, properties,
                objectMapper, cipher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstLogoutAtomicallyPersistsTypedEncryptedResponseSnapshot() throws Exception {
        String digest = hmac("4:null");
        var claimed = new AdminSecurityStore.IdempotencyRow(81L, digest, null);
        when(repository.claimIdempotency(eq(SCOPE), eq(KEY), eq(digest), any(Instant.class)))
                .thenReturn(new AdminSecurityStore.IdempotencyClaim(claimed, false));
        when(repository.revokeSession(23L, 4L, NOW)).thenReturn(true);

        CommandResultResource result = service.logout(
                principal(false), null, null, KEY, "request-first", "203.0.113.9");

        ArgumentCaptor<String> ciphertext = ArgumentCaptor.forClass(String.class);
        verify(repository).completeIdempotencySnapshot(
                eq(81L), eq("session:23"), eq(TYPE), ciphertext.capture());
        byte[] plaintext = cipher.decrypt(SCOPE, KEY, digest, TYPE, ciphertext.getValue());
        assertEquals(result, objectMapper.readValue(plaintext, CommandResultResource.class));
        verify(repository, never()).completeIdempotency(anyLong(), anyString());
    }

    @Test
    void revokedBearerReplaysExactEncryptedSnapshotWithoutBusinessSideEffects() throws Exception {
        String digest = hmac("4:null");
        CommandResultResource original = new CommandResultResource(
                "23", "23", "REVOKED", 5L, NOW.minusSeconds(17));
        String encrypted = cipher.encrypt(
                SCOPE, KEY, digest, TYPE, objectMapper.writeValueAsBytes(original));
        when(repository.findIdempotency(SCOPE, KEY)).thenReturn(Optional.of(
                new AdminSecurityStore.IdempotencyRow(82L, digest, "session:23", TYPE, encrypted)));

        CommandResultResource replayed = service.logout(
                principal(true), null, null, KEY, "request-replay", "203.0.113.9");

        assertEquals(original, replayed);
        verify(repository).findIdempotency(SCOPE, KEY);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void revokedBearerWithoutCompletedSnapshotIsUnauthorizedAndHasZeroSideEffects() {
        String digest = hmac("4:null");
        when(repository.findIdempotency(SCOPE, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(83L, digest, null)));

        BusinessException absent = assertThrows(BusinessException.class,
                () -> service.logout(principal(true), null, null, KEY, "request-absent", "203.0.113.9"));
        BusinessException incomplete = assertThrows(BusinessException.class,
                () -> service.logout(principal(true), null, null, KEY, "request-incomplete", "203.0.113.9"));

        assertEquals("COMMON-401-UNAUTHENTICATED", absent.code());
        assertEquals("COMMON-401-UNAUTHENTICATED", incomplete.code());
        verify(repository, never()).revokeSession(anyLong(), anyLong(), any());
        verify(repository, never()).operationLog(
                anyLong(), anyString(), anyString(), anyLong(), any(), anyString(), anyString());
        verify(repository, never()).completeIdempotencySnapshot(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void revokedBearerDigestConflictWinsBeforeAnySideEffect() {
        when(repository.findIdempotency(SCOPE, KEY)).thenReturn(Optional.of(
                new AdminSecurityStore.IdempotencyRow(84L, "different-digest", null)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.logout(principal(true), null, null, KEY, "request-conflict", "203.0.113.9"));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(repository, never()).revokeSession(anyLong(), anyLong(), any());
        verify(repository, never()).completeIdempotencySnapshot(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void corruptedUnknownTypeAndPartialSnapshotsFailClosedWithoutLegacyFallback() throws Exception {
        String digest = hmac("4:null");
        CommandResultResource response = new CommandResultResource("23", "23", "REVOKED", 5L, NOW);
        String encrypted = cipher.encrypt(
                SCOPE, KEY, digest, TYPE, objectMapper.writeValueAsBytes(response));
        String invalidJson = cipher.encrypt(
                SCOPE, KEY, digest, TYPE, "not-json".getBytes(StandardCharsets.UTF_8));
        String maliciousStructure = cipher.encrypt(
                SCOPE, KEY, digest, TYPE,
                "{\"resourceId\":{\"unexpected\":\"object\"},\"status\":\"REVOKED\"}"
                        .getBytes(StandardCharsets.UTF_8));
        String corrupted = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");
        when(repository.findIdempotency(SCOPE, KEY))
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(
                        85L, digest, "session:23", TYPE, corrupted)))
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(
                        86L, digest, "session:23", "r01.wrong.v1", encrypted)))
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(
                        87L, digest, "session:23", TYPE, null)))
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(
                        88L, digest, "session:23", TYPE, invalidJson)))
                .thenReturn(Optional.of(new AdminSecurityStore.IdempotencyRow(
                        89L, digest, "session:23", TYPE, maliciousStructure)));

        for (int attempt = 0; attempt < 5; attempt++) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.logout(principal(true), null, null, KEY, "request-fail-closed", "203.0.113.9"));
            assertTrue(error.getMessage().contains("snapshot"));
        }
        verify(repository, never()).revokeSession(anyLong(), anyLong(), any());
    }

    @Test
    void legacyFallbackIsAllowedOnlyWhenBothSnapshotColumnsAreNull() {
        String digest = hmac("4:null");
        when(repository.findIdempotency(SCOPE, KEY)).thenReturn(Optional.of(
                new AdminSecurityStore.IdempotencyRow(90L, digest, "session:23", null, null)));

        CommandResultResource replayed = service.logout(
                principal(true), null, null, KEY, "request-legacy", "203.0.113.9");

        assertEquals("REVOKED", replayed.status());
        assertEquals(5L, replayed.version());
        verify(repository, never()).revokeSession(anyLong(), anyLong(), any());
    }

    @Test
    void reservedMarkerCannotBecomeANormalPrincipalOrBypassServiceWriteBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root",
                Set.of(AdminPrincipal.IDEMPOTENCY_REPLAY_MARKER), false));
        AdminPrincipal noWritePermission = new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root", Set.of(), false);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.enrollMfa(
                        noWritePermission, "idem-no-write-000000001", "request-denied", "203.0.113.9"));

        assertEquals("COMMON-403-FORBIDDEN", denied.code());
        verifyNoMoreInteractions(repository);
    }

    private static AdminPrincipal principal(boolean replayOnly) {
        return new AdminPrincipal(
                17L, 23L, 4L, "access-jti-17", "root",
                replayOnly ? Set.of() : Set.of("admin.self.security"), replayOnly);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static AdminSecurityProperties properties() {
        return new AdminSecurityProperties(
                "test-only-admin-jwt-secret-at-least-32-characters",
                "test-only-admin-mfa-root-secret-at-least-32-characters",
                "test-only-idempotency-hmac-secret-at-least-32-chars",
                "test/admin",
                Duration.ofHours(8), Duration.ofMinutes(5), Duration.ofMinutes(10),
                8, 72, 5, 900);
    }
}
