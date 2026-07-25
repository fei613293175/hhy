package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R12ReviewStore {
    ReviewPageRows reviews(PageQuery query);
    Optional<ReviewRow> review(long contentId);
    Optional<ReviewRow> lockReview(long contentId);
    ReportPageRows reports(PageQuery query);
    AppealPageRows appeals(PageQuery query);
    boolean activeAdmin(long adminId);
    Optional<String> sessionDevice(long adminId, long sessionId);
    Optional<SnapshotRow> submittedSnapshot(long contentId, long maximumVersion);
    SecondReviewState secondReviewState(long snapshotId);
    boolean advance(long contentId, long expectedVersion, String status, Instant now);
    void reviewRecord(
            long contentId, SnapshotRow snapshot, String decision, String reason,
            long adminId, String commandId, Instant now);
    void statusLog(
            long contentId, String fromStatus, String toStatus, String reason,
            long transitionVersion, String operator, Instant now);
    void audit(
            long adminId, String action, long resourceId,
            String beforeJson, String afterJson, String ip, Instant now);
    void outbox(String eventType, long contentId, String requestId, String payloadJson);
    IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt);
    void complete(long claimId, String responseRef, String responseType, String ciphertext);

    record PageQuery(
            int page, int pageSize, CursorKey cursor, String status,
            String keyword, String sort) { }

    record CursorKey(long id, Instant sortValue) { }

    record ReviewRow(
            long id, String status, long version, Instant createdAt, Instant updatedAt,
            String riskLevel, Long assigneeId, String decision, String reason) { }

    record ReportRow(
            long id, Long reporterId, Long contentId, String reasonCode,
            String status, Instant createdAt, Instant updatedAt, long version) { }

    record AppealRow(
            long id, Long appellantId, long contentId, String status,
            Instant createdAt, Instant updatedAt, long version) { }

    record SnapshotRow(long id, String versionNo) { }
    record SecondReviewState(boolean escalated, Long assigneeId) { }
    record ReviewPageRows(List<ReviewRow> items, long total, boolean hasMore) { }
    record ReportPageRows(List<ReportRow> items, long total, boolean hasMore) { }
    record AppealPageRows(List<AppealRow> items, long total, boolean hasMore) { }

    record IdempotencyClaim(
            long id, String requestHash, String responseRef, String responseType,
            String responsePayloadCiphertext, boolean replay) { }
}
