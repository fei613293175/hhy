package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R11Contracts.TeamLeaderAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class R11PostgresStore implements R11Store {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public R11PostgresStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<Long> lockOwnerTeamLeader(long userId) {
        jdbc.queryForObject("SELECT id FROM hhy.users WHERE id=? FOR UPDATE", Long.class, userId);
        return jdbc.query("""
                SELECT content.id FROM hhy.content_posts content
                JOIN hhy.team_leader_details detail ON detail.content_id=content.id
                WHERE content.owner_id=? AND content.type='TEAM_LEADER' AND content.status<>'DELETED'
                ORDER BY content.id LIMIT 1
                """, (rs, row) -> rs.getLong(1), userId).stream().findFirst();
    }

    @Override
    public long createTeamLeader(
            long userId, String title, String summary, String categoryCode, String region,
            TeamLeaderAttributes attributes, String snapshotJson, List<Long> mediaIds, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(
                  owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
                ) VALUES (?,'TEAM_LEADER',?,?,'DRAFT',NULL,0,0,?,?) RETURNING id
                """, Long.class, userId, title, summary, time(now), time(now));
        if (id == null) throw new IllegalStateException("R11 team leader id was not returned");
        jdbc.update("""
                INSERT INTO hhy.team_leader_details(
                  content_id,team_name,nickname,logo_media_id,region,personal_intro,team_intro,
                  size_range,skills,cooperation_types,cooperation_requirement,past_cases,
                  accept_private_chat,created_at,updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)
                """, id, attributes.teamName(), attributes.nickname(), attributes.logoMediaId(), region,
                attributes.personalIntro(), attributes.teamIntro(), attributes.sizeRange(), attributes.skills(),
                attributes.cooperationTypes(), attributes.cooperationRequirement(), json(attributes.pastCases()),
                attributes.acceptPrivateChat(), time(now), time(now));
        insertVersion(id, "0", snapshotJson, userId, now);
        jdbc.update("""
                INSERT INTO hhy.content_stats(
                  content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at
                ) VALUES (?,'0','0','0','0','0','0',?,?)
                """, id, time(now), time(now));
        replaceMedia(id, mediaIds, now);
        return id;
    }

    @Override
    public Optional<TeamLeaderRow> teamLeader(long contentId) {
        return teamLeader(contentId, false);
    }

    @Override
    public Optional<TeamLeaderRow> lockTeamLeader(long contentId) {
        return teamLeader(contentId, true);
    }

    private Optional<TeamLeaderRow> teamLeader(long contentId, boolean lock) {
        return jdbc.query("""
                SELECT content.id,content.owner_id,content.type,content.status,content.version,
                       content.title,content.summary,latest.snapshot_json->>'categoryCode',detail.region,
                       detail.team_name,detail.nickname,detail.logo_media_id,detail.personal_intro,
                       detail.team_intro,detail.size_range,detail.skills,detail.cooperation_types,
                       detail.cooperation_requirement,detail.past_cases,detail.accept_private_chat,
                       COALESCE(latest.snapshot_json,'{}'::jsonb)::text
                FROM hhy.content_posts content
                JOIN hhy.team_leader_details detail ON detail.content_id=content.id
                LEFT JOIN LATERAL (
                  SELECT version.snapshot_json FROM hhy.content_versions version
                  WHERE version.content_id=content.id
                  ORDER BY version.created_at DESC,version.id DESC LIMIT 1
                ) latest ON true
                WHERE content.id=?
                """ + (lock ? " FOR UPDATE OF content" : ""), this::row, contentId)
                .stream().findFirst();
    }

    @Override
    public boolean updateTeamLeader(
            long contentId, long expectedVersion, String title, String summary,
            String categoryCode, String region, TeamLeaderAttributes attributes,
            String snapshotJson, List<Long> mediaIds, boolean replaceMedia,
            Instant now, long userId) {
        int changed = jdbc.update("""
                UPDATE hhy.content_posts SET title=?,summary=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, title, summary, time(now), contentId, expectedVersion);
        if (changed != 1) return false;
        jdbc.update("""
                UPDATE hhy.team_leader_details
                SET team_name=?,nickname=?,logo_media_id=?,region=?,personal_intro=?,team_intro=?,
                    size_range=?,skills=?,cooperation_types=?,cooperation_requirement=?,
                    past_cases=CAST(? AS jsonb),accept_private_chat=?,updated_at=?
                WHERE content_id=?
                """, attributes.teamName(), attributes.nickname(), attributes.logoMediaId(), region,
                attributes.personalIntro(), attributes.teamIntro(), attributes.sizeRange(), attributes.skills(),
                attributes.cooperationTypes(), attributes.cooperationRequirement(), json(attributes.pastCases()),
                attributes.acceptPrivateChat(), time(now), contentId);
        insertVersion(contentId, Long.toString(expectedVersion + 1), snapshotJson, userId, now);
        if (replaceMedia) replaceMedia(contentId, mediaIds, now);
        return true;
    }

    private void insertVersion(long contentId, String version, String snapshot, long userId, Instant now) {
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                VALUES (?,?,CAST(? AS jsonb),?,?)
                """, contentId, version, snapshot, "user:" + userId, time(now));
    }

    private void replaceMedia(long contentId, List<Long> mediaIds, Instant now) {
        R12ContentBindings.replaceMedia(jdbc, contentId, mediaIds, now);
    }

    private TeamLeaderRow row(ResultSet rs, int row) throws SQLException {
        long logo = rs.getLong(12);
        Long logoMediaId = rs.wasNull() ? null : logo;
        Boolean privateChat = (Boolean) rs.getObject(20);
        TeamLeaderAttributes attributes = new TeamLeaderAttributes(
                rs.getString(10), rs.getString(11), logoMediaId, rs.getString(13), rs.getString(14),
                rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18),
                strings(rs.getString(19)), privateChat);
        return new TeamLeaderRow(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), attributes,
                rs.getString(21));
    }

    private List<String> strings(String value) {
        if (value == null) return null;
        try { return mapper.readValue(value, new TypeReference<List<String>>() { }); }
        catch (Exception failure) { throw new IllegalStateException("R11 stored past cases are invalid", failure); }
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R11 JSON serialization failed", failure); }
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
