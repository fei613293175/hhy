package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R07Store {
    SearchRows search(SearchQuery query);
    void recordSearch(long userId, String keyword, Instant now);
    TermRows hotTerms(TermQuery query, Instant now);
    TermRows history(long userId, TermQuery query);
    Optional<PublisherRow> publisher(long publisherId, long viewerId, Instant now);
    Optional<ContactRow> contact(long contentId, String channel);
    int clearHistory(long userId);
    IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt);
    void complete(long claimId, String responseRef, String responseType, String ciphertext);
    void abandon(long claimId);
    void contactAudit(long userId, long contentId, String channel, String action, Instant now);
    void contactRejected(long userId, long contentId, String channel, String action, Instant now);
    void outbox(long userId, String aggregateType, String eventType,
                String aggregateId, String status, Instant now);

    record SearchQuery(
            long viewerId, String keyword, String contentType, String categoryCode, String regionCode,
            int page, int pageSize, Long beforeId, String orderBy) { }
    record SearchRows(List<SearchRow> items, long total, boolean hasMore) { }
    record SearchRow(
            long id, long ownerId, String type, String title, String summary,
            Instant createdAt, String nickname, String avatar, String bio,
            boolean verified, String memberBadge, boolean followed, double score) { }
    record TermQuery(int page, int pageSize, Long beforeId, String keyword, String orderBy) { }
    record TermRows(List<TermRow> items, long total, boolean hasMore) { }
    record TermRow(long id, String keyword, Instant createdAt) { }
    record PublisherRow(
            long userId, String nickname, String avatar, String bio,
            boolean verified, String memberBadge, boolean followed) { }
    record ContactRow(long contentId, String channel, String valueCipher) { }
    record IdempotencyClaim(
            long id, String requestHash, String responseRef, String responseType,
            String responsePayloadCiphertext, boolean replay) { }
}
