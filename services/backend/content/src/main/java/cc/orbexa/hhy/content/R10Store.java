package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R10Store {
    long createGroup(
            long userId, String title, String summary,
            String platform, String sizeRange, String joinRequirement,
            Long qrMediaId, String groupLink, String groupNo,
            String snapshotJson, List<Long> mediaIds, Instant now);

    Optional<GroupRow> group(long contentId);
    Optional<GroupRow> lockGroup(long contentId);

    boolean updateGroup(
            long contentId, long expectedVersion, String title, String summary,
            String platform, String sizeRange, String joinRequirement,
            Long qrMediaId, String groupLink, String groupNo,
            String snapshotJson, List<Long> mediaIds, boolean replaceMedia,
            Instant now, long userId);

    record GroupRow(
            long id, long ownerId, String type, String status, long version,
            String title, String summary, String platform, String sizeRange,
            String joinRequirement, Long qrMediaId, String groupLink,
            String groupNo, String attributesJson) { }
}
