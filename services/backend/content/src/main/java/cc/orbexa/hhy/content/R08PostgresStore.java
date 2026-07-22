package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R08PostgresStore implements R08Store {
    private final JdbcTemplate jdbc;

    public R08PostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean identityVerified(long userId) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM hhy.identity_profiles
                              WHERE user_id=? AND status='VERIFIED')
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public int integerConfig(String key) {
        Integer value = jdbc.queryForObject("""
                SELECT (value_json #>> '{}')::integer FROM hhy.system_configs
                WHERE key=? AND scope='GLOBAL'
                """, Integer.class, key);
        if (value == null || value < 0) throw new IllegalStateException("Invalid R08 configuration: " + key);
        return value;
    }

    @Override
    public String textConfig(String key) {
        String value = jdbc.queryForObject("""
                SELECT value_json #>> '{}' FROM hhy.system_configs
                WHERE key=? AND scope='GLOBAL'
                """, String.class, key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing R08 configuration: " + key);
        return value;
    }

    @Override
    public long countOwnedInStatus(long userId, String status) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.content_posts WHERE owner_id=? AND status=?",
                Long.class, userId, status);
        return count == null ? 0 : count;
    }

    @Override
    public boolean ownsReadyMedia(long userId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return true;
        String placeholders = String.join(",", java.util.Collections.nCopies(mediaIds.size(), "?"));
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(userId); args.addAll(mediaIds);
        Long count = jdbc.queryForObject("SELECT count(*) FROM hhy.media_objects WHERE owner_id=?"
                + " AND status='READY' AND deleted_at IS NULL AND id IN (" + placeholders + ")",
                Long.class, args.toArray());
        return count != null && count == mediaIds.size();
    }

    @Override
    public long createProject(
            long userId, String title, String summary, String description,
            String categoryCode, String regionCode, String conditions, String website,
            String snapshotJson, List<Long> mediaIds, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at)
                VALUES (?,'PROJECT',?,?,'DRAFT',NULL,0,0,?,?) RETURNING id
                """, Long.class, userId, title, summary, time(now), time(now));
        if (id == null) throw new IllegalStateException("R08 project id was not returned");
        jdbc.update("""
                INSERT INTO hhy.project_details(content_id,cooperation,conditions,region,website,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?)
                """, id, description, conditions, regionCode, website, time(now), time(now));
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
    public Optional<ProjectRow> project(long contentId) { return project(contentId, false); }

    @Override
    public Optional<ProjectRow> lockProject(long contentId) { return project(contentId, true); }

    private Optional<ProjectRow> project(long contentId, boolean lock) {
        return jdbc.query("""
                SELECT content.id,content.owner_id,content.type,content.status,content.version,
                       content.title,content.summary,detail.cooperation,latest.snapshot_json->>'categoryCode',
                       detail.region,detail.conditions,detail.website,
                       COALESCE(latest.snapshot_json,'{}'::jsonb)::text
                FROM hhy.content_posts content
                JOIN hhy.project_details detail ON detail.content_id=content.id
                LEFT JOIN LATERAL (
                  SELECT version.snapshot_json FROM hhy.content_versions version
                  WHERE version.content_id=content.id ORDER BY version.created_at DESC,version.id DESC LIMIT 1
                ) latest ON true
                WHERE content.id=?
                """ + (lock ? " FOR UPDATE OF content" : ""), this::projectRow, contentId)
                .stream().findFirst();
    }

    @Override
    public boolean updateProject(
            long contentId, long expectedVersion, String title, String summary,
            String description, String categoryCode, String regionCode,
            String conditions, String website, String snapshotJson,
            List<Long> mediaIds, boolean replaceMedia, Instant now, long userId) {
        int changed = jdbc.update("""
                UPDATE hhy.content_posts SET title=?,summary=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, title, summary, time(now), contentId, expectedVersion);
        if (changed != 1) return false;
        jdbc.update("""
                UPDATE hhy.project_details
                SET cooperation=?,conditions=?,region=?,website=?,updated_at=? WHERE content_id=?
                """, description, conditions, regionCode, website, time(now), contentId);
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

    @Override
    public void replaceContacts(long contentId, List<ContactWrite> contacts, Instant now) {
        jdbc.update("DELETE FROM hhy.content_contacts WHERE content_id=?", contentId);
        for (ContactWrite contact : contacts) {
            jdbc.update("""
                    INSERT INTO hhy.content_contacts(content_id,channel,value_cipher,display_mask,sort_order,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, contentId, contact.channel(), contact.valueCipher(), contact.displayMask(),
                    contact.sortOrder(), time(now), time(now));
        }
    }

    @Override
    public boolean favorite(long userId, long contentId, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO hhy.content_favorites(user_id,content_id,created_at,updated_at)
                VALUES (?,?,?,?) ON CONFLICT(user_id,content_id) DO NOTHING
                """, userId, contentId, time(now), time(now));
        if (inserted == 1) {
            jdbc.update("""
                    UPDATE hhy.content_stats SET favorites=(COALESCE(favorites,'0')::bigint+1)::text,updated_at=?
                    WHERE content_id=?
                    """, time(now), contentId);
        }
        return inserted == 1;
    }

    @Override
    public void share(long userId, long contentId, String channel, Instant now) {
        jdbc.update("""
                INSERT INTO hhy.content_view_logs(user_id,content_id,traffic_type,duration,source,created_at)
                VALUES (?,?,'SHARE',0,?,?)
                """, userId, contentId, channel, time(now));
    }

    @Override
    public boolean activeUser(long userId) {
        Boolean value = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM hhy.users WHERE id=? AND status='ACTIVE')", Boolean.class, userId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public boolean blockedEitherWay(long userId, long peerId) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM hhy.user_blocks
                  WHERE (user_id=? AND blocked_user_id=?) OR (user_id=? AND blocked_user_id=?))
                """, Boolean.class, userId, peerId, peerId, userId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public long directConversationCountToday(long userId, Instant now) {
        Long value = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.conversations conversation
                JOIN hhy.conversation_members member ON member.conversation_id=conversation.id
                WHERE member.user_id=? AND conversation.type='DIRECT'
                  AND conversation.created_at>=date_trunc('day',?::timestamptz)
                  AND conversation.created_at<date_trunc('day',?::timestamptz)+interval '1 day'
                """, Long.class, userId, time(now), time(now));
        return value == null ? 0 : value;
    }

    @Override
    public void lockDirectPair(long userId, long peerId) {
        long low = Math.min(userId, peerId);
        long high = Math.max(userId, peerId);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                low + ":" + high);
    }

    @Override
    public Optional<ConversationRow> directConversation(long userId, long peerId) {
        return jdbc.query("""
                SELECT conversation.id,conversation.updated_at,0::bigint AS version
                FROM hhy.conversations conversation
                WHERE conversation.type='DIRECT'
                  AND EXISTS(SELECT 1 FROM hhy.conversation_members member
                             WHERE member.conversation_id=conversation.id AND member.user_id=?)
                  AND EXISTS(SELECT 1 FROM hhy.conversation_members member
                             WHERE member.conversation_id=conversation.id AND member.user_id=?)
                  AND (SELECT count(*) FROM hhy.conversation_members member
                       WHERE member.conversation_id=conversation.id)=2
                ORDER BY conversation.id LIMIT 1
                """, (rs, row) -> conversationRow(rs, peerId), userId, peerId).stream().findFirst();
    }

    @Override
    public ConversationRow createDirectConversation(long userId, long peerId, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.conversations(type,created_at,updated_at)
                VALUES ('DIRECT',?,?) RETURNING id
                """, Long.class, time(now), time(now));
        if (id == null) throw new IllegalStateException("R08 conversation id was not returned");
        jdbc.update("""
                INSERT INTO hhy.conversation_members(conversation_id,user_id,unread_count,created_at,updated_at)
                VALUES (?,?,0,?,?),(?,?,0,?,?)
                """, id, userId, time(now), time(now), id, peerId, time(now), time(now));
        return new ConversationRow(id, peerId, now, 0);
    }

    @Override
    public Optional<PublisherRow> publisher(long userId) {
        return jdbc.query("""
                SELECT user_account.id,profile.nickname,profile.avatar,profile.bio,
                       EXISTS(SELECT 1 FROM hhy.identity_profiles identity
                              WHERE identity.user_id=user_account.id AND identity.status='VERIFIED'),
                       (SELECT plan.public_badge FROM hhy.user_memberships membership
                        JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                        WHERE membership.user_id=user_account.id AND membership.status='ACTIVE'
                        ORDER BY membership.id DESC LIMIT 1)
                FROM hhy.users user_account LEFT JOIN hhy.user_profiles profile ON profile.user_id=user_account.id
                WHERE user_account.id=? AND user_account.status='ACTIVE'
                """, (rs, row) -> new PublisherRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), rs.getString(6)), userId).stream().findFirst();
    }

    @Override
    public IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt) {
        jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND idem_key=? AND expires_at<=clock_timestamp()",
                scope, key);
        int inserted = jdbc.update("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?) ON CONFLICT(scope,idem_key) DO NOTHING
                """, scope, key, requestHash, time(expiresAt));
        IdempotencyClaim result = jdbc.queryForObject("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, row) -> new IdempotencyClaim(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), inserted == 0), scope, key);
        if (result == null) throw new IllegalStateException("R08 idempotency claim disappeared");
        return result;
    }

    @Override
    public void complete(long claimId, String responseRef, String responseType, String ciphertext) {
        if (jdbc.update("""
                UPDATE hhy.idempotency_records SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL AND response_type IS NULL AND response_payload_ciphertext IS NULL
                """, responseRef, responseType, ciphertext, claimId) != 1) {
            throw new IllegalStateException("R08 idempotency response already completed");
        }
    }

    @Override
    public void outbox(long actorId, String aggregateType, String eventType,
                       String aggregateId, String status, Instant now) {
        String payload = "{\"actorId\":" + actorId + ",\"resourceId\":\"" + aggregateId
                + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + now + "\"}";
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,?,?,?,1,'{"source":"r08-api"}'::jsonb,CAST(? AS jsonb))
                """, aggregateId, aggregateType, UUID.randomUUID().toString(), eventType, payload);
    }

    private ProjectRow projectRow(ResultSet rs, int row) throws SQLException {
        return new ProjectRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13));
    }

    private static ConversationRow conversationRow(ResultSet rs, long peerId) throws SQLException {
        return new ConversationRow(rs.getLong(1), peerId, instant(rs.getObject(2, OffsetDateTime.class)), rs.getLong(3));
    }
    private static OffsetDateTime time(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
}
