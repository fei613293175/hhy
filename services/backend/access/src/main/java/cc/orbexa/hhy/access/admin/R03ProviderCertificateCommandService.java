package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderCertificateService.CertificateView;
import cc.orbexa.hhy.access.admin.R03ConfigurationContracts.CertificateResource;
import cc.orbexa.hhy.shared.api.BusinessException;
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

/** Idempotent write boundary for certificate upload and approved rotation. */
@Service
public class R03ProviderCertificateCommandService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SNAPSHOT_TYPE = "r03.provider-certificate-command.v1";

    private final ProviderCertificateService certificates;
    private final AdminSecurityStore security;
    private final AdminIdempotencySnapshotCipher snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] hmacSecret;

    public R03ProviderCertificateCommandService(
            ProviderCertificateService certificates,
            AdminSecurityStore security,
            AdminIdempotencySnapshotCipher snapshots,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminSecurityProperties properties,
            Clock clock) {
        this.certificates = certificates;
        this.security = security;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hmacSecret = properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CertificateResource upload(
            AdminPrincipal principal, UploadRequest request, String key) {
        return idempotent(principal, "upload", request.provider(), key, request, () -> view(
                certificates.upload(request.provider(), request.certificateType(), request.alias(),
                        request.encryptedContentBase64(), request.passwordSecretRef(),
                        request.expiresAt(), principal.adminId())));
    }

    @Transactional
    public CertificateResource rotate(
            AdminPrincipal principal, String currentId, RotateRequest request, String key) {
        return idempotent(principal, "rotate", currentId, key, request, () -> view(
                certificates.rotate(currentId, request.newCertificateId(), request.approvalId(),
                        request.reason(), request.expectedVersion(), principal.adminId())));
    }

    private CertificateResource idempotent(
            AdminPrincipal principal, String operation, String resource, String key,
            Object request, Supplier<CertificateResource> command) {
        String scope = "admin.provider-cert:" + principal.adminId() + ":" + operation + ":"
                + resource.toLowerCase(java.util.Locale.ROOT);
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyClaim claim = security.claimIdempotency(
                scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (claim.replay()) return replay(claim.row(), scope, key, requestHash);
        CertificateResource result = command.get();
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(scope, key, requestHash, SNAPSHOT_TYPE, plaintext);
            security.completeIdempotencySnapshot(
                    claim.row().id(), result.id(), SNAPSHOT_TYPE, ciphertext);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Certificate response snapshot is unavailable", exception);
        }
    }

    private CertificateResource replay(
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
            return objectMapper.readValue(plaintext, CertificateResource.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Certificate response replay is unavailable", exception);
        }
    }

    private String hash(Object request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(objectMapper.writeValueAsBytes(request)));
        } catch (Exception exception) {
            throw new IllegalStateException("Certificate command hash failed", exception);
        }
    }

    private static CertificateResource view(CertificateView value) {
        return new CertificateResource(
                value.id(), value.provider(), value.certificateType(), value.alias(),
                value.fingerprint(), value.notBefore() == null ? null : value.notBefore().toString(),
                value.expiresAt(), value.status().name(), value.version());
    }

    public record UploadRequest(
            String provider,
            String certificateType,
            String alias,
            String encryptedContentBase64,
            String passwordSecretRef,
            Instant expiresAt) { }

    public record RotateRequest(
            String newCertificateId,
            String approvalId,
            String reason,
            long expectedVersion) { }
}
