package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackConsumeRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void concurrentDuplicateCallbacksHaveExactlyOneBusinessResult() throws Exception {
        AtomicBoolean available = new AtomicBoolean(true);
        var service = new IdentityCallbackService((state, now) ->
                available.compareAndSet(true, false) ? "VERIFIED" : null,
                Clock.fixed(NOW, ZoneOffset.UTC));
        int attempts = 16;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<Future<Boolean>>();
        try {
            for (int index = 0; index < attempts; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return "VERIFIED".equals(
                                service.consume(new CallbackConsumeRequest(STATE)).status());
                    } catch (BusinessException replay) {
                        assertEquals(422, replay.httpStatus());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) if (future.get()) successes++;
            assertEquals(1, successes);
            assertTrue(futures.stream().allMatch(Future::isDone));
        } finally {
            executor.shutdownNow();
        }
    }
}
