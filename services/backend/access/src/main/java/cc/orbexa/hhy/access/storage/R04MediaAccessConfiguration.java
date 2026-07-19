package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageMigrationService.PortResolver;
import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class R04MediaAccessConfiguration {
    @Bean
    @ConditionalOnMissingBean(PortResolver.class)
    PortResolver unavailableStoragePortResolver() {
        return provider -> { throw new BusinessException(
                "COMMON-422-BUSINESS_RULE", "对象存储供应商尚未激活", 422, true); };
    }

    @Bean
    R04MediaPurposePolicy r04MediaPurposePolicy(R04StoragePostgresStore bindings) {
        return new R04MediaPurposePolicy(bindings);
    }

    @Bean
    R04MediaStorageGateway r04MediaStorageGateway(
            R04StoragePostgresStore bindings, PortResolver ports) {
        return new R04MediaStorageGateway(bindings, ports);
    }

    @Bean
    MediaIdempotencyService mediaIdempotencyService(
            UserAuthStore store, UserIdempotencySnapshotCipher snapshots,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper, Clock clock) {
        return new MediaIdempotencyService(store, snapshots, objectMapper, clock);
    }

    @Bean
    MediaUploadService mediaUploadService(
            R04MediaPostgresStore store, R04MediaStorageGateway storage,
            R04MediaPurposePolicy purposes, MediaIdempotencyService idempotency, Clock clock) {
        return new MediaUploadService(store, storage, purposes, idempotency, clock);
    }
}
