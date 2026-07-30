package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R08Store {
    boolean identityVerified(long userId);
    int integerConfig(String key);
    String textConfig(String key);
    long countOwnedInStatus(long userId, String status);
    boolean ownsReadyMedia(long userId, List<Long> mediaIds);
    long createProject(long userId, String title, String summary, String description,
                       String categoryCode, String regionCode, String conditions, String website,
                       String snapshotJson, List<Long> mediaIds, Instant now);
    Optional<ProjectRow> project(long contentId);
    Optional<ProjectRow> lockProject(long contentId);
    default Optional<ContentRow> content(long contentId) {
        return project(contentId).map(R08Store::asContent);
    }
    default Optional<ContentRow> lockContent(long contentId) {
        return lockProject(contentId).map(R08Store::asContent);
    }
    boolean updateProject(long contentId, long expectedVersion, String title, String summary,
                          String description, String categoryCode, String regionCode,
                          String conditions, String website, String snapshotJson,
                          List<Long> mediaIds, boolean replaceMedia, Instant now, long userId);
    void replaceContacts(long contentId, List<ContactWrite> contacts, Instant now);
    boolean favorite(long userId, long contentId, Instant now);
    void share(long userId, long contentId, String channel, Instant now);
    boolean activeUser(long userId);
    boolean blockedEitherWay(long userId, long peerId);
    long directConversationCountToday(long userId, Instant now);
    void lockDirectPair(long userId, long peerId);
    Optional<ConversationRow> directConversation(long userId, long peerId);
    ConversationRow createDirectConversation(long userId, long peerId, Instant now);
    Optional<PublisherRow> publisher(long userId);
    IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt);
    void complete(long claimId, String responseRef, String responseType, String ciphertext);
    void outbox(long actorId, String aggregateType, String eventType,
                String aggregateId, String status, Instant now);

    record ProjectRow(
            long id, long ownerId, String type, String status, long version,
            String title, String summary, String description, String categoryCode,
            String regionCode, String conditions, String website, String attributesJson) { }
    record ContentRow(long id, long ownerId, String type, String status, long version) { }

    private static ContentRow asContent(ProjectRow row) {
        return new ContentRow(row.id(), row.ownerId(), row.type(), row.status(), row.version());
    }
    record ContactWrite(String channel, String valueCipher, String displayMask, int sortOrder) { }
    record ConversationRow(long id, long peerId, Instant updatedAt, long version) { }
    record PublisherRow(
            long userId, String nickname, String avatar, String bio,
            boolean verified, String memberBadge) { }
    record IdempotencyClaim(
            long id, String requestHash, String responseRef, String responseType,
            String responsePayloadCiphertext, boolean replay) { }
}
