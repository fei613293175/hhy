package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class R05IdentityAccessConfiguration {
    @Bean
    IdentityProviderClient identityProviderClient(
            R05IdentityProviderSettings settings, SecretResolver secrets,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        return new AliyunMarketIdentityProviderClient(settings, secrets, objectMapper);
    }

    @Bean
    IdentityIdempotencyService identityIdempotencyService(
            UserAuthStore store, UserIdempotencySnapshotCipher snapshots,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper, Clock clock) {
        return new IdentityIdempotencyService(store, snapshots, objectMapper, clock);
    }

    @Bean
    R05IdentityProviderGateway r05IdentityProviderGateway(IdentityProviderClient client) {
        return new R05IdentityProviderGateway(client);
    }

    @Bean
    IdentityService identityService(
            R05IdentityPostgresStore store, R05IdentityRuntimePolicy policy,
            R05IdentityProviderGateway provider, IdentitySensitiveCipher sensitiveData,
            IdentityIdempotencyService idempotency, Clock clock) {
        return new IdentityService(store, policy, provider, sensitiveData, idempotency, clock);
    }
}
