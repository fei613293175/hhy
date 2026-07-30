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
public class R12ReviewPostgresStore implements R12ReviewStore {
    private static final String REVIEW_FROM = """
            FROM hhy.content_posts content
            LEFT JOIN LATERAL (
              SELECT version.snapshot_json
              FROM hhy.content_versions version
              WHERE version.content_id=content.id
              ORDER BY version.version_no::bigint DESC,version.id DESC LIMIT 1
            ) snapshot ON true
            LEFT JOIN LATERAL (
              SELECT review.admin_id
              FROM hhy.content_review_records review
              WHERE review.content_id=content.id AND review.decision='ASSIGN'
              ORDER BY review.created_at DESC,review.id DESC LIMIT 1
            ) assignment ON true
            LEFT JOIN LATERAL (
              SELECT review.decision,review.reason
              FROM hhy.content_review_records review
              WHERE review.content_id=content.id AND review.decision IN ('APPROVE','REJECT','ESCALATE')
              ORDER BY review.created_at DESC,review.id DESC LIMIT 1
            ) decision ON true
            LEFT JOIN LATERAL (
              SELECT history.created_at
              FROM hhy.content_status_logs history
              WHERE history.content_id=content.id AND history.to_status='PENDING_REVIEW'
              ORDER BY history.transition_version DESC NULLS LAST,history.id DESC LIMIT 1
            ) submitted ON true
            """;

    private static final String REVIEW_SELECT = """
            SELECT content.id,content.status,content.version,
                   COALESCE(submitted.created_at,content.created_at),content.updated_at,
                   snapshot.snapshot_json->>'riskLevel',assignment.admin_id,
                   decision.decision,decision.reason
            """;

    private final JdbcTemplate jdbc;

