package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stores provider certificates outside the application database and exposes only
 * immutable metadata. Raw certificate/private-key bytes are zeroized after the
 * vault hand-off and are never included in audit events or returned resources.
 */
public final class ProviderCertificateService {
    private static final int MAX_ENCODED_LENGTH = 2_000;
    private final Store store;
    private final MaterialVault materialVault;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ProviderCertificateService(
            Store store, MaterialVault materialVault,
            TransactionRunner transactions, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.materialVault = Objects.requireNonNull(materialVault, "materialVault");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CertificateView upload(
            String provider,
            String certificateType,
            String alias,
            String encryptedContentBase64,
            String passwordSecretRef,
            Instant expiresAt,
            long actorId) {
        String safeProvider = identifier(provider, "供应商");
        String safeType = identifier(certificateType, "证书类型").toUpperCase(Locale.ROOT);
        String safeAlias = text(alias, "证书别名", 200);
        String safePasswordRef = optionalSecretReference(passwordSecretRef);
        requireActor(actorId);
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw validation("证书到期时间必须晚于当前时间");
        }
        byte[] material = decode(encryptedContentBase64);
        String fingerprint = sha256(material);
        try {
            String secretRef = materialVault.store(
                    new MaterialDescriptor(safeProvider, safeType, safeAlias),
                    material, safePasswordRef);
            requireSecretReference(secretRef, "证书存储引用");
            return transactions.inTransaction(() -> {
                String id = store.nextId();
                Status status = store.activeForUpdate(safeProvider, safeType).isPresent()
                        ? Status.STAGED : Status.ACTIVE;
                StoredCertificate stored = new StoredCertificate(
                        id, safeProvider, safeType, safeAlias, fingerprint, secretRef,
                        clock.instant(), expiresAt, status, 0L);
                store.insert(stored, new AuditEvent(
                        "PROVIDER_CERTIFICATE_UPLOADED", id, safeProvider, safeType,
                        actorId, "fingerprint=" + fingerprint, clock.instant()));
                return view(stored);
            });
        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    public CertificateView rotate(
            String currentId,
            String newCertificateId,
            String approvalId,
            String reason,
            long expectedVersion,
            long actorId) {
        String safeCurrentId = identifier(currentId, "当前证书");
        String safeNewId = identifier(newCertificateId, "新证书");
        String safeReason = text(reason, "轮换原因", 1_000);
        requireActor(actorId);
        return transactions.inTransaction(() -> {
            StoredCertificate current = locked(safeCurrentId);
            if (current.version() != expectedVersion) throw versionConflict();
            StoredCertificate replacement = locked(safeNewId);
            if (current.id().equals(replacement.id())) throw businessRule("新旧证书不能相同");
            if (current.status() != Status.ACTIVE || replacement.status() != Status.STAGED) {
                throw businessRule("只有生效证书可以轮换到待启用证书");
            }
            if (!current.provider().equals(replacement.provider())
                    || !current.certificateType().equals(replacement.certificateType())) {
                throw businessRule("轮换证书的供应商和类型必须一致");
            }
            Approval approval = approved(approvalId);
            StoredCertificate retired = current.withStatus(Status.ROTATED, current.version() + 1);
            StoredCertificate activated = replacement.withStatus(
                    Status.ACTIVE, replacement.version() + 1);
            store.rotateAtomically(current, retired, replacement, activated, new AuditEvent(
                    "PROVIDER_CERTIFICATE_ROTATED", current.id(), current.provider(),
                    current.certificateType(), actorId,
                    "newCertificateId=" + replacement.id() + ";approval=" + approval.id()
                            + ";reason=" + safeReason,
                    clock.instant()));
            return view(activated);
        });
    }

    private StoredCertificate locked(String id) {
        return store.findForUpdate(id).orElseThrow(() -> notFound("供应商证书不存在"));
    }

    private Approval approved(String approvalId) {
        String safeApprovalId = identifier(approvalId, "审批单");
        Approval approval = store.approvalForUpdate(safeApprovalId)
                .orElseThrow(() -> notFound("审批单不存在"));
        if (approval.status() != ApprovalStatus.APPROVED) throw businessRule("审批单尚未通过");
        if (approval.requesterId() <= 0 || approval.reviewerId() <= 0
                || approval.requesterId() == approval.reviewerId()) {
            throw businessRule("证书轮换必须由不同管理员申请和复核");
        }
        return approval;
    }

    private static CertificateView view(StoredCertificate value) {
        return new CertificateView(
                value.id(), value.provider(), value.certificateType(), value.alias(),
                value.fingerprint(), value.notBefore(), value.expiresAt(),
                value.status(), value.version());
    }

    private static byte[] decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw validation("加密证书内容格式无效");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < 16) {
                Arrays.fill(decoded, (byte) 0);
                throw validation("加密证书内容过短");
            }
            return decoded;
        } catch (IllegalArgumentException error) {
            throw validation("加密证书内容不是有效Base64");
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String optionalSecretReference(String value) {
        if (value == null || value.isBlank()) return null;
        return requireSecretReference(value, "证书密码引用");
    }

    private static String requireSecretReference(String value, String field) {
        if (value == null || !value.matches("^(vault|kms)://[A-Za-z0-9_./:@-]{3,512}$")) {
            throw validation(field + "必须是Vault或KMS引用");
        }
        return value;
    }

    private static String identifier(String value, String field) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")) {
            throw validation(field + "标识无效");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) throw validation(field + "不能为空");
        String safe = value.strip();
        if (safe.length() > maximum || safe.chars().anyMatch(Character::isISOControl)) {
            throw validation(field + "格式无效");
        }
        return safe;
    }

    private static void requireActor(long actorId) {
        if (actorId <= 0) throw validation("操作管理员无效");
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException("COMMON-404-NOT_FOUND", message, 404, false);
    }

    private static BusinessException businessRule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "证书版本已变化，请刷新后重试", 409, false);
    }

    public interface MaterialVault {
        /** Stores an owned, short-lived byte array and returns an opaque Vault/KMS reference. */
        String store(MaterialDescriptor descriptor, byte[] material, String passwordSecretRef);
    }

    public interface TransactionRunner {
        <T> T inTransaction(Supplier<T> work);
    }

    public interface Store {
        String nextId();

        Optional<StoredCertificate> findForUpdate(String id);

        Optional<StoredCertificate> activeForUpdate(String provider, String certificateType);

        Optional<Approval> approvalForUpdate(String approvalId);

        void insert(StoredCertificate certificate, AuditEvent audit);

        void rotateAtomically(
                StoredCertificate current,
                StoredCertificate retiredCurrent,
                StoredCertificate replacement,
                StoredCertificate activatedReplacement,
                AuditEvent audit);
    }

    public record MaterialDescriptor(String provider, String certificateType, String alias) { }

    /** Internal persistence model. The secret reference must never be mapped to an API response. */
    public record StoredCertificate(
            String id,
            String provider,
            String certificateType,
            String alias,
            String fingerprint,
            String secretRef,
            Instant notBefore,
            Instant expiresAt,
            Status status,
            long version) {
        public StoredCertificate withStatus(Status next, long nextVersion) {
            return new StoredCertificate(id, provider, certificateType, alias, fingerprint,
                    secretRef, notBefore, expiresAt, next, nextVersion);
        }
    }

    /** Frozen CertificateResource projection; deliberately excludes secretRef and raw material. */
    public record CertificateView(
            String id,
            String provider,
            String certificateType,
            String alias,
            String fingerprint,
            Instant notBefore,
            Instant expiresAt,
            Status status,
            long version) { }

    public record Approval(
            String id, long requesterId, long reviewerId, ApprovalStatus status) { }

    public record AuditEvent(
            String action,
            String certificateId,
            String provider,
            String certificateType,
            long actorId,
            String detail,
            Instant createdAt) { }

    public enum Status { STAGED, ACTIVE, ROTATED, REVOKED }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }
}
