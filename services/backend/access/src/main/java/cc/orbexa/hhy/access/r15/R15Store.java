package cc.orbexa.hhy.access.r15;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R15Store {
    PageRows<NotificationRow> notifications(long userId, PageQuery query);
    Optional<NotificationRow> notification(long userId, long id);
    Optional<NotificationRow> lockNotification(long userId, long id);
    boolean markNotificationRead(long userId, long id, long expectedVersion, Instant now);
    int markAllNotificationsRead(long userId, Instant now);
    PageRows<NotificationRow> announcements(PageQuery query);
    Optional<NotificationRow> announcement(long id);
    PageRows<SupportTicketRow> helpArticles(PageQuery query);
    Optional<SupportTicketRow> helpArticle(long id);
    PageRows<SupportTicketRow> supportTickets(Long userId, PageQuery query);
    Optional<SupportTicketRow> supportTicket(Long userId, long id);
    Optional<SupportTicketRow> lockSupportTicket(long id);
    boolean assignTicket(long id, String assigneeId, long expectedVersion, Instant now);
    boolean closeTicket(long id, String reason, long expectedVersion, Instant now);
    void appendSupportMessage(long ticketId, String senderType, long senderId, String body, Instant now);
    PageRows<ConversationRow> chatReports(PageQuery query);
    Optional<ConversationRow> lockChatReport(long id);
    Optional<ConversationRow> chatReport(long id);
    boolean decideChatReport(long id, String decision, String reason, long expectedVersion, Instant now);
    IdempotencyClaim claim(String scope, String key, String requestHash, Instant expiresAt);
    void complete(long claimId, String responseRef);
    void audit(long adminId, String action, String resource, long resourceId,
               String beforeJson, String afterJson, String ip, Instant now);
    void outbox(String aggregateType, long aggregateId, String eventType, String requestId, String payloadJson);

    record PageQuery(int page, int pageSize, String status, String keyword, String sort) { }
    record PageRows<T>(List<T> items, long total, boolean hasMore) { }
    record NotificationRow(long id, String type, String title, String body,
                           Instant readAt, Instant createdAt, long version) { }
    record SupportTicketRow(long id, String ticketNo, String category, String subject, String status,
                            String assignee, Instant lastMessageAt, Instant createdAt, Instant updatedAt,
                            long version) { }
    record ConversationRow(long reportId, long conversationId, Instant updatedAt, long version) { }
    record IdempotencyClaim(long id, String requestHash, String responseRef, boolean replay) { }
}
