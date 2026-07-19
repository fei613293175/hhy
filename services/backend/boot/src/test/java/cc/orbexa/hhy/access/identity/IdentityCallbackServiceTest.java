package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackConsumeRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdentityCallbackServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T19:20:00Z");
    private static final String STATE = "ff738a6c-71a1-4647-ab50-5d6f3cb5f544";

    @Test
    void consumesValidStateOnceAndReturnsOnlyBusinessStatus() {
        AtomicInteger calls = new AtomicInteger();
        var service = new IdentityCallbackService((state, now) -> {
            assertEquals(STATE, state);
            assertEquals(NOW, now);
            return calls.incrementAndGet() == 1 ? "LIVENESS_PENDING" : null;
        }, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.consume(new CallbackConsumeRequest(STATE));
        assertEquals("LIVENESS_PENDING", result.status());

        BusinessException replay = assertThrows(BusinessException.class,
                () -> service.consume(new CallbackConsumeRequest(STATE)));
        assertEquals(422, replay.httpStatus());
        assertEquals("认证页面已失效，请返回合伙云重新开始", replay.getMessage());
    }

    @Test
    void rejectsMalformedStateWithoutCallingStorage() {
        AtomicInteger calls = new AtomicInteger();
        var service = new IdentityCallbackService((state, now) -> {
            calls.incrementAndGet();
            return "VERIFIED";
        }, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(BusinessException.class,
                () -> service.consume(new CallbackConsumeRequest("not-a-state")));
        assertEquals(0, calls.get());
    }
}
