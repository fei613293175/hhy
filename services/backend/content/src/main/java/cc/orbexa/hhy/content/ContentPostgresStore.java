package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContentPostgresStore implements ContentStore {
    private static final String SELECT_CONTENT = """
            SELECT p.id,p.owner_id,p.type,p.title,p.summary,p.status,p.review_status,p.version,
                   p.created_at,p.updated_at,profile.nickname,profile.avatar,profile.bio,
                   COALESCE(snapshot.snapshot_json,'{}'::jsonb)::text AS attributes_json,
                   stats.organic_views,stats.favorites,stats.chats,stats.contacts
            FROM hhy.content_posts p
            LEFT JOIN hhy.user_profiles profile ON profile.user_id=p.owner_id
            LEFT JOIN LATERAL (
              SELECT v.snapshot_json FROM hhy.content_versions v
              WHERE v.content_id=p.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1
            ) snapshot ON true
            LEFT JOIN LATERAL (
              SELECT s.organic_views,s.favorites,s.chats,s.contacts FROM hhy.content_stats s
              WHERE s.content_id=p.id ORDER BY s.updated_at DESC,s.id DESC LIMIT 1
            ) stats ON true
            """;
    private final JdbcTemplate jdbc;

    public ContentPostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageRows page(ContentQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        append(where, args, "p.status=?", query.status());
        if (query.keyword() != null) {
            where.append(" AND (p.title ILIKE ? OR p.summary ILIKE ?)");
            String value = "%" + query.keyword() + "%";
            args.add(value); args.add(value);
        }
        append(where, args, "p.type=?", query.contentType());
        append(where, args, "snapshot.snapshot_json->>'categoryCode'=?", query.categoryCode());
        append(where, args, "snapshot.snapshot_json->>'regionCode'=?", query.regionCode());
        append(where, args, "p.owner_id=?", query.publisherId());
        if (query.beforeId() != null) { where.append(" AND p.id<?"); args.add(query.beforeId()); }
        Long total = jdbc.queryForObject("SELECT count(*) FROM hhy.content_posts p "
                + "LEFT JOIN LATERAL (SELECT v.snapshot_json FROM hhy.content_versions v "
                + "WHERE v.content_id=p.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1) snapshot ON true"
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.pageSize() + 1);
        pageArgs.add(query.beforeId() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<ContentRow> rows = jdbc.query(SELECT_CONTENT + where + " ORDER BY " + query.orderBy()
                + " LIMIT ? OFFSET ?", this::content, pageArgs.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new PageRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    @Override
    public Optional<ContentRow> detail(long id) {
        return jdbc.query(SELECT_CONTENT + " WHERE p.id=?", this::content, id).stream().findFirst();
    }

    @Override
    public List<ContactRow> contacts(long contentId) {
        return jdbc.query("""
                SELECT channel,display_mask,sort_order FROM hhy.content_contacts
                WHERE content_id=? ORDER BY sort_order,id
                """, (rs, row) -> new ContactRow(rs.getString(1), rs.getString(2), rs.getInt(3)), contentId);
    }

    @Override
    public List<DictionaryRow> dictionaries(
            int page, int pageSize, String keyword, Boolean enabled, String sort) {
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            filter.append(" AND (code ILIKE ? OR title ILIKE ?)");
            String value = "%" + keyword + "%";
            args.add(value);
            args.add(value);
        }
        if (enabled != null) {
            filter.append(" AND enabled=?");
            args.add(enabled);
        }
        args.add(pageSize); args.add((page - 1) * pageSize);
        return jdbc.query("""
                SELECT id,code,title,enabled,COALESCE((config_json->>'version')::bigint,0),
                       COALESCE(config_json->'items','{}'::jsonb)::text
                FROM hhy.home_modules WHERE source_type='DICTIONARY'
                """ + filter + " ORDER BY " + sort + " LIMIT ? OFFSET ?", this::dictionary, args.toArray());
    }

    @Override
    public long dictionaryCount(String keyword, Boolean enabled) {
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            filter.append(" AND (code ILIKE ? OR title ILIKE ?)");
            String value = "%" + keyword + "%";
            args.add(value);
            args.add(value);
        }
        if (enabled != null) {
            filter.append(" AND enabled=?");
            args.add(enabled);
        }
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.home_modules WHERE source_type='DICTIONARY'" + filter,
                Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt) {
        jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND idem_key=? AND expires_at<=clock_timestamp()",
                scope, key);
        int inserted = jdbc.update("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?) ON CONFLICT(scope,idem_key) DO NOTHING
                """, scope, key, requestHash, time(expiresAt));
        IdempotencyClaim claim = jdbc.queryForObject("""
                SELECT id,request_hash,response_ref FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, row) -> new IdempotencyClaim(
                        rs.getLong(1), rs.getString(2), rs.getString(3), inserted == 0), scope, key);
        if (claim == null) throw new IllegalStateException("Content idempotency claim disappeared");
        return claim;
    }

    @Override
    public void complete(long claimId, String responseRef) {
        if (jdbc.update("UPDATE hhy.idempotency_records SET response_ref=? WHERE id=? AND response_ref IS NULL",
                responseRef, claimId) != 1) throw new IllegalStateException("Content idempotency result already completed");
    }

    @Override
    public Optional<LockedContent> lock(long id) {
        return jdbc.query("SELECT id,status,version FROM hhy.content_posts WHERE id=? FOR UPDATE",
                (rs, row) -> new LockedContent(rs.getLong(1), rs.getString(2), rs.getLong(3)), id)
                .stream().findFirst();
    }

    @Override
    public boolean transition(long id, long expectedVersion, String toStatus, Instant now) {
        return jdbc.update("""
                UPDATE hhy.content_posts SET status=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, toStatus, time(now), id, expectedVersion) == 1;
    }

    @Override
    public boolean updateAttributes(long id, long expectedVersion, String patchJson, String actor, Instant now) {
        int changed = jdbc.update("""
                UPDATE hhy.content_posts SET version=version+1,updated_at=? WHERE id=? AND version=?
                """, time(now), id, expectedVersion);
        if (changed != 1) return false;
        jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                SELECT p.id,p.version::text,
                  COALESCE((SELECT v.snapshot_json FROM hhy.content_versions v
                            WHERE v.content_id=p.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1),'{}'::jsonb)
                    || CAST(? AS jsonb),?,?
                FROM hhy.content_posts p WHERE p.id=?
                """, patchJson, actor, time(now), id);
        return true;
    }

    @Override
    public DictionaryRow putDictionary(String code, long expectedVersion, String itemsJson, Instant now) {
        List<DictionaryRow> existing = jdbc.query("""
                SELECT id,code,title,enabled,COALESCE((config_json->>'version')::bigint,0),
                       COALESCE(config_json->'items','{}'::jsonb)::text
                FROM hhy.home_modules WHERE code=? AND source_type='DICTIONARY' FOR UPDATE
                """, this::dictionary, code);
        if (existing.isEmpty()) {
            if (expectedVersion != 0) return null;
            return jdbc.queryForObject("""
                    INSERT INTO hhy.home_modules(code,title,source_type,config_json,display_order,enabled,updated_at)
                    VALUES (?,?,'DICTIONARY',jsonb_build_object('version',1,'items',CAST(? AS jsonb)),0,true,?)
                    RETURNING id,code,title,enabled,1,(config_json->'items')::text
                    """, this::dictionary, code, code, itemsJson, time(now));
        }
        DictionaryRow row = existing.getFirst();
        if (row.version() != expectedVersion) return null;
        int changed = jdbc.update("""
                UPDATE hhy.home_modules
                SET config_json=jsonb_build_object('version',?,'items',CAST(? AS jsonb)),updated_at=?
                WHERE id=? AND COALESCE((config_json->>'version')::bigint,0)=?
                """, expectedVersion + 1, itemsJson, time(now), row.id(), expectedVersion);
        if (changed != 1) return null;
        return new DictionaryRow(row.id(), code, row.title(), row.enabled(), expectedVersion + 1, itemsJson);
    }

    @Override
    public void statusLog(long id, String from, String to, String reason, String actor) {
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(content_id,from_status,to_status,reason,operator)
                VALUES (?,?,?,?,?)
                """, id, from, to, reason, actor);
    }

    @Override
    public void audit(long adminId, String action, long resourceId, String beforeJson, String afterJson, String ip) {
        jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(admin_id,action,resource,resource_id,before_json,after_json,ip)
                VALUES (?,?,'CONTENT',?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, adminId, action, resourceId, beforeJson, afterJson, ip);
    }

    @Override
    public void outbox(long actorId, String eventType, String aggregateId, String status, Instant occurredAt) {
        String payload = "{\"actorId\":" + actorId + ",\"resourceId\":\"" + aggregateId
                + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + occurredAt + "\"}";
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,'CONTENT',?,?,1,'{"source":"content-api"}'::jsonb,CAST(? AS jsonb))
                """, aggregateId, UUID.randomUUID().toString(), eventType, payload);
    }

    @Override
    public List<HomeRow> homeModules() {
        return jdbc.query("""
                SELECT id,code,title,source_type,COALESCE(config_json,'{}'::jsonb)::text
                FROM hhy.home_modules
                WHERE enabled=true AND source_type IS DISTINCT FROM 'DICTIONARY'
                ORDER BY display_order,id
                """, (rs, row) -> new HomeRow(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    private ContentRow content(ResultSet rs, int row) throws SQLException {
        return new ContentRow(
                rs.getLong("id"), rs.getLong("owner_id"), rs.getString("type"), rs.getString("title"),
                rs.getString("summary"), rs.getString("status"), rs.getString("review_status"),
                rs.getLong("version"), instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("updated_at", OffsetDateTime.class)), rs.getString("nickname"),
                rs.getString("avatar"), rs.getString("bio"), rs.getString("attributes_json"),
                rs.getString("organic_views"), rs.getString("favorites"), rs.getString("chats"),
                rs.getString("contacts"));
    }

    private DictionaryRow dictionary(ResultSet rs, int row) throws SQLException {
        return new DictionaryRow(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getLong(5), rs.getString(6));
    }

    private static void append(StringBuilder sql, List<Object> args, String expression, Object value) {
        if (value != null) { sql.append(" AND ").append(expression); args.add(value); }
    }
    private static OffsetDateTime time(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
}
