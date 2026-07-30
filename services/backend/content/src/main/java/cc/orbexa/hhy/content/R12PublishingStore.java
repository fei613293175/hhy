package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R12PublishingStore {
    PageIds ownerPage(OwnerQuery query);
    Optional<OwnedContent> lockOwned(long contentId);
    void lockOwnerQuota(long ownerId);
    String publishingTier(long ownerId);
    boolean transitionOwned(
            long contentId, long ownerId, long expectedVersion, String fromStatus,
            String toStatus, String reviewStatus, Instant now);
    void submissionSnapshot(long contentId, long ownerId, Instant now);
    void statusLog(
            long contentId, String fromStatus, String toStatus, String reason,
            long ownerId, long transitionVersion);
    boolean approvedSnapshotIsCurrent(long contentId);
    long latestCorrectionVersion(long contentId);
    long submissionsSince(long ownerId, Instant since);
    ReviewPage reviews(long contentId, ReviewQuery query);
    long copySkeleton(long sourceContentId, long ownerId, Instant now);
    List<ContactEnvelope> activeContacts(long contentId);
    void copyContacts(long contentId, List<ContactEnvelope> contacts, Instant now);
    void outbox(
            long actorId, String eventType, long contentId, String fromStatus, String toStatus,
            long version, String reason, CommandAudit audit, Instant occurredAt);

    record OwnerQuery(
            long ownerId, int page, int pageSize, CursorKey cursor, String status, String keyword,
            String sort, String contentType, String categoryCode, String regionCode,
            boolean draftsOnly) { }
    record PageItem(long id, Instant sortValue) { }
    record PageIds(List<PageItem> items, long total, boolean hasMore) {
        public PageIds { items = List.copyOf(items); }
    }
    record OwnedContent(long id, long ownerId, String type, String status, long version) { }
    record ReviewQuery(
            int page, int pageSize, CursorKey cursor, String status, String keyword, String sort) { }
    record ReviewFact(
            long id, String decision, String reason, Long adminId,
            String versionNo, Instant createdAt) { }
    record ReviewPage(List<ReviewFact> items, long total, boolean hasMore) {
        public ReviewPage { items = List.copyOf(items); }
    }
    record ContactEnvelope(String channel, String valueCipher, String displayMask, int sortOrder) { }
    record CursorKey(long id, Instant sortValue) { }
    record CommandAudit(String requestId, String clientIp, String device) { }
}
