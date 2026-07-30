package cc.orbexa.hhy.access.identity;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit, default-off configuration for the non-production identity test provider. */
@ConfigurationProperties("hhy.identity-sandbox")
public record IdentitySandboxProperties(
        boolean enabled,
        URI publicBaseUrl,
        String allowedReturnHost,
        Integer maxDailyAttempts,
        Duration sessionTtl) {

    public static final String PROVIDER = "STAGING_SANDBOX";

    public IdentitySandboxProperties {
        maxDailyAttempts = maxDailyAttempts == null ? 5 : maxDailyAttempts;
        sessionTtl = sessionTtl == null ? Duration.ofMinutes(10) : sessionTtl;
    }

    void requireValid() {
        if (!enabled) return;
        if (publicBaseUrl == null || !"https".equalsIgnoreCase(publicBaseUrl.getScheme())
                || publicBaseUrl.getHost() == null || publicBaseUrl.getUserInfo() != null
                || publicBaseUrl.getFragment() != null) {
            throw new IllegalStateException("Identity sandbox public base URL must be HTTPS");
        }
        if (allowedReturnHost == null || allowedReturnHost.isBlank()
                || maxDailyAttempts < 1 || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalStateException("Identity sandbox configuration is incomplete");
        }
    }
}
