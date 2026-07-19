package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessCommand;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessResult;
import cc.orbexa.hhy.access.identity.IdentityService.LivenessTicket;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import java.net.URI;

/** Converts the domain command into the activated provider port without leaking provider DTOs. */
public final class R05IdentityProviderGateway implements IdentityService.Provider {
    private final IdentityProviderClient client;

    public R05IdentityProviderGateway(IdentityProviderClient client) {
        this.client = client;
    }

    @Override
    public LivenessTicket issue(Session session, URI returnUrl, String idempotencyKey) {
        ProviderLivenessResult result = client.createLiveness(new ProviderLivenessCommand(
                session.provider(), session.id(), session.userId(), session.state(),
                returnUrl, idempotencyKey));
        return result == null ? null
                : new LivenessTicket(result.providerOrderNo(), result.livenessUrl());
    }
}
