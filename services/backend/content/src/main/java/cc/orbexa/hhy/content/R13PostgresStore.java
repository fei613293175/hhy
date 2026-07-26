package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R13PostgresStore implements R13Store {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public R13PostgresStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public ActivityPage favorites(ActivityQuery query) {
        return activityPage(query, false);
    }

    @Override
    public ActivityPage history(ActivityQuery query) {
        return activityPage(query, true);
    }

    @Override
    public boolean unfavorite(long userId, long contentId, Instant now) {
        int removed = jdbc.update(
                "DELETE FROM hhy.content_favorites WHERE user_id=? AND content_id=?",
                userId, contentId);
        if (removed == 1) {
            jdbc.update("""
                    UPDATE hhy.content_stats
                    SET favorites=GREATEST(COALESCE(NULLIF(favorites,''),'0')::bigint-1,0)::text,
                        updated_at=?
                    WHERE content_id=?
                    """, time(now), contentId);
        }
        return removed == 1;
    }

    @Override
    public long invalidFeedback(
            long userId, long contentId, String reasonCode, String description, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.content_reports(
                  reporter_id,content_id,type,description,status,created_at,updated_at)
                VALUES (?,?,?,?,'PENDING',?,?) RETURNING id
                """, Long.class, userId, contentId, reasonCode, description, time(now), time(now));
        if (id == null) throw new IllegalStateException("R13 feedback id was not returned");
        return id;
    }

    private ActivityPage activityPage(ActivityQuery query, boolean history) {
        String prefix = history ? """
                WITH activity AS (
                  SELECT id,content_id,created_at FROM (
                    SELECT log.id,log.content_id,log.created_at,
                           row_number() OVER (
                             PARTITION BY log.content_id ORDER BY log.created_at DESC,log.id DESC
                           ) AS activity_rank
                    FROM hhy.content_view_logs log
                    WHERE log.user_id=:userId AND log.traffic_type<>'SHARE'
                  ) ranked WHERE activity_rank=1
                )
                """ : "";
        String from = history
                ? " FROM activity JOIN hhy.content_posts content ON content.id=activity.content_id"
                : " FROM hhy.content_favorites activity JOIN hhy.content_posts content ON content.id=activity.content_id";
        StringBuilder filter = new StringBuilder(" WHERE content.status='ONLINE'");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", query.userId());
        if (!history) filter.append(" AND activity.user_id=:userId");
        if (query.status() != null) {
            filter.append(" AND content.status=:status");
            parameters.addValue("status", query.status());
        }
        if (query.keyword() != null) {
            filter.append(" AND (content.title ILIKE :keyword OR content.summary ILIKE :keyword)");
            parameters.addValue("keyword", "%" + query.keyword() + "%");
        }
        Long total = named.queryForObject(prefix + "SELECT count(*)" + from + filter,
                parameters, Long.class);
        if (query.cursor() != null) {
            String comparison = query.direction() == SortDirection.ASC ? ">" : "<";
            filter.append(" AND (activity.created_at,activity.id) ")
                    .append(comparison).append(" (:cursorAt,:cursorId)");
            parameters.addValue("cursorAt", time(query.cursor().occurredAt()))
                    .addValue("cursorId", query.cursor().activityId());
        }
        parameters.addValue("limit", query.pageSize() + 1)
                .addValue("offset", query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        String direction = query.direction() == SortDirection.ASC ? "ASC" : "DESC";
        List<ActivityRow> rows = named.query(prefix
                + "SELECT activity.id,activity.content_id,activity.created_at"
                + from + filter
                + " ORDER BY activity.created_at " + direction + ",activity.id " + direction
                + " LIMIT :limit OFFSET :offset", parameters, this::activityRow);
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new ActivityPage(rows, total == null ? 0 : total, more);
    }

    private ActivityRow activityRow(ResultSet rs, int row) throws SQLException {
        return new ActivityRow(rs.getLong(1), rs.getLong(2),
                rs.getObject(3, OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
