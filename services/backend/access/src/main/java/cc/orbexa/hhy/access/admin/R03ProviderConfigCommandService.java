package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.ProviderConfigResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent command boundary for the four frozen provider-configuration writes. */
@Service
public class R03ProviderConfigCommandService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SNAPSHOT_TYPE = "r03.provider-config-command.v1";

    private final ProviderConfigVersionService versions;
    private final R03ProviderConfigPostgresStore store;
    private final R03ConfigurationQueryStore queries;
    private final AdminSecurityStore security;
    private final AdminIdempotencySnapshotCipher snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] hmacSecret;

    public R03ProviderConfigCommandService(
            ProviderConfigVersionService versions,
            R03ProviderConfigPostgresStore store,
            R03ConfigurationQueryStore queries,
            AdminSecurityStore security,
            AdminIdempotencySnapshotCipher snapshots,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminSecurityProperties properties,
            Clock clock) {
        this.versions = versions;
        this.store = store;
        this.queries = queries;
        this.security = security;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hmacSecret = properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ProviderConfigResource create(
            AdminPrincipal principal, String provider, CreateRequest request, String key) {
        return idempotent(principal, provider, "create", key, request, () -> {
            versions.create(provider, request.environment(), request.values(),
                    request.secretRefs(), request.remark(), principal.adminId());
            return resource(provider);
        });
    }

    @Transactional
    public ProviderConfigResource test(
            AdminPrincipal principal, String provider, TestRequest request, String key) {
        return idempotent(principal, provider, "test", key, request, () -> {
            long expectedVersion = store.currentVersion(provider, request.versionId());
            versions.testConnection(provider, request.versionId(), request.testRecipient(),
                    expectedVersion, principal.adminId());
            return resource(provider);
        });
    }

    @Transactional
    public ProviderConfigResource activate(
            AdminPrincipal principal, String provider, ActivateRequest request, String key) {
        return idempotent(principal, provider, "activate", key, request, () -> {
            versions.activate(provider, request.versionId(), request.approvalId(),
                    request.expectedVersion(), principal.adminId());
            return resource(provider);
        });
    }

    @Transactional
    public ProviderConfigResource rollback(
            AdminPrincipal principal, String provider, RollbackRequest request, String key) {
        return idempotent(principal, provider, "rollback", key, request, () -> {
            versions.rollback(provider, request.targetVersionId(), request.approvalId(),
                    request.reason(), request.expectedVersion(), principal.adminId());
            return resource(provider);
        });
    }

    private ProviderConfigResource idempotent(
            AdminPrincipal principal, String provider, String operation, String key,
            Object request, Supplier<ProviderConfigResource> command) {
        String scope = "admin.provider-config:" + principal.adminId() + ":"
                + operation + ":" + provider.toLowerCase(java.util.Locale.ROOT);
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyClaim claim = security.claimIdempotency(
                scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (claim.replay()) return replay(claim.row(), scope, key, requestHash);
        ProviderConfigResource result = command.get();
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(scope, key, requestHash, SNAPSHOT_TYPE, plaintext);
            security.completeIdempotencySnapshot(
                    claim.row().id(), reference(result), SNAPSHOT_TYPE, ciphertext);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Provider configuration snapshot is unavailable", exception);
        }
    }

    private ProviderConfigResource replay(
            AdminSecurityStore.IdempotencyRow row, String scope, String key, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键对应不同请求", 409, false);
        }
        if (!SNAPSHOT_TYPE.equals(row.responseType()) || row.responsePayloadCiphertext() == null) {
            throw new BusinessException("COMMON-409-VERSION_CONFLICT",
                    "请求正在处理或首次响应不可用", 409, false);
        }
        try {
            byte[] plaintext = snapshots.decrypt(
                    scope, key, requestHash, SNAPSHOT_TYPE, row.responsePayloadCiphertext());
            return objectMapper.readValue(plaintext, ProviderConfigResource.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Provider configuration replay is unavailable", exception);
        }
    }

    private ProviderConfigResource resource(String provider) {
        return queries.providerConfig(provider).orElseThrow(() -> new IllegalStateException(
                "Provider configuration projection disappeared after command"));
    }

    private String hash(Object request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(objectMapper.writeValueAsBytes(request)));
        } catch (Exception exception) {
            throw new IllegalStateException("Provider configuration request hash failed", exception);
        }
    }

    private static String reference(ProviderConfigResource result) {
        String version = result.draftVersion() != null ? result.draftVersion() : result.activeVersion();
        return result.provider() + ":" + (version == null ? "none" : version);
    }

    public record CreateRequest(
            String environment, JsonNode values, JsonNode secretRefs, String remark) { }

    public record TestRequest(String versionId, String testRecipient) { }

    public record ActivateRequest(String versionId, String approvalId, long expectedVersion) { }

    public record RollbackRequest(
            String targetVersionId, String approvalId, String reason, long expectedVersion) { }
}
