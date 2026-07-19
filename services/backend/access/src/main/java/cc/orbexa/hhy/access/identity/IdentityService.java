package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityContracts.CreateLivenessTokenRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.CreateSessionRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentityConsentResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.RetrySessionRequest;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Application service for the frozen R05 authenticated identity operations. */
public class IdentityService {
    private static final String RESPONSE_TYPE = "r05-identity-session-v1";
    private static final List<String> ACTIVE = List.of(
            "SESSION_CREATED", "LIVENESS_PENDING", "PROVIDER_PROCESSING", "MANUAL_REVIEW");
    private static final List<String> TERMINAL = List.of("VERIFIED", "REJECTED", "EXPIRED");
    private final Store store;
    private final Policy policy;
    private final Provider provider;
    private final SensitiveData sensitiveData;
    private final Idempotency idempotency;
    private final Clock clock;

    public IdentityService(
            Store store, Policy policy, Provider provider, SensitiveData sensitiveData,
            Idempotency idempotency, Clock clock) {
        this.store = store;
        this.policy = policy;
        this.provider = provider;
        this.sensitiveData = sensitiveData;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdentitySessionResource create(
            UserPrincipal principal, CreateSessionRequest request, String key) {
        long userId = requirePrincipal(principal);
        requireKey(key);
        String realName = request.realName().strip();
        String idNumber = request.idNumber().strip();
        String consent = request.consentVersion().strip();
        String fingerprint = digest(realName + "\u0000" + idNumber + "\u0000" + consent);
        return idempotency.execute(scope("create", userId), key, fingerprint,
                RESPONSE_TYPE, IdentitySessionResource.class,
                () -> createOnce(userId, realName, idNumber, consent, key));
    }

    public IdentityConsentResource consent(UserPrincipal principal) {
        requirePrincipal(principal);
        IdentityConsent current = requireConsent();
        return new IdentityConsentResource(
                current.versionId(), "实名认证授权说明", current.content());
    }

    public IdentitySessionResource livenessToken(
            UserPrincipal principal, String id, CreateLivenessTokenRequest request, String key) {
        long userId = requirePrincipal(principal);
        requireKey(key);
        long sessionId = resourceId(id);
        URI returnUrl = httpsUri(request.returnUrl());
        String fingerprint = digest(id + "\u0000" + returnUrl);
        return idempotency.execute(scope("liveness", userId), key, fingerprint,
                RESPONSE_TYPE, IdentitySessionResource.class,
                () -> livenessOnce(userId, sessionId, returnUrl, key));
    }

    public IdentitySessionResource get(UserPrincipal principal, String id) {
        long userId = requirePrincipal(principal);
        Session session = store.find(resourceId(id), userId).orElseThrow(IdentityService::notFound);
        Instant now = Instant.now(clock);
        if (ACTIVE.contains(session.status()) && !session.expiresAt().isAfter(now)) {
            session = store.expire(session.id(), userId, session.version(), now);
        }
        return resource(session);
    }

    public IdentitySessionResource retry(
            UserPrincipal principal, String id, RetrySessionRequest request, String key) {
        long userId = requirePrincipal(principal);
        requireKey(key);
        long sessionId = resourceId(id);
        String fingerprint = digest(id + "\u0000" + request.reason() + "\u0000"
                + request.expectedVersion() + "\u0000" + request.payload());
        return idempotency.execute(scope("retry", userId), key, fingerprint,
                RESPONSE_TYPE, IdentitySessionResource.class,
                () -> retryOnce(userId, sessionId, request, key));
    }

    private IdentitySessionResource createOnce(
            long userId, String realName, String idNumber, String consent, String key) {
        IdentityConsent currentConsent = requireConsent();
        if (!currentConsent.versionId().equals(consent)) {
            throw business("实名认证授权说明已更新，请重新阅读并同意");
        }
        PolicySnapshot current = requirePolicy();
        if ("VERIFIED".equals(store.profileStatus(userId).orElse(null))) {
            throw business("当前账号已完成实名认证");
        }
        if (store.active(userId).isPresent()) {
            throw conflict("已有进行中的实名认证，请先完成当前流程");
        }
        Instant now = Instant.now(clock);
        long attempts = store.countCreatedSince(userId, now.truncatedTo(ChronoUnit.DAYS));
        if (attempts >= current.maxDailyAttempts()) {
            throw new BusinessException("COMMON-429-RATE_LIMITED", "今日认证次数已达上限", 429, true, 86400L);
        }
        ProtectedIdentity protectedIdentity = sensitiveData.protect(userId, realName, idNumber);
        Session created = store.create(new SessionDraft(
                userId, protectedIdentity, consent, current.provider(), key,
                now.plus(current.sessionTtl()), now));
        return resource(created);
    }

    private IdentitySessionResource livenessOnce(
            long userId, long sessionId, URI returnUrl, String key) {
        Session session = store.find(sessionId, userId).orElseThrow(IdentityService::notFound);
        if (!List.of("SESSION_CREATED", "LIVENESS_PENDING").contains(session.status())) {
            throw conflict("当前认证状态不能开始活体检测");
        }
        if (!session.expiresAt().isAfter(Instant.now(clock))) {
            throw business("认证会话已过期，请重新开始");
        }
        LivenessTicket ticket = provider.issue(session, returnUrl, key);
        if (ticket == null || ticket.url() == null || !"https".equalsIgnoreCase(ticket.url().getScheme())) {
            throw new BusinessException("COMMON-500-INTERNAL", "活体检测服务暂时不可用", 500, true);
        }
        return resource(store.attachLiveness(session, ticket, key, Instant.now(clock)));
    }

    private IdentitySessionResource retryOnce(
            long userId, long sessionId, RetrySessionRequest request, String key) {
        Session previous = store.find(sessionId, userId).orElseThrow(IdentityService::notFound);
        if (!TERMINAL.contains(previous.status()) || "VERIFIED".equals(previous.status())) {
            throw conflict("当前认证状态不能重新认证");
        }
        if (request.expectedVersion() != null && request.expectedVersion() != previous.version()) {
            throw conflict("认证状态已变化，请刷新后重试");
        }
        PolicySnapshot current = requirePolicy();
        Instant now = Instant.now(clock);
        return resource(store.retry(previous, current.provider(), key,
                now.plus(current.sessionTtl()), now));
    }

    private PolicySnapshot requirePolicy() {
        PolicySnapshot current = policy.current();
        if (current == null || current.provider() == null || current.provider().isBlank()
                || current.maxDailyAttempts() < 1 || current.sessionTtl() == null
                || current.sessionTtl().isZero() || current.sessionTtl().isNegative()) {
            throw new BusinessException("COMMON-500-INTERNAL", "实名认证服务暂时不可用", 500, true);
        }
        return current;
    }

    private IdentityConsent requireConsent() {
        IdentityConsent consent = store.currentConsent().orElseThrow(() ->
                new BusinessException(
                        "COMMON-500-INTERNAL",
                        "实名认证授权说明暂时无法加载，请稍后重试", 500, true));
        if (consent.versionId() == null || consent.versionId().isBlank()
                || consent.content() == null || consent.content().isBlank()) {
            throw new BusinessException(
                    "COMMON-500-INTERNAL",
                    "实名认证授权说明暂时无法加载，请稍后重试", 500, true);
        }
        return consent;
    }

    private static IdentitySessionResource resource(Session session) {
        return new IdentitySessionResource(
                Long.toString(session.id()), Long.toString(session.userId()), session.status(),
                session.provider(), session.livenessUrl() == null ? null : session.livenessUrl().toString(),
                session.failureCode(), session.expiresAt(), session.version());
    }

    private static long requirePrincipal(UserPrincipal principal) {
        if (principal == null || principal.userId() < 1) throw notFound();
        if (!principal.active()) {
            throw new BusinessException("COMMON-403-FORBIDDEN", "当前账号不可执行实名认证", 403, false);
        }
        return principal.userId();
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 16 || key.length() > 128) {
            throw new BusinessException("COMMON-400-VALIDATION", "幂等键不符合要求", 400, false);
        }
    }

