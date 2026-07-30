package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.access.user.UserAuthProperties;
import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityIdempotencyServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T16:00:00Z");
    private static final String SCOPE = "identity:create:11";
    private static final String KEY = "identity-idempotency-01";
    private static final String HASH = "a".repeat(64);
    private static final String TYPE = "r05-identity-session-v1";

    @Test
    void replaysEncryptedFirstResponseAndRejectsChangedRequest() {
        UserAuthStore store = mock(UserAuthStore.class);
        UserAuthStore.IdempotencyRow empty = new UserAuthStore.IdempotencyRow(1, HASH, null, null, null);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(empty, false));
        IdentityIdempotencyService service = service(store);
        IdentitySessionResource expected = new IdentitySessionResource(
                "1", "11", "SESSION_CREATED", "ALIYUN_MARKET_FACE",
                null, null, NOW.plusSeconds(600), 0);

        assertEquals(expected, service.execute(
                SCOPE, KEY, HASH, TYPE, IdentitySessionResource.class, () -> expected));
        ArgumentCaptor<String> cipher = ArgumentCaptor.forClass(String.class);
        verify(store).completeIdempotencySnapshot(eq(1L), eq(TYPE + ":ok"), eq(TYPE), cipher.capture());

        UserAuthStore.IdempotencyRow complete = new UserAuthStore.IdempotencyRow(
                1, HASH, TYPE + ":ok", TYPE, cipher.getValue());
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(complete, true));
        assertEquals(expected, service.execute(SCOPE, KEY, HASH, TYPE, IdentitySessionResource.class,
                () -> { throw new AssertionError("replay executed action"); }));

        String changedHash = "c".repeat(64);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(changedHash), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(complete, true));
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.execute(
                        SCOPE, KEY, changedHash, TYPE, IdentitySessionResource.class, () -> expected));
        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void abandonsClaimWhenIdentityRuleFails() {
        UserAuthStore store = mock(UserAuthStore.class);
        UserAuthStore.IdempotencyRow empty = new UserAuthStore.IdempotencyRow(9, HASH, null, null, null);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(empty, false));
        IdentityIdempotencyService service = service(store);

        assertThrows(BusinessException.class, () -> service.execute(
                SCOPE, KEY, HASH, TYPE, IdentitySessionResource.class,
                () -> { throw new BusinessException(
                        "COMMON-422-BUSINESS_RULE", "认证条件不满足", 422, false); }));
        verify(store).abandonIdempotency(9L);
    }

    @Test
    void duplicateRequestWhileFirstResponseIsPendingReturnsRetryableConflict() {
        UserAuthStore store = mock(UserAuthStore.class);
        UserAuthStore.IdempotencyRow pending = new UserAuthStore.IdempotencyRow(
                12, HASH, null, null, null);
        when(store.claimIdempotency(eq(SCOPE), eq(KEY), eq(HASH), any()))
                .thenReturn(new UserAuthStore.IdempotencyClaim(pending, true));
        AtomicInteger actionCalls = new AtomicInteger();

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service(store).execute(
                        SCOPE, KEY, HASH, TYPE, IdentitySessionResource.class,
                        () -> {
                            actionCalls.incrementAndGet();
                            return null;
                        }));

        assertEquals("COMMON-409-VERSION_CONFLICT", conflict.code());
        assertEquals(409, conflict.httpStatus());
        assertEquals(true, conflict.retryable());
        assertEquals(0, actionCalls.get());
    }

    private static IdentityIdempotencyService service(UserAuthStore store) {
        UserAuthProperties properties = new UserAuthProperties(
                "jwt-secret-012345678901234567890123456789",
                "hmac-secret-0123456789012345678901234567",
                "snapshot-secret-0123456789012345678901234",
                "test", Duration.ofMinutes(15), Duration.ofDays(1), Duration.ofDays(1));
        return new IdentityIdempotencyService(store, new UserIdempotencySnapshotCipher(properties),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
