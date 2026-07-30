package cc.orbexa.hhy.access.identity;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Staging-only provider adapter. Completion is written atomically by the public sandbox handoff. */
public final class R05IdentitySandboxClient implements IdentityProviderClient {
    private final IdentitySandboxProperties properties;

    public R05IdentitySandboxClient(IdentitySandboxProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderLivenessResult createLiveness(ProviderLivenessCommand command) {
        requireSandbox(command.provider());
        properties.requireValid();
        requireAllowedReturn(command.returnUrl());
        String query = "state=" + encode(command.state())
                + "&returnUrl=" + encode(command.returnUrl().toString());
        URI page = URI.create(properties.publicBaseUrl().toString().replaceAll("/+$", "")
                + "/liveness?" + query);
        return new ProviderLivenessResult("stg-" + UUID.randomUUID(), page);
    }

    @Override
    public ProviderLivenessOutcome queryLiveness(ProviderLivenessQuery query) {
        requireSandbox(query.provider());
        return new ProviderLivenessOutcome(
                LivenessDecision.PENDING, query.providerOrderNo(), null,
                "sandbox-pending".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ProviderFaceComparison compareFace(ProviderFaceComparisonCommand command) {
        requireSandbox(command.provider());
        return new ProviderFaceComparison(
                FaceDecision.REJECTED, 0, BigDecimal.ZERO, null,
                "sandbox-direct-completion-only".getBytes(StandardCharsets.UTF_8));
    }

    private void requireAllowedReturn(URI returnUrl) {
        if (returnUrl == null || !"https".equalsIgnoreCase(returnUrl.getScheme())
                || !properties.allowedReturnHost().equalsIgnoreCase(returnUrl.getHost())
                || returnUrl.getUserInfo() != null || returnUrl.getFragment() != null) {
            throw new IllegalArgumentException("Identity sandbox return URL is not allowed");
        }
    }

    private static void requireSandbox(String provider) {
        if (!IdentitySandboxProperties.PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Identity sandbox provider mismatch");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
