package cc.orbexa.hhy.access.admin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public final class AdminSecurityStartupGuard implements InitializingBean {
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "change-me", "changeme", "test-only", "local-dev", "placeholder",
            "default-secret", "example-secret", "password");

    private final AdminSecurityProperties properties;
    private final Environment environment;

    public AdminSecurityStartupGuard(AdminSecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of("test"))) return;
        List<String> values = List.of(
                properties.jwtSecret(),
                properties.mfaRootSecret(),
                properties.idempotencyHmacSecret());
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalStateException("Administrator security keys must be independent");
        }
        for (String value : values) validate(value);
    }

    private static void validate(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_MARKERS.stream().anyMatch(lower::contains)) {
            throw new IllegalStateException("Administrator security key contains a forbidden weak marker");
        }
        Set<Integer> distinct = new HashSet<>();
        value.codePoints().forEach(distinct::add);
        if (distinct.size() < 12) {
            throw new IllegalStateException("Administrator security key has insufficient character diversity");
        }
    }
}
