package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R09PostgresStore implements R09Store {
    private final JdbcTemplate jdbc;

    public R09PostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean ownsReadyNonApkMedia(long userId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return true;
        String placeholders = String.join(",", Collections.nCopies(mediaIds.size(), "?"));
        ArrayList<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(mediaIds);
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.media_objects
                WHERE owner_id=? AND status='READY' AND deleted_at IS NULL
                  AND lower(btrim(COALESCE(mime,''))) <> 'application/vnd.android.package-archive'
                  AND COALESCE(object_key,'') !~* '\\.apk($|[?#])'
                  AND id IN (
                """ + placeholders + ")", Long.class, args.toArray());
        return count != null && count == mediaIds.size();
    }

    @Override
    public long createApp(
            long userId, String title, String summary, String appName,
            String platform, String versionText, String downloadUrl, String website,
            String snapshotJson, List<Long> mediaIds, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at)
                VALUES (?,'APP',?,?,'DRAFT',NULL,0,0,?,?) RETURNING id
                """, Long.class, userId, title, summary, time(now), time(now));
        if (id == null) throw new IllegalStateException("R09 App id was not returned");
        jdbc.update("""
                INSERT INTO hhy.app_details(content_id,app_name,platform,version_text,download_url,website,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, appName, platform, versionText, downloadUrl, website, time(now), time(now));
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                VALUES (?,'0',CAST(? AS jsonb),?,?)
                """, id, snapshotJson, "user:" + userId, time(now));
        jdbc.update("""
                INSERT INTO hhy.content_stats(content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at)
                VALUES (?,'0','0','0','0','0','0',?,?)
                """, id, time(now), time(now));
        replaceMedia(id, mediaIds, now);
        return id;
    }

    @Override
    public Optional<AppRow> app(long contentId) { return app(contentId, false); }

    @Override
    public Optional<AppRow> lockApp(long contentId) { return app(contentId, true); }

    private Optional<AppRow> app(long contentId, boolean lock) {
        return jdbc.query("""
                SELECT content.id,content.owner_id,content.type,content.status,content.version,
                       content.title,content.summary,detail.app_name,detail.platform,
                       detail.version_text,detail.download_url,detail.website,
                       COALESCE(latest.snapshot_json,'{}'::jsonb)::text
                FROM hhy.content_posts content
                JOIN hhy.app_details detail ON detail.content_id=content.id
                LEFT JOIN LATERAL (
                  SELECT version.snapshot_json FROM hhy.content_versions version
                  WHERE version.content_id=content.id ORDER BY version.created_at DESC,version.id DESC LIMIT 1
                ) latest ON true
                WHERE content.id=?
                """ + (lock ? " FOR UPDATE OF content" : ""), this::appRow, contentId)
                .stream().findFirst();
    }

    @Override
    public boolean updateApp(
            long contentId, long expectedVersion, String title, String summary,
            String appName, String platform, String versionText, String downloadUrl, String website,
            String snapshotJson, List<Long> mediaIds, boolean replaceMedia,
            Instant now, long userId) {
        int changed = jdbc.update("""
                UPDATE hhy.content_posts SET title=?,summary=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, title, summary, time(now), contentId, expectedVersion);
        if (changed != 1) return false;
        jdbc.update("""
                UPDATE hhy.app_details
                SET app_name=?,platform=?,version_text=?,download_url=?,website=?,updated_at=?
                WHERE content_id=?
                """, appName, platform, versionText, downloadUrl, website, time(now), contentId);
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

    private AppRow appRow(ResultSet rs, int row) throws SQLException {
        return new AppRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13));
    }

    private static OffsetDateTime time(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
}
