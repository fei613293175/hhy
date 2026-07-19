package cc.orbexa.hhy.access.identity;

import java.net.URI;

/** Provider-specific port. Implementations must not log names, ID numbers, tokens, or signed URLs. */
public interface IdentityProviderClient {
    ProviderLivenessResult createLiveness(ProviderLivenessCommand command);

    record ProviderLivenessCommand(
            String provider, long sessionId, long userId, URI returnUrl, String idempotencyKey) { }

    record ProviderLivenessResult(String providerOrderNo, URI livenessUrl) { }
}
