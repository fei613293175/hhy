package cc.orbexa.hhy.access.admin;

import java.util.Arrays;
import org.springframework.core.env.Environment;

/** Production refuses to start without the read-only provider-secret mount. */
public final class ProviderSecretStartupGuard {
    public ProviderSecretStartupGuard(
            ProviderSecretMaterialProperties properties, Environment environment) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"));
        verify(properties, production);
    }

    static void verify(ProviderSecretMaterialProperties properties, boolean production) {
        if (properties == null || !properties.configured()) {
            if (production) {
                throw new IllegalStateException(
                        "Provider secret material directory is required in production");
            }
            return;
        }
        MountedProviderSecretResolver.requireDirectory(properties.directory());
    }
}
