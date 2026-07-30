package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProcessingContext;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProviderCompletion;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** One-time Staging completion boundary; it never accepts identity data or exposes request IDs. */
public class IdentitySandboxService {
    private static final Pattern STATE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private final Store store;
    private final IdentitySandboxProperties properties;
    private final Clock clock;

    public IdentitySandboxService(
            Store store, IdentitySandboxProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    public PageContext page(String state, String returnUrl) {
        properties.requireValid();
        String normalizedState = normalizeState(state);
        URI normalizedReturn = allowedReturn(returnUrl);
        if (store.sandboxContextByState(normalizedState).isEmpty()) throw expired();
        return new PageContext(normalizedState, normalizedReturn);
    }

    public Completion complete(String state, String returnUrl, String decision) {
        PageContext page = page(state, returnUrl);
        ProcessingContext context = store.sandboxContextByState(page.state()).orElseThrow(
                IdentitySandboxService::expired);
        boolean passed = "PASS".equals(normalizeDecision(decision));
        String status = passed ? "VERIFIED" : "REJECTED";
        byte[] evidence = (passed ? "sandbox-pass" : "sandbox-reject")
                .getBytes(StandardCharsets.UTF_8);
        store.complete(context, new ProviderCompletion(
                status, passed ? null : "LIVENESS_REJECTED", null,
                passed ? BigDecimal.valueOf(100) : BigDecimal.ZERO,
                evidence, new byte[0], null), clock.instant());
        return new Completion(status, page.returnUrl());
    }

    private URI allowedReturn(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !properties.allowedReturnHost().equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException invalid) {
            throw expired();
        }
    }

    private static String normalizeState(String state) {
        String normalized = state == null ? "" : state.strip();
        if (!STATE.matcher(normalized).matches()) throw expired();
        return normalized;
    }

    private static String normalizeDecision(String decision) {
        String normalized = decision == null ? "" : decision.strip().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PASS") && !normalized.equals("REJECT")) throw expired();
        return normalized;
    }

    private static BusinessException expired() {
        return new BusinessException(
                "COMMON-422-BUSINESS_RULE", "认证页面已失效，请返回合伙云重新开始", 422, false);
    }

    public record PageContext(String state, URI returnUrl) { }
    public record Completion(String status, URI returnUrl) { }

    public interface Store {
        Optional<ProcessingContext> sandboxContextByState(String state);
        IdentityService.Session complete(
                ProcessingContext context, ProviderCompletion completion, java.time.Instant now);
    }
}
