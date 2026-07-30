package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.Objects;

/** Immutable lifecycle rules for a provider configuration version. */
public final class ProviderConfigLifecycle {
    private ProviderConfigLifecycle() { }

    public static Snapshot draft(String provider, String versionId, long creatorId) {
        if (provider == null || provider.isBlank() || versionId == null || versionId.isBlank()) {
            throw validation("供应商和配置版本不能为空");
        }
        if (creatorId <= 0) throw validation("配置创建人无效");
        return new Snapshot(provider, versionId, Status.DRAFT, creatorId,
                null, null, null, null, null, null, null, 0L);
    }

    public static Snapshot validated(Snapshot current, long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.DRAFT);
        return current.with(Status.VALIDATED, null, null, null, null, null, null,
                current.version() + 1);
    }

    public static Snapshot connectionTested(
            Snapshot current, boolean successful, String maskedResult,
            Instant testedAt, long expectedVersion) {
        requireVersion(current, expectedVersion);
        if (current.status() != Status.VALIDATED && current.status() != Status.CONNECTION_TESTED) {
            throw businessRule("只有已校验版本可以执行连接测试");
        }
        if (testedAt == null) throw validation("连接测试时间不能为空");
        String safeResult = requireMaskedResult(maskedResult);
        if (!successful) {
            return current.with(Status.VALIDATED, false, safeResult, testedAt,
                    null, null, null, current.version() + 1);
        }
        return current.with(Status.CONNECTION_TESTED, true, safeResult, testedAt,
                null, null, null, current.version() + 1);
    }

    public static Snapshot approvalRequested(
            Snapshot current, String approvalId, long requesterId, long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.CONNECTION_TESTED);
        requireSuccessfulTest(current);
        if (approvalId == null || !approvalId.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw validation("审批标识无效");
        }
        if (requesterId <= 0) throw validation("审批申请人无效");
        return new Snapshot(current.provider(), current.versionId(), Status.PENDING_APPROVAL,
                current.creatorId(), current.connectionSuccessful(), current.maskedTestResult(),
                current.testedAt(), approvalId, requesterId, null, null, current.version() + 1);
    }

    public static Snapshot approved(
            Snapshot current, String approvalId, long reviewerId, long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.PENDING_APPROVAL);
        if (!Objects.equals(current.approvalId(), approvalId)) {
            throw businessRule("审批单与当前配置版本不匹配");
        }
        if (reviewerId <= 0) throw validation("审批复核人无效");
        if (current.approvalRequesterId() != null
                && current.approvalRequesterId() == reviewerId) {
            throw businessRule("审批申请人与复核人必须是不同管理员");
        }
        return new Snapshot(current.provider(), current.versionId(), current.status(),
                current.creatorId(), current.connectionSuccessful(), current.maskedTestResult(),
                current.testedAt(), current.approvalId(), current.approvalRequesterId(),
                reviewerId, current.activatedAt(), current.version() + 1);
    }

    public static Snapshot activated(
            Snapshot current, String approvalId, Instant activatedAt, long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.PENDING_APPROVAL);
        requireSuccessfulTest(current);
        if (!Objects.equals(current.approvalId(), approvalId)
                || current.approvalReviewerId() == null) {
            throw businessRule("配置版本尚未完成双人审批");
        }
        if (activatedAt == null) throw validation("激活时间不能为空");
        return new Snapshot(current.provider(), current.versionId(), Status.ACTIVE,
                current.creatorId(), current.connectionSuccessful(), current.maskedTestResult(),
                current.testedAt(), current.approvalId(), current.approvalRequesterId(),
                current.approvalReviewerId(), activatedAt, current.version() + 1);
    }

    public static Snapshot superseded(Snapshot current, long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.ACTIVE);
        return current.with(Status.SUPERSEDED, current.connectionSuccessful(),
                current.maskedTestResult(), current.testedAt(), current.approvalId(),
                current.approvalReviewerId(), current.activatedAt(), current.version() + 1);
    }

    public static Snapshot rolledBack(
            Snapshot current, String approvalId, long expectedVersion) {
        requireVersion(current, expectedVersion);
        if (current.status() != Status.ACTIVE && current.status() != Status.SUPERSEDED) {
            throw businessRule("只有已激活或已被替代的版本可以标记回滚");
        }
        if (!Objects.equals(current.approvalId(), approvalId)
                || current.approvalReviewerId() == null) {
            throw businessRule("回滚必须绑定已完成的双人审批");
        }
        return current.with(Status.ROLLED_BACK, current.connectionSuccessful(),
                current.maskedTestResult(), current.testedAt(), current.approvalId(),
                current.approvalReviewerId(), current.activatedAt(), current.version() + 1);
    }

    public static Snapshot rollbackApproved(
            Snapshot current,
            String approvalId,
            long requesterId,
            long reviewerId,
            long expectedVersion) {
        requireVersion(current, expectedVersion);
        requireStatus(current, Status.ACTIVE);
        requireApprovalActors(approvalId, requesterId, reviewerId);
        return new Snapshot(current.provider(), current.versionId(), Status.ROLLED_BACK,
                current.creatorId(), current.connectionSuccessful(), current.maskedTestResult(),
                current.testedAt(), approvalId, requesterId, reviewerId,
                current.activatedAt(), current.version() + 1);
    }

    public static Snapshot restored(
            Snapshot target,
            String approvalId,
            long requesterId,
            long reviewerId,
            Instant restoredAt,
            long expectedVersion) {
        requireVersion(target, expectedVersion);
        if (target.status() != Status.SUPERSEDED && target.status() != Status.ROLLED_BACK) {
            throw businessRule("只有已被替代或已回滚的历史版本可以恢复激活");
        }
        requireSuccessfulTest(target);
        requireApprovalActors(approvalId, requesterId, reviewerId);
        if (restoredAt == null) throw validation("回滚恢复时间不能为空");
        return new Snapshot(target.provider(), target.versionId(), Status.ACTIVE,
                target.creatorId(), target.connectionSuccessful(), target.maskedTestResult(),
                target.testedAt(), approvalId, requesterId, reviewerId,
                restoredAt, target.version() + 1);
    }

    private static void requireVersion(Snapshot current, long expectedVersion) {
        if (current == null) throw validation("配置版本不能为空");
        if (current.version() != expectedVersion) {
            throw new BusinessException(
                    "COMMON-409-VERSION_CONFLICT", "配置版本已变化，请刷新后重试", 409, false);
        }
    }

    private static void requireStatus(Snapshot current, Status expected) {
        if (current.status() != expected) {
            throw businessRule("当前配置状态不允许此操作");
        }
    }

    private static void requireSuccessfulTest(Snapshot current) {
        if (!Boolean.TRUE.equals(current.connectionSuccessful()) || current.testedAt() == null) {
            throw businessRule("连接测试未成功，禁止进入审批或激活");
        }
    }

    private static void requireApprovalActors(
            String approvalId, long requesterId, long reviewerId) {
        if (approvalId == null || !approvalId.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw validation("审批标识无效");
        }
        if (requesterId <= 0 || reviewerId <= 0) throw validation("审批参与人无效");
        if (requesterId == reviewerId) {
            throw businessRule("审批申请人与复核人必须是不同管理员");
        }
    }

    private static String requireMaskedResult(String result) {
        if (result == null || result.isBlank() || result.length() > 255) {
            throw validation("连接测试结果必须是长度不超过255的脱敏摘要");
        }
        String safe = result.strip();
        String lowered = safe.toLowerCase();
        if (lowered.contains("secret") || lowered.contains("appcode")
                || lowered.contains("accesskey") || lowered.contains("authorization")) {
            throw validation("连接测试结果包含潜在秘密字段");
        }
        return safe;
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException businessRule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public enum Status {
        DRAFT,
        VALIDATED,
        CONNECTION_TESTED,
        PENDING_APPROVAL,
        ACTIVE,
        SUPERSEDED,
        REJECTED,
        ROLLED_BACK
    }

    public record Snapshot(
            String provider,
            String versionId,
            Status status,
            long creatorId,
            Boolean connectionSuccessful,
            String maskedTestResult,
            Instant testedAt,
            String approvalId,
            Long approvalRequesterId,
            Long approvalReviewerId,
            Instant activatedAt,
            long version) {
        private Snapshot with(
                Status nextStatus,
                Boolean nextConnectionSuccessful,
                String nextMaskedTestResult,
                Instant nextTestedAt,
                String nextApprovalId,
                Long nextApprovalReviewerId,
                Instant nextActivatedAt,
                long nextVersion) {
            return new Snapshot(provider, versionId, nextStatus, creatorId,
                    nextConnectionSuccessful, nextMaskedTestResult, nextTestedAt,
                    nextApprovalId, approvalRequesterId, nextApprovalReviewerId,
                    nextActivatedAt, nextVersion);
        }
    }
}
