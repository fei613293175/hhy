package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageMigrationService.PortResolver;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class R04MediaAccessConfiguration {
    @Bean
    PortResolver r04StoragePortResolver(
            R04StorageProviderSettings settings, SecretResolver secrets, Clock clock) {
        StorageObjectPort r2 = new StorageProviderAdapter(
                StorageObjectPort.Provider.CLOUDFLARE_R2,
                new R04S3StorageTransport(settings, secrets, clock), clock);
        return new R04StoragePortResolver(r2);
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
