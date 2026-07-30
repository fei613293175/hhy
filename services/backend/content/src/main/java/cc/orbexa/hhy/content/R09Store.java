package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R09Store {
    boolean ownsReadyNonApkMedia(long userId, List<Long> mediaIds);
    long createApp(long userId, String title, String summary, String appName,
                   String platform, String versionText, String downloadUrl, String website,
                   String snapshotJson, List<Long> mediaIds, Instant now);
    Optional<AppRow> app(long contentId);
    Optional<AppRow> lockApp(long contentId);
    boolean updateApp(long contentId, long expectedVersion, String title, String summary,
                      String appName, String platform, String versionText,
                      String downloadUrl, String website, String snapshotJson,
                      List<Long> mediaIds, boolean replaceMedia, Instant now, long userId);

    record AppRow(
            long id, long ownerId, String type, String status, long version,
            String title, String summary, String appName, String platform,
            String versionText, String downloadUrl, String website, String attributesJson) { }
}
