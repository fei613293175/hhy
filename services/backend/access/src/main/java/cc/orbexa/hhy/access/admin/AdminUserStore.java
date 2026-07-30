package cc.orbexa.hhy.access.admin;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminUserStore {
    private static final String USER_PROJECTION = """
            SELECT u.id,u.phone,u.status,u.created_at,u.version,
                   p.nickname,p.avatar,p.bio,
                   (SELECT identity.status FROM hhy.identity_profiles identity
                    WHERE identity.user_id=u.id ORDER BY identity.updated_at DESC,identity.id DESC LIMIT 1)
                     AS identity_status,
                   (SELECT membership.status FROM hhy.user_memberships membership
                    WHERE membership.user_id=u.id ORDER BY membership.updated_at DESC,membership.id DESC LIMIT 1)
                     AS membership_status
            FROM hhy.users u
            LEFT JOIN hhy.user_profiles p ON p.user_id=u.id
            """;

    private final JdbcTemplate jdbc;

    public AdminUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserPage listUsers(
            int page, int pageSize, String status, String keyword, String orderBy) {
        QueryFilter filter = filter(status, keyword);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.users u LEFT JOIN hhy.user_profiles p ON p.user_id=u.id"
                        + filter.where(),
                Long.class,
                filter.arguments().toArray());
        List<Object> pageArguments = new ArrayList<>(filter.arguments());
        pageArguments.add(pageSize);
        pageArguments.add((page - 1L) * pageSize);
        List<UserRow> items = jdbc.query(
                USER_PROJECTION + filter.where() + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                (rs, row) -> new UserRow(
                        rs.getLong("id"),
                        rs.getString("phone"),
                        rs.getString("nickname"),
                        rs.getString("avatar"),
                        rs.getString("bio"),
                        rs.getString("status"),
                        rs.getString("identity_status"),
                        rs.getString("membership_status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getLong("version")),
                pageArguments.toArray());
        return new UserPage(items, total == null ? 0 : total);
    }

    public Optional<UserRow> findUser(long userId) {
        return jdbc.query(
                USER_PROJECTION + " WHERE u.id=?",
                (rs, row) -> new UserRow(
                        rs.getLong("id"),
                        rs.getString("phone"),
                        rs.getString("nickname"),
                        rs.getString("avatar"),
                        rs.getString("bio"),
                        rs.getString("status"),
                        rs.getString("identity_status"),
                        rs.getString("membership_status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getLong("version")),
                userId).stream().findFirst();
    }

    public Optional<UserRow> findUserForUpdate(long userId) {
        return jdbc.query(
                USER_PROJECTION + " WHERE u.id=? FOR UPDATE OF u",
                (rs, row) -> new UserRow(
                        rs.getLong("id"), rs.getString("phone"), rs.getString("nickname"),
                        rs.getString("avatar"), rs.getString("bio"), rs.getString("status"),
                        rs.getString("identity_status"), rs.getString("membership_status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getLong("version")),
                userId).stream().findFirst();
    }

    public void expireRestrictions(long userId, Instant now) {
        jdbc.update("""
                UPDATE hhy.user_restrictions
                SET status='EXPIRED',removed_at=?,version=version+1
                WHERE user_id=? AND status='ACTIVE' AND expires_at IS NOT NULL AND expires_at<=?
                """, time(now), userId, time(now));
    }

    public void upsertRestriction(
            long userId, String type, String reason, Instant expiresAt, long adminId) {
        jdbc.update("""
                INSERT INTO hhy.user_restrictions(
                  user_id,restriction_type,reason,expires_at,status,created_by)
                VALUES (?,?,?,?,'ACTIVE',?)
                ON CONFLICT (user_id,restriction_type) WHERE status='ACTIVE'
                DO UPDATE SET reason=EXCLUDED.reason,expires_at=EXCLUDED.expires_at,
                  created_by=EXCLUDED.created_by,version=hhy.user_restrictions.version+1
                """, userId, type, reason, time(expiresAt), adminId);
    }

    public boolean removeRestriction(long userId, String type, long adminId, Instant now) {
        return jdbc.update("""
                UPDATE hhy.user_restrictions
                SET status='REMOVED',removed_by=?,removed_at=?,version=version+1
                WHERE user_id=? AND restriction_type=? AND status='ACTIVE'
                """, adminId, time(now), userId, type) == 1;
    }

    public boolean hasActiveRestrictions(long userId, Instant now) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.user_restrictions
                WHERE user_id=? AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>?)
                """, Long.class, userId, time(now));
        return count != null && count > 0;
    }

    public boolean applyRestrictionStatus(long userId, long expectedVersion) {
        return jdbc.update("""
                UPDATE hhy.users SET status=CASE WHEN status='FROZEN' THEN status ELSE 'RESTRICTED' END,
                  version=version+1
                WHERE id=? AND version=? AND status IN ('ACTIVE','RESTRICTED','FROZEN')
                """, userId, expectedVersion) == 1;
    }

    public boolean reconcileRestrictionStatus(long userId, long expectedVersion, boolean restricted) {
        String target = restricted ? "RESTRICTED" : "ACTIVE";
        return jdbc.update("""
                UPDATE hhy.users SET status=CASE WHEN status='FROZEN' THEN status ELSE ? END,
                  version=version+1
                WHERE id=? AND version=? AND status IN ('ACTIVE','RESTRICTED','FROZEN')
                """, target, userId, expectedVersion) == 1;
    }

    public boolean freezeUser(long userId, long expectedVersion) {
        return jdbc.update("""
                UPDATE hhy.users SET status='FROZEN',version=version+1
                WHERE id=? AND version=? AND status IN ('ACTIVE','RESTRICTED')
                """, userId, expectedVersion) == 1;
    }

    public boolean unfreezeUser(long userId, long expectedVersion, boolean restricted) {
        return jdbc.update("""
                UPDATE hhy.users SET status=?,version=version+1
                WHERE id=? AND version=? AND status='FROZEN'
                """, restricted ? "RESTRICTED" : "ACTIVE", userId, expectedVersion) == 1;
    }

    public boolean touchUserVersion(long userId, long expectedVersion) {
        return jdbc.update("""
                UPDATE hhy.users SET version=version+1 WHERE id=? AND version=?
                  AND status IN ('ACTIVE','RESTRICTED','FROZEN')
                """, userId, expectedVersion) == 1;
    }

    public int revokeUserSessions(long userId, Instant now) {
        return jdbc.update("""
                UPDATE hhy.user_sessions SET refresh_hash=NULL,expires_at=?,version=version+1
                WHERE user_id=? AND refresh_hash IS NOT NULL
                """, time(now), userId);
    }

    public void statusLog(
            long userId, String fromStatus, String toStatus, String reason, long adminId) {
        jdbc.update("""
                INSERT INTO hhy.user_status_logs(user_id,from_status,to_status,reason,operator)
                VALUES (?,?,?,?,?)
                """, userId, fromStatus, toStatus, reason, "ADMIN:" + adminId);
    }

    public Optional<FreezeApproval> pendingFreezeApprovalForUpdate(long userId) {
        return jdbc.query("""
                SELECT id,requester,version FROM hhy.admin_approval_requests
                WHERE type='USER_FREEZE' AND biz_id=? AND status='PENDING'
                ORDER BY created_at ASC,id ASC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new FreezeApproval(
                rs.getLong("id"), Long.parseLong(rs.getString("requester")), rs.getLong("version")),
                userId).stream().findFirst();
    }

    public long createFreezeApproval(long userId, long adminId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.admin_approval_requests(type,biz_id,requester,status)
                VALUES ('USER_FREEZE',?,?,'PENDING') RETURNING id
                """, Long.class, userId, Long.toString(adminId));
        if (id == null) throw new IllegalStateException("Freeze approval insert returned no id");
        return id;
    }

    public boolean approveFreeze(long approvalId, long expectedVersion, long reviewerId) {
        return jdbc.update("""
                UPDATE hhy.admin_approval_requests
                SET reviewer=?,status='APPROVED',version=version+1
                WHERE id=? AND version=? AND status='PENDING' AND requester<>?
                """, Long.toString(reviewerId), approvalId, expectedVersion,
                Long.toString(reviewerId)) == 1;
    }

    private static QueryFilter filter(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND u.status=?");
            arguments.add(status.strip().toUpperCase(java.util.Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.strip().toLowerCase(java.util.Locale.ROOT) + "%";
            where.append(" AND (LOWER(u.phone) LIKE ? OR LOWER(COALESCE(p.nickname,'')) LIKE ?")
                    .append(" OR CAST(u.id AS text) LIKE ?)");
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return new QueryFilter(where.toString(), List.copyOf(arguments));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }

    private record QueryFilter(String where, List<Object> arguments) { }

    public record UserRow(
            long id,
            String phone,
            String nickname,
            String avatarUrl,
            String bio,
            String status,
            String identityStatus,
            String membershipStatus,
            Instant createdAt,
            long version) { }

    public record UserPage(List<UserRow> items, long total) {
        public UserPage {
            items = List.copyOf(items);
        }
    }

    public record FreezeApproval(long id, long requesterId, long version) { }
}
