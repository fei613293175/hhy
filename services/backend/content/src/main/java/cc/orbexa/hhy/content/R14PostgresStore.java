package cc.orbexa.hhy.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R14PostgresStore implements R14Store {
    private final JdbcTemplate jdbc;

    public R14PostgresStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ConversationPageRow conversations(ConversationQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT conversation.id,peer.user_id,message.id,message.type,
                       CASE message.type
                         WHEN 'TEXT' THEN left(message.body_json->>'text',300)
                         WHEN 'IMAGE' THEN '[图片]'
                         WHEN 'CONTENT_CARD' THEN left(message.body_json->>'title',300)
                         WHEN 'CONTACT_CARD' THEN '[联系方式]'
                       END,
                       message.sender_id,message.created_at,member.unread_count,
                       member.last_read_message_id,conversation.updated_at,conversation.version,
                       count(*) OVER()
                FROM hhy.conversation_members member
                JOIN hhy.conversations conversation ON conversation.id=member.conversation_id
                JOIN hhy.conversation_members peer
                  ON peer.conversation_id=conversation.id AND peer.user_id<>member.user_id
                LEFT JOIN hhy.user_profiles profile ON profile.user_id=peer.user_id
                LEFT JOIN hhy.chat_messages message ON message.id=conversation.last_message_id
                WHERE member.user_id=? AND member.hidden_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(query.userId());
        if (query.cursor() != null) {
            sql.append(" AND (conversation.updated_at<? OR (conversation.updated_at=? AND conversation.id<?))");
            args.add(time(query.cursor().updatedAt()));
            args.add(time(query.cursor().updatedAt()));
            args.add(query.cursor().id());
        }
        if (query.keyword() != null) {
            sql.append(" AND lower(COALESCE(profile.nickname,'')) LIKE ? ESCAPE '\\\\'");
            args.add("%" + escapeLike(query.keyword().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        sql.append(" ORDER BY conversation.updated_at DESC,conversation.id DESC LIMIT ? OFFSET ?");
        args.add(query.pageSize() + 1);
        args.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<ConversationRowWithTotal> rows = jdbc.query(sql.toString(), this::conversationRow, args.toArray());
        boolean hasMore = rows.size() > query.pageSize();
        List<ConversationRow> items = rows.stream().limit(query.pageSize())
                .map(ConversationRowWithTotal::row).toList();
        long total = rows.isEmpty() ? 0 : rows.getFirst().total();
        return new ConversationPageRow(items, total, hasMore);
    }

    @Override
    public MessagePageRow messages(MessageQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT message.id,message.conversation_id,message.sender_id,message.client_msg_id,
                       message.type,message.body_json::text,message.status,message.created_at,
                       (SELECT max(receipt.read_at) FROM hhy.chat_read_receipts receipt
                        WHERE receipt.message_id=message.id),count(*) OVER()
                FROM hhy.chat_messages message WHERE message.conversation_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(query.conversationId());
        if (query.beforeMessageId() != null) {
            sql.append(" AND message.id<?");
            args.add(query.beforeMessageId());
        }
        sql.append(" ORDER BY message.id DESC LIMIT ? OFFSET ?");
        args.add(query.pageSize() + 1);
        args.add(query.beforeMessageId() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<MessageRowWithTotal> rows = jdbc.query(sql.toString(), this::messageRowWithTotal, args.toArray());
        boolean hasMore = rows.size() > query.pageSize();
        List<MessageRow> items = rows.stream().limit(query.pageSize()).map(MessageRowWithTotal::row).toList();
        long total = rows.isEmpty() ? 0 : rows.getFirst().total();
        return new MessagePageRow(items, total, hasMore);
    }

    @Override
    public Optional<MembershipRow> membership(long conversationId, long userId, boolean lock) {
        return jdbc.query("""
                SELECT conversation.id,peer.user_id,conversation.version
                FROM hhy.conversations conversation
                JOIN hhy.conversation_members member
                  ON member.conversation_id=conversation.id AND member.user_id=?
                JOIN hhy.conversation_members peer
                  ON peer.conversation_id=conversation.id AND peer.user_id<>member.user_id
                WHERE conversation.id=?
                """ + (lock ? " FOR UPDATE OF conversation,member" : ""),
                (rs, row) -> new MembershipRow(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                userId, conversationId).stream().findFirst();
    }

    @Override
    public Optional<MessageRow> message(long conversationId, long messageId) {
        return jdbc.query("""
                SELECT message.id,message.conversation_id,message.sender_id,message.client_msg_id,
                       message.type,message.body_json::text,message.status,message.created_at,
                       (SELECT max(receipt.read_at) FROM hhy.chat_read_receipts receipt
                        WHERE receipt.message_id=message.id)
                FROM hhy.chat_messages message WHERE message.conversation_id=? AND message.id=?
                """, this::messageRow, conversationId, messageId).stream().findFirst();
    }

    @Override
    public Optional<MessageRow> messageByClient(long senderId, String clientMessageId) {
        return jdbc.query("""
                SELECT message.id,message.conversation_id,message.sender_id,message.client_msg_id,
                       message.type,message.body_json::text,message.status,message.created_at,
                       (SELECT max(receipt.read_at) FROM hhy.chat_read_receipts receipt
                        WHERE receipt.message_id=message.id)
                FROM hhy.chat_messages message WHERE message.sender_id=? AND message.client_msg_id=?
                """, this::messageRow, senderId, clientMessageId).stream().findFirst();
    }

    @Override
    public long messagesSentSince(long conversationId, long senderId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.chat_messages
                WHERE conversation_id=? AND sender_id=? AND created_at>=?
                """, Long.class, conversationId, senderId, time(since));
        return count == null ? 0 : count;
    }

    @Override
    public Optional<MediaRow> privateChatMedia(long userId, long mediaId) {
        return jdbc.query("""
                SELECT id,size FROM hhy.media_objects
                WHERE id=? AND owner_id=? AND status='READY' AND deleted_at IS NULL
                  AND storage_scope='private_chat' AND size IS NOT NULL AND size>=0
                """, (rs, row) -> new MediaRow(rs.getLong(1), rs.getLong(2)),
                mediaId, userId).stream().findFirst();
    }

    @Override
    public boolean reportEvidenceValid(
            long conversationId, long reporterId, List<Long> messageIds, List<Long> mediaIds) {
        if (!messageIds.isEmpty()) {
            String placeholders = placeholders(messageIds.size());
            List<Object> args = new ArrayList<>();
            args.add(conversationId);
            args.addAll(messageIds);
            Long count = jdbc.queryForObject("SELECT count(*) FROM hhy.chat_messages"
                    + " WHERE conversation_id=? AND id IN (" + placeholders + ")", Long.class, args.toArray());
            if (count == null || count != messageIds.size()) return false;
        }
        if (!mediaIds.isEmpty()) {
            String placeholders = placeholders(mediaIds.size());
            List<Object> args = new ArrayList<>();
            args.add(reporterId);
            args.add(conversationId);
            args.addAll(mediaIds);
            Long count = jdbc.queryForObject("""
                    SELECT count(*) FROM hhy.media_objects media
                    WHERE media.status='READY' AND media.deleted_at IS NULL
                      AND media.storage_scope IN ('private_chat','audit_evidence')
                      AND (media.owner_id=? OR EXISTS (
                        SELECT 1 FROM hhy.chat_message_attachments attachment
                        JOIN hhy.chat_messages message ON message.id=attachment.message_id
                        WHERE attachment.media_id=media.id AND message.conversation_id=?))
                      AND media.id IN (""" + placeholders + ")", Long.class, args.toArray());
            if (count == null || count != mediaIds.size()) return false;
        }
        return true;
    }

    @Override
    public MessageRow insertMessage(
            long conversationId, long senderId, String clientMessageId,
            String messageType, String payloadJson, Instant now) {
        return jdbc.queryForObject("""
                INSERT INTO hhy.chat_messages(
                  conversation_id,sender_id,type,body_json,status,client_msg_id,created_at,updated_at
                ) VALUES (?,?,?,CAST(? AS jsonb),'SENT',?,?,?)
                RETURNING id,conversation_id,sender_id,client_msg_id,type,body_json::text,status,created_at,NULL
                """, this::messageRow, conversationId, senderId, messageType, payloadJson,
                clientMessageId, time(now), time(now));
    }

    @Override
    public void insertAttachment(long messageId, long mediaId, Instant now) {
        jdbc.update("""
                INSERT INTO hhy.chat_message_attachments(message_id,media_id,sort_order,created_at,updated_at)
                VALUES (?,?,0,?,?)
                """, messageId, mediaId, time(now), time(now));
    }

    @Override
    public void advanceConversation(long conversationId, long senderId, long messageId, Instant now) {
        if (jdbc.update("""
                UPDATE hhy.conversations
                SET last_message_id=?,last_message_at=?,updated_at=?,version=version+1 WHERE id=?
                """, messageId, time(now), time(now), conversationId) != 1) {
            throw new IllegalStateException("R14 conversation disappeared while sending");
        }
        if (jdbc.update("""
                UPDATE hhy.conversation_members
                SET hidden_at=NULL,
                    unread_count=CASE WHEN user_id=? THEN unread_count ELSE unread_count+1 END,
                    updated_at=? WHERE conversation_id=?
                """, senderId, time(now), conversationId) != 2) {
            throw new IllegalStateException("R14 direct conversation membership changed");
        }
    }

    @Override
    public void markRead(long conversationId, long userId, long lastReadMessageId, Instant now) {
        jdbc.update("""
                UPDATE hhy.chat_messages SET status='DELIVERED',updated_at=?
                WHERE conversation_id=? AND sender_id<>? AND id<=? AND status='SENT'
                """, time(now), conversationId, userId, lastReadMessageId);
        jdbc.update("""
                UPDATE hhy.chat_messages SET status='READ',updated_at=?
                WHERE conversation_id=? AND sender_id<>? AND id<=? AND status='DELIVERED'
                """, time(now), conversationId, userId, lastReadMessageId);
        jdbc.update("""
                INSERT INTO hhy.chat_read_receipts(message_id,user_id,read_at,created_at,updated_at)
                SELECT id,?,?,?,? FROM hhy.chat_messages
                WHERE conversation_id=? AND sender_id<>? AND id<=?
                ON CONFLICT(message_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at,updated_at=EXCLUDED.updated_at
                """, userId, time(now), time(now), time(now), conversationId, userId, lastReadMessageId);
        if (jdbc.update("""
                UPDATE hhy.conversation_members
                SET last_read_message_id=?,unread_count=0,updated_at=?
                WHERE conversation_id=? AND user_id=?
                """, lastReadMessageId, time(now), conversationId, userId) != 1) {
            throw new IllegalStateException("R14 reader membership disappeared");
        }
    }

    @Override
    public boolean hideConversation(long conversationId, long userId, Instant now) {
        return jdbc.update("""
                UPDATE hhy.conversation_members SET hidden_at=?,updated_at=?
                WHERE conversation_id=? AND user_id=? AND hidden_at IS NULL
                """, time(now), time(now), conversationId, userId) == 1;
    }

    @Override
    public boolean block(long userId, long blockedUserId, String reason, Instant now) {
        return jdbc.update("""
                INSERT INTO hhy.user_blocks(user_id,blocked_user_id,reason,created_at,updated_at)
                VALUES (?,?,?,?,?) ON CONFLICT(user_id,blocked_user_id) DO NOTHING
                """, userId, blockedUserId, reason, time(now), time(now)) == 1;
    }

    @Override
    public boolean unblock(long userId, long blockedUserId) {
        return jdbc.update("DELETE FROM hhy.user_blocks WHERE user_id=? AND blocked_user_id=?",
                userId, blockedUserId) == 1;
    }

    @Override
    public long report(
            long reporterId, long targetUserId, long conversationId,
            String reasonCode, String description, List<Long> messageIds,
            List<Long> evidenceMediaIds, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO hhy.chat_reports(
                  reporter_id,target_user_id,conversation_id,message_ids,status,
                  reason_code,description,evidence_media_ids,version,created_at,updated_at
                ) VALUES (?,?,?,CAST(? AS jsonb),'PENDING',?,?,CAST(? AS jsonb),0,?,?) RETURNING id
                """, Long.class, reporterId, targetUserId, conversationId, idArray(messageIds),
                reasonCode, description, idArray(evidenceMediaIds), time(now), time(now));
        if (id == null) throw new IllegalStateException("R14 report id was not returned");
        return id;
    }

    @Override
    public void outbox(long actorId, String aggregateType, String eventType,
                       String aggregateId, String status, Instant now) {
        String payload = "{\"actorId\":" + actorId + ",\"resourceId\":\"" + aggregateId
                + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + now + "\"}";
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
                ) VALUES (?,?,?,?,1,'{"source":"r14-api"}'::jsonb,CAST(? AS jsonb))
                """, aggregateId, aggregateType, UUID.randomUUID().toString(), eventType, payload);
    }

    private ConversationRowWithTotal conversationRow(ResultSet rs, int row) throws SQLException {
        ConversationRow item = new ConversationRow(
                rs.getLong(1), rs.getLong(2), nullableLong(rs, 3), rs.getString(4), rs.getString(5),
                nullableLong(rs, 6), instant(rs.getObject(7, OffsetDateTime.class)), rs.getLong(8),
                nullableLong(rs, 9), instant(rs.getObject(10, OffsetDateTime.class)), rs.getLong(11));
        return new ConversationRowWithTotal(item, rs.getLong(12));
    }

    private MessageRowWithTotal messageRowWithTotal(ResultSet rs, int row) throws SQLException {
        return new MessageRowWithTotal(messageRow(rs, row), rs.getLong(10));
    }

    private MessageRow messageRow(ResultSet rs, int row) throws SQLException {
        return new MessageRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7),
                instant(rs.getObject(8, OffsetDateTime.class)), instant(rs.getObject(9, OffsetDateTime.class)));
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static String idArray(List<Long> ids) {
        StringJoiner values = new StringJoiner(",", "[", "]");
        ids.forEach(id -> values.add("\"" + id + "\""));
        return values.toString();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static OffsetDateTime time(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    private record ConversationRowWithTotal(ConversationRow row, long total) { }
    private record MessageRowWithTotal(MessageRow row, long total) { }
}
