package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.R12ProfileContracts.ProfilePatchRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R12ProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-25T03:00:00Z");
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(7, 3, 1, "jti", "ACTIVE");

    private final R12ProfileStore profiles = mock(R12ProfileStore.class);
    private final UserAuthStore idempotency = mock(UserAuthStore.class);
    private final UserAuthService users = mock(UserAuthService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private UserIdempotencySnapshotCipher snapshots;
    private R12ProfileService service;

    @BeforeEach
    void setUp() {
        UserAuthProperties properties = new UserAuthProperties(
                "jwt-secret-0123456789-0123456789-012345",
                "hmac-secret-0123456789-0123456789-01234",
                "snapshot-secret-0123456789-0123456789-0",
                "hhy-test", Duration.ofMinutes(10), Duration.ofDays(30), Duration.ofHours(24));
        snapshots = new UserIdempotencySnapshotCipher(
                properties.snapshotRootSecret(), new SecureRandom());
        service = new R12ProfileService(
                profiles, idempotency, snapshots, users, properties,
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void patchUsesAggregateVersionAndCompletesTheFirstResponseSnapshot() {
        UserAuthStore.IdempotencyRow claimRow = new UserAuthStore.IdempotencyRow(
                91, "request-hash", null, null, null);
        when(idempotency.claimIdempotency(anyString(), anyString(), anyString(), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(claimRow, false));
        R12ProfileStore.ProfileAggregate locked = new R12ProfileStore.ProfileAggregate(
                7, 4, 2, "old", null, "old bio");
        when(profiles.lock(7)).thenReturn(Optional.of(locked));
        when(profiles.resolvePublicAvatar(7, 45))
                .thenReturn(Optional.of("https://cdn.orbexa.cc/avatar/45.jpg"));
        when(profiles.update(eq(locked), eq(4L), eq("新昵称"),
                eq("https://cdn.orbexa.cc/avatar/45.jpg"), eq("新简介"),
                eq(NOW), eq(List.of("nickname", "bio", "avatarMediaId")), anyString()))
                .thenReturn(R12ProfileStore.UpdateResult.updated(
                        5, 3, List.of("nickname", "bio", "avatarMediaId")));
        UserResource response = new UserResource(
                "7", "138****0000", "新昵称", "https://cdn.orbexa.cc/avatar/45.jpg",
                "新简介", "ACTIVE", "VERIFIED", "ACTIVE", NOW, 5);
        when(users.self(PRINCIPAL)).thenReturn(response);
        UserResource actual = service.patch(
                PRINCIPAL, new ProfilePatchRequest("新昵称", "45", "新简介", 4L),
                "idem-profile-key-0001");

        assertEquals(response, actual);
        verify(idempotency).completeIdempotencySnapshot(
                eq(91L), eq("user:7:v5"), eq("r12-profile-user-v1"),
                org.mockito.ArgumentMatchers.argThat(
                        envelope -> envelope.startsWith("hhy-user-idem-v1.A256GCM.v1.")));
    }

    @Test
    void staleExpectedVersionReturnsTheFrozenConflictCodeBeforeMutation() {
        when(idempotency.claimIdempotency(anyString(), anyString(), anyString(), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(
                        new UserAuthStore.IdempotencyRow(92, "request-hash", null, null, null), false));
        when(profiles.lock(7)).thenReturn(Optional.of(new R12ProfileStore.ProfileAggregate(
                7, 5, 2, "old", null, null)));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.patch(
                PRINCIPAL, new ProfilePatchRequest("新昵称", null, null, 4L),
                "idem-profile-key-0002"));

        assertEquals("COMMON-409-VERSION_CONFLICT", failure.code());
        assertEquals(409, failure.httpStatus());
        verify(profiles, never()).update(any(), anyLong(), anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    void supplementaryCharactersUseTheDatabaseCodePointLimit() {
        String value = "😀".repeat(255);
        UserAuthStore.IdempotencyRow claimRow = new UserAuthStore.IdempotencyRow(
                94, "request-hash", null, null, null);
        when(idempotency.claimIdempotency(anyString(), anyString(), anyString(), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(claimRow, false));
        R12ProfileStore.ProfileAggregate locked = new R12ProfileStore.ProfileAggregate(
                7, 4, 2, "old", null, "old bio");
        when(profiles.lock(7)).thenReturn(Optional.of(locked));
        when(profiles.update(eq(locked), eq(4L), eq(value), any(), eq(value),
                eq(NOW), eq(List.of("nickname", "bio")), anyString()))
                .thenReturn(R12ProfileStore.UpdateResult.updated(5, 3, List.of("nickname", "bio")));
        UserResource response = new UserResource(
                "7", "138****0000", value, null, value,
                "ACTIVE", "VERIFIED", "ACTIVE", NOW, 5);
        when(users.self(PRINCIPAL)).thenReturn(response);

        assertEquals(response, service.patch(
                PRINCIPAL, new ProfilePatchRequest(value, null, value, 4L),
                "idem-profile-key-0004"));
    }

    @Test
    void twoHundredFiftySixCodePointsAreRejectedBeforeAnyWriteClaim() {
        String value = "😀".repeat(256);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.patch(
                PRINCIPAL, new ProfilePatchRequest(value, null, null, 4L),
                "idem-profile-key-0005"));

        assertEquals("COMMON-400-VALIDATION", failure.code());
        verify(idempotency, never()).claimIdempotency(anyString(), anyString(), anyString(), any());
        verify(profiles, never()).lock(anyLong());
        verify(profiles, never()).update(any(), anyLong(), any(), any(), any(), any(), any(), anyString());
        verify(idempotency, never()).completeIdempotencySnapshot(
                anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void completedIdempotencyClaimReplaysTheOriginalResponseWithoutASecondWrite() throws Exception {
        UserResource original = new UserResource(
                "7", "138****0000", "首次结果", null, null,
                "ACTIVE", "VERIFIED", null, NOW, 8);
        byte[] encoded = objectMapper.writeValueAsBytes(original);
        when(idempotency.claimIdempotency(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String scope = invocation.getArgument(0);
                    String key = invocation.getArgument(1);
                    String hash = invocation.getArgument(2);
                    String envelope = snapshots.encrypt(
                            scope, key, hash, "r12-profile-user-v1", encoded);
                    return new UserAuthStore.IdempotencyClaim(
                            new UserAuthStore.IdempotencyRow(
                                    93, hash, "user:7:v8", "r12-profile-user-v1", envelope), true);
                });

        UserResource replay = service.patch(
                PRINCIPAL, new ProfilePatchRequest("首次结果", null, null, 7L),
                "idem-profile-key-0003");

        assertEquals(original, replay);
        verify(profiles, never()).lock(anyLong());
        verify(profiles, never()).update(any(), anyLong(), any(), any(), any(), any(), any(), anyString());
    }
}
