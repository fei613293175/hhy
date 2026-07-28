package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R14RealtimeContracts.Delivery;
import cc.orbexa.hhy.content.R14RealtimeContracts.GapWatermark;
import cc.orbexa.hhy.content.R14RealtimeContracts.Scope;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R14RealtimePostgresStore implements R14RealtimeStore {
    private final JdbcTemplate jdbc;

    public R14RealtimePostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public long nextSequence(long userId, Instant now) {
        Long value = jdbc.queryForObject("""
                INSERT INTO hhy.websocket_user_sequences(user_id,high_watermark,created_at,updated_at)
                VALUES (?,1,?,?)
                ON CONFLICT (user_id) DO UPDATE
                SET high_watermark=hhy.websocket_user_sequences.high_watermark+1,updated_at=EXCLUDED.updated_at
                RETURNING high_watermark
                """, Long.class, userId, time(now), time(now));
        if (value == null) throw new IllegalStateException("WebSocket sequence was not allocated");
        return value;
    }

    @Override
    public Delivery insert(
            UUID eventId, long userId, long serverSequence, String eventType,
            Long conversationId, Scope scope, String envelopeJson,
            boolean ackRequired, Instant expiresAt, Instant now) {
        return jdbc.queryForObject("""
                INSERT INTO hhy.websocket_deliveries(
                  event_id,user_id,server_sequence,event_type,conversation_id,affected_scope,
                  envelope,ack_required,expires_at,created_at)
                VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)
                RETURNING event_id,user_id,server_sequence,event_type,conversation_id,affected_scope,
                          envelope::text,ack_required,delivery_attempts,last_delivered_at,expires_at
                """, this::delivery, eventId, userId, serverSequence, eventType, conversationId,
                scope.name(), envelopeJson, ackRequired, time(expiresAt), time(now));
    }

    @Override
    public long highWatermark(long userId) {
        List<Long> values = jdbc.queryForList(
                "SELECT high_watermark FROM hhy.websocket_user_sequences WHERE user_id=?",
                Long.class, userId);
        return values.isEmpty() ? 0 : values.getFirst();
    }

    @Override
    public List<Delivery> deliveriesAfter(long userId, long sequence, long through, Instant now) {
        return jdbc.query("""
                SELECT event_id,user_id,server_sequence,event_type,conversation_id,affected_scope,
                       envelope::text,ack_required,delivery_attempts,last_delivered_at,expires_at
                FROM hhy.websocket_deliveries
                WHERE user_id=? AND server_sequence>? AND server_sequence<=? AND expires_at>?
                ORDER BY server_sequence
                """, this::delivery, userId, sequence, through, time(now));
    }

    @Override
    public Optional<GapWatermark> gapWatermark(long userId) {
        return jdbc.query("""
                SELECT expired_through_sequence,affected_scopes
                FROM hhy.websocket_gap_watermarks WHERE user_id=?
                """, (rs, row) -> new GapWatermark(rs.getLong(1), scopes(rs.getArray(2))), userId)
                .stream().findFirst();
    }

    @Override
    public void expireDeliveries(long userId, Instant now) {
        List<GapWatermark> expired = jdbc.query("""
                SELECT max(server_sequence),array_agg(DISTINCT affected_scope ORDER BY affected_scope)
                FROM hhy.websocket_deliveries
                WHERE user_id=? AND expires_at<=?
                HAVING count(*)>0
                """, (rs, row) -> new GapWatermark(rs.getLong(1), scopes(rs.getArray(2))),
                userId, time(now));
        if (expired.isEmpty()) return;
        GapWatermark gap = expired.getFirst();
        jdbc.update("""
                INSERT INTO hhy.websocket_gap_watermarks(
                  user_id,expired_through_sequence,affected_scopes,created_at,updated_at)
                VALUES (?,?,?::varchar(32)[],?,?)
                ON CONFLICT (user_id) DO UPDATE
                SET expired_through_sequence=GREATEST(
                      hhy.websocket_gap_watermarks.expired_through_sequence,
                      EXCLUDED.expired_through_sequence),
                    affected_scopes=ARRAY(
                      SELECT DISTINCT unnest(
                        hhy.websocket_gap_watermarks.affected_scopes||EXCLUDED.affected_scopes)
                      ORDER BY 1),
                    updated_at=EXCLUDED.updated_at
                """, userId, gap.expiredThroughSequence(), scopeArray(gap.affectedScopes()),
                time(now), time(now));
        jdbc.update("DELETE FROM hhy.websocket_deliveries WHERE user_id=? AND expires_at<=?",
                userId, time(now));
    }

    @Override
    public boolean acknowledge(long userId, UUID eventId, long serverSequence, Instant now) {
        return jdbc.update("""
                UPDATE hhy.websocket_deliveries
                SET acked_at=COALESCE(acked_at,?)
                WHERE user_id=? AND event_id=? AND server_sequence=? AND ack_required
                """, time(now), userId, eventId, serverSequence) == 1;
    }

    @Override
    public boolean recordDeliveryAttempt(
            long userId, UUID eventId, long serverSequence, Instant now) {
        return jdbc.update("""
                UPDATE hhy.websocket_deliveries
                SET delivery_attempts=delivery_attempts+1,
                    first_delivered_at=COALESCE(first_delivered_at,?),last_delivered_at=?
                WHERE user_id=? AND event_id=? AND server_sequence=?
                  AND delivery_attempts<6 AND (NOT ack_required OR acked_at IS NULL)
                """, time(now), time(now), userId, eventId, serverSequence) == 1;
    }

    @Override
    public List<Delivery> dueRedeliveries(Instant dueBefore, Instant now, int limit) {
        return jdbc.query("""
                SELECT event_id,user_id,server_sequence,event_type,conversation_id,affected_scope,
                       envelope::text,ack_required,delivery_attempts,last_delivered_at,expires_at
                FROM hhy.websocket_deliveries
                WHERE ack_required AND acked_at IS NULL AND delivery_attempts<6 AND expires_at>?
                  AND COALESCE(last_delivered_at,created_at)<=?
                ORDER BY COALESCE(last_delivered_at,created_at),id
                LIMIT ?
                """, this::delivery, time(now), time(dueBefore), limit);
    }

    private Delivery delivery(ResultSet rs, int row) throws SQLException {
        long conversation = rs.getLong(5);
        Long conversationId = rs.wasNull() ? null : conversation;
        return new Delivery(
                rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3), rs.getString(4),
                conversationId, Scope.valueOf(rs.getString(6)), rs.getString(7),
                rs.getBoolean(8), rs.getInt(9), instant(rs.getObject(10, OffsetDateTime.class)),
                instant(rs.getObject(11, OffsetDateTime.class)));
    }

    private static Set<Scope> scopes(Array value) throws SQLException {
        if (value == null) return Set.of();
        Object[] raw = (Object[]) value.getArray();
        LinkedHashSet<Scope> result = new LinkedHashSet<>();
        Arrays.stream(raw).map(Object::toString).map(Scope::valueOf).forEach(result::add);
        return Set.copyOf(result);
    }

    private static String scopeArray(Set<Scope> scopes) {
        return "{" + String.join(",", scopes.stream().map(Enum::name).sorted().toList()) + "}";
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
