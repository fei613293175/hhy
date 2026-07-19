package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.identity.IdentityContracts.CreateLivenessTokenRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.CreateSessionRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.RetrySessionRequest;
import cc.orbexa.hhy.access.identity.IdentityService.LivenessTicket;
import cc.orbexa.hhy.access.identity.IdentityService.IdentityConsent;
import cc.orbexa.hhy.access.identity.IdentityService.PolicySnapshot;
import cc.orbexa.hhy.access.identity.IdentityService.ProtectedIdentity;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.identity.IdentityService.SessionDraft;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T16:00:00Z");
    private static final String KEY = "identity-test-key-0001";
    private final FakeStore store = new FakeStore();
    private final RecordingSensitiveData sensitive = new RecordingSensitiveData();
    private int providerCalls;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        providerCalls = 0;
        service = new IdentityService(
                store,
                () -> new PolicySnapshot("ALIYUN_MARKET_FACE", 3, Duration.ofMinutes(10)),
                (session, returnUrl, key) -> {
                    providerCalls++;
                    return new LivenessTicket("provider-order-1",
                            URI.create("https://identity.example.test/liveness/" + session.id()));
                },
                sensitive,
                new PassThroughIdempotency(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsEncryptedSessionWithoutReturningSensitiveInput() {
        var result = service.create(principal(11),
                new CreateSessionRequest(" 张三 ", " 110101199001010011 ", " consent-v1 "), KEY);

        assertEquals("1", result.id());
        assertEquals("SESSION_CREATED", result.status());
        assertNull(result.livenessUrl());
        assertEquals("张三", sensitive.realName);
        assertEquals("110101199001010011", sensitive.idNumber);
        assertEquals("cipher-name", store.lastDraft.identity().nameCipher());
        assertEquals("consent-v1", store.lastDraft.consentVersion());
        assertFalse(result.toString().contains("110101199001010011"));
    }

    @Test
    void exposesCurrentConsentAndRejectsStaleVersionBeforeSensitiveProcessing() {
        var consent = service.consent(principal(11));
        assertEquals("consent-v1", consent.consentVersion());
        assertEquals("实名认证授权说明", consent.title());
        assertEquals("当前实名授权正文", consent.content());

        BusinessException stale = assertThrows(BusinessException.class,
                () -> service.create(principal(11),
                        new CreateSessionRequest("张三", "110101199001010011", "old-consent"), KEY));
        assertEquals(422, stale.httpStatus());
        assertEquals(0, sensitive.calls);
    }

    @Test
    void rejectsExistingActiveSessionAndDailyAttemptLimitBeforeSensitiveProcessing() {
        service.create(principal(11), request(), KEY);
        BusinessException active = assertThrows(BusinessException.class,
                () -> service.create(principal(11), request(), "identity-test-key-0002"));
        assertEquals(409, active.httpStatus());

        store.sessions.clear();
        store.dailyAttempts = 3;
        sensitive.calls = 0;
        BusinessException limited = assertThrows(BusinessException.class,
                () -> service.create(principal(11), request(), "identity-test-key-0003"));
        assertEquals(429, limited.httpStatus());
        assertEquals(86400L, limited.retryAfterSeconds());
        assertEquals(0, sensitive.calls);
    }

    @Test
    void issuesHttpsLivenessTicketOnlyForOwnedActiveSession() {
        service.create(principal(11), request(), KEY);

        var result = service.livenessToken(principal(11), "1",
                new CreateLivenessTokenRequest("https://h5.orbexa.cc/identity/callback"),
                "identity-live-key-0001");
        assertEquals("LIVENESS_PENDING", result.status());
        assertEquals("https://identity.example.test/liveness/1", result.livenessUrl());
        assertEquals(1, providerCalls);

        BusinessException hidden = assertThrows(BusinessException.class,
                () -> service.livenessToken(principal(12), "1",
                        new CreateLivenessTokenRequest("https://h5.orbexa.cc/identity/callback"),
                        "identity-live-key-0002"));
        assertEquals(404, hidden.httpStatus());

        BusinessException unsafe = assertThrows(BusinessException.class,
                () -> service.livenessToken(principal(11), "1",
                        new CreateLivenessTokenRequest("http://example.test/callback"),
                        "identity-live-key-0003"));
        assertEquals(400, unsafe.httpStatus());
    }

    @Test
    void expiresSessionDuringStatusRead() {
        service.create(principal(11), request(), KEY);
        store.sessions.compute(1L, (id, session) -> new Session(
                session.id(), session.userId(), session.state(), session.status(), session.provider(),
                session.livenessUrl(), session.failureCode(), NOW.minusSeconds(1),
                session.version(), session.attemptNo()));

        var result = service.get(principal(11), "1");
        assertEquals("EXPIRED", result.status());
        assertEquals(1, result.version());
    }

    @Test
    void retriesOnlyRejectedOrExpiredSessionWithMatchingVersion() {
        service.create(principal(11), request(), KEY);
        BusinessException active = assertThrows(BusinessException.class,
                () -> service.retry(principal(11), "1", new RetrySessionRequest("重试", 0L, Map.of()),
                        "identity-retry-key-0001"));
        assertEquals(409, active.httpStatus());

        store.sessions.compute(1L, (id, session) -> new Session(
                session.id(), session.userId(), session.state(), "REJECTED", session.provider(), null,
                "FACE_MISMATCH", session.expiresAt(), 2, session.attemptNo()));
        BusinessException stale = assertThrows(BusinessException.class,
                () -> service.retry(principal(11), "1", new RetrySessionRequest("重试", 1L, Map.of()),
                        "identity-retry-key-0002"));
        assertEquals(409, stale.httpStatus());

        var retried = service.retry(principal(11), "1",
                new RetrySessionRequest("重试", 2L, Map.of()), "identity-retry-key-0003");
        assertEquals("2", retried.id());
        assertEquals("SESSION_CREATED", retried.status());
        assertEquals(2, store.sessions.get(2L).attemptNo());
    }

    @Test
    void sensitiveCipherUsesAeadBindingAndKeyedStableHash() {
        IdentitySensitiveCipher cipher = new IdentitySensitiveCipher("s".repeat(48), new SecureRandom());
        ProtectedIdentity first = cipher.protect(11, "张三", "110101199001010011");
        ProtectedIdentity second = cipher.protect(11, "张三", "110101199001010011");

        assertNotEquals(first.nameCipher(), second.nameCipher());
        assertFalse(first.idNumberCipher().contains("110101199001010011"));
        assertEquals(first.idHash(), second.idHash());
        assertEquals(64, first.idHash().length());
        assertEquals("张三", cipher.decrypt(11, "name", first.nameCipher()));
        assertEquals("110101199001010011",
                cipher.decrypt(11, "id-number", first.idNumberCipher()));
        assertThrows(IdentitySensitiveCipher.IdentityPayloadIntegrityException.class,
                () -> cipher.decrypt(12, "name", first.nameCipher()));
    }

    private static CreateSessionRequest request() {
        return new CreateSessionRequest("张三", "110101199001010011", "consent-v1");
    }

    private static UserPrincipal principal(long id) {
        return new UserPrincipal(id, 1, 0, "jti", "ACTIVE");
    }

    private static final class PassThroughIdempotency implements IdentityService.Idempotency {
        @Override
        public <T> T execute(
                String scope, String key, String requestHash, String responseType,
                Class<T> type, java.util.function.Supplier<T> action) {
            return action.get();
        }
    }

    private static final class RecordingSensitiveData implements IdentityService.SensitiveData {
        int calls;
        String realName;
        String idNumber;

        @Override
        public ProtectedIdentity protect(long userId, String realName, String idNumber) {
            calls++;
            this.realName = realName;
            this.idNumber = idNumber;
            return new ProtectedIdentity("cipher-name", "cipher-id", "a".repeat(64));
        }
    }

    private static final class FakeStore implements IdentityService.Store {
        final Map<Long, Session> sessions = new HashMap<>();
        SessionDraft lastDraft;
        long dailyAttempts;

        @Override
        public Optional<IdentityConsent> currentConsent() {
            return Optional.of(new IdentityConsent("consent-v1", "当前实名授权正文"));
        }

        @Override
        public Optional<String> profileStatus(long userId) {
            return Optional.empty();
        }

        @Override
        public Optional<Session> active(long userId) {
            return sessions.values().stream()
                    .filter(session -> session.userId() == userId)
                    .filter(session -> session.status().endsWith("PENDING")
                            || "SESSION_CREATED".equals(session.status())
                            || "PROVIDER_PROCESSING".equals(session.status())
                            || "MANUAL_REVIEW".equals(session.status()))
                    .findFirst();
        }

        @Override
        public long countCreatedSince(long userId, Instant since) {
            return dailyAttempts;
        }

        @Override
        public Session create(SessionDraft draft) {
            lastDraft = draft;
            long id = sessions.size() + 1L;
            Session session = new Session(id, draft.userId(), "state-for-session-" + id,
                    "SESSION_CREATED", draft.provider(),
                    null, null, draft.expiresAt(), 0, 1);
            sessions.put(id, session);
            dailyAttempts++;
            return session;
        }

        @Override
        public Optional<Session> find(long id, long userId) {
            return Optional.ofNullable(sessions.get(id)).filter(value -> value.userId() == userId);
        }

        @Override
        public Session attachLiveness(
                Session current, LivenessTicket ticket, String idempotencyKey, Instant now) {
            Session changed = new Session(current.id(), current.userId(), current.state(), "LIVENESS_PENDING",
                    current.provider(), ticket.url(), null, current.expiresAt(),
                    current.version() + 1, current.attemptNo());
            sessions.put(changed.id(), changed);
            return changed;
        }

        @Override
        public Session expire(long id, long userId, long expectedVersion, Instant now) {
            Session current = find(id, userId).orElseThrow();
            Session changed = new Session(current.id(), current.userId(), current.state(), "EXPIRED",
                    current.provider(), current.livenessUrl(), "SESSION_EXPIRED", current.expiresAt(),
                    current.version() + 1, current.attemptNo());
            sessions.put(id, changed);
            return changed;
        }

        @Override
        public Session retry(
                Session previous, String provider, String key, Instant expiresAt, Instant now) {
            long id = sessions.size() + 1L;
            Session retried = new Session(id, previous.userId(), "state-for-session-" + id,
                    "SESSION_CREATED", provider,
                    null, null, expiresAt, 0, previous.attemptNo() + 1);
            sessions.put(id, retried);
            return retried;
        }
    }
}
