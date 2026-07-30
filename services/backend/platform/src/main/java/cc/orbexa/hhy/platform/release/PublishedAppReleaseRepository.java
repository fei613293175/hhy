package cc.orbexa.hhy.platform.release;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublishedAppReleaseRepository {
    private static final String FIND_LATEST = """
            SELECT r.version_code,
                   r.version_name,
                   r.update_type,
                   r.min_supported_version_code,
                   r.release_notes,
                   c.download_domain,
                   a.object_key,
                   a.sha256
              FROM hhy.app_release_records r
              JOIN hhy.app_release_channels c ON c.id = r.channel_id
              JOIN hhy.app_build_artifacts a ON a.id = r.artifact_id
             WHERE c.code = ?
               AND c.environment = ?
               AND c.status = 'ACTIVE'
               AND r.status = 'PUBLISHED'
               AND r.published_at <= ?
             ORDER BY r.published_at DESC, r.id DESC
             FETCH FIRST 1 ROW ONLY
            """;
    private static final String FIND_LATEST_PUBLIC = """
            SELECT r.version_code,
                   r.version_name,
                   r.update_type,
                   r.min_supported_version_code,
                   r.release_notes,
                   c.download_domain,
                   a.object_key,
                   a.sha256
              FROM hhy.app_release_records r
              JOIN hhy.app_release_channels c ON c.id = r.channel_id
              JOIN hhy.app_build_artifacts a ON a.id = r.artifact_id
             WHERE c.code = 'official'
               AND c.environment = 'PROD'
               AND c.status = 'ACTIVE'
               AND r.status = 'PUBLISHED'
               AND r.published_at <= ?
             ORDER BY r.published_at DESC, r.id DESC
             FETCH FIRST 1 ROW ONLY
            """;

    private final JdbcTemplate jdbcTemplate;

    public PublishedAppReleaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<PublishedAppRelease> findLatest(String channel, String environment, Instant publishedAtOrBefore) {
        return jdbcTemplate.query(
                        FIND_LATEST,
                        (resultSet, rowNumber) -> new PublishedAppRelease(
                                resultSet.getLong("version_code"),
                                resultSet.getString("version_name"),
                                resultSet.getString("update_type"),
                                resultSet.getLong("min_supported_version_code"),
                                resultSet.getString("release_notes"),
                                resultSet.getString("download_domain"),
                                resultSet.getString("object_key"),
                                resultSet.getString("sha256")),
                        channel,
                        environment,
                        OffsetDateTime.ofInstant(publishedAtOrBefore, ZoneOffset.UTC))
                .stream()
                .findFirst();
    }

    Optional<PublishedAppRelease> findLatestPublic(Instant publishedAtOrBefore) {
        return jdbcTemplate.query(
                        FIND_LATEST_PUBLIC,
                        (resultSet, rowNumber) -> new PublishedAppRelease(
                                resultSet.getLong("version_code"),
                                resultSet.getString("version_name"),
                                resultSet.getString("update_type"),
                                resultSet.getLong("min_supported_version_code"),
                                resultSet.getString("release_notes"),
                                resultSet.getString("download_domain"),
                                resultSet.getString("object_key"),
                                resultSet.getString("sha256")),
                        OffsetDateTime.ofInstant(publishedAtOrBefore, ZoneOffset.UTC))
                .stream()
                .findFirst();
    }
}
