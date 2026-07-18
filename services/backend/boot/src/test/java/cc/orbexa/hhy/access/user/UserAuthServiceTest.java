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
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");
    private static final String OLD_REFRESH_TOKEN =
            "hhy_rt1_old-refresh-token-value-with-enough-entropy-for-test";
    private static final String IDEMPOTENCY_KEY = "refresh-idem-key-0001";

    @Mock UserAuthStore repository;

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
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refreshRotatesSecretsAndReplaysTheEncryptedFirstResponse() {
        RefreshRequest request = new RefreshRequest(OLD_REFRESH_TOKEN, "31");
        String oldHash = tokens.refreshHash(OLD_REFRESH_TOKEN);
        String requestHash = tokens.intentHash(
                "authPostAuthRefresh", OLD_REFRESH_TOKEN, "31");
        String scope = "user-auth-refresh:" + oldHash;
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
                eq(91L), eq("user-session:23"), responseType.capture(), ciphertext.capture());
        assertEquals("user-auth-session-v1", responseType.getValue());
        assertFalse(ciphertext.getValue().contains(first.refreshToken()));
        assertFalse(ciphertext.getValue().contains(first.accessToken()));

        var replayRow = new UserAuthStore.IdempotencyRow(
                91L, requestHash, "user-session:23", responseType.getValue(), ciphertext.getValue());
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
