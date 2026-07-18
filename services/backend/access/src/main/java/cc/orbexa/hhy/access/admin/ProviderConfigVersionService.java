package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Snapshot;
import cc.orbexa.hhy.access.admin.ProviderConfigValidator.ValidatedConfig;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.TestOutcome;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Application service for the R03 provider configuration vertical slice. */
public final class ProviderConfigVersionService {
    private final ProviderConfigValidator validator;
    private final ProviderConnectionTestCoordinator connectionTests;
    private final Store store;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ProviderConfigVersionService(
            ProviderConfigValidator validator,
            ProviderConnectionTestCoordinator connectionTests,
            Store store,
            TransactionRunner transactions,
            Clock clock) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.connectionTests = Objects.requireNonNull(connectionTests, "connectionTests");
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VersionAggregate create(
            String provider,
            String environment,
            JsonNode values,
            JsonNode secretRefs,
            String remark,
            long actorId) {
        requireActor(actorId);
        ValidatedConfig config = validator.validate(provider, environment, values, secretRefs);
        String safeRemark = safeReason(remark, 500, false);
        return transactions.inTransaction(() -> {
            String versionId = store.nextVersionId(config.provider());
            Snapshot validated = ProviderConfigLifecycle.validated(
                    ProviderConfigLifecycle.draft(config.provider(), versionId, actorId), 0L);
            VersionAggregate aggregate = new VersionAggregate(validated, config, safeRemark);
            store.insert(aggregate, new AuditEvent(
                    "PROVIDER_CONFIG_VERSION_CREATED", config.provider(), versionId,
                    actorId, safeRemark, clock.instant()));
            return aggregate;
        });
    }

    public VersionAggregate testConnection(
            String provider,
            String versionId,
            String testRecipient,
            long expectedVersion,
            long actorId) {
        requireActor(actorId);
        return transactions.inTransaction(() -> {
            VersionAggregate current = locked(provider, versionId);
            TestOutcome result = connectionTests.test(
                    current.lifecycle(), current.config(), testRecipient, expectedVersion);
            VersionAggregate updated = current.withLifecycle(result.snapshot());
            store.saveConnectionTest(current, updated, result, actorId, new AuditEvent(
                    "PROVIDER_CONNECTION_TESTED", current.config().provider(), versionId,
                    actorId, result.code().name(), result.testedAt()));
            return updated;
        });
    }

    public VersionAggregate activate(
            String provider,
            String versionId,
            String approvalId,
            long expectedVersion,
            long actorId) {
        requireActor(actorId);
        return transactions.inTransaction(() -> {
            VersionAggregate target = locked(provider, versionId);
            Approval approval = approved(
                    approvalId, "PROVIDER_CONFIG_ACTIVATE",
                    target.config().provider(), target.lifecycle().versionId());
            Snapshot pending = ProviderConfigLifecycle.approvalRequested(
                    target.lifecycle(), approval.id(), approval.requesterId(), expectedVersion);
            Snapshot reviewed = ProviderConfigLifecycle.approved(
                    pending, approval.id(), approval.reviewerId(), pending.version());
            Snapshot activated = ProviderConfigLifecycle.activated(
                    reviewed, approval.id(), clock.instant(), reviewed.version());
            VersionAggregate nextTarget = target.withLifecycle(activated);

            Optional<VersionAggregate> active = store.activeForUpdate(target.config().provider());
            VersionAggregate previous = active.orElse(null);
            VersionAggregate nextPrevious = null;
            if (previous != null) {
                if (previous.lifecycle().versionId().equals(versionId)) {
                    throw businessRule("该配置版本已经激活");
                }
                nextPrevious = previous.withLifecycle(ProviderConfigLifecycle.superseded(
                        previous.lifecycle(), previous.lifecycle().version()));
            }
            store.activateAtomically(previous, nextPrevious, target, nextTarget, new AuditEvent(
                    "PROVIDER_CONFIG_ACTIVATED", target.config().provider(), versionId,
                    actorId, "approval=" + approval.id(), clock.instant()));
            return nextTarget;
        });
    }

