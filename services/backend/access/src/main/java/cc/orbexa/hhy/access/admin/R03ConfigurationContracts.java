package cc.orbexa.hhy.access.admin;

import java.time.Instant;
import java.util.List;

/** API projections shared by the frozen R03 configuration-center endpoints. */
public final class R03ConfigurationContracts {
    private R03ConfigurationContracts() { }

    public record PageMeta(int page, int pageSize, long total, boolean hasMore) { }

    public record Page<T>(List<T> items, PageMeta page) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record ConfiguredSecret(
            String key, boolean configured, String secretRefMasked) { }

    public record ProviderConfigResource(
            String provider,
            String environment,
            String activeVersion,
            String draftVersion,
            List<ConfiguredSecret> configuredSecrets,
            String connectionStatus,
            Instant lastTestAt,
            long version) {
        public ProviderConfigResource {
            configuredSecrets = configuredSecrets == null
                    ? List.of() : List.copyOf(configuredSecrets);
        }
    }

    public record CertificateResource(
            String id,
            String provider,
            String certificateType,
            String alias,
            String fingerprint,
            String notBefore,
            Instant expiresAt,
            String status,
            long version) { }

    public record DomainResource(
            String code,
            String environment,
            String hostname,
            String dnsStatus,
            String httpsStatus,
            String certificateStatus,
            Instant lastVerifiedAt,
            long version) { }
}
