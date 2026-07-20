package cc.orbexa.hhy.access.identity;

import java.util.Objects;

/** Routes the explicit staging provider without weakening the real provider path. */
public final class R05IdentityProviderRouter implements IdentityProviderClient {
    private final IdentityProviderClient production;
    private final IdentityProviderClient sandbox;

    public R05IdentityProviderRouter(
            IdentityProviderClient production, IdentityProviderClient sandbox) {
        this.production = Objects.requireNonNull(production, "production");
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public ProviderLivenessResult createLiveness(ProviderLivenessCommand command) {
        return delegate(command.provider()).createLiveness(command);
    }

    @Override
    public ProviderLivenessOutcome queryLiveness(ProviderLivenessQuery query) {
        return delegate(query.provider()).queryLiveness(query);
    }

    @Override
    public ProviderFaceComparison compareFace(ProviderFaceComparisonCommand command) {
        return delegate(command.provider()).compareFace(command);
    }

    private IdentityProviderClient delegate(String provider) {
        return IdentitySandboxProperties.PROVIDER.equals(provider) ? sandbox : production;
    }
}
