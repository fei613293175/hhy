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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class R07PostgresStore implements R07Store {
    private static final String SEARCH_SELECT = """
            SELECT p.id,p.owner_id,p.type,p.title,p.summary,p.created_at,
                   profile.nickname,profile.avatar,profile.bio,
                   EXISTS(SELECT 1 FROM hhy.identity_profiles identity
                          WHERE identity.user_id=p.owner_id AND identity.status='VERIFIED') AS verified,
                   (SELECT plan.public_badge FROM hhy.user_memberships membership
                    JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                    WHERE membership.user_id=p.owner_id AND membership.status='ACTIVE'
                      AND (membership.starts_at IS NULL OR membership.starts_at<=clock_timestamp())
                      AND (membership.ends_at IS NULL OR membership.ends_at>clock_timestamp())
                    ORDER BY membership.ends_at DESC NULLS LAST,membership.id DESC LIMIT 1) AS member_badge,
                   EXISTS(SELECT 1 FROM hhy.publisher_follows follow
                          WHERE follow.user_id=? AND follow.publisher_id=p.owner_id) AS followed,
                   CASE WHEN lower(p.title)=lower(?) THEN 4.0
                        WHEN lower(p.title) LIKE lower(?) THEN 3.0
                        WHEN p.title ILIKE ? THEN 2.0 ELSE 1.0 END AS score
            FROM hhy.content_posts p
            LEFT JOIN hhy.user_profiles profile ON profile.user_id=p.owner_id
            LEFT JOIN LATERAL (
              SELECT version.snapshot_json FROM hhy.content_versions version
              WHERE version.content_id=p.id ORDER BY version.created_at DESC,version.id DESC LIMIT 1
            ) snapshot ON true
            """;

    private final JdbcTemplate jdbc;

    public R07PostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public SearchRows search(SearchQuery query) {
        StringBuilder where = new StringBuilder(" WHERE p.status='ONLINE'");
        List<Object> filters = new ArrayList<>();
        String contains = "%" + query.keyword() + "%";
        where.append(" AND (p.title ILIKE ? OR p.summary ILIKE ?")
                .append(" OR snapshot.snapshot_json->>'description' ILIKE ?")
                .append(" OR profile.nickname ILIKE ?)");
        filters.add(contains); filters.add(contains); filters.add(contains); filters.add(contains);
        append(where, filters, "p.type=?", query.contentType());
        append(where, filters, "snapshot.snapshot_json->>'categoryCode'=?", query.categoryCode());
        append(where, filters, "snapshot.snapshot_json->>'regionCode'=?", query.regionCode());
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.content_posts p
                LEFT JOIN hhy.user_profiles profile ON profile.user_id=p.owner_id
                LEFT JOIN LATERAL (
                  SELECT version.snapshot_json FROM hhy.content_versions version
                  WHERE version.content_id=p.id ORDER BY version.created_at DESC,version.id DESC LIMIT 1
                ) snapshot ON true
                """ + where, Long.class, filters.toArray());

        if (query.beforeId() != null) { where.append(" AND p.id<?"); filters.add(query.beforeId()); }

        List<Object> args = new ArrayList<>();
        args.add(query.viewerId());
        args.add(query.keyword());
        args.add(query.keyword() + "%");
        args.add(contains);
        args.addAll(filters);
        args.add(query.pageSize() + 1);
        args.add(query.beforeId() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<SearchRow> rows = jdbc.query(SEARCH_SELECT + where + " ORDER BY " + query.orderBy()
                + " LIMIT ? OFFSET ?", this::searchRow, args.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new SearchRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    @Override
    public void recordSearch(long userId, String keyword, Instant now) {
        jdbc.update("INSERT INTO hhy.search_histories(user_id,keyword,created_at,updated_at) VALUES (?,?,?,?)",
                userId, keyword, time(now), time(now));
    }

    @Override
    public TermRows hotTerms(TermQuery query, Instant now) {
        StringBuilder where = new StringBuilder(" WHERE enabled=true AND (starts_at IS NULL OR starts_at<=?)"
                + " AND (ends_at IS NULL OR ends_at>?)");
        List<Object> args = new ArrayList<>(List.of(time(now), time(now)));
        termFilters(where, args, query);
        Long total = jdbc.queryForObject("SELECT count(*) FROM hhy.hot_search_terms" + where,
                Long.class, args.toArray());
        args.add(query.pageSize() + 1);
        args.add(query.beforeId() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<TermRow> rows = jdbc.query("SELECT id,keyword,created_at FROM hhy.hot_search_terms" + where
                + " ORDER BY " + query.orderBy() + " LIMIT ? OFFSET ?", this::termRow, args.toArray());
        return terms(rows, total, query.pageSize());
    }

    @Override
    public TermRows history(long userId, TermQuery query) {
        StringBuilder where = new StringBuilder(" WHERE user_id=?");
        List<Object> args = new ArrayList<>(List.of(userId));
        termFilters(where, args, query);
        Long total = jdbc.queryForObject("SELECT count(*) FROM (SELECT lower(btrim(keyword))"
                        + " FROM hhy.search_histories" + where
                        + " GROUP BY lower(btrim(keyword))) distinct_terms",
                Long.class, args.toArray());
        args.add(query.pageSize() + 1);
        args.add(query.beforeId() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<TermRow> rows = jdbc.query("""
                SELECT id,keyword,created_at FROM (
                  SELECT id,keyword,created_at,
                         row_number() OVER (PARTITION BY lower(btrim(keyword))
                                            ORDER BY created_at DESC,id DESC) AS duplicate_rank
                  FROM hhy.search_histories
                """ + where + ") recent WHERE duplicate_rank=1 ORDER BY " + query.orderBy()
                + " LIMIT ? OFFSET ?", this::termRow, args.toArray());
        return terms(rows, total, query.pageSize());
    }

    @Override
    public Optional<PublisherRow> publisher(long publisherId, long viewerId, Instant now) {
        return jdbc.query("""
                SELECT u.id,profile.nickname,profile.avatar,profile.bio,
                       EXISTS(SELECT 1 FROM hhy.identity_profiles identity
                              WHERE identity.user_id=u.id AND identity.status='VERIFIED') AS verified,
                       (SELECT plan.public_badge FROM hhy.user_memberships membership
                        JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                        WHERE membership.user_id=u.id AND membership.status='ACTIVE'
                          AND (membership.starts_at IS NULL OR membership.starts_at<=?)
                          AND (membership.ends_at IS NULL OR membership.ends_at>?)
                        ORDER BY membership.ends_at DESC NULLS LAST,membership.id DESC LIMIT 1) member_badge,
                       EXISTS(SELECT 1 FROM hhy.publisher_follows follow
                              WHERE follow.user_id=? AND follow.publisher_id=u.id) followed
                FROM hhy.users u JOIN hhy.user_profiles profile ON profile.user_id=u.id
                WHERE u.id=? AND u.status='ACTIVE'
                """, (rs, row) -> new PublisherRow(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5), rs.getString(6), rs.getBoolean(7)),
                time(now), time(now), viewerId, publisherId).stream().findFirst();
    }

    @Override
    public Optional<ContactRow> contact(long contentId, String channel) {
        return jdbc.query("""
                SELECT contact.content_id,contact.channel,contact.value_cipher
                FROM hhy.content_contacts contact
                JOIN hhy.content_posts content ON content.id=contact.content_id
                WHERE contact.content_id=? AND upper(contact.channel)=? AND content.status='ONLINE'
                ORDER BY contact.sort_order,contact.id LIMIT 1
                """, (rs, row) -> new ContactRow(rs.getLong(1), rs.getString(2), rs.getString(3)),
                contentId, channel).stream().findFirst();
    }

    @Override
    public int clearHistory(long userId) {
        return jdbc.update("DELETE FROM hhy.search_histories WHERE user_id=?", userId);
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
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, row) -> new IdempotencyClaim(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), inserted == 0), scope, key);
        if (result == null) throw new IllegalStateException("R07 idempotency claim disappeared");
        return result;
    }

    @Override
    public void complete(long claimId, String responseRef, String responseType, String ciphertext) {
        if (jdbc.update("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL AND response_type IS NULL
                  AND response_payload_ciphertext IS NULL
                """, responseRef, responseType, ciphertext, claimId) != 1) {
            throw new IllegalStateException("R07 idempotency response already completed");
        }
    }

    @Override
    public void abandon(long claimId) {
        jdbc.update("DELETE FROM hhy.idempotency_records WHERE id=? AND response_ref IS NULL", claimId);
    }

    @Override
    public void contactAudit(long userId, long contentId, String channel, String action, Instant now) {
        jdbc.update("""
                INSERT INTO hhy.content_contact_access_logs(user_id,content_id,channel,action,created_at)
                VALUES (?,?,?,?,?)
                """, userId, contentId, channel, action, time(now));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void contactRejected(long userId, long contentId, String channel, String action, Instant now) {
        contactAudit(userId, contentId, channel, action, now);
    }

    @Override
    public void outbox(long userId, String aggregateType, String eventType,
                       String aggregateId, String status, Instant now) {
        String payload = "{\"actorId\":" + userId + ",\"resourceId\":\"" + aggregateId
                + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + now + "\"}";
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,?,?,?,1,'{"source":"r07-api"}'::jsonb,CAST(? AS jsonb))
                """, aggregateId, aggregateType, UUID.randomUUID().toString(), eventType, payload);
    }

    private void termFilters(StringBuilder where, List<Object> args, TermQuery query) {
        if (query.keyword() != null) { where.append(" AND keyword ILIKE ?"); args.add("%" + query.keyword() + "%"); }
        if (query.beforeId() != null) { where.append(" AND id<?"); args.add(query.beforeId()); }
    }

    private SearchRow searchRow(ResultSet rs, int row) throws SQLException {
        return new SearchRow(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("type"),
                rs.getString("title"), rs.getString("summary"), instant(rs, "created_at"),
                rs.getString("nickname"), rs.getString("avatar"), rs.getString("bio"),
                rs.getBoolean("verified"), rs.getString("member_badge"), rs.getBoolean("followed"),
                rs.getDouble("score"));
    }

    private TermRow termRow(ResultSet rs, int row) throws SQLException {
        return new TermRow(rs.getLong("id"), rs.getString("keyword"), instant(rs, "created_at"));
    }

    private static TermRows terms(List<TermRow> rows, Long total, int pageSize) {
        boolean more = rows.size() > pageSize;
        if (more) rows = new ArrayList<>(rows.subList(0, pageSize));
        return new TermRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    private static void append(StringBuilder sql, List<Object> args, String expression, Object value) {
        if (value != null) { sql.append(" AND ").append(expression); args.add(value); }
    }

    private static OffsetDateTime time(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        OffsetDateTime value = rs.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
