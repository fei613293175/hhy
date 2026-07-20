package cc.orbexa.hhy.access.identity;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Fails closed if the test provider is ever enabled outside an isolated staging profile. */
public final class IdentitySandboxStartupGuard {
    public IdentitySandboxStartupGuard(
            IdentitySandboxProperties properties, Environment environment) {
        verify(properties, environment);
    }

    static void verify(IdentitySandboxProperties properties, Environment environment) {
        if (properties == null || !properties.enabled()) return;
        properties.requireValid();
        boolean staging = environment.acceptsProfiles(Profiles.of("staging"));
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (!staging || production) {
            throw new IllegalStateException(
                    "Identity sandbox can only be enabled in the staging profile");
        }
    }
}
