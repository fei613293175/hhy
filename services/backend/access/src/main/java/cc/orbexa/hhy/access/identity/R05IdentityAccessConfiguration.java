package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class R05IdentityAccessConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdentityProviderClient.class)
    IdentityProviderClient unavailableIdentityProviderClient() {
        return command -> { throw new BusinessException(
                "COMMON-500-INTERNAL", "活体检测服务暂时不可用", 500, true); };
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
