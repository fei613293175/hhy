package cc.orbexa.hhy.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Java shapes that mirror the frozen R12 admin review schemas. */
public final class R12ReviewContracts {
    private R12ReviewContracts() { }

    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record ReviewResource(
            String id,
            String subjectType,
            String subjectId,
            String status,
            String riskLevel,
            String assigneeId,
            String decision,
            String reason,
            Instant createdAt,
            long version) { }

    public record ReportResource(
            String id,
            String reporterId,
            String subjectType,
            String subjectId,
            String reasonCode,
            String status,
            String decision,
            Instant createdAt,
            long version) { }

    public record AppealResource(
            String id,
            String appellantId,
            String subjectType,
            String subjectId,
            String reason,
            String status,
            String decision,
            Instant createdAt,
            long version) { }

    public record ReviewPage(List<ReviewResource> items, PageMeta page) {
        public ReviewPage { items = List.copyOf(items); }
    }

    public record ReportPage(List<ReportResource> items, PageMeta page) {
        public ReportPage { items = List.copyOf(items); }
    }

    public record AppealPage(List<AppealResource> items, PageMeta page) {
        public AppealPage { items = List.copyOf(items); }
    }

    public record ReviewActorContext(
            long adminId,
            long sessionId,
            String username,
            String permission,
            String requestId,
            String ip) { }

    public record ReviewDecisionRequest(
            @NotBlank @Size(max = 2000) String decision,
            @NotBlank @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 100) List<String> evidenceIds) {
        public ReviewDecisionRequest {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record ReviewAssignRequest(
            @NotBlank @Size(max = 64) String assigneeId,
            @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion) { }
}