    public VersionAggregate rollback(
            String provider,
            String targetVersionId,
            String approvalId,
            String reason,
            long expectedVersion,
            long actorId) {
        requireActor(actorId);
        String safeReason = safeReason(reason, 1000, true);
        return transactions.inTransaction(() -> {
            String normalizedProvider = normalizedProvider(provider);
            VersionAggregate current = store.activeForUpdate(normalizedProvider)
                    .orElseThrow(() -> businessRule("当前供应商没有激活版本"));
            if (current.lifecycle().version() != expectedVersion) throw versionConflict();
            VersionAggregate target = locked(normalizedProvider, targetVersionId);
            if (current.lifecycle().versionId().equals(targetVersionId)) {
                throw businessRule("回滚目标不能是当前激活版本");
            }
            Approval approval = approved(
                    approvalId, "PROVIDER_CONFIG_ROLLBACK",
                    target.config().provider(), target.lifecycle().versionId());
            Instant now = clock.instant();
            VersionAggregate nextCurrent = current.withLifecycle(
                    ProviderConfigLifecycle.rollbackApproved(
                            current.lifecycle(), approval.id(), approval.requesterId(),
                            approval.reviewerId(), expectedVersion));
            VersionAggregate nextTarget = target.withLifecycle(
                    ProviderConfigLifecycle.restored(
                            target.lifecycle(), approval.id(), approval.requesterId(),
                            approval.reviewerId(), now, target.lifecycle().version()));
            store.rollbackAtomically(current, nextCurrent, target, nextTarget, new AuditEvent(
                    "PROVIDER_CONFIG_ROLLED_BACK", normalizedProvider, targetVersionId,
                    actorId, safeReason + ";approval=" + approval.id(), now));
            return nextTarget;
        });
    }

    private VersionAggregate locked(String provider, String versionId) {
        String normalizedProvider = normalizedProvider(provider);
        if (versionId == null || !versionId.matches("^[A-Za-z0-9_-]{1,128}$")) {
            throw validation("供应商配置版本标识无效");
        }
        return store.findForUpdate(normalizedProvider, versionId)
                .orElseThrow(() -> notFound("供应商配置版本不存在"));
    }

    private Approval approved(
            String approvalId, String requiredType,
            String requiredProvider, String requiredVersionId) {
        if (approvalId == null || !approvalId.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw validation("审批标识无效");
        }
        Approval approval = store.approvalForUpdate(approvalId)
                .orElseThrow(() -> notFound("审批单不存在"));
        if (!requiredType.equals(approval.type())
                || !requiredProvider.equals(approval.provider())
                || !requiredVersionId.equals(approval.versionId())) {
            throw businessRule("审批单与当前配置操作不匹配");
        }
        if (approval.status() != ApprovalStatus.APPROVED) {
            throw businessRule("审批单尚未完成复核");
        }
        if (approval.requesterId() <= 0 || approval.reviewerId() <= 0
                || approval.requesterId() == approval.reviewerId()) {
            throw businessRule("审批申请人与复核人必须是不同管理员");
        }
        return approval;
    }

    private static String normalizedProvider(String provider) {
        if (provider == null || !provider.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")) {
            throw validation("供应商标识无效");
        }
        return provider.toLowerCase(java.util.Locale.ROOT);
    }

    private static String safeReason(String value, int maximum, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw validation("操作原因不能为空");
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() > maximum || normalized.chars().anyMatch(Character::isISOControl)) {
            throw validation("操作原因格式无效");
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

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "配置版本已变化，请刷新后重试", 409, false);
    }

    public interface TransactionRunner {
        <T> T inTransaction(Supplier<T> work);
    }

    public interface Store {
        String nextVersionId(String provider);

        Optional<VersionAggregate> findForUpdate(String provider, String versionId);

        Optional<VersionAggregate> activeForUpdate(String provider);

        Optional<Approval> approvalForUpdate(String approvalId);

        void insert(VersionAggregate aggregate, AuditEvent audit);

        void saveConnectionTest(
                VersionAggregate previous,
                VersionAggregate updated,
                TestOutcome test,
                long actorId,
                AuditEvent audit);

        void activateAtomically(
                VersionAggregate previousActive,
                VersionAggregate updatedPreviousActive,
                VersionAggregate target,
                VersionAggregate activatedTarget,
                AuditEvent audit);

        void rollbackAtomically(
                VersionAggregate current,
                VersionAggregate rolledBackCurrent,
                VersionAggregate target,
                VersionAggregate restoredTarget,
                AuditEvent audit);
    }

    public record VersionAggregate(
            Snapshot lifecycle, ValidatedConfig config, String remark) {
        public VersionAggregate {
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(config, "config");
            remark = remark == null ? "" : remark;
            if (!Objects.equals(lifecycle.provider(), config.provider())) {
                throw new IllegalArgumentException("Lifecycle and configuration provider mismatch");
            }
        }

        public VersionAggregate withLifecycle(Snapshot next) {
            return new VersionAggregate(next, config, remark);
        }
    }

    public record Approval(
            String id, String type, String provider, String versionId,
            long requesterId, long reviewerId, ApprovalStatus status) {
        public Approval {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(status, "status");
        }
    }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }

    public record AuditEvent(
            String action,
            String provider,
            String versionId,
            long actorId,
            String detail,
            Instant createdAt) { }
}
