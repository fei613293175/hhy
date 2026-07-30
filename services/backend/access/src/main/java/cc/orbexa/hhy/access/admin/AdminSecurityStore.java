package cc.orbexa.hhy.access.admin;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class AdminSecurityStore {
    private final JdbcTemplate jdbc;

    public AdminSecurityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdminAccount> findAdminByUsername(String username) {
        return jdbc.query("""
                SELECT u.id,u.username,u.password_hash,u.status,u.version,
                       EXISTS(SELECT 1 FROM hhy.admin_mfa_methods m
                              WHERE m.admin_user_id=u.id AND m.status='ACTIVE') AS mfa_enabled
                FROM hhy.admin_users u WHERE u.username=?
                """, (rs, row) -> new AdminAccount(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("status"), rs.getLong("version"), rs.getBoolean("mfa_enabled")), username)
                .stream().findFirst();
    }

    public Optional<AdminAccount> findAdmin(long adminId) {
        return jdbc.query("""
                SELECT u.id,u.username,u.password_hash,u.status,u.version,
                       EXISTS(SELECT 1 FROM hhy.admin_mfa_methods m
                              WHERE m.admin_user_id=u.id AND m.status='ACTIVE') AS mfa_enabled
                FROM hhy.admin_users u WHERE u.id=?
                """, (rs, row) -> new AdminAccount(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("status"), rs.getLong("version"), rs.getBoolean("mfa_enabled")), adminId)
                .stream().findFirst();
    }

    public List<String> permissionCodes(long adminId) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.code
                FROM hhy.admin_user_roles ur
                JOIN hhy.admin_roles r ON r.id=ur.role_id AND r.status='ACTIVE'
                JOIN hhy.admin_role_permissions rp ON rp.role_id=r.id
                JOIN hhy.admin_permissions p ON p.id=rp.permission_id
                WHERE ur.admin_id=? AND p.code<>? ORDER BY p.code
                """, String.class, adminId, AdminPrincipal.IDEMPOTENCY_REPLAY_MARKER);
    }

    public long recentPasswordFailures(long adminId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs l
                WHERE l.admin_user_id=? AND l.event_type='PASSWORD_LOGIN'
                  AND l.result='FAILED' AND l.created_at>=?
                  AND l.created_at>COALESCE((SELECT max(s.created_at) FROM hhy.admin_login_logs s
                                             WHERE s.admin_user_id=? AND s.event_type='PASSWORD_LOGIN'
                                               AND s.result='SUCCESS'),'-infinity')
                """, Long.class, adminId, time(since), adminId);
        return count == null ? 0 : count;
    }

    public long recentLoginFailuresByIp(String ip, Instant since) {
        if (ip == null || ip.isBlank()) return 0;
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs
                WHERE ip=? AND event_type='PASSWORD_LOGIN' AND result='FAILED' AND created_at>=?
                """, Long.class, ip, time(since));
        return count == null ? 0 : count;
    }

    public long recentLoginFailuresByDevice(String device, Instant since) {
        if (device == null || device.isBlank()) return 0;
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs
                WHERE device_fingerprint=? AND event_type='PASSWORD_LOGIN'
                  AND result='FAILED' AND created_at>=?
                """, Long.class, device, time(since));
        return count == null ? 0 : count;
    }

    public long recentMfaFailures(long adminId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs
                WHERE admin_user_id=? AND event_type LIKE 'MFA_%'
                  AND result='FAILED' AND created_at>=?
                """, Long.class, adminId, time(since));
        return count == null ? 0 : count;
    }

    public long recentMfaFailuresByIp(String ip, Instant since) {
        if (ip == null || ip.isBlank()) return 0;
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs
                WHERE ip=? AND event_type LIKE 'MFA_%' AND result='FAILED' AND created_at>=?
                """, Long.class, ip, time(since));
        return count == null ? 0 : count;
    }

    public long recentMfaFailuresByDevice(String device, Instant since) {
        if (device == null || device.isBlank()) return 0;
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_login_logs
                WHERE device_fingerprint=? AND event_type LIKE 'MFA_%'
                  AND result='FAILED' AND created_at>=?
                """, Long.class, device, time(since));
        return count == null ? 0 : count;
    }

    public Instant oldestPasswordFailure(long adminId, Instant since) {
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE admin_user_id=? AND event_type='PASSWORD_LOGIN'
                  AND result='FAILED' AND created_at>=?
                """, adminId, time(since));
    }

    public Instant oldestLoginFailureByIp(String ip, Instant since) {
        if (ip == null || ip.isBlank()) return null;
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE ip=? AND event_type='PASSWORD_LOGIN' AND result='FAILED' AND created_at>=?
                """, ip, time(since));
    }

    public Instant oldestLoginFailureByDevice(String device, Instant since) {
        if (device == null || device.isBlank()) return null;
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE device_fingerprint=? AND event_type='PASSWORD_LOGIN'
                  AND result='FAILED' AND created_at>=?
                """, device, time(since));
    }

    public Instant oldestMfaFailure(long adminId, Instant since) {
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE admin_user_id=? AND event_type LIKE 'MFA_%'
                  AND result='FAILED' AND created_at>=?
                """, adminId, time(since));
    }

    public Instant oldestMfaFailureByIp(String ip, Instant since) {
        if (ip == null || ip.isBlank()) return null;
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE ip=? AND event_type LIKE 'MFA_%' AND result='FAILED' AND created_at>=?
                """, ip, time(since));
    }

    public Instant oldestMfaFailureByDevice(String device, Instant since) {
        if (device == null || device.isBlank()) return null;
        return oldest("""
                SELECT min(created_at) FROM hhy.admin_login_logs
                WHERE device_fingerprint=? AND event_type LIKE 'MFA_%'
                  AND result='FAILED' AND created_at>=?
                """, device, time(since));
    }

    public void lockRateLimitBuckets(List<String> buckets) {
        List<String> ordered = new ArrayList<>(buckets.stream().distinct().toList());
        Collections.sort(ordered);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                for (String bucket : ordered) {
                    statement.setString(1, bucket);
                    statement.execute();
                }
            }
            return null;
        });
    }

    private Instant oldest(String sql, Object... arguments) {
        List<Instant> values = jdbc.query(sql, (rs, row) -> {
            OffsetDateTime value = rs.getObject(1, OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        }, arguments);
        return values.isEmpty() ? null : values.getFirst();
    }

    public long createSession(
            long adminId, String accessJti, Instant expiresAt, String ip, String device) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.admin_sessions(
                      admin_user_id,access_jti,mfa_level,device_fingerprint,ip,expires_at,last_active_at
                    ) VALUES (?,?, 'NONE',?,?,?,clock_timestamp())
                    """, new String[] {"id"});
            statement.setLong(1, adminId);
            statement.setString(2, accessJti);
            statement.setString(3, device);
            statement.setString(4, ip);
            statement.setObject(5, time(expiresAt));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("Administrator session insert did not return an id");
        }
        return id.longValue();
    }

    public Optional<SessionRow> findSession(long sessionId) {
        return jdbc.query("""
                SELECT s.id,s.admin_user_id,s.access_jti,s.mfa_level,s.expires_at,s.revoked_at,s.version,
                       u.username,u.status AS admin_status
                FROM hhy.admin_sessions s JOIN hhy.admin_users u ON u.id=s.admin_user_id
                WHERE s.id=?
                """, (rs, row) -> session(rs), sessionId).stream().findFirst();
    }

    public Optional<AdminPrincipal> authenticate(AdminTokenService.TokenClaims claims, Instant now) {
        long adminId = Long.parseLong(claims.sub());
        record PrincipalRow(long adminId, long sessionId, long version, String jti, String username) { }
        List<PrincipalRow> rows = jdbc.query("""
                SELECT s.id,s.version,s.access_jti,u.id AS admin_id,u.username
                FROM hhy.admin_sessions s JOIN hhy.admin_users u ON u.id=s.admin_user_id
                WHERE s.id=? AND s.admin_user_id=? AND s.access_jti=? AND s.version=?
                  AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='ACTIVE'
                  AND (s.mfa_level<>'NONE' OR NOT EXISTS(
                       SELECT 1 FROM hhy.admin_mfa_methods m
                       WHERE m.admin_user_id=u.id AND m.status='ACTIVE'))
                """, (rs, row) -> new PrincipalRow(
                rs.getLong("admin_id"), rs.getLong("id"), rs.getLong("version"),
                rs.getString("access_jti"), rs.getString("username")),
                claims.sid(), adminId, claims.jti(), claims.ver(), time(now));
        return rows.stream().findFirst().map(row -> new AdminPrincipal(
                row.adminId(), row.sessionId(), row.version(), row.jti(), row.username(),
                java.util.Set.copyOf(permissionCodes(row.adminId()))));
    }

    public Optional<AdminPrincipal> authenticateRevokedReplay(
            AdminTokenService.TokenClaims claims, Instant now) {
        long adminId = Long.parseLong(claims.sub());
        record ReplayRow(long adminId, long sessionId, String jti, String username) { }
        List<ReplayRow> rows = jdbc.query("""
                SELECT s.id,s.access_jti,u.id AS admin_id,u.username
                FROM hhy.admin_sessions s JOIN hhy.admin_users u ON u.id=s.admin_user_id
                WHERE s.id=? AND s.admin_user_id=? AND s.access_jti=?
                  AND s.revoked_at IS NOT NULL AND s.expires_at>? AND u.status='ACTIVE'
                """, (rs, row) -> new ReplayRow(
                rs.getLong("admin_id"), rs.getLong("id"),
                rs.getString("access_jti"), rs.getString("username")),
                claims.sid(), adminId, claims.jti(), time(now));
        return rows.stream().findFirst().map(row -> new AdminPrincipal(
                row.adminId(), row.sessionId(), claims.ver(), row.jti(), row.username(),
                java.util.Set.of(), true));
    }

    public boolean verifyMfaTicket(AdminTokenService.TokenClaims claims, Instant now) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_sessions s JOIN hhy.admin_users u ON u.id=s.admin_user_id
                WHERE s.id=? AND s.admin_user_id=? AND s.access_jti=? AND s.version=?
                  AND s.revoked_at IS NULL AND s.expires_at>? AND s.mfa_level='NONE' AND u.status='ACTIVE'
                """, Long.class, claims.sid(), Long.parseLong(claims.sub()), claims.jti(), claims.ver(), time(now));
        return count != null && count == 1;
    }

    public boolean upgradeSessionMfa(long sessionId, long expectedVersion) {
        return jdbc.update("""
                UPDATE hhy.admin_sessions SET mfa_level='VERIFIED',version=version+1
                WHERE id=? AND version=? AND revoked_at IS NULL AND mfa_level='NONE'
                """, sessionId, expectedVersion) == 1;
    }

    public boolean revokeSession(long sessionId, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.admin_sessions SET revoked_at=?,version=version+1
                WHERE id=? AND version=? AND revoked_at IS NULL
                """, time(now), sessionId, expectedVersion) == 1;
    }

    public int revokeAllSessions(long adminId, Instant now) {
        return jdbc.update("""
                UPDATE hhy.admin_sessions SET revoked_at=?,version=version+1
                WHERE admin_user_id=? AND revoked_at IS NULL
                """, time(now), adminId);
    }

    public Optional<MfaMethodRow> mfaMethod(long adminId) {
        return jdbc.query("""
                SELECT id,admin_user_id,method,secret_ref,status,version,created_at,updated_at
                FROM hhy.admin_mfa_methods WHERE admin_user_id=? AND method='TOTP'
                """, (rs, row) -> new MfaMethodRow(
                rs.getLong("id"), rs.getLong("admin_user_id"), rs.getString("method"),
                rs.getString("secret_ref"), rs.getString("status"), rs.getLong("version"),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("updated_at", OffsetDateTime.class))), adminId).stream().findFirst();
    }

    public long beginMfaEnrollment(long adminId, String secretRef, Instant now) {
        Optional<MfaMethodRow> existing = mfaMethod(adminId);
        if (existing.isEmpty()) {
            var keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO hhy.admin_mfa_methods(admin_user_id,method,secret_ref,status,created_at)
                        VALUES (?, 'TOTP',?, 'PENDING',?)
                        """, new String[] {"id"});
                statement.setLong(1, adminId);
                statement.setString(2, secretRef);
                statement.setObject(3, time(now));
                return statement;
            }, keys);
            Number id = keys.getKey();
            if (id == null) {
                throw new IllegalStateException("MFA enrollment insert did not return an id");
            }
            return id.longValue();
        }
        MfaMethodRow row = existing.get();
        if ("ACTIVE".equals(row.status())) return -1;
        int updated = jdbc.update("""
                UPDATE hhy.admin_mfa_methods
                SET status='PENDING',secret_ref=?,confirmed_at=NULL,disabled_at=NULL,
                    last_accepted_step=NULL,version=version+1
                WHERE id=? AND version=? AND status IN ('PENDING','DISABLED')
                """, secretRef, row.id(), row.version());
        if (updated != 1) {
            return -1;
        }
        return row.id();
    }

    public boolean confirmMfa(long id, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.admin_mfa_methods
                SET status='ACTIVE',confirmed_at=?,version=version+1
                WHERE id=? AND version=? AND status='PENDING'
                """, time(now), id, expectedVersion) == 1;
    }

    public boolean disableMfa(long id, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.admin_mfa_methods
                SET status='DISABLED',secret_ref=NULL,disabled_at=?,version=version+1
                WHERE id=? AND version=? AND status='ACTIVE'
                """, time(now), id, expectedVersion) == 1;
    }

    public boolean consumeMfaStep(
            long methodId, String expectedStatus, long expectedVersion, long step) {
        return jdbc.update("""
                UPDATE hhy.admin_mfa_methods
                SET last_accepted_step=?,version=version+1
                WHERE id=? AND status=? AND version=?
                  AND (last_accepted_step IS NULL OR last_accepted_step < ?)
                """, step, methodId, expectedStatus, expectedVersion, step) == 1;
    }

    public void expireUnusedRecoveryCodes(long adminId, Instant now) {
        jdbc.update("""
                UPDATE hhy.admin_recovery_codes SET expires_at=?
                WHERE admin_user_id=? AND used_at IS NULL AND (expires_at IS NULL OR expires_at>?)
                """, time(now), adminId, time(now));
    }

    public boolean updatePassword(long adminId, long expectedVersion, String passwordHash) {
        return jdbc.update("""
                UPDATE hhy.admin_users SET password_hash=?,version=version+1
                WHERE id=? AND version=? AND status='ACTIVE'
                """, passwordHash, adminId, expectedVersion) == 1;
    }

    public SecuritySnapshot securitySnapshot(long adminId, Instant now) {
        AdminAccount admin = findAdmin(adminId).orElseThrow();
        List<String> methods = jdbc.queryForList("""
                SELECT method FROM hhy.admin_mfa_methods
                WHERE admin_user_id=? AND status='ACTIVE' ORDER BY method
                """, String.class, adminId);
        Long sessions = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_sessions
                WHERE admin_user_id=? AND revoked_at IS NULL AND expires_at>?
                """, Long.class, adminId, time(now));
        Long recovery = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.admin_recovery_codes
                WHERE admin_user_id=? AND used_at IS NULL AND (expires_at IS NULL OR expires_at>?)
                """, Long.class, adminId, time(now));
        Optional<LoginFact> lastLogin = jdbc.query("""
                SELECT created_at,ip FROM hhy.admin_login_logs
                WHERE admin_user_id=? AND result='SUCCESS' ORDER BY created_at DESC,id DESC LIMIT 1
                """, (rs, row) -> new LoginFact(
                instant(rs.getObject("created_at", OffsetDateTime.class)), rs.getString("ip")), adminId)
                .stream().findFirst();
        Instant passwordChanged = jdbc.query("""
                SELECT created_at FROM hhy.admin_operation_logs
                WHERE admin_id=? AND action='ADMIN_PASSWORD_CHANGED'
                ORDER BY created_at DESC,id DESC LIMIT 1
                """, (rs, row) -> instant(rs.getObject(1, OffsetDateTime.class)), adminId)
                .stream().findFirst().orElse(null);
        return new SecuritySnapshot(
                admin.id(), admin.username(), methods, sessions == null ? 0 : sessions,
                passwordChanged, lastLogin.map(LoginFact::at).orElse(null),
                lastLogin.map(LoginFact::ip).orElse(null), recovery == null ? 0 : recovery);
    }

    public void loginLog(
            Long adminId, String usernameMasked, String eventType, String result,
            String failureCode, String ip, String device, String requestId) {
        jdbc.update("""
                INSERT INTO hhy.admin_login_logs(
                  admin_user_id,username_masked,event_type,result,failure_code,ip,device_fingerprint,request_id
                ) VALUES (?,?,?,?,?,?,?,?)
                """, adminId, usernameMasked, eventType, result, failureCode, ip, device, requestId);
    }

    public void operationLog(
            long adminId, String action, String resource, Long resourceId,
            String beforeJson, String afterJson, String ip) {
        jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip
                ) VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, adminId, action, resource, resourceId, beforeJson, afterJson, ip);
    }

    public IdempotencyClaim claimIdempotency(String scope, String key, String requestHash, Instant expiresAt) {
        jdbc.update("""
                DELETE FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at<=clock_timestamp()
                """, scope, key);
        int inserted = jdbc.update("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?) ON CONFLICT (scope,idem_key) DO NOTHING
                """, scope, key, requestHash, time(expiresAt));
        IdempotencyRow row = jdbc.queryForObject("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, number) -> idempotency(rs), scope, key);
        if (row == null) {
            throw new IllegalStateException("Idempotency claim disappeared");
        }
        return new IdempotencyClaim(row, inserted == 0);
    }

    public Optional<IdempotencyRow> findIdempotency(String scope, String key) {
        return jdbc.query("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, number) -> idempotency(rs), scope, key)
                .stream().findFirst();
    }

    public void completeIdempotency(long id, String responseRef) {
        int updated = jdbc.update("""
                UPDATE hhy.idempotency_records SET response_ref=?
                WHERE id=? AND response_ref IS NULL
                """, responseRef, id);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency result was already completed");
        }
    }

    public void completeIdempotencySnapshot(
            long id, String responseRef, String responseType, String responsePayloadCiphertext) {
        if (responseType == null || responseType.isBlank()
                || responsePayloadCiphertext == null || responsePayloadCiphertext.isBlank()) {
            throw new IllegalArgumentException("Idempotency snapshot pair is required");
        }
        int updated = jdbc.update("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL
                  AND response_type IS NULL AND response_payload_ciphertext IS NULL
                """, responseRef, responseType, responsePayloadCiphertext, id);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency snapshot was already completed");
        }
    }

    private static SessionRow session(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime revoked = rs.getObject("revoked_at", OffsetDateTime.class);
        return new SessionRow(
                rs.getLong("id"), rs.getLong("admin_user_id"), rs.getString("access_jti"),
                rs.getString("mfa_level"), instant(rs.getObject("expires_at", OffsetDateTime.class)),
                revoked == null ? null : instant(revoked), rs.getLong("version"),
                rs.getString("username"), rs.getString("admin_status"));
    }

    private static IdempotencyRow idempotency(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IdempotencyRow(
                rs.getLong("id"), rs.getString("request_hash"), rs.getString("response_ref"),
                rs.getString("response_type"), rs.getString("response_payload_ciphertext"));
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record AdminAccount(
            long id, String username, String passwordHash, String status, long version, boolean mfaEnabled) { }
    public record SessionRow(
            long id, long adminId, String accessJti, String mfaLevel, Instant expiresAt,
            Instant revokedAt, long version, String username, String adminStatus) { }
    public record MfaMethodRow(
            long id, long adminId, String method, String secretRef, String status, long version,
            Instant createdAt, Instant updatedAt) { }
    public record SecuritySnapshot(
            long adminId, String username, List<String> methods, long activeSessions,
            Instant lastPasswordChangedAt, Instant lastLoginAt, String lastLoginIp, long recoveryCodesRemaining) { }
    public record LoginFact(Instant at, String ip) { }
    public record IdempotencyRow(
            long id, String requestHash, String responseRef,
            String responseType, String responsePayloadCiphertext) {
        public IdempotencyRow(long id, String requestHash, String responseRef) {
            this(id, requestHash, responseRef, null, null);
        }
    }
    public record IdempotencyClaim(IdempotencyRow row, boolean replay) { }
}
