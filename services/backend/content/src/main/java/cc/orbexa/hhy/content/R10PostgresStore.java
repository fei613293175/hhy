package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R10PostgresStore implements R10Store {
    private final JdbcTemplate jdbc;

    public R10PostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public long createGroup(
            long userId, String title, String summary,
            String platform, String sizeRange, String joinRequirement,
            Long qrMediaId, String groupLink, String groupNo,
            String snapshotJson, List<Long> mediaIds, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(
                  owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
                ) VALUES (?,'GROUP',?,?,'DRAFT',NULL,0,0,?,?) RETURNING id
                """, Long.class, userId, title, summary, time(now), time(now));
        if (id == null) throw new IllegalStateException("R10 group id was not returned");
        jdbc.update("""
                INSERT INTO hhy.group_details(
                  content_id,platform,size_range,join_requirement,qr_media_id,group_link,group_no,created_at,updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """, id, platform, sizeRange, joinRequirement, qrMediaId, groupLink, groupNo,
                time(now), time(now));
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                VALUES (?,'0',CAST(? AS jsonb),?,?)
                """, id, snapshotJson, "user:" + userId, time(now));
        jdbc.update("""
                INSERT INTO hhy.content_stats(
                  content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at
                ) VALUES (?,'0','0','0','0','0','0',?,?)
                """, id, time(now), time(now));
        replaceMedia(id, mediaIds, now);
        return id;
    }

    @Override
    public Optional<GroupRow> group(long contentId) { return group(contentId, false); }

    @Override
    public Optional<GroupRow> lockGroup(long contentId) { return group(contentId, true); }

    private Optional<GroupRow> group(long contentId, boolean lock) {
        return jdbc.query("""
                SELECT content.id,content.owner_id,content.type,content.status,content.version,
                       content.title,content.summary,detail.platform,detail.size_range,
                       detail.join_requirement,detail.qr_media_id,detail.group_link,detail.group_no,
                       COALESCE(latest.snapshot_json,'{}'::jsonb)::text
                FROM hhy.content_posts content
                JOIN hhy.group_details detail ON detail.content_id=content.id
                LEFT JOIN LATERAL (
                  SELECT version.snapshot_json FROM hhy.content_versions version
                  WHERE version.content_id=content.id
                  ORDER BY version.created_at DESC,version.id DESC LIMIT 1
                ) latest ON true
                WHERE content.id=?
                """ + (lock ? " FOR UPDATE OF content" : ""), this::groupRow, contentId)
                .stream().findFirst();
    }

    @Override
    public boolean updateGroup(
            long contentId, long expectedVersion, String title, String summary,
            String platform, String sizeRange, String joinRequirement,
            Long qrMediaId, String groupLink, String groupNo,
            String snapshotJson, List<Long> mediaIds, boolean replaceMedia,
            Instant now, long userId) {
        int changed = jdbc.update("""
                UPDATE hhy.content_posts SET title=?,summary=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, title, summary, time(now), contentId, expectedVersion);
        if (changed != 1) return false;
        jdbc.update("""
                UPDATE hhy.group_details
                SET platform=?,size_range=?,join_requirement=?,qr_media_id=?,group_link=?,group_no=?,updated_at=?
                WHERE content_id=?
                """, platform, sizeRange, joinRequirement, qrMediaId, groupLink, groupNo,
                time(now), contentId);
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                VALUES (?,?,CAST(? AS jsonb),?,?)
                """, contentId, Long.toString(expectedVersion + 1), snapshotJson,
                "user:" + userId, time(now));
        if (replaceMedia) replaceMedia(contentId, mediaIds, now);
        return true;
    }

    private void replaceMedia(long contentId, List<Long> mediaIds, Instant now) {
        jdbc.update("DELETE FROM hhy.content_media WHERE content_id=?", contentId);
        for (int index = 0; index < mediaIds.size(); index++) {
            jdbc.update("""
                    INSERT INTO hhy.content_media(content_id,media_id,media_type,sort_order,created_at,updated_at)
                    SELECT ?,id,mime,?,?,? FROM hhy.media_objects WHERE id=?
                    """, contentId, index, time(now), time(now), mediaIds.get(index));
        }
    }

    private GroupRow groupRow(ResultSet rs, int row) throws SQLException {
        long qr = rs.getLong(11);
        Long qrMediaId = rs.wasNull() ? null : qr;
        return new GroupRow(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), qrMediaId, rs.getString(12),
                rs.getString(13), rs.getString(14));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
