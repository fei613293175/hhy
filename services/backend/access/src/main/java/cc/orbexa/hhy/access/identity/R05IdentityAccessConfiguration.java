package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import cc.orbexa.hhy.access.storage.R04MediaPostgresStore;
import cc.orbexa.hhy.access.storage.R04MediaPurposePolicy;
import cc.orbexa.hhy.access.storage.R04MediaStorageGateway;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentitySandboxProperties.class)
public class R05IdentityAccessConfiguration {
    @Bean("productionIdentityProviderClient")
    IdentityProviderClient productionIdentityProviderClient(
            R05IdentityProviderSettings settings, SecretResolver secrets,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        return new AliyunMarketIdentityProviderClient(settings, secrets, objectMapper);
    }

    @Bean("sandboxIdentityProviderClient")
    IdentityProviderClient sandboxIdentityProviderClient(IdentitySandboxProperties properties) {
        return new R05IdentitySandboxClient(properties);
    }

    @Bean
    @Primary
    IdentityProviderClient identityProviderClient(
            @Qualifier("productionIdentityProviderClient") IdentityProviderClient production,
            @Qualifier("sandboxIdentityProviderClient") IdentityProviderClient sandbox) {
        return new R05IdentityProviderRouter(production, sandbox);
    }

    @Bean
    IdentitySandboxStartupGuard identitySandboxStartupGuard(
            IdentitySandboxProperties properties, Environment environment) {
        return new IdentitySandboxStartupGuard(properties, environment);
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
    IdentitySandboxService identitySandboxService(
            IdentitySandboxService.Store store,
            IdentitySandboxProperties properties, Clock clock) {
        return new IdentitySandboxService(store, properties, clock);
    }

    @Bean
    ProviderFaceImageDownloader providerFaceImageDownloader() {
        return new ProviderFaceImageDownloader();
    }

    @Bean
    IdentityProviderResultCoordinator.EvidenceStorage identityEvidenceStorage(
            R04MediaPurposePolicy purposes, R04MediaStorageGateway storage,
            R04MediaPostgresStore media, Clock clock) {
        return new R05PrivateIdentityEvidenceStorage(purposes, storage, media, clock);
    }

    @Bean
    IdentityProviderResultCoordinator identityProviderResultCoordinator(
            R05IdentityPostgresStore store, R05IdentityProviderGateway provider,
            ProviderFaceImageDownloader downloader,
            IdentityProviderResultCoordinator.EvidenceStorage evidenceStorage,
            Clock clock) {
        return new IdentityProviderResultCoordinator(
                store, provider, downloader, evidenceStorage, clock);
    }

    @Bean
    IdentityService identityService(
            R05IdentityPostgresStore store, R05IdentityRuntimePolicy policy,
            R05IdentityProviderGateway provider, IdentitySensitiveCipher sensitiveData,
            IdentityIdempotencyService idempotency,
            IdentityProviderResultCoordinator results, Clock clock) {
        return new IdentityService(
                store, policy, provider, sensitiveData, idempotency, results, clock);
    }
}
