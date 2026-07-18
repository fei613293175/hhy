package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserAuthContracts.RefreshRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordLoginRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordResetRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegisterRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");
    private static final String OLD_REFRESH_TOKEN =
            "hhy_rt1_old-refresh-token-value-with-enough-entropy-for-test";
    private static final String IDEMPOTENCY_KEY = "refresh-idem-key-0001";

    @Mock UserAuthStore repository;
    @Mock UserAuthPolicy policy;
    @Mock UserAuthVerificationService verification;
    @Mock PasswordEncoder passwords;

    private UserTokenService tokens;
    private UserAuthService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UserAuthProperties properties = properties();
        tokens = new UserTokenService(objectMapper, properties);
        service = new UserAuthService(
                repository,
                tokens,
                new UserIdempotencySnapshotCipher(properties),
                properties,
                policy,
                verification,
                passwords,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refreshRotatesSecretsAndReplaysTheEncryptedFirstResponse() {
        RefreshRequest request = new RefreshRequest(OLD_REFRESH_TOKEN, "31");
        String oldHash = tokens.refreshHash(OLD_REFRESH_TOKEN);
        String requestHash = tokens.intentHash(
                "authPostAuthRefresh", OLD_REFRESH_TOKEN, "31");
        String scope = "ua:rf:" + tokens.intentHash("idempotency-scope", oldHash).substring(0, 48);
        var firstRow = new UserAuthStore.IdempotencyRow(91L, requestHash, null, null, null);
        when(repository.claimIdempotency(
                eq(scope), eq(IDEMPOTENCY_KEY), eq(requestHash), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(firstRow, false));
        when(repository.findSessionForUpdate(oldHash)).thenReturn(Optional.of(
                new UserAuthStore.UserSessionRow(
                        23L, 17L, "old-access-jti", oldHash, 31L,
                        NOW.plus(Duration.ofDays(2)), 4L, "ACTIVE", "Pixel 9", NOW.minusSeconds(60))));
        when(repository.rotateSession(
                eq(23L), eq(4L), eq(oldHash), anyString(), anyString(), any(Instant.class)))
                .thenReturn(true);

        var first = service.refresh(request, OLD_REFRESH_TOKEN, IDEMPOTENCY_KEY);

        assertEquals("17", first.userId());
        assertEquals("23", first.sessionId());
        assertEquals("31", first.device().deviceId());
        assertEquals("ANDROID", first.device().platform());
        assertNotEquals(OLD_REFRESH_TOKEN, first.refreshToken());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), first.expiresAt());

        ArgumentCaptor<String> newRefreshHash = ArgumentCaptor.forClass(String.class);
        verify(repository).rotateSession(
                eq(23L), eq(4L), eq(oldHash), anyString(), newRefreshHash.capture(), any(Instant.class));
        assertEquals(tokens.refreshHash(first.refreshToken()), newRefreshHash.getValue());
        assertNotEquals(first.refreshToken(), newRefreshHash.getValue());

        ArgumentCaptor<String> responseType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ciphertext = ArgumentCaptor.forClass(String.class);
        verify(repository).completeIdempotencySnapshot(
                eq(91L), eq("user-auth-session-v1:ok"), responseType.capture(), ciphertext.capture());
        assertEquals("user-auth-session-v1", responseType.getValue());
        assertFalse(ciphertext.getValue().contains(first.refreshToken()));
        assertFalse(ciphertext.getValue().contains(first.accessToken()));

        var replayRow = new UserAuthStore.IdempotencyRow(
                91L, requestHash, "user-auth-session-v1:ok", responseType.getValue(), ciphertext.getValue());
        when(repository.claimIdempotency(
                eq(scope), eq(IDEMPOTENCY_KEY), eq(requestHash), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(replayRow, true));

        var replay = service.refresh(request, OLD_REFRESH_TOKEN, IDEMPOTENCY_KEY);

        assertEquals(first, replay);
        verify(repository, times(1)).findSessionForUpdate(oldHash);
        verify(repository, times(1)).rotateSession(
                eq(23L), eq(4L), eq(oldHash), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void headerAndBodyRefreshTokensMustMatchBeforePersistence() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.refresh(
                        new RefreshRequest(OLD_REFRESH_TOKEN, "31"),
                        "different-refresh-token", IDEMPOTENCY_KEY));

        assertEquals("COMMON-401-UNAUTHENTICATED", error.code());
        verify(repository, never()).claimIdempotency(anyString(), anyString(), anyString(), any());
    }

    @Test
    void restrictedAccountCannotRotateTheSession() {
        RefreshRequest request = new RefreshRequest(OLD_REFRESH_TOKEN, "31");
        String oldHash = tokens.refreshHash(OLD_REFRESH_TOKEN);
        String requestHash = tokens.intentHash(
                "authPostAuthRefresh", OLD_REFRESH_TOKEN, "31");
        when(repository.claimIdempotency(anyString(), anyString(), eq(requestHash), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(92L, requestHash, null, null, null), false));
        when(repository.findSessionForUpdate(oldHash)).thenReturn(Optional.of(
                new UserAuthStore.UserSessionRow(
                        23L, 17L, "old-access-jti", oldHash, 31L,
                        NOW.plusSeconds(60), 4L, "FROZEN", "Pixel 9", NOW)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.refresh(request, OLD_REFRESH_TOKEN, IDEMPOTENCY_KEY));

        assertEquals("AUTH-423-ACCOUNT_RESTRICTED", error.code());
        assertEquals(423, error.httpStatus());
        verify(repository, never()).rotateSession(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sameKeyWithDifferentIntentReturnsFrozenIdempotencyConflict() {
        RefreshRequest request = new RefreshRequest(OLD_REFRESH_TOKEN, "31");
        when(repository.claimIdempotency(anyString(), eq(IDEMPOTENCY_KEY), anyString(), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(
                                93L, "different-request-hash", "user-session:23",
                                "user-auth-session-v1", "ciphertext"), true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.refresh(request, OLD_REFRESH_TOKEN, IDEMPOTENCY_KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        assertEquals(409, error.httpStatus());
        verify(repository, never()).findSessionForUpdate(anyString());
    }

    @Test
    void passwordLoginVerifiesChallengeAndCreatesDeviceBoundSession() {
        PasswordLoginRequest request = new PasswordLoginRequest(
                "13800000000", "Correct99", "challenge-1", "proof-1",
                Map.of(
                        "deviceFingerprint", "install-fingerprint-1",
                        "model", "Pixel 9",
                        "platform", "ANDROID",
                        "osVersion", "15",
                        "appVersion", "1.2.2-debug"));
        when(repository.claimIdempotency(anyString(), eq("password-idem-key-0001"), anyString(), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(94L, "request-hash", null, null, null), false));
        when(repository.findCredentialForUpdate("13800000000")).thenReturn(Optional.of(
                new UserAuthStore.CredentialRow(17L, "ACTIVE", 71L, "bcrypt-hash", 0, null)));
        when(passwords.matches("Correct99", "bcrypt-hash")).thenReturn(true);
        when(repository.upsertDevice(eq(17L), anyString(), eq("Pixel 9"), any(Instant.class))).thenReturn(31L);
        when(repository.createSession(eq(17L), eq(31L), anyString(), anyString(), any(Instant.class)))
                .thenReturn(23L);

        var session = service.passwordLogin(request, "password-idem-key-0001", "203.0.113.7");

        assertEquals("17", session.userId());
        assertEquals("23", session.sessionId());
        assertEquals("31", session.device().deviceId());
        assertEquals("ANDROID", session.device().platform());
        verify(verification).verifyChallenge("challenge-1", "proof-1", UserAuthContracts.AuthScene.LOGIN);
        verify(repository).clearPasswordFailures(71L);
        verify(repository).recordLogin(17L, "13800000000", 31L, "203.0.113.7", "PASSWORD");
        verify(repository).completeIdempotencySnapshot(
                eq(94L), eq("user-auth-session-v1:ok"), eq("user-auth-session-v1"), anyString());
    }

    @Test
    void passwordLoginLocksAccountAfterConfiguredFailureThresholdWithoutCreatingSession() {
        PasswordLoginRequest request = new PasswordLoginRequest(
                "13800000000", "WrongPass99", "challenge-2", "proof-2", Map.of());
        when(repository.claimIdempotency(anyString(), eq("password-idem-key-0002"), anyString(), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(95L, "request-hash", null, null, null), false));
        when(repository.findCredentialForUpdate("13800000000")).thenReturn(Optional.of(
                new UserAuthStore.CredentialRow(17L, "ACTIVE", 71L, "bcrypt-hash", 2, null)));
        when(passwords.matches("WrongPass99", "bcrypt-hash")).thenReturn(false);
        when(policy.passwordMaxFailures()).thenReturn(3);
        when(policy.passwordLockDuration()).thenReturn(Duration.ofMinutes(10));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.passwordLogin(request, "password-idem-key-0002", "203.0.113.7"));

        assertEquals("COMMON-401-UNAUTHENTICATED", error.code());
        verify(verification).verifyChallenge("challenge-2", "proof-2", UserAuthContracts.AuthScene.LOGIN);
        verify(repository).recordPasswordFailure(71L, 3, NOW.plus(Duration.ofMinutes(10)));
        verify(repository, never()).createSession(anyLong(), any(), anyString(), anyString(), any(Instant.class));
        verify(repository).abandonIdempotency(95L);
    }

    @Test
    void registrationPersistsValidatedInviteAgreementsDeviceAndSessionTogether() {
        RegisterRequest request = new RegisterRequest(
                "13900000000", "481516", "Correct99", "INVITE-R02", List.of("101", "102"),
                Map.of(
                        "deviceFingerprint", "install-fingerprint-2",
                        "model", "Pixel 9",
                        "platform", "ANDROID",
                        "osVersion", "15",
                        "appVersion", "1.2.2-debug"));
        when(repository.claimIdempotency(anyString(), eq("register-idem-key-0001"), anyString(), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(96L, "request-hash", null, null, null), false));
        when(policy.passwordMinLength()).thenReturn(8);
        when(policy.passwordMaxLength()).thenReturn(72);
        when(policy.passwordRequireLetters()).thenReturn(true);
        when(policy.passwordRequireDigits()).thenReturn(true);
        when(repository.validateAgreementVersions(List.of("101", "102"))).thenReturn(List.of(101L, 102L));
        when(repository.findUser("13900000000")).thenReturn(Optional.empty());
        when(repository.findActiveInviter("INVITE-R02")).thenReturn(Optional.of(13L));
        when(passwords.encode("Correct99")).thenReturn("bcrypt-new-hash");
        when(repository.createUser("13900000000")).thenReturn(17L);
        when(repository.upsertDevice(eq(17L), anyString(), eq("Pixel 9"), any(Instant.class))).thenReturn(31L);
        when(repository.createSession(eq(17L), eq(31L), anyString(), anyString(), any(Instant.class)))
                .thenReturn(23L);

        var session = service.register(request, "register-idem-key-0001", "203.0.113.8");

        assertEquals("17", session.userId());
        assertEquals("23", session.sessionId());
        verify(repository).lockRegistration("13900000000");
        verify(verification).verifySms("13900000000", UserAuthContracts.AuthScene.REGISTER, "481516");
        verify(repository).createCredential(17L, "bcrypt-new-hash", NOW);
        verify(repository).createProfile(17L);
        verify(repository).recordRegistration(17L, "13900000000", "INVITE-R02", 13L, 31L, "203.0.113.8");
        verify(repository).acceptAgreementVersions(17L, 31L, List.of(101L, 102L));
        verify(repository).recordLogin(17L, "13900000000", 31L, "203.0.113.8", "REGISTER");
        verify(repository).completeIdempotencySnapshot(
                eq(96L), eq("user-auth-session-v1:ok"), eq("user-auth-session-v1"), anyString());
    }

    @Test
    void passwordResetVerifiesSmsAndRevokesExistingSessionsBeforeReturningSuccess() {
        PasswordResetRequest request = new PasswordResetRequest("13800000000", "481516", "NewPass99");
        when(repository.claimIdempotency(anyString(), eq("reset-idem-key-0001"), anyString(), any(Instant.class)))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(97L, "request-hash", null, null, null), false));
        when(policy.passwordMinLength()).thenReturn(8);
        when(policy.passwordMaxLength()).thenReturn(72);
        when(policy.passwordRequireLetters()).thenReturn(true);
        when(policy.passwordRequireDigits()).thenReturn(true);
        when(repository.findCredentialForUpdate("13800000000")).thenReturn(Optional.of(
                new UserAuthStore.CredentialRow(17L, "ACTIVE", 71L, "bcrypt-old-hash", 2, null)));
        when(passwords.encode("NewPass99")).thenReturn("bcrypt-reset-hash");

        var result = service.resetPassword(request, "reset-idem-key-0001");

        assertEquals("17", result.resourceId());
        assertEquals("PASSWORD_RESET", result.status());
        assertEquals(NOW, result.acceptedAt());
        verify(verification).verifySms("13800000000", UserAuthContracts.AuthScene.RESET_PASSWORD, "481516");
        verify(repository).updatePasswordAndRevokeSessions(71L, 17L, "bcrypt-reset-hash", NOW);
        verify(repository).completeIdempotencySnapshot(
                eq(97L), eq("password-reset-v1:ok"), eq("password-reset-v1"), anyString());
    }

    private static UserAuthProperties properties() {
        return new UserAuthProperties(
                "test-only-user-jwt-secret-at-least-32-characters",
                "test-only-user-token-hmac-secret-at-least-32-characters",
                "test-only-user-snapshot-root-secret-at-least-32-characters",
                "test/user",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofHours(24));
    }
}
