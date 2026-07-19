package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.storage.MediaContracts.MediaResource;
import cc.orbexa.hhy.access.user.UserAuthProperties;
import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediaIdempotencyServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final String SCOPE = "media:create:11";
    private static final String KEY = "media-idempotency-01";
    private static final String HASH = "a".repeat(64);
    private static final String TYPE = "r04-media-resource-v1";

    @Test
    void encryptsFirstResponseReplaysItAndRejectsChangedRequest() {
        UserAuthStore store = mock(UserAuthStore.class);
        UserAuthStore.IdempotencyRow empty = new UserAuthStore.IdempotencyRow(1, HASH, null, null, null);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(empty, false));
        MediaIdempotencyService service = service(store);
        MediaResource expected = new MediaResource("1", "public_media", "image/png", 32L,
                "b".repeat(64), null, null, "CREATED", NOW.plusSeconds(300));

        assertEquals(expected, service.execute(
                SCOPE, KEY, HASH, TYPE, MediaResource.class, () -> expected));
        ArgumentCaptor<String> cipher = ArgumentCaptor.forClass(String.class);
        verify(store).completeIdempotencySnapshot(eq(1L), eq(TYPE + ":ok"), eq(TYPE), cipher.capture());

        UserAuthStore.IdempotencyRow complete = new UserAuthStore.IdempotencyRow(
                1, HASH, TYPE + ":ok", TYPE, cipher.getValue());
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(complete, true));
        assertEquals(expected, service.execute(SCOPE, KEY, HASH, TYPE, MediaResource.class,
                () -> { throw new AssertionError("replay executed action"); }));

        String changedHash = "c".repeat(64);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(changedHash), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(complete, true));
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.execute(SCOPE, KEY, changedHash, TYPE, MediaResource.class, () -> expected));
        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void abandonsClaimWhenBusinessActionFails() {
        UserAuthStore store = mock(UserAuthStore.class);
        UserAuthStore.IdempotencyRow empty = new UserAuthStore.IdempotencyRow(9, HASH, null, null, null);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(empty, false));
        MediaIdempotencyService service = service(store);

        assertThrows(BusinessException.class, () -> service.execute(
                SCOPE, KEY, HASH, TYPE, MediaResource.class,
                () -> { throw new BusinessException("COMMON-422-BUSINESS_RULE", "失败", 422, false); }));
        verify(store).abandonIdempotency(9L);
    }

    private static MediaIdempotencyService service(UserAuthStore store) {
        UserAuthProperties properties = new UserAuthProperties(
                "jwt-secret-012345678901234567890123456789",
                "hmac-secret-0123456789012345678901234567",
                "snapshot-secret-0123456789012345678901234",
                "test", Duration.ofMinutes(15), Duration.ofDays(1), Duration.ofDays(1));
        return new MediaIdempotencyService(store, new UserIdempotencySnapshotCipher(properties),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