    public R12ReviewPostgresStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReviewPageRows reviews(PageQuery query) {
        QueryParts parts = reviewWhere(query, false);
        Long total = jdbc.queryForObject("SELECT count(*) " + REVIEW_FROM + parts.sql(),
                Long.class, parts.args().toArray());
        QueryParts pageParts = reviewWhere(query, true);
        List<Object> args = new ArrayList<>(pageParts.args());
        args.add(query.pageSize() + 1);
        args.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<ReviewRow> rows = jdbc.query(REVIEW_SELECT + REVIEW_FROM + pageParts.sql()
                + " ORDER BY " + reviewOrder(query.sort()) + " LIMIT ? OFFSET ?",
                this::reviewRow, args.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new ReviewPageRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    @Override
    public Optional<ReviewRow> review(long contentId) {
        return review(contentId, false);
    }

    @Override
    public Optional<ReviewRow> lockReview(long contentId) {
        return review(contentId, true);
    }

    private Optional<ReviewRow> review(long contentId, boolean lock) {
        String suffix = lock ? " FOR UPDATE OF content" : "";
        return jdbc.query(REVIEW_SELECT + REVIEW_FROM + " WHERE content.id=?" + suffix,
                this::reviewRow, contentId).stream().findFirst();
    }

    @Override
    public ReportPageRows reports(PageQuery query) {
        QueryParts count = simpleWhere(query, "report", "report.type", "report.description", false);
        Long total = jdbc.queryForObject("SELECT count(*) FROM hhy.content_reports report" + count.sql(),
                Long.class, count.args().toArray());
        QueryParts page = simpleWhere(query, "report", "report.type", "report.description", true);
        List<Object> args = new ArrayList<>(page.args());
        args.add(query.pageSize() + 1);
        args.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<ReportRow> rows = jdbc.query("""
                SELECT report.id,report.reporter_id,report.content_id,report.type,report.status,
                       report.created_at,report.updated_at,
                       (extract(epoch FROM report.updated_at)*1000000)::bigint
                FROM hhy.content_reports report
                """ + page.sql() + " ORDER BY " + simpleOrder("report", query.sort()) + " LIMIT ? OFFSET ?",
                this::reportRow, args.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new ReportPageRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    @Override
    public AppealPageRows appeals(PageQuery query) {
        QueryParts count = simpleWhere(query, "appeal", null, null, false);
        Long total = jdbc.queryForObject("SELECT count(*) FROM hhy.content_appeals appeal" + count.sql(),
                Long.class, count.args().toArray());
        QueryParts page = simpleWhere(query, "appeal", null, null, true);
        List<Object> args = new ArrayList<>(page.args());
        args.add(query.pageSize() + 1);
        args.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<AppealRow> rows = jdbc.query("""
                SELECT appeal.id,appeal.owner_id,appeal.content_id,appeal.status,
                       appeal.created_at,appeal.updated_at,
                       (extract(epoch FROM appeal.updated_at)*1000000)::bigint
                FROM hhy.content_appeals appeal
                """ + page.sql() + " ORDER BY " + simpleOrder("appeal", query.sort()) + " LIMIT ? OFFSET ?",
                this::appealRow, args.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new AppealPageRows(List.copyOf(rows), total == null ? 0 : total, more);
    }

    @Override
    public boolean activeAdmin(long adminId) {
        Boolean result = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM hhy.admin_users WHERE id=? AND status='ACTIVE')",
                Boolean.class, adminId);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Optional<String> sessionDevice(long adminId, long sessionId) {
        List<String> devices = jdbc.query("""
                SELECT device_fingerprint FROM hhy.admin_sessions
                WHERE id=? AND admin_user_id=?
                """, (rs, row) -> rs.getString(1), sessionId, adminId);
        return devices.isEmpty() || devices.getFirst() == null
                ? Optional.empty() : Optional.of(devices.getFirst());
    }

    @Override
    public Optional<SnapshotRow> submittedSnapshot(long contentId, long maximumVersion) {
        List<SnapshotRow> submitted = jdbc.query("""
                SELECT version.id,version.version_no
                FROM hhy.content_status_logs history
                JOIN hhy.content_versions version
                  ON version.content_id=history.content_id
                 AND version.version_no=history.transition_version::text
                WHERE history.content_id=? AND history.to_status='PENDING_REVIEW'
                  AND history.transition_version<=?
                ORDER BY history.transition_version DESC,version.id DESC LIMIT 1
                """, this::snapshotRow, contentId, maximumVersion);
        if (!submitted.isEmpty()) return Optional.of(submitted.getFirst());
        return jdbc.query("""
                SELECT id,version_no FROM hhy.content_versions
                WHERE content_id=? AND version_no ~ '^(0|[1-9][0-9]*)$'
                  AND version_no::bigint<=?
                ORDER BY version_no::bigint DESC,id DESC LIMIT 1
                """, this::snapshotRow, contentId, maximumVersion).stream().findFirst();
    }

    @Override
    public SecondReviewState secondReviewState(long snapshotId) {
        return jdbc.queryForObject("""
                WITH escalation AS (
                  SELECT id FROM hhy.content_review_records
                  WHERE snapshot_version_id=? AND decision='ESCALATE'
                  ORDER BY id DESC LIMIT 1
                )
                SELECT escalation.id IS NOT NULL,
                       (SELECT review.admin_id FROM hhy.content_review_records review
                        WHERE review.snapshot_version_id=? AND review.decision='ASSIGN'
                          AND escalation.id IS NOT NULL AND review.id>escalation.id
                        ORDER BY review.id DESC LIMIT 1)
                FROM (SELECT 1) singleton LEFT JOIN escalation ON true
                """, (rs, row) -> new SecondReviewState(
                        rs.getBoolean(1), nullableLong(rs, 2)), snapshotId, snapshotId);
    }

    @Override
    public boolean advance(long contentId, long expectedVersion, String status, Instant now) {
        return jdbc.update("""
                UPDATE hhy.content_posts
                SET status=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, status, time(now), contentId, expectedVersion) == 1;
    }

    @Override
    public void reviewRecord(
            long contentId, SnapshotRow snapshot, String decision, String reason,
            long adminId, String commandId, Instant now) {
        if (jdbc.update("""
                INSERT INTO hhy.content_review_records(
                  content_id,version_no,decision,reason,admin_id,created_at,snapshot_version_id,command_id
                ) VALUES (?,?,?,?,?,?,?,?)
                """, contentId, snapshot.versionNo(), decision, reason, adminId, time(now),
                snapshot.id(), commandId) != 1) {
            throw new IllegalStateException("R12 review record was not inserted");
        }
    }

    @Override
    public void statusLog(
            long contentId, String fromStatus, String toStatus, String reason,
            long transitionVersion, String operator, Instant now) {
        if (jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,reason,operator,created_at,transition_version
                ) VALUES (?,?,?,?,?,?,?)
                """, contentId, fromStatus, toStatus, reason, operator, time(now), transitionVersion) != 1) {
            throw new IllegalStateException("R12 review status history was not inserted");
        }
    }

    @Override
    public void audit(
            long adminId, String action, long resourceId,
            String beforeJson, String afterJson, String ip, Instant now) {
        if (jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip,created_at
                ) VALUES (?,?,'CONTENT_REVIEW',?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
                """, adminId, action, resourceId, beforeJson, afterJson, ip, time(now)) != 1) {
            throw new IllegalStateException("R12 review audit record was not inserted");
        }
    }

    @Override
    public void outbox(String eventType, long contentId, String requestId, String payloadJson) {
        if (jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
                ) VALUES (?, 'CONTENT', ?, ?, 1,
                  jsonb_build_object('source','r12-review-api','requestId',?),
                  CAST(? AS jsonb))
                """, Long.toString(contentId), UUID.randomUUID().toString(), eventType, requestId,
                payloadJson) != 1) {
            throw new IllegalStateException("R12 review outbox event was not inserted");
        }
    }

    @Override
    public IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt) {
        jdbc.update("""
                DELETE FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at<=clock_timestamp()
                """, scope, key);
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
        if (result == null) throw new IllegalStateException("R12 review idempotency claim disappeared");
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
            throw new IllegalStateException("R12 review idempotency response already completed");
        }
    }

