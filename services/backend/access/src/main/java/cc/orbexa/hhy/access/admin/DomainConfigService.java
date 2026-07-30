package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.DomainConfigPolicy.ValidatedDomain;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.OverallStatus;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.VerificationResult;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Transaction boundary for domain plan updates and idempotent layered verification. */
public final class DomainConfigService {
    private final DomainConfigPolicy policy;
    private final DomainVerificationCoordinator verification;
    private final Store store;
    private final TransactionRunner transactions;
    private final Clock clock;

    public DomainConfigService(
            DomainConfigPolicy policy,
            DomainVerificationCoordinator verification,
            Store store,
            TransactionRunner transactions,
            Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DomainAggregate update(
            String code,
            String hostname,
            String certificateMode,
            long expectedVersion,
            String idempotencyKey,
            long actorId) {
        requireActor(actorId);
        String normalizedCode = normalizeCode(code);
        String key = requireIdempotencyKey(idempotencyKey);
        return transactions.inTransaction(() -> {
            DomainAggregate current = locked(normalizedCode);
            ValidatedDomain nextConfig = policy.validate(
                    current.config().code(), current.config().environment().name(),
                    hostname, certificateMode);
            String fingerprint = String.join("\n", nextConfig.hostname(),
                    nextConfig.certificateMode().name(), Long.toString(expectedVersion));
            Optional<UpdateReceipt> replay = store.updateReceiptForUpdate(normalizedCode, key);
            if (replay.isPresent()) {
                requireSameFingerprint(replay.orElseThrow().fingerprint(), fingerprint);
                return replay.orElseThrow().result();
            }
            requireVersion(current, expectedVersion);
            DomainAggregate updated = current.updated(nextConfig);
            DnsAction action = new DnsAction(
                    normalizedCode, nextConfig.environment().name(), nextConfig.hostname(),
                    "CNAME_OR_A", "PENDING_USER_DNS", true,
                    "PROJECT_OWNER", "BEFORE_DOMAIN_VERIFICATION");
            UpdateReceipt receipt = new UpdateReceipt(key, fingerprint, updated);
            store.saveUpdate(current, updated, action, receipt, new AuditEvent(
                    "DOMAIN_PLAN_UPDATED", normalizedCode, actorId,
                    "version=" + updated.version(), clock.instant()));
            return updated;
        });
    }

    public DomainAggregate verify(
            String code,
            boolean force,
            String idempotencyKey,
            long actorId) {
        requireActor(actorId);
        String normalizedCode = normalizeCode(code);
        String key = requireIdempotencyKey(idempotencyKey);
        String fingerprint = Boolean.toString(force);
        return transactions.inTransaction(() -> {
            Optional<VerificationReceipt> replay =
                    store.verificationReceiptForUpdate(normalizedCode, key);
            if (replay.isPresent()) {
                requireSameFingerprint(replay.orElseThrow().fingerprint(), fingerprint);
                return replay.orElseThrow().result();
            }
            DomainAggregate current = locked(normalizedCode);
            if (current.status() == DomainStatus.HEALTHY && !force) {
                throw businessRule("域名已健康；如需重新验证请显式使用 force");
            }
            VerificationResult result = verification.verify(current.config());
            DomainAggregate updated = current.verified(result);
            VerificationReceipt receipt = new VerificationReceipt(key, fingerprint, updated);
            store.saveVerification(current, updated, result, receipt, new AuditEvent(
                    "DOMAIN_VERIFIED", normalizedCode, actorId,
                    result.overallStatus().name(), result.attemptedAt()));
            return updated;
        });
    }

    private DomainAggregate locked(String code) {
        return store.findForUpdate(code)
                .orElseThrow(() -> notFound("域名配置不存在"));
    }

    private static void requireVersion(DomainAggregate aggregate, long expectedVersion) {
        if (aggregate.version() != expectedVersion) {
            throw new BusinessException(
                    "COMMON-409-VERSION_CONFLICT", "域名配置已变化，请刷新后重试", 409, false);
        }
    }

    private static void requireSameFingerprint(String previous, String current) {
        if (!Objects.equals(previous, current)) {
            throw new BusinessException(
                    "COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键不能用于不同请求", 409, false);
        }
    }

    private static String normalizeCode(String code) {
        if (code == null) throw validation("域名代码不能为空");
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z][a-z0-9_]{1,31}$")) {
            throw validation("域名代码格式无效");
        }
        return normalized;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) throw validation("幂等键不能为空");
        String normalized = value.strip();
        if (!normalized.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")) {
            throw validation("幂等键格式无效");
        }
        return normalized;
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

    public interface TransactionRunner {
        <T> T inTransaction(Supplier<T> work);
    }

    public interface Store {
        Optional<DomainAggregate> findForUpdate(String code);

        Optional<UpdateReceipt> updateReceiptForUpdate(String code, String idempotencyKey);

        Optional<VerificationReceipt> verificationReceiptForUpdate(
                String code, String idempotencyKey);

        void saveUpdate(
                DomainAggregate previous,
                DomainAggregate updated,
                DnsAction action,
                UpdateReceipt receipt,
                AuditEvent audit);

        void saveVerification(
                DomainAggregate previous,
                DomainAggregate updated,
                VerificationResult verification,
                VerificationReceipt receipt,
                AuditEvent audit);
    }

    public enum DomainStatus {
        PENDING_DNS, DNS_FAILED, TLS_FAILED, SERVICE_UNHEALTHY, HEALTHY;

        static DomainStatus from(OverallStatus status) {
            return switch (status) {
                case DNS_FAILED -> DNS_FAILED;
                case TLS_FAILED -> TLS_FAILED;
                case SERVICE_UNHEALTHY -> SERVICE_UNHEALTHY;
                case HEALTHY -> HEALTHY;
            };
        }
    }

    public record DomainAggregate(
            ValidatedDomain config,
            DomainStatus status,
            VerificationResult lastVerification,
            Instant verifiedAt,
            long version) {
        public DomainAggregate {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(status, "status");
            if (version < 0) throw new IllegalArgumentException("version cannot be negative");
            if (status == DomainStatus.HEALTHY && verifiedAt == null) {
                throw new IllegalArgumentException("Healthy domain requires verifiedAt");
            }
            if (status != DomainStatus.HEALTHY && verifiedAt != null) {
                throw new IllegalArgumentException("Non-healthy domain cannot retain verifiedAt");
            }
        }

        public static DomainAggregate pending(ValidatedDomain config, long version) {
            return new DomainAggregate(config, DomainStatus.PENDING_DNS, null, null, version);
        }

        DomainAggregate updated(ValidatedDomain nextConfig) {
            return new DomainAggregate(
                    nextConfig, DomainStatus.PENDING_DNS, null, null, version + 1);
        }

        DomainAggregate verified(VerificationResult result) {
            if (!config.code().equals(result.code())
                    || !config.hostname().equals(result.hostname())) {
                throw new IllegalArgumentException("Verification target mismatch");
            }
            return new DomainAggregate(
                    config, DomainStatus.from(result.overallStatus()), result,
                    result.verifiedAt(), version + 1);
        }
    }

    public record DnsAction(
            String code,
            String environment,
            String hostname,
            String recordType,
            String status,
            boolean userActionRequired,
            String owner,
            String dueCondition) { }

    public record UpdateReceipt(
            String idempotencyKey, String fingerprint, DomainAggregate result) { }

    public record VerificationReceipt(
            String idempotencyKey, String fingerprint, DomainAggregate result) { }

    public record AuditEvent(
            String action, String code, long actorId, String detail, Instant createdAt) { }
}
