package cc.orbexa.hhy.access.r15;

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
public class R15PostgresStore implements R15Store {
    private final JdbcTemplate jdbc;

    public R15PostgresStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageRows<NotificationRow> notifications(long userId, PageQuery query) {
        QueryParts parts = where("item.user_id=?", List.of(userId), query, "item", "title", false);
        return page("""
                SELECT item.id,item.type,item.title,item.body,item.read_at,item.created_at,item.version
                FROM hhy.notifications item
                """, parts, order("item", query.sort()), query, this::notificationRow);
    }

    @Override
    public Optional<NotificationRow> notification(long userId, long id) {
        return single("""
                SELECT id,type,title,body,read_at,created_at,version
                FROM hhy.notifications WHERE user_id=? AND id=?
                """, this::notificationRow, userId, id);
    }

    @Override
    public Optional<NotificationRow> lockNotification(long userId, long id) {
        return single("""
                SELECT id,type,title,body,read_at,created_at,version
                FROM hhy.notifications WHERE user_id=? AND id=? FOR UPDATE
                """, this::notificationRow, userId, id);
    }

    @Override
    public boolean markNotificationRead(long userId, long id, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.notifications
                SET status='READ',read_at=COALESCE(read_at,?),updated_at=?,version=version+1
                WHERE user_id=? AND id=? AND version=?
                """, time(now), time(now), userId, id, expectedVersion) == 1;
    }

    @Override
    public int markAllNotificationsRead(long userId, Instant now) {
        return jdbc.update("""
                UPDATE hhy.notifications
                SET status='READ',read_at=COALESCE(read_at,?),updated_at=?,version=version+1
                WHERE user_id=? AND status<>'READ'
                """, time(now), time(now), userId);
    }

    @Override
    public PageRows<NotificationRow> announcements(PageQuery query) {
        QueryParts parts = where("item.status='PUBLISHED'", List.of(), query, "item", "title", false);
        return page("""
                SELECT item.id,'ANNOUNCEMENT',item.title,item.content,NULL,item.created_at,0
                FROM hhy.announcements item
                """, parts, order("item", query.sort()), query, this::notificationRow);
    }

    @Override
    public Optional<NotificationRow> announcement(long id) {
        return single("""
                SELECT id,'ANNOUNCEMENT',title,content,NULL,created_at,0
                FROM hhy.announcements WHERE id=? AND status='PUBLISHED'
                """, this::notificationRow, id);
    }

    @Override
    public Optional<PublicPageRow> publicAgreement(String code) {
        return single("""
                SELECT version_row.id,'H5-010',agreement.code,NULL,version_row.content,version_row.version
                FROM hhy.agreements agreement
                JOIN hhy.agreement_versions version_row ON version_row.id=agreement.current_version_id
                WHERE agreement.code=? AND version_row.effective_at IS NOT NULL
                  AND version_row.effective_at<=clock_timestamp()
                """, this::publicPageRow, code);
    }

    @Override
    public Optional<PublicPageRow> publicHelpArticle(long id) {
        return single("""
                SELECT id,'H5-011',title,type,content,0
                FROM hhy.cms_articles
                WHERE id=? AND status='PUBLISHED'
                """, this::publicPageRow, id);
    }

    @Override
    public PageRows<SupportTicketRow> helpArticles(PageQuery query) {
        QueryParts parts = where("item.status='PUBLISHED'", List.of(), query, "item", "title", false);
        return page("""
                SELECT item.id,'HELP-' || item.id,item.type,item.title,item.status,NULL,
                       COALESCE(item.published_at,item.updated_at),item.created_at,item.updated_at,0
                FROM hhy.cms_articles item
                """, parts, order("item", query.sort()), query, this::supportTicketRow);
    }

    @Override
    public Optional<SupportTicketRow> helpArticle(long id) {
        return single("""
                SELECT id,'HELP-' || id,type,title,status,NULL,
                       COALESCE(published_at,updated_at),created_at,updated_at,0
                FROM hhy.cms_articles WHERE id=? AND status='PUBLISHED'
                """, this::supportTicketRow, id);
    }

    @Override
    public PageRows<SupportTicketRow> supportTickets(Long userId, PageQuery query) {
        List<Object> initial = new ArrayList<>();
        String base = "1=1";
        if (userId != null) {
            base = "item.user_id=?";
            initial.add(userId);
        }
        QueryParts parts = where(base, initial, query, "item", "biz_type", false);
        return page("""
                SELECT item.id,item.ticket_no,item.type,item.biz_type,item.status,item.assignee,
                       (SELECT max(message.created_at) FROM hhy.ticket_messages message WHERE message.ticket_id=item.id),
                       item.created_at,item.updated_at,item.version
                FROM hhy.support_tickets item
                """, parts, order("item", query.sort()), query, this::supportTicketRow);
    }

    @Override
    public Optional<SupportTicketRow> supportTicket(Long userId, long id) {
        String scope = userId == null ? "" : " AND item.user_id=?";
        List<Object> args = userId == null ? List.of(id) : List.of(id, userId);
        return single("""
                SELECT item.id,item.ticket_no,item.type,item.biz_type,item.status,item.assignee,
                       (SELECT max(message.created_at) FROM hhy.ticket_messages message WHERE message.ticket_id=item.id),
                       item.created_at,item.updated_at,item.version
                FROM hhy.support_tickets item WHERE item.id=?""" + scope,
                this::supportTicketRow, args.toArray());
    }

    @Override
    public Optional<SupportTicketRow> lockSupportTicket(long id) {
        return single("""
                SELECT item.id,item.ticket_no,item.type,item.biz_type,item.status,item.assignee,
                       (SELECT max(message.created_at) FROM hhy.ticket_messages message WHERE message.ticket_id=item.id),
                       item.created_at,item.updated_at,item.version
                FROM hhy.support_tickets item WHERE item.id=? FOR UPDATE
                """, this::supportTicketRow, id);
    }

    @Override
    public boolean assignTicket(long id, String assigneeId, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.support_tickets
                SET assignee=?,status='PENDING',updated_at=?,version=version+1
                WHERE id=? AND version=?
                """, assigneeId, time(now), id, expectedVersion) == 1;
    }

    @Override
    public boolean closeTicket(long id, String reason, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.support_tickets
                SET status='CLOSED',close_reason=?,updated_at=?,version=version+1
                WHERE id=? AND version=? AND status='RESOLVED'
                """, reason, time(now), id, expectedVersion) == 1;
    }

    @Override
    public boolean attachmentsAvailable(List<Long> attachmentIds, Long ownerId) {
        if (attachmentIds.isEmpty()) return true;
        String placeholders = String.join(",", java.util.Collections.nCopies(attachmentIds.size(), "?"));
        List<Object> args = new ArrayList<>(attachmentIds);
        String owner = "";
        if (ownerId != null) {
            owner = " AND owner_id=?";
            args.add(ownerId);
        }
        Long count = jdbc.queryForObject("SELECT count(*) FROM hhy.media_objects WHERE id IN ("
                        + placeholders + ") AND deleted_at IS NULL AND (status IS NULL OR status='READY')" + owner,
                Long.class, args.toArray());
        return count != null && count == attachmentIds.size();
    }

    @Override
    public void appendSupportMessage(long ticketId, String senderType, long senderId, String body,
                                     List<Long> attachmentIds, Long attachmentOwnerId, Instant now) {
        Long messageId = jdbc.queryForObject("""
                INSERT INTO hhy.ticket_messages(ticket_id,sender_type,sender_id,body,created_at,updated_at)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, ticketId, senderType, senderId, body, time(now), time(now));
        if (messageId == null) {
            throw new IllegalStateException("R15 support message was not inserted");
        }
        for (long mediaId : attachmentIds) {
            String owner = attachmentOwnerId == null ? "" : " AND owner_id=?";
            List<Object> args = new ArrayList<>(List.of(ticketId, messageId, mediaId));
            if (attachmentOwnerId != null) args.add(attachmentOwnerId);
            int inserted = jdbc.update("""
                    INSERT INTO hhy.ticket_attachments(ticket_id,message_id,media_id)
                    SELECT ?,?,id FROM hhy.media_objects
                    WHERE id=? AND deleted_at IS NULL
                      AND (status IS NULL OR status='READY')
                    """ + owner, args.toArray());
            if (inserted != 1) throw new IllegalStateException("R15 support attachment became unavailable");
        }
        jdbc.update("""
                UPDATE hhy.support_tickets
                SET status=CASE
                    WHEN ?='ADMIN' AND status='PENDING' THEN 'RESOLVED'
                    WHEN ?='USER' AND status='RESOLVED' THEN 'PENDING'
                    ELSE status END,
                    updated_at=?,version=version+1
                WHERE id=?
                """, senderType, senderType, time(now), ticketId);
    }

    @Override
    public PageRows<ConversationRow> chatReports(PageQuery query) {
        QueryParts parts = where("1=1", List.of(), query, "item", "reason_code", false);
        return page("""
                SELECT item.id,item.conversation_id,item.status,item.updated_at,item.version
                FROM hhy.chat_reports item
                """, parts, order("item", query.sort()), query, this::conversationRow);
    }

    @Override
    public Optional<ConversationRow> lockChatReport(long id) {
        return single("""
                SELECT id,conversation_id,status,updated_at,version
                FROM hhy.chat_reports WHERE id=? FOR UPDATE
                """, this::conversationRow, id);
    }

    @Override
    public Optional<ConversationRow> chatReport(long id) {
        return single("""
                SELECT id,conversation_id,status,updated_at,version
                FROM hhy.chat_reports WHERE id=?
                """, this::conversationRow, id);
    }

    @Override
    public boolean decideChatReport(long id, String decision, String reason, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE hhy.chat_reports
                SET status=CASE ? WHEN 'APPROVE' THEN 'APPROVED' WHEN 'REJECT' THEN 'REJECTED'
                           WHEN 'ESCALATE' THEN 'ESCALATED' END,
                    decision=?,decision_reason=?,updated_at=?,version=version+1
                WHERE id=? AND version=?
                """, decision, decision, reason, time(now), id, expectedVersion) == 1;
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
        IdempotencyClaim claim = jdbc.queryForObject("""
                SELECT id,request_hash,response_ref
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>clock_timestamp()
                """, (rs, row) -> new IdempotencyClaim(rs.getLong(1), rs.getString(2),
                        rs.getString(3), inserted == 0), scope, key);
        if (claim == null) throw new IllegalStateException("R15 idempotency claim disappeared");
        return claim;
    }

    @Override
    public void complete(long claimId, String responseRef) {
        if (jdbc.update("""
                UPDATE hhy.idempotency_records SET response_ref=?
                WHERE id=? AND response_ref IS NULL
                """, responseRef, claimId) != 1) {
            throw new IllegalStateException("R15 idempotency response already completed");
        }
    }

    @Override
    public void audit(long adminId, String action, String resource, long resourceId,
                      String beforeJson, String afterJson, String ip, Instant now) {
        if (jdbc.update("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip,created_at)
                VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
                """, adminId, action, resource, resourceId, beforeJson, afterJson, ip, time(now)) != 1) {
            throw new IllegalStateException("R15 audit record was not inserted");
        }
    }

    @Override
    public void outbox(String aggregateType, long aggregateId, String eventType,
                       String requestId, String payloadJson) {
        if (jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?, ?, ?, ?, 1,
                  jsonb_build_object('source','r15-access-api','requestId',?), CAST(? AS jsonb))
                """, Long.toString(aggregateId), aggregateType, UUID.randomUUID().toString(),
                eventType, requestId, payloadJson) != 1) {
            throw new IllegalStateException("R15 outbox event was not inserted");
        }
    }

    private <T> PageRows<T> page(String select, QueryParts parts, String order,
                                  PageQuery query, Row<T> row) {
        String sql = select + parts.sql() + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(parts.args());
        args.add(query.pageSize() + 1);
        args.add((long) (query.page() - 1) * query.pageSize());
        List<T> rows = jdbc.query(sql, row::map, args.toArray());
        boolean hasMore = rows.size() > query.pageSize();
        List<T> pageRows = hasMore ? rows.subList(0, query.pageSize()) : rows;
        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + select + parts.sql() + ") counted",
                Long.class, parts.args().toArray());
        return new PageRows<>(List.copyOf(pageRows), total == null ? pageRows.size() : total, hasMore);
    }

    private QueryParts where(String base, List<Object> initial, PageQuery query,
                             String alias, String keywordColumn, boolean defaultPublished) {
        StringBuilder where = new StringBuilder(" WHERE ").append(base);
        List<Object> args = new ArrayList<>(initial);
        if (query.status() != null) {
            where.append(" AND ").append(alias).append(".status=?");
            args.add(query.status());
        } else if (defaultPublished) {
            where.append(" AND ").append(alias).append(".status='PUBLISHED'");
        }
        if (query.keyword() != null) {
            where.append(" AND (").append(alias).append(".id::text=?");
            args.add(query.keyword());
            where.append(" OR ").append(alias).append('.').append(keywordColumn).append(" ILIKE ?)");
            args.add("%" + query.keyword() + "%");
        }
        return new QueryParts(where.toString(), List.copyOf(args));
    }

    private static String order(String alias, String sort) {
        return switch (sort) {
            case "createdAt:asc" -> alias + ".created_at ASC," + alias + ".id ASC";
            case "updatedAt:desc" -> alias + ".updated_at DESC," + alias + ".id DESC";
            case "updatedAt:asc" -> alias + ".updated_at ASC," + alias + ".id ASC";
            case "id:desc" -> alias + ".id DESC";
            case "id:asc" -> alias + ".id ASC";
            default -> alias + ".created_at DESC," + alias + ".id DESC";
        };
    }

    private <T> Optional<T> single(String sql, Row<T> row, Object... args) {
        return jdbc.query(sql, row::map, args).stream().findFirst();
    }

    private NotificationRow notificationRow(ResultSet rs, int row) throws SQLException {
        return new NotificationRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                instant(rs.getObject(5, OffsetDateTime.class)), instant(rs.getObject(6, OffsetDateTime.class)),
                rs.getLong(7));
    }

    private SupportTicketRow supportTicketRow(ResultSet rs, int row) throws SQLException {
        return new SupportTicketRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), instant(rs.getObject(7, OffsetDateTime.class)),
                instant(rs.getObject(8, OffsetDateTime.class)), instant(rs.getObject(9, OffsetDateTime.class)),
                rs.getLong(10));
    }

    private PublicPageRow publicPageRow(ResultSet rs, int row) throws SQLException {
        return new PublicPageRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getLong(6));
    }

    private ConversationRow conversationRow(ResultSet rs, int row) throws SQLException {
        return new ConversationRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                instant(rs.getObject(4, OffsetDateTime.class)), rs.getLong(5));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record QueryParts(String sql, List<Object> args) { }

    @FunctionalInterface
    private interface Row<T> {
        T map(ResultSet rs, int row) throws SQLException;
    }
}