    private QueryParts reviewWhere(PageQuery query, boolean includeCursor) {
        StringBuilder where = new StringBuilder(
                " WHERE content.status IN ('PENDING_REVIEW','REVIEWING','APPROVED','REJECTED')");
        List<Object> args = new ArrayList<>();
        append(where, args, "content.status=?", query.status());
        if (query.keyword() != null) {
            where.append(" AND (content.id::text=? OR content.title ILIKE ? OR content.summary ILIKE ?)");
            args.add(query.keyword());
            String contains = "%" + query.keyword() + "%";
            args.add(contains);
            args.add(contains);
        }
        if (includeCursor) appendCursor(where, args, query, "content",
                "COALESCE(submitted.created_at,content.created_at)", "content.updated_at");
        return new QueryParts(where.toString(), List.copyOf(args));
    }

    private QueryParts simpleWhere(
            PageQuery query, String alias, String keywordColumn, String secondaryKeywordColumn,
            boolean includeCursor) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        append(where, args, alias + ".status=?", query.status());
        if (query.keyword() != null) {
            where.append(" AND (").append(alias).append(".id::text=?");
            args.add(query.keyword());
            if (keywordColumn != null) {
                where.append(" OR ").append(keywordColumn).append(" ILIKE ?");
                args.add("%" + query.keyword() + "%");
            }
            if (secondaryKeywordColumn != null) {
                where.append(" OR ").append(secondaryKeywordColumn).append(" ILIKE ?");
                args.add("%" + query.keyword() + "%");
            }
            where.append(')');
        }
        if (includeCursor) appendCursor(where, args, query, alias,
                alias + ".created_at", alias + ".updated_at");
        return new QueryParts(where.toString(), List.copyOf(args));
    }

    private ReviewRow reviewRow(ResultSet rs, int row) throws SQLException {
        return new ReviewRow(
                rs.getLong(1), rs.getString(2), rs.getLong(3), instant(rs.getObject(4, OffsetDateTime.class)),
                instant(rs.getObject(5, OffsetDateTime.class)), rs.getString(6), nullableLong(rs, 7),
                rs.getString(8), rs.getString(9));
    }

    private ReportRow reportRow(ResultSet rs, int row) throws SQLException {
        return new ReportRow(
                rs.getLong(1), nullableLong(rs, 2), nullableLong(rs, 3), rs.getString(4),
                rs.getString(5), instant(rs.getObject(6, OffsetDateTime.class)),
                instant(rs.getObject(7, OffsetDateTime.class)), rs.getLong(8));
    }

    private AppealRow appealRow(ResultSet rs, int row) throws SQLException {
        return new AppealRow(
                rs.getLong(1), nullableLong(rs, 2), rs.getLong(3), rs.getString(4),
                instant(rs.getObject(5, OffsetDateTime.class)),
                instant(rs.getObject(6, OffsetDateTime.class)), rs.getLong(7));
    }

    private SnapshotRow snapshotRow(ResultSet rs, int row) throws SQLException {
        return new SnapshotRow(rs.getLong(1), rs.getString(2));
    }

    private static void appendCursor(
            StringBuilder where, List<Object> args, PageQuery query, String alias,
            String createdExpression, String updatedExpression) {
        CursorKey cursor = query.cursor();
        if (cursor == null) return;
        String comparison = query.sort().endsWith(":asc") ? ">" : "<";
        String sortExpression = switch (query.sort()) {
            case "createdAt:asc", "createdAt:desc" -> createdExpression;
            case "updatedAt:asc", "updatedAt:desc" -> updatedExpression;
            case "id:asc", "id:desc" -> null;
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        };
        if (sortExpression == null) {
            where.append(" AND ").append(alias).append(".id ").append(comparison).append(" ?");
            args.add(cursor.id());
            return;
        }
        if (cursor.sortValue() == null) throw new IllegalArgumentException("R12 review cursor time is required");
        where.append(" AND (").append(sortExpression).append(',').append(alias).append(".id) ")
                .append(comparison).append(" (?,?)");
        args.add(time(cursor.sortValue()));
        args.add(cursor.id());
    }

    private static String reviewOrder(String sort) {
        return switch (sort) {
            case "priority:desc,createdAt:asc" -> "CASE upper(COALESCE(snapshot.snapshot_json->>'riskLevel','')) WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END DESC,COALESCE(submitted.created_at,content.created_at) ASC,content.id ASC";
            case "createdAt:desc" -> "COALESCE(submitted.created_at,content.created_at) DESC,content.id DESC";
            case "createdAt:asc" -> "COALESCE(submitted.created_at,content.created_at) ASC,content.id ASC";
            case "updatedAt:desc" -> "content.updated_at DESC,content.id DESC";
            case "updatedAt:asc" -> "content.updated_at ASC,content.id ASC";
            case "id:desc" -> "content.id DESC";
            case "id:asc" -> "content.id ASC";
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        };
    }

    private static String simpleOrder(String alias, String sort) {
        return switch (sort) {
            case "createdAt:desc" -> alias + ".created_at DESC," + alias + ".id DESC";
            case "createdAt:asc" -> alias + ".created_at ASC," + alias + ".id ASC";
            case "updatedAt:desc" -> alias + ".updated_at DESC," + alias + ".id DESC";
            case "updatedAt:asc" -> alias + ".updated_at ASC," + alias + ".id ASC";
            case "id:desc" -> alias + ".id DESC";
            case "id:asc" -> alias + ".id ASC";
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        };
    }

    private static void append(StringBuilder where, List<Object> args, String clause, Object value) {
        if (value != null) {
            where.append(" AND ").append(clause);
            args.add(value);
        }
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record QueryParts(String sql, List<Object> args) { }
}
