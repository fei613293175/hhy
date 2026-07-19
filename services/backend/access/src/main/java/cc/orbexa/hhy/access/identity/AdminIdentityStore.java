package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL queries for administrator identity review; only the authorized liveness URL is decrypted. */
@Component
public class AdminIdentityStore {
    private static final String LATEST_SESSIONS = """
            FROM hhy.identity_verification_sessions s
            JOIN (
              SELECT user_id,max(id) AS latest_id
              FROM hhy.identity_verification_sessions GROUP BY user_id
            ) latest ON latest.latest_id=s.id
            """;
    private static final String SELECT_SESSION = """
            SELECT s.id,s.user_id,s.status,s.provider,s.expires_at,s.version,s.attempt_no,s.failure_code,
                   (SELECT p.response_cipher FROM hhy.identity_provider_requests p
                    WHERE p.session_id=s.id AND p.request_type='LIVENESS_TOKEN'
                      AND p.status='SUCCEEDED' AND p.response_cipher IS NOT NULL
                    ORDER BY p.created_at DESC,p.id DESC LIMIT 1) liveness_url_cipher
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdentitySensitiveCipher sensitiveData;

    public AdminIdentityStore(
            JdbcTemplate jdbc,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            IdentitySensitiveCipher sensitiveData) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.sensitiveData = sensitiveData;
    }

    public IdentityPage page(
            int page, int pageSize, String status, String keyword, String orderBy) {
        Filter filter = filter(status, keyword);
        List<Object> countArgs = new ArrayList<>(filter.arguments());
        Long total = jdbc.queryForObject(
                "SELECT count(*) " + LATEST_SESSIONS + filter.sql(), Long.class,
                countArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(filter.arguments());
        pageArgs.add(pageSize);
        pageArgs.add((page - 1L) * pageSize);
        List<Session> rows = jdbc.query(
                SELECT_SESSION + LATEST_SESSIONS + filter.sql()
                        + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                this::session, pageArgs.toArray());
        return new IdentityPage(rows, total == null ? 0 : total);
    }

    public Optional<Session> latestByUser(long userId) {
        return jdbc.query(SELECT_SESSION + """
                FROM hhy.identity_verification_sessions s
                WHERE s.user_id=? ORDER BY s.created_at DESC,s.id DESC LIMIT 1
                """, this::session, userId).stream().findFirst();
    }

    public boolean profileFrozen(long userId) {
        Boolean frozen = jdbc.queryForObject("""
                SELECT frozen_at IS NOT NULL FROM hhy.identity_profiles WHERE user_id=?
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(frozen);
    }

    public Optional<Session> sessionForUpdate(long sessionId) {
        return jdbc.query(SELECT_SESSION + """
                FROM hhy.identity_verification_sessions s
                WHERE s.id=? FOR UPDATE
                """, this::session, sessionId).stream().findFirst();
    }

    public Optional<Session> latestByUserForUpdate(long userId) {
        return jdbc.query(SELECT_SESSION + """
                FROM hhy.identity_verification_sessions s
                WHERE s.user_id=? ORDER BY s.created_at DESC,s.id DESC LIMIT 1 FOR UPDATE
                """, this::session, userId).stream().findFirst();
    }

    public Optional<MediaObject> latestMedia(long userId) {
        return jdbc.query("""
                SELECT m.id,m.owner_id,m.purpose,m.mime,m.size,m.sha256,m.storage_scope,
                       m.storage_binding_id,m.object_key,m.status,m.version,m.updated_at
                FROM hhy.identity_media im
                JOIN hhy.identity_verification_sessions s ON s.id=im.session_id
                JOIN hhy.media_objects m ON m.id=im.media_object_id
                WHERE s.user_id=? AND m.status='READY'
                ORDER BY im.created_at DESC,im.id DESC LIMIT 1
                """, this::media, userId).stream().findFirst();
    }

    public long createMediaAccessToken(long mediaId, String subject, Instant expiresAt) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.media_access_tokens(media_id,subject,expires_at)
                VALUES (?,?,?) RETURNING id
                """, Long.class, mediaId, subject, expiresAt);
        if (id == null) throw new IllegalStateException("Identity media token insert returned no id");
        return id;
    }

    public boolean evidenceBelongsToSession(long sessionId, List<Long> evidenceIds) {
        if (evidenceIds.isEmpty()) return true;
        String placeholders = String.join(",", java.util.Collections.nCopies(evidenceIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(sessionId);
        arguments.addAll(evidenceIds);
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.identity_media
                WHERE session_id=? AND media_object_id IN (
                """ + placeholders + ")", Long.class, arguments.toArray());
        return count != null && count == evidenceIds.size();
    }

    public Session review(
            Session before, String decision, String reason, long adminId,
            String idempotencyKey, List<Long> evidenceIds, Instant now) {
        String toStatus = switch (decision) {
            case "APPROVE" -> "VERIFIED";
            case "REJECT" -> "REJECTED";
            case "ESCALATE" -> "MANUAL_REVIEW";
            default -> throw new IllegalArgumentException("unsupported identity decision");
        };
        Instant completedAt = "ESCALATE".equals(decision) ? null : now;
        String failureCode = "REJECT".equals(decision) ? "MANUAL_REJECTED" : null;
        int changed = jdbc.update("""
                UPDATE hhy.identity_verification_sessions
                SET status=?,completed_at=?,failure_code=?,last_event=?,version=version+1,updated_at=?
                WHERE id=? AND version=?
                  AND status IN ('PROVIDER_PROCESSING','MANUAL_REVIEW','LIVENESS_PENDING')
                """, toStatus, completedAt, failureCode, "MANUAL_" + decision,
                now, before.id(), before.version());
        if (changed != 1) throw new IllegalStateException("Identity review changed concurrently");
        jdbc.update("""
                INSERT INTO hhy.identity_review_records(
                  session_id,decision,reason,admin_id,from_status,to_status,event,
                  idempotency_key,expected_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, before.id(), "ESCALATE".equals(decision) ? null : decision,
                reason + evidenceSuffix(evidenceIds), adminId, before.status(), toStatus,
                "MANUAL_" + decision, idempotencyKey, before.version());
        if ("APPROVE".equals(decision)) {
            jdbc.update("""
                    UPDATE hhy.identity_profiles
                    SET status='VERIFIED',verified_at=?,frozen_at=NULL,freeze_reason=NULL,
                        version=version+1,updated_at=? WHERE user_id=?
                    """, now, now, before.userId());
        } else if ("REJECT".equals(decision)) {
            jdbc.update("""
                    UPDATE hhy.identity_profiles
                    SET status='REJECTED',verified_at=NULL,version=version+1,updated_at=?
                    WHERE user_id=? AND status<>'VERIFIED'
                    """, now, before.userId());
        }
        outbox(adminId, "identity.reviewed.v1", before.id(), toStatus, now);
        return sessionForUpdate(before.id()).orElseThrow();
    }

    public Optional<FreezeApproval> pendingFreezeApprovalForUpdate(long userId) {
        return jdbc.query("""
                SELECT id,requester,version FROM hhy.admin_approval_requests
                WHERE type='IDENTITY_FREEZE' AND biz_id=? AND status='PENDING'
                ORDER BY created_at ASC,id ASC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new FreezeApproval(
                rs.getLong("id"), Long.parseLong(rs.getString("requester")),
                rs.getLong("version")), userId).stream().findFirst();
    }

    public long createFreezeApproval(long userId, long adminId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.admin_approval_requests(type,biz_id,requester,status)
                VALUES ('IDENTITY_FREEZE',?,?,'PENDING') RETURNING id
                """, Long.class, userId, Long.toString(adminId));
        if (id == null) throw new IllegalStateException("Identity freeze approval returned no id");
        return id;
    }

    public boolean approveFreeze(long approvalId, long version, long reviewerId) {
        return jdbc.update("""
                UPDATE hhy.admin_approval_requests
                SET reviewer=?,status='APPROVED',version=version+1
                WHERE id=? AND version=? AND status='PENDING' AND requester<>?
                """, Long.toString(reviewerId), approvalId, version,
                Long.toString(reviewerId)) == 1;
    }

    public void freeze(
            Session session, String reason, long adminId, String idempotencyKey, Instant now) {
        int changed = jdbc.update("""
                UPDATE hhy.identity_profiles
                SET status='FROZEN',frozen_at=?,freeze_reason=?,version=version+1,updated_at=?
                WHERE user_id=? AND frozen_at IS NULL
                """, now, reason, now, session.userId());
        if (changed != 1) throw new IllegalStateException("Identity profile cannot be frozen");
        jdbc.update("""
                INSERT INTO hhy.identity_review_records(
                  session_id,decision,reason,admin_id,from_status,to_status,event,
                  idempotency_key,expected_version)
                VALUES (?,'FREEZE',?,?,?,?,?,?,?)
                """, session.id(), reason, adminId, session.status(), session.status(),
                "IDENTITY_FROZEN", idempotencyKey, session.version());
        outbox(adminId, "identity.frozen.v1", session.id(), "FROZEN", now);
    }

    public void profileAccess(
            long adminId, long userId, String operation, String reason,
            String requestId, String ip, Long mediaObjectId) {
        jdbc.update("""
                INSERT INTO hhy.sensitive_data_access_logs(
                  admin_id,user_id,resource,reason,ip,operation,request_id,media_object_id)
                VALUES (?,?,'IDENTITY_PROFILE',?,?,?,?,?)
                """, adminId, userId, reason, ip, operation, requestId, mediaObjectId);
    }

    private Session session(ResultSet rs, int row) throws SQLException {
        OffsetDateTime expiry = rs.getObject("expires_at", OffsetDateTime.class);
        String protectedUrl = rs.getString("liveness_url_cipher");
        return new Session(
                rs.getLong("id"), rs.getLong("user_id"), null, rs.getString("status"),
                rs.getString("provider"), protectedUrl == null ? null : java.net.URI.create(
                        sensitiveData.decrypt(rs.getLong("user_id"), "liveness-url", protectedUrl)),
                rs.getString("failure_code"),
                expiry == null ? null : expiry.toInstant(), rs.getLong("version"),
                rs.getInt("attempt_no"));
    }

    private MediaObject media(ResultSet rs, int row) throws SQLException {
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        return new MediaObject(
                rs.getLong("id"), rs.getLong("owner_id"), rs.getString("purpose"),
                rs.getString("mime"), rs.getLong("size"), rs.getString("sha256"),
                Scope.valueOf(rs.getString("storage_scope").toUpperCase(java.util.Locale.ROOT)),
                rs.getLong("storage_binding_id"), rs.getString("object_key"),
                rs.getString("status"), rs.getLong("version"), updated.toInstant());
    }

    private void outbox(long actorId, String eventType, long sessionId, String state, Instant at) {
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,'IDENTITY_SESSION',?,?,1,?::jsonb,?::jsonb)
                """, Long.toString(sessionId), UUID.randomUUID().toString(), eventType,
                json(java.util.Map.of("source", "admin-identity-api")),
                json(java.util.Map.of("actorId", actorId, "resourceId", sessionId,
                        "status", state, "occurredAt", at.toString())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Identity administrator audit serialization failed", failure);
        }
    }

    private static String evidenceSuffix(List<Long> evidenceIds) {
        return evidenceIds.isEmpty() ? "" : " [evidence=" + evidenceIds + "]";
    }

    private static Filter filter(String status, String keyword) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status=?");
            arguments.add(status.strip().toUpperCase(java.util.Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.strip().toLowerCase(java.util.Locale.ROOT) + "%";
            sql.append(" AND (CAST(s.id AS text) LIKE ? OR CAST(s.user_id AS text) LIKE ?)");
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return new Filter(sql.toString(), List.copyOf(arguments));
    }

    public record IdentityPage(List<Session> items, long total) { }
    public record FreezeApproval(long id, long requesterId, long version) { }
    private record Filter(String sql, List<Object> arguments) { }
}
