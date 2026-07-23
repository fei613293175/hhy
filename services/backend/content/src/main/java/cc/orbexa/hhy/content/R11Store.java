package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R11Contracts.TeamLeaderAttributes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R11Store {
    Optional<Long> lockOwnerTeamLeader(long userId);

    long createTeamLeader(
            long userId, String title, String summary, String categoryCode, String region,
            TeamLeaderAttributes attributes, String snapshotJson, List<Long> mediaIds, Instant now);

    Optional<TeamLeaderRow> teamLeader(long contentId);
    Optional<TeamLeaderRow> lockTeamLeader(long contentId);

    boolean updateTeamLeader(
            long contentId, long expectedVersion, String title, String summary,
            String categoryCode, String region, TeamLeaderAttributes attributes,
            String snapshotJson, List<Long> mediaIds, boolean replaceMedia,
            Instant now, long userId);

    record TeamLeaderRow(
            long id, long ownerId, String type, String status, long version,
            String title, String summary, String categoryCode, String region,
            TeamLeaderAttributes attributes, String attributesJson) { }
}
