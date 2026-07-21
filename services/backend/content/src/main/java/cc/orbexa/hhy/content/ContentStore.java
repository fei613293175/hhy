package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContentStore {
    PageRows page(ContentQuery query);
    Optional<ContentRow> detail(long id);
    List<ContactRow> contacts(long contentId);
    List<DictionaryRow> dictionaries(int page, int pageSize, String keyword, Boolean enabled, String sort);
    long dictionaryCount(String keyword, Boolean enabled);
    IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt);
    void complete(long claimId, String responseRef);
    Optional<LockedContent> lock(long id);
    boolean transition(long id, long expectedVersion, String toStatus, Instant now);
    boolean updateAttributes(long id, long expectedVersion, String patchJson, String actor, Instant now);
    DictionaryRow putDictionary(String code, long expectedVersion, String itemsJson, Instant now);
    void statusLog(long id, String from, String to, String reason, String actor);
    void audit(long adminId, String action, long resourceId, String beforeJson, String afterJson, String ip);
    void outbox(long actorId, String eventType, String aggregateId, String status, Instant occurredAt);
    List<HomeRow> homeModules();

    record ContentQuery(
            int page, int pageSize, Long beforeId, String status, String keyword,
            String orderBy, String contentType, String categoryCode, String regionCode,
            Long publisherId) { }
    record PageRows(List<ContentRow> items, long total, boolean hasMore) { }
    record ContentRow(
            long id, long ownerId, String type, String title, String summary,
            String status, String reviewStatus, long version, Instant createdAt, Instant updatedAt,
            String nickname, String avatar, String bio, String attributesJson,
            String views, String favorites, String chats, String contacts) { }
    record ContactRow(String channel, String displayMask, int sortOrder) { }
    record DictionaryRow(long id, String code, String title, boolean enabled, long version, String itemsJson) { }
    record IdempotencyClaim(long id, String requestHash, String responseRef, boolean replay) { }
    record LockedContent(long id, String status, long version) { }
    record HomeRow(long id, String code, String title, String sourceType, String configJson) { }
}
