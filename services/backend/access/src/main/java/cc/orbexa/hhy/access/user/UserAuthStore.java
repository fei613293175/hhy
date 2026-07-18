package cc.orbexa.hhy.access.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserAuthStore {
    private final JdbcTemplate jdbc;

    public UserAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public IdempotencyClaim claimIdempotency(
            String scope, String key, String requestHash, Instant expiresAt) {
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
                """, (rs, number) -> new IdempotencyRow(
                rs.getLong("id"), rs.getString("request_hash"), rs.getString("response_ref"),
                rs.getString("response_type"), rs.getString("response_payload_ciphertext")), scope, key);
        if (row == null) throw new IllegalStateException("User idempotency claim disappeared");
        return new IdempotencyClaim(row, inserted == 0);
    }

    public Optional<UserSessionRow> findSessionForUpdate(String refreshHash) {
        return jdbc.query("""
                SELECT s.id,s.user_id,s.access_jti,s.refresh_hash,s.device_id,s.expires_at,s.version,
                       u.status AS user_status,d.model,d.last_seen
                FROM hhy.user_sessions s
                JOIN hhy.users u ON u.id=s.user_id
                LEFT JOIN hhy.user_devices d ON d.id=s.device_id AND d.user_id=s.user_id
                WHERE s.refresh_hash=?
                FOR UPDATE OF s
                """, (rs, row) -> new UserSessionRow(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("access_jti"),
                rs.getString("refresh_hash"), nullableLong(rs, "device_id"),
                instant(rs.getObject("expires_at", OffsetDateTime.class)), rs.getLong("version"),
                rs.getString("user_status"), rs.getString("model"),
                instant(rs.getObject("last_seen", OffsetDateTime.class))), refreshHash)
                .stream().findFirst();
    }

    public boolean rotateSession(
            long sessionId, long expectedVersion, String oldRefreshHash,
            String newAccessJti, String newRefreshHash, Instant refreshExpiresAt) {
        return jdbc.update("""
                UPDATE hhy.user_sessions
                SET access_jti=?,refresh_hash=?,expires_at=?,version=version+1
                WHERE id=? AND version=? AND refresh_hash=?
                """, newAccessJti, newRefreshHash, time(refreshExpiresAt),
                sessionId, expectedVersion, oldRefreshHash) == 1;
    }

    public void touchDevice(Long deviceId, long userId, Instant now) {
        if (deviceId == null) return;
        jdbc.update("""
                UPDATE hhy.user_devices SET last_seen=? WHERE id=? AND user_id=?
                """, time(now), deviceId, userId);
    }

    public void completeIdempotencySnapshot(
            long id, String responseRef, String responseType, String ciphertext) {
        int updated = jdbc.update("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL
                  AND response_type IS NULL AND response_payload_ciphertext IS NULL
                """, responseRef, responseType, ciphertext, id);
        if (updated != 1) {
            throw new IllegalStateException("User idempotency result was already completed");
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record UserSessionRow(
            long id, long userId, String accessJti, String refreshHash, Long deviceId,
            Instant expiresAt, long version, String userStatus, String deviceName,
            Instant lastSeen) { }

    public record IdempotencyRow(
            long id, String requestHash, String responseRef,
            String responseType, String responsePayloadCiphertext) { }

    public record IdempotencyClaim(IdempotencyRow row, boolean replay) { }
}
