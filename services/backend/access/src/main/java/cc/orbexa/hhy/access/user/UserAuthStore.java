package cc.orbexa.hhy.access.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserAuthStore {
    private final JdbcTemplate jdbc;

    public UserAuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public IdempotencyClaim claimIdempotency(String scope, String key, String requestHash, Instant expiresAt) {
        jdbc.update("DELETE FROM hhy.idempotency_records WHERE scope=? AND idem_key=? AND expires_at<=clock_timestamp()", scope, key);
        int inserted = jdbc.update("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?) ON CONFLICT (scope,idem_key) DO NOTHING
                """, scope, key, requestHash, time(expiresAt));
        IdempotencyRow row = jdbc.queryForObject("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, number) -> new IdempotencyRow(
                rs.getLong("id"), rs.getString("request_hash"), rs.getString("response_ref"),
                rs.getString("response_type"), rs.getString("response_payload_ciphertext")), scope, key);
        if (row == null) throw new IllegalStateException("User idempotency claim disappeared");
        return new IdempotencyClaim(row, inserted == 0);
    }

    public void abandonIdempotency(long id) {
        jdbc.update("""
                DELETE FROM hhy.idempotency_records WHERE id=? AND response_ref IS NULL
                  AND response_type IS NULL AND response_payload_ciphertext IS NULL
                """, id);
    }

    public void completeIdempotencySnapshot(long id, String responseRef, String responseType, String ciphertext) {
        int updated = jdbc.update("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL
                  AND response_type IS NULL AND response_payload_ciphertext IS NULL
                """, responseRef, responseType, ciphertext, id);
        if (updated != 1) throw new IllegalStateException("User idempotency result was already completed");
    }

    public long createChallenge(String type, String answerHash, String clientNonceHash,
                                String deviceFingerprintHash, Instant expiresAt, int maxAttempts) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.auth_security_challenges(
                  type,answer_hash,client_nonce_hash,device_fingerprint_hash,
                  expires_at,attempts,max_attempts)
                VALUES (?,?,?,?,?,0,?) RETURNING id
                """, Long.class, type, answerHash, clientNonceHash, deviceFingerprintHash,
                time(expiresAt), maxAttempts);
        if (id == null) throw new IllegalStateException("Challenge insert did not return an id");
        return id;
    }

    public Optional<ChallengeRow> findChallengeForUpdate(long id) {
        return jdbc.query("""
                SELECT id,type,answer_hash,expires_at,used_at,attempts,max_attempts
                FROM hhy.auth_security_challenges WHERE id=? FOR UPDATE
                """, (rs, row) -> new ChallengeRow(
                rs.getLong("id"), rs.getString("type"), rs.getString("answer_hash"),
                instant(rs.getObject("expires_at", OffsetDateTime.class)),
                instant(rs.getObject("used_at", OffsetDateTime.class)),
                rs.getInt("attempts"), rs.getInt("max_attempts")), id).stream().findFirst();
    }

    public void failChallenge(long id) {
        jdbc.update("UPDATE hhy.auth_security_challenges SET attempts=attempts+1 WHERE id=? AND used_at IS NULL", id);
    }

    public boolean consumeChallenge(long id) {
        return jdbc.update("""
                UPDATE hhy.auth_security_challenges SET used_at=clock_timestamp()
                WHERE id=? AND used_at IS NULL AND expires_at>clock_timestamp()
                  AND attempts<max_attempts
                """, id) == 1;
    }

    public Optional<CredentialRow> findCredentialForUpdate(String phone) {
        return jdbc.query("""
                SELECT u.id AS user_id,u.status,c.id AS credential_id,c.password_hash,c.failed_count,c.locked_until
                FROM hhy.users u JOIN hhy.user_credentials c ON c.user_id=u.id
                WHERE u.phone=? FOR UPDATE OF c
                """, (rs, row) -> new CredentialRow(
                rs.getLong("user_id"), rs.getString("status"), rs.getLong("credential_id"),
                rs.getString("password_hash"), rs.getInt("failed_count"),
                instant(rs.getObject("locked_until", OffsetDateTime.class))), phone).stream().findFirst();
    }

    public Optional<CredentialRow> findCredentialForUpdate(long userId) {
        return jdbc.query("""
                SELECT u.id AS user_id,u.status,c.id AS credential_id,c.password_hash,c.failed_count,c.locked_until
                FROM hhy.users u JOIN hhy.user_credentials c ON c.user_id=u.id
                WHERE u.id=? FOR UPDATE OF c
                """, (rs, row) -> new CredentialRow(
                rs.getLong("user_id"), rs.getString("status"), rs.getLong("credential_id"),
                rs.getString("password_hash"), rs.getInt("failed_count"),
                instant(rs.getObject("locked_until", OffsetDateTime.class))), userId).stream().findFirst();
    }

    /**
     * Serializes expiry reconciliation with administrator writes so an expired
     * restriction cannot leave the account permanently marked RESTRICTED.
     */
    public boolean reconcileExpiredRestrictions(long userId, Instant now) {
        if (jdbc.queryForList("SELECT id FROM hhy.users WHERE id=? FOR UPDATE", Long.class, userId).isEmpty()) {
            return false;
        }
        jdbc.update("""
                UPDATE hhy.user_restrictions SET status='EXPIRED',removed_at=?,version=version+1
                WHERE user_id=? AND status='ACTIVE' AND expires_at IS NOT NULL AND expires_at<=?
                """, time(now), userId, time(now));
        int restored = jdbc.update("""
                UPDATE hhy.users SET status='ACTIVE',version=version+1
                WHERE id=? AND status='RESTRICTED' AND NOT EXISTS (
                  SELECT 1 FROM hhy.user_restrictions
                  WHERE user_id=? AND status='ACTIVE'
                    AND (expires_at IS NULL OR expires_at>?)
                )
                """, userId, userId, time(now));
        if (restored == 1) {
            jdbc.update("""
                    INSERT INTO hhy.user_status_logs(user_id,from_status,to_status,reason,operator)
                    VALUES (?,'RESTRICTED','ACTIVE','全部账号限制已到期','SYSTEM:RESTRICTION_EXPIRY')
                    """, userId);
        }
        return restored == 1;
    }

    public void recordPasswordFailure(long credentialId, int nextCount, Instant lockedUntil) {
        jdbc.update("UPDATE hhy.user_credentials SET failed_count=?,locked_until=? WHERE id=?",
                nextCount, time(lockedUntil), credentialId);
    }

    public void clearPasswordFailures(long credentialId) {
        jdbc.update("UPDATE hhy.user_credentials SET failed_count=0,locked_until=NULL WHERE id=?", credentialId);
    }

    public Optional<UserRow> findUser(String phone) {
        return jdbc.query("SELECT id,status FROM hhy.users WHERE phone=?",
                (rs, row) -> new UserRow(rs.getLong("id"), rs.getString("status")), phone)
                .stream().findFirst();
    }

    public long upsertDevice(long userId, String fingerprintHash, String model, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.user_devices(user_id,device_fingerprint,model,last_seen)
                VALUES (?,?,?,?)
                ON CONFLICT (user_id,device_fingerprint) DO UPDATE
                  SET model=COALESCE(EXCLUDED.model,hhy.user_devices.model),last_seen=EXCLUDED.last_seen
                RETURNING id
                """, Long.class, userId, fingerprintHash, model, time(now));
        if (id == null) throw new IllegalStateException("Device upsert did not return an id");
        return id;
    }

    public long createSession(long userId, Long deviceId, String accessJti,
                              String refreshHash, Instant expiresAt) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.user_sessions(user_id,access_jti,refresh_hash,device_id,expires_at)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, userId, accessJti, refreshHash, deviceId, time(expiresAt));
        if (id == null) throw new IllegalStateException("Session insert did not return an id");
        return id;
    }

    public Optional<UserSessionRow> findSessionForUpdate(String refreshHash) {
        return jdbc.query("""
                SELECT s.id,s.user_id,s.access_jti,s.refresh_hash,s.device_id,s.expires_at,s.version,
                       u.status AS user_status,d.model,d.last_seen
                FROM hhy.user_sessions s JOIN hhy.users u ON u.id=s.user_id
                LEFT JOIN hhy.user_devices d ON d.id=s.device_id AND d.user_id=s.user_id
                WHERE s.refresh_hash=? FOR UPDATE OF s
                """, (rs, row) -> new UserSessionRow(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("access_jti"),
                rs.getString("refresh_hash"), nullableLong(rs, "device_id"),
                instant(rs.getObject("expires_at", OffsetDateTime.class)), rs.getLong("version"),
                rs.getString("user_status"), rs.getString("model"),
                instant(rs.getObject("last_seen", OffsetDateTime.class))), refreshHash).stream().findFirst();
    }

    public Optional<Long> findSessionUserId(String refreshHash) {
        return jdbc.query("SELECT user_id FROM hhy.user_sessions WHERE refresh_hash=?",
                (rs, row) -> rs.getLong("user_id"), refreshHash).stream().findFirst();
    }

    /**
     * Re-validates a signed access token against the live user/session state.
     * A signature alone is insufficient because password changes and device
     * revocations must take effect before a token's own expiry time.
     */
    @Transactional
    public Optional<UserPrincipal> authenticate(UserTokenService.AccessClaims claims, Instant now) {
        long userId = Long.parseLong(claims.sub());
        reconcileExpiredRestrictions(userId, now);
        return jdbc.query("""
                SELECT s.user_id,s.id AS session_id,s.version,s.access_jti,u.status AS user_status
                FROM hhy.user_sessions s JOIN hhy.users u ON u.id=s.user_id
                WHERE s.id=? AND s.user_id=? AND s.version=? AND s.access_jti=?
                  AND s.refresh_hash IS NOT NULL AND s.expires_at>?
                  AND u.status IN ('ACTIVE','FROZEN','RESTRICTED')
                """, (rs, row) -> new UserPrincipal(
                rs.getLong("user_id"), rs.getLong("session_id"), rs.getLong("version"),
                rs.getString("access_jti"), rs.getString("user_status")),
                claims.sid(), userId, claims.ver(), claims.jti(), time(now))
                .stream().findFirst();
    }

    public Optional<SelfRow> findSelf(long userId) {
        return jdbc.query("""
                SELECT u.id,u.phone,u.status,u.created_at,u.version,
                       p.nickname,p.avatar,p.bio
                FROM hhy.users u
                LEFT JOIN hhy.user_profiles p ON p.user_id=u.id
                WHERE u.id=?
                """, (rs, row) -> new SelfRow(
                rs.getLong("id"), rs.getString("phone"), rs.getString("nickname"),
                rs.getString("avatar"), rs.getString("bio"), rs.getString("status"),
                instant(rs.getObject("created_at", OffsetDateTime.class)), rs.getLong("version")), userId)
                .stream().findFirst();
    }

    public Optional<SelfRow> findSelfForUpdate(long userId) {
        return jdbc.query("""
                SELECT u.id,u.phone,u.status,u.created_at,u.version,
                       p.nickname,p.avatar,p.bio
                FROM hhy.users u
                LEFT JOIN hhy.user_profiles p ON p.user_id=u.id
                WHERE u.id=? FOR UPDATE OF u
                """, (rs, row) -> new SelfRow(
                rs.getLong("id"), rs.getString("phone"), rs.getString("nickname"),
                rs.getString("avatar"), rs.getString("bio"), rs.getString("status"),
                instant(rs.getObject("created_at", OffsetDateTime.class)), rs.getLong("version")), userId)
                .stream().findFirst();
    }

    public Optional<Long> requestCancellation(long userId, long expectedVersion, String reason, Instant now) {
        Optional<Long> nextVersion = jdbc.query("""
                UPDATE hhy.users SET status='CANCEL_PENDING',version=version+1
                WHERE id=? AND status='ACTIVE' AND version=? RETURNING version
                """, (rs, row) -> rs.getLong("version"), userId, expectedVersion).stream().findFirst();
        if (nextVersion.isEmpty()) return Optional.empty();
        jdbc.update("""
                INSERT INTO hhy.user_status_logs(user_id,from_status,to_status,reason,operator)
                VALUES (?,'ACTIVE','CANCEL_PENDING',?,'SELF_SERVICE')
                """, userId, reason);
        jdbc.update("""
                UPDATE hhy.user_sessions SET refresh_hash=NULL,expires_at=?,version=version+1
                WHERE user_id=? AND refresh_hash IS NOT NULL
                """, time(now), userId);
        return nextVersion;
    }

    public TicketRow createSupportTicket(long userId, String ticketNo, String category,
                                         String subject, String content, List<Long> attachmentIds,
                                         Instant now) {
        Long ticketId = jdbc.queryForObject("""
                INSERT INTO hhy.support_tickets(ticket_no,user_id,type,biz_type,status)
                VALUES (?,?,?,?,'OPEN') RETURNING id
                """, Long.class, ticketNo, userId, category, subject);
        if (ticketId == null) throw new IllegalStateException("Support ticket insert did not return an id");
        Long messageId = jdbc.queryForObject("""
                INSERT INTO hhy.ticket_messages(ticket_id,sender_type,sender_id,body)
                VALUES (?,'USER',?,?) RETURNING id
                """, Long.class, ticketId, userId, content);
        for (long mediaId : attachmentIds) {
            jdbc.update("""
                    INSERT INTO hhy.ticket_attachments(ticket_id,message_id,media_id)
                    SELECT ?,?,id FROM hhy.media_objects WHERE id=? AND owner_id=?
                    """, ticketId, messageId, mediaId, userId);
        }
        return new TicketRow(ticketId, ticketNo, category, subject, "OPEN", null, now, now, 0L);
    }

    public List<SecuritySessionRow> listActiveSessions(long userId, int offset, int limit,
                                                        long currentSessionId, Instant now) {
        return jdbc.query("""
                SELECT s.id,s.version,s.device_id,s.created_at,s.expires_at,d.model,d.last_seen
                FROM hhy.user_sessions s
                LEFT JOIN hhy.user_devices d ON d.id=s.device_id AND d.user_id=s.user_id
                WHERE s.user_id=? AND s.refresh_hash IS NOT NULL AND s.expires_at>?
                ORDER BY s.created_at DESC,s.id DESC OFFSET ? LIMIT ?
                """, (rs, row) -> new SecuritySessionRow(
                rs.getLong("id"), rs.getLong("version"), nullableLong(rs, "device_id"),
                rs.getString("model"), instant(rs.getObject("last_seen", OffsetDateTime.class)),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("expires_at", OffsetDateTime.class)), rs.getLong("id") == currentSessionId),
                userId, time(now), offset, limit);
    }

    public long countActiveSessions(long userId, Instant now) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.user_sessions
                WHERE user_id=? AND refresh_hash IS NOT NULL AND expires_at>?
                """, Long.class, userId, time(now));
        return count == null ? 0L : count;
    }

    public Optional<Long> revokeSession(long userId, long sessionId, Instant now) {
        return jdbc.query("""
                UPDATE hhy.user_sessions SET refresh_hash=NULL,expires_at=?,version=version+1
                WHERE id=? AND user_id=? AND refresh_hash IS NOT NULL AND expires_at>?
                RETURNING version
                """, (rs, row) -> rs.getLong("version"), time(now), sessionId, userId, time(now))
                .stream().findFirst();
    }

    public boolean rotateSession(long sessionId, long expectedVersion, String oldRefreshHash,
                                 String newAccessJti, String newRefreshHash, Instant refreshExpiresAt) {
        return jdbc.update("""
                UPDATE hhy.user_sessions SET access_jti=?,refresh_hash=?,expires_at=?,version=version+1
                WHERE id=? AND version=? AND refresh_hash=?
                """, newAccessJti, newRefreshHash, time(refreshExpiresAt),
                sessionId, expectedVersion, oldRefreshHash) == 1;
    }

    public void touchDevice(Long deviceId, long userId, Instant now) {
        if (deviceId != null) jdbc.update(
                "UPDATE hhy.user_devices SET last_seen=? WHERE id=? AND user_id=?",
                time(now), deviceId, userId);
    }

    public boolean smsCooldownActive(String phone, String scene, Instant since) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.sms_send_logs
                WHERE phone=? AND scene=? AND created_at>=?
                """, Integer.class, phone, scene, time(since));
        return count != null && count > 0;
    }

    public int smsPhoneCountToday(String phone) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.sms_send_logs WHERE phone=?
                  AND created_at>=date_trunc('day',clock_timestamp())
                """, Integer.class, phone);
        return count == null ? 0 : count;
    }

    public int smsIpCountToday(String ip) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.sms_send_logs WHERE ip=?
                  AND created_at>=date_trunc('day',clock_timestamp())
                """, Integer.class, ip);
        return count == null ? 0 : count;
    }

    public long createSmsCode(String phone, String scene, String codeHash,
                              Instant expiresAt, int maxAttempts) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.sms_verification_codes(
                  phone,scene,code_hash,expires_at,attempts,max_attempts)
                VALUES (?,?,?,?,0,?) RETURNING id
                """, Long.class, phone, scene, codeHash, time(expiresAt), maxAttempts);
        if (id == null) throw new IllegalStateException("SMS code insert did not return an id");
        return id;
    }

    public void recordSmsDelivery(long smsId, String phone, String scene, String templateCode,
                                  String providerMessageId, String ip) {
        jdbc.update("UPDATE hhy.sms_verification_codes SET provider_message_id=? WHERE id=?",
                providerMessageId, smsId);
        jdbc.update("""
                INSERT INTO hhy.sms_send_logs(phone,scene,template_code,provider_message_id,result,ip)
                VALUES (?,?,?,?,CAST(? AS jsonb),?)
                """, phone, scene, templateCode, providerMessageId, "{\"status\":\"SENT\"}", ip);
    }

    public Optional<SmsCodeRow> findSmsCodeForUpdate(String phone, String scene) {
        return jdbc.query("""
                SELECT id,code_hash,expires_at,used_at,attempts,max_attempts
                FROM hhy.sms_verification_codes
                WHERE phone=? AND scene=? AND used_at IS NULL
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new SmsCodeRow(
                rs.getLong("id"), rs.getString("code_hash"),
                instant(rs.getObject("expires_at", OffsetDateTime.class)),
                instant(rs.getObject("used_at", OffsetDateTime.class)),
                rs.getInt("attempts"), rs.getInt("max_attempts")), phone, scene).stream().findFirst();
    }

    public void failSmsCode(long id) {
        jdbc.update("UPDATE hhy.sms_verification_codes SET attempts=attempts+1 WHERE id=? AND used_at IS NULL", id);
    }

    public boolean consumeSmsCode(long id) {
        return jdbc.update("""
                UPDATE hhy.sms_verification_codes SET used_at=clock_timestamp()
                WHERE id=? AND used_at IS NULL AND expires_at>clock_timestamp()
                  AND attempts<max_attempts
                """, id) == 1;
    }

    public Optional<Long> findActiveInviter(String inviteCode) {
        return jdbc.query("SELECT user_id FROM hhy.invite_codes WHERE code=? AND status='ACTIVE' FOR SHARE",
                (rs, row) -> rs.getLong("user_id"), inviteCode).stream().findFirst();
    }

    public void lockRegistration(String phone) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", phone);
    }

    public long createUser(String phone) {
        Long id = jdbc.queryForObject(
                "INSERT INTO hhy.users(phone,status) VALUES (?,'ACTIVE') RETURNING id",
                Long.class, phone);
        if (id == null) throw new IllegalStateException("User insert did not return an id");
        return id;
    }

    public void createCredential(long userId, String passwordHash, Instant now) {
        jdbc.update("""
                INSERT INTO hhy.user_credentials(
                  user_id,password_hash,algorithm,password_changed_at,failed_count)
                VALUES (?,?,'BCRYPT',?,0)
                """, userId, passwordHash, time(now));
    }

    public void createProfile(long userId) {
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)",
                userId, "合伙人" + userId);
    }

    public void recordRegistration(long userId, String phone, String inviteCode,
                                   long inviterId, Long deviceId, String ip) {
        jdbc.update("""
                INSERT INTO hhy.registration_records(
                  user_id,channel,phone,invite_code,inviter_id,rule_version,ip,device_id)
                VALUES (?,'APP',?,?,?,'R02-V1.2.2',?,?)
                """, userId, phone, inviteCode, inviterId, ip, deviceId);
    }

    public List<CurrentAgreementVersion> findCurrentAgreementVersions() {
        return jdbc.query("""
                SELECT version_row.id,agreement.code,version_row.effective_at
                FROM hhy.agreements agreement
                JOIN hhy.agreement_versions version_row ON version_row.id=agreement.current_version_id
                WHERE version_row.effective_at IS NOT NULL AND version_row.effective_at<=clock_timestamp()
                ORDER BY agreement.code ASC
                """, (rs, row) -> new CurrentAgreementVersion(
                rs.getLong("id"), rs.getString("code"),
                instant(rs.getObject("effective_at", OffsetDateTime.class))));
    }

    public Optional<H5RegistrationPageRow> findH5RegistrationPage() {
        return jdbc.query("""
                SELECT version,config_json->>'title' AS title,
                       config_json->>'description' AS description
                FROM hhy.h5_page_configs
                WHERE page_code='H5-013' AND status IN ('ACTIVE','PUBLISHED')
                ORDER BY version DESC,id DESC LIMIT 1
                """, (rs, row) -> new H5RegistrationPageRow(
                rs.getLong("version"), rs.getString("title"), rs.getString("description")))
                .stream().findFirst();
    }

    public List<Long> validateAgreementVersions(List<String> versionIds) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String value : versionIds) {
            long versionId;
            try { versionId = Long.parseLong(value); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Agreement version id must be numeric"); }
            Integer exists = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM hhy.agreements agreement
                    JOIN hhy.agreement_versions version_row ON version_row.id=agreement.current_version_id
                    WHERE version_row.id=? AND version_row.effective_at IS NOT NULL
                      AND version_row.effective_at<=clock_timestamp()
                    """, Integer.class, versionId);
            if (exists == null || exists != 1) throw new IllegalArgumentException("Agreement version is not active");
            ids.add(versionId);
        }
        return List.copyOf(ids);
    }

    public void acceptAgreementVersions(long userId, Long deviceId, List<Long> versionIds) {
        for (long versionId : versionIds) {
            jdbc.update("""
                    INSERT INTO hhy.user_agreement_acceptances(user_id,version_id,device_id)
                    VALUES (?,?,?)
                    """, userId, versionId, deviceId);
        }
    }

    public void updatePasswordAndRevokeSessions(long credentialId, long userId,
                                                String passwordHash, Instant now) {
        jdbc.update("""
                UPDATE hhy.user_credentials SET password_hash=?,algorithm='BCRYPT',
                  password_changed_at=?,failed_count=0,locked_until=NULL WHERE id=?
                """, passwordHash, time(now), credentialId);
        jdbc.update("""
                UPDATE hhy.user_sessions SET refresh_hash=NULL,expires_at=?,version=version+1
                WHERE user_id=? AND refresh_hash IS NOT NULL
                """, time(now), userId);
    }

    public void recordLogin(long userId, String phone, Long deviceId, String ip, String method) {
        jdbc.update("""
                INSERT INTO hhy.login_logs(user_id,phone,ip,device_id,result)
                VALUES (?,?,?,?,CAST(? AS jsonb))
                """, userId, phone, ip, deviceId, "{\"status\":\"SUCCESS\",\"method\":\"" + method + "\"}");
    }

    private static Long nullableLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }

    public record CredentialRow(long userId, String userStatus, long credentialId,
                                String passwordHash, int failedCount, Instant lockedUntil) { }
    public record UserRow(long id, String status) { }
    public record ChallengeRow(long id, String type, String answerHash, Instant expiresAt,
                               Instant usedAt, int attempts, int maxAttempts) { }
    public record SmsCodeRow(long id, String codeHash, Instant expiresAt,
                             Instant usedAt, int attempts, int maxAttempts) { }
    public record UserSessionRow(long id, long userId, String accessJti, String refreshHash,
                                 Long deviceId, Instant expiresAt, long version, String userStatus,
                                 String deviceName, Instant lastSeen) { }
    public record IdempotencyRow(long id, String requestHash, String responseRef,
                                 String responseType, String responsePayloadCiphertext) { }
    public record IdempotencyClaim(IdempotencyRow row, boolean replay) { }
    public record CurrentAgreementVersion(long id, String code, Instant effectiveAt) { }
    public record H5RegistrationPageRow(long version, String title, String description) { }
    public record SecuritySessionRow(long id, long version, Long deviceId, String deviceName,
                                     Instant lastActiveAt, Instant createdAt, Instant expiresAt,
                                     boolean current) { }
    public record SelfRow(long id, String phone, String nickname, String avatarUrl, String bio,
                          String status, Instant createdAt, long version) { }
    public record TicketRow(long id, String ticketNo, String category, String subject, String status,
                            String assignee, Instant lastMessageAt, Instant createdAt, long version) { }
}