    private static long resourceId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (RuntimeException invalid) {
            throw notFound();
        }
    }

    private static URI httpsUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException invalid) {
            throw new BusinessException("COMMON-400-VALIDATION", "回跳地址不符合要求", 400, false);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Identity request hash unavailable", failure);
        }
    }

    private static String scope(String operation, long userId) {
        return "identity:" + operation + ":" + userId;
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可见", 404, false);
    }

    public interface Store {
        Optional<IdentityConsent> currentConsent();
        Optional<String> profileStatus(long userId);
        Optional<Session> active(long userId);
        long countCreatedSince(long userId, Instant since);
        Session create(SessionDraft draft);
        Optional<Session> find(long id, long userId);
        Session attachLiveness(
                Session current, LivenessTicket ticket, String idempotencyKey, Instant now);
        Session expire(long id, long userId, long expectedVersion, Instant now);
        Session retry(Session previous, String provider, String idempotencyKey, Instant expiresAt, Instant now);
    }

    public interface Policy {
        PolicySnapshot current();
    }

    public interface Provider {
        LivenessTicket issue(Session session, URI returnUrl, String idempotencyKey);
    }

    public interface SensitiveData {
        ProtectedIdentity protect(long userId, String realName, String idNumber);
    }

    public interface Idempotency {
        <T> T execute(
                String scope, String key, String requestHash, String responseType,
                Class<T> type, Supplier<T> action);
    }

    public record ProtectedIdentity(String nameCipher, String idNumberCipher, String idHash) { }
    public record IdentityConsent(String versionId, String content) { }
    public record PolicySnapshot(String provider, int maxDailyAttempts, Duration sessionTtl) { }
    public record LivenessTicket(String providerOrderNo, URI url) { }
    public record SessionDraft(
            long userId, ProtectedIdentity identity, String consentVersion, String provider,
            String idempotencyKey, Instant expiresAt, Instant createdAt) { }
    public record Session(
            long id, long userId, String state, String status, String provider, URI livenessUrl,
            String failureCode, Instant expiresAt, long version, int attemptNo) { }
}
