package cc.orbexa.hhy.access.r15;

import cc.orbexa.hhy.access.r15.R15Contracts.CommandResultResource;
import cc.orbexa.hhy.access.r15.R15Contracts.ConversationPage;
import cc.orbexa.hhy.access.r15.R15Contracts.ConversationResource;
import cc.orbexa.hhy.access.r15.R15Contracts.DecideRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationPage;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationReadRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.NotificationResource;
import cc.orbexa.hhy.access.r15.R15Contracts.PageMeta;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportAssignRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportCloseRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportMessageRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketPage;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportTicketResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R15Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> SORTS = Set.of("createdAt:desc", "createdAt:asc", "updatedAt:desc", "updatedAt:asc", "id:desc", "id:asc");
    private final R15Store store;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R15Service(R15Store store, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPage notifications(long userId, int page, int pageSize, String status, String keyword, String sort) {
        R15Store.PageQuery query = query(page, pageSize, status, keyword, sort);
        R15Store.PageRows<R15Store.NotificationRow> rows = store.notifications(userId, query);
        return new NotificationPage(rows.items().stream().map(this::notification).toList(), page(query, rows));
    }

    @Transactional
    public NotificationResource readNotification(
            long userId, String idValue, NotificationReadRequest request, String key, String requestId) {
        long id = id(idValue);
        String lastReadMessageId = clean(request.lastReadMessageId());
        String requestHash = hash(Map.of("id", id, "lastReadMessageId", lastReadMessageId));
        String scope = "r15.notificationPostNotificationsByIdRead:user:" + userId + ":notification:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return store.notification(userId, id).map(this::notification).orElseThrow(R15Service::notFound);
        }
        R15Store.NotificationRow locked = store.lockNotification(userId, id).orElseThrow(R15Service::notFound);
        long expectedVersion = locked.version();
        Instant now = Instant.now(clock);
        if (!store.markNotificationRead(userId, id, expectedVersion, now)) throw versionConflict();
        NotificationResource result = store.notification(userId, id).map(this::notification).orElseThrow(R15Service::notFound);
        store.outbox("NOTIFICATION", id, "notification.read.v1", requestId, json(result));
        store.complete(claim.id(), "notification:" + id + ":read");
        return result;
    }

    @Transactional
    public CommandResultResource readAllNotifications(long userId, String key, String requestId) {
        String requestHash = hash(Map.of("userId", userId, "operation", "readAll"));
        String scope = "r15.notificationPostNotificationsReadAll:user:" + userId;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return new CommandResultResource(Long.toString(userId), null, "READ", 0L, Instant.now(clock));
        }
        Instant now = Instant.now(clock);
        store.markAllNotificationsRead(userId, now);
        store.outbox("NOTIFICATION", userId, "notification.read-all.v1", requestId,
                json(Map.of("userId", userId, "occurredAt", now)));
        store.complete(claim.id(), "notifications:read-all");
        return new CommandResultResource(Long.toString(userId), null, "READ", 0L, now);
    }

    @Transactional(readOnly = true)
    public NotificationPage announcements(int page, int pageSize, String status, String keyword, String sort) {
        R15Store.PageQuery query = query(page, pageSize, status, keyword, sort);
        R15Store.PageRows<R15Store.NotificationRow> rows = store.announcements(query);
        return new NotificationPage(rows.items().stream().map(this::notification).toList(), page(query, rows));
    }

    @Transactional(readOnly = true)
    public NotificationResource announcement(String idValue) {
        return store.announcement(id(idValue)).map(this::notification).orElseThrow(R15Service::notFound);
    }

    @Transactional(readOnly = true)
    public SupportTicketPage helpArticles(int page, int pageSize, String status, String keyword, String sort) {
        R15Store.PageQuery query = query(page, pageSize, status, keyword, sort);
        R15Store.PageRows<R15Store.SupportTicketRow> rows = store.helpArticles(query);
        return new SupportTicketPage(rows.items().stream().map(this::supportTicket).toList(), page(query, rows));
    }

    @Transactional(readOnly = true)
    public SupportTicketResource helpArticle(String idValue) {
        return store.helpArticle(id(idValue)).map(this::supportTicket).orElseThrow(R15Service::notFound);
    }

    @Transactional(readOnly = true)
    public SupportTicketPage supportTickets(Long userId, int page, int pageSize, String status, String keyword, String sort) {
        R15Store.PageQuery query = query(page, pageSize, status, keyword, sort);
        R15Store.PageRows<R15Store.SupportTicketRow> rows = store.supportTickets(userId, query);
        return new SupportTicketPage(rows.items().stream().map(this::supportTicket).toList(), page(query, rows));
    }

    @Transactional(readOnly = true)
    public SupportTicketResource supportTicket(Long userId, String idValue) {
        return store.supportTicket(userId, id(idValue)).map(this::supportTicket).orElseThrow(R15Service::notFound);
    }

    @Transactional
    public SupportTicketResource addUserMessage(long userId, String idValue, SupportMessageRequest request, String key) {
        long id = id(idValue);
        String requestHash = hash(Map.of("id", id, "content", clean(request.content()),
                "attachments", request.attachments()));
        String scope = "r15.supportPostSupportTicketsByIdMessages:user:" + userId + ":ticket:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return supportTicket(userId, idValue);
        }
        R15Store.SupportTicketRow ticket = store.supportTicket(userId, id).orElseThrow(R15Service::notFound);
        store.appendSupportMessage(id, "USER", userId, clean(request.content()), Instant.now(clock));
        store.outbox("SUPPORT_TICKET", id, "support.ticket.message-added.v1", null, json(ticket));
        store.complete(claim.id(), idValue);
        return supportTicket(userId, idValue);
    }

    @Transactional
    public SupportTicketResource assignTicket(long adminId, String idValue, SupportAssignRequest request, String key, String requestId, String ip) {
        long id = id(idValue);
        String assignee = clean(request.assigneeId());
        long expectedVersion = version(request.expectedVersion());
        String reason = clean(request.reason());
        String requestHash = hash(Map.of("id", id, "assignee", assignee, "expectedVersion", expectedVersion,
                "reason", reason == null ? "" : reason));
        String scope = "r15.adminSupportAssign:admin:" + adminId + ":ticket:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return supportTicket(null, idValue);
        }
        R15Store.SupportTicketRow before = store.lockSupportTicket(id).orElseThrow(R15Service::notFound);
        if (before.version() != expectedVersion) throw versionConflict();
        Instant now = Instant.now(clock);
        if (!store.assignTicket(id, assignee, expectedVersion, now)) throw versionConflict();
        SupportTicketResource result = supportTicket(null, idValue);
        audit(adminId, "SUPPORT_ASSIGNED", "SUPPORT_TICKET", id, supportTicket(before), result, requestId, ip, now);
        store.outbox("SUPPORT_TICKET", id, "support.ticket.assigned.v1", requestId, json(result));
        store.complete(claim.id(), idValue);
        return result;
    }

    @Transactional
    public SupportTicketResource replyTicket(long adminId, String idValue, SupportMessageRequest request, String key, String requestId, String ip) {
        long id = id(idValue);
        String requestHash = hash(Map.of("id", id, "content", clean(request.content()),
                "attachments", request.attachments()));
        String scope = "r15.adminSupportReply:admin:" + adminId + ":ticket:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return supportTicket(null, idValue);
        }
        R15Store.SupportTicketRow before = store.supportTicket(null, id).orElseThrow(R15Service::notFound);
        Instant now = Instant.now(clock);
        store.appendSupportMessage(id, "ADMIN", adminId, clean(request.content()), now);
        SupportTicketResource result = supportTicket(null, idValue);
        audit(adminId, "SUPPORT_REPLIED", "SUPPORT_TICKET", id, supportTicket(before), result, requestId, ip, now);
        store.outbox("SUPPORT_TICKET", id, "support.ticket.replied.v1", requestId, json(result));
        store.complete(claim.id(), idValue);
        return result;
    }

    @Transactional
    public SupportTicketResource closeTicket(long adminId, String idValue, SupportCloseRequest request, String key, String requestId, String ip) {
        long id = id(idValue);
        long expectedVersion = version(request.expectedVersion());
        String reason = clean(request.reason());
        String requestHash = hash(Map.of("id", id, "reason", reason == null ? "" : reason,
                "expectedVersion", expectedVersion, "payload", request.payload()));
        String scope = "r15.adminSupportClose:admin:" + adminId + ":ticket:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return supportTicket(null, idValue);
        }
        R15Store.SupportTicketRow before = store.lockSupportTicket(id).orElseThrow(R15Service::notFound);
        if (before.version() != expectedVersion) throw versionConflict();
        Instant now = Instant.now(clock);
        if (!store.closeTicket(id, reason, expectedVersion, now)) throw versionConflict();
        SupportTicketResource result = supportTicket(null, idValue);
        audit(adminId, "SUPPORT_CLOSED", "SUPPORT_TICKET", id, supportTicket(before), result, requestId, ip, now);
        store.outbox("SUPPORT_TICKET", id, "support.ticket.closed.v1", requestId, json(result));
        store.complete(claim.id(), idValue);
        return result;
    }

    @Transactional(readOnly = true)
    public ConversationPage chatReports(int page, int pageSize, String status, String keyword, String sort) {
        R15Store.PageQuery query = query(page, pageSize, status, keyword, sort);
        R15Store.PageRows<R15Store.ConversationRow> rows = store.chatReports(query);
        return new ConversationPage(rows.items().stream().map(this::conversation).toList(), page(query, rows));
    }

    @Transactional
    public ConversationResource decideChatReport(long adminId, String idValue, DecideRequest request, String key, String requestId, String ip) {
        long id = id(idValue);
        long expectedVersion = version(request.expectedVersion());
        String decision = clean(request.decision());
        String reason = clean(request.reason());
        String requestHash = hash(Map.of("id", id, "decision", decision, "reason", reason,
                "expectedVersion", expectedVersion, "evidenceIds", request.evidenceIds()));
        String scope = "r15.adminChatPostChatReportsByIdDecide:admin:" + adminId + ":report:" + id;
        R15Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return store.chatReport(id).map(this::conversation).orElseThrow(R15Service::notFound);
        }
        R15Store.ConversationRow before = store.lockChatReport(id).orElseThrow(R15Service::notFound);
        if (before.version() != expectedVersion) throw versionConflict();
        Instant now = Instant.now(clock);
        if (!store.decideChatReport(id, decision, reason, expectedVersion, now)) throw versionConflict();
        ConversationResource result = store.chatReport(id).map(this::conversation).orElseThrow(R15Service::notFound);
        audit(adminId, "CHAT_REPORT_DECIDED", "CHAT_REPORT", id, conversation(before), result, requestId, ip, now);
        store.outbox("CHAT_REPORT", id, "chat.report.decided.v1", requestId, json(result));
        store.complete(claim.id(), idValue);
        return result;
    }

    private R15Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        R15Store.IdempotencyClaim claim = store.claim(scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (claim.replay() && !requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        return claim;
    }

    private void audit(long adminId, String action, String resource, long id, Object before, Object after, String requestId, String ip, Instant now) {
        Map<String, Object> afterJson = new LinkedHashMap<>();
        afterJson.put("requestId", requestId);
        afterJson.put("result", after);
        store.audit(adminId, action, resource, id, json(before), json(afterJson), ip, now);
    }

    private R15Store.PageQuery query(int page, int pageSize, String status, String keyword, String sort) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数不符合要求");
        String normalizedSort = sort == null || sort.isBlank() ? "createdAt:desc" : sort;
        if (!SORTS.contains(normalizedSort)) throw validation("排序字段不在允许范围内");
        return new R15Store.PageQuery(page, pageSize, clean(status), clean(keyword), normalizedSort);
    }

    private <T> PageMeta page(R15Store.PageQuery query, R15Store.PageRows<T> rows) {
        return new PageMeta(query.page(), query.pageSize(), Long.toString(rows.total()), null, Boolean.toString(rows.hasMore()));
    }

    private NotificationResource notification(R15Store.NotificationRow row) {
        return new NotificationResource(Long.toString(row.id()), row.type(), row.title(), row.body(), null,
                row.readAt(), row.createdAt());
    }

    private SupportTicketResource supportTicket(R15Store.SupportTicketRow row) {
        return new SupportTicketResource(Long.toString(row.id()), row.ticketNo(), row.category(), row.subject(),
                row.status(), row.assignee(), row.lastMessageAt(), row.createdAt(), row.version());
    }

    private ConversationResource conversation(R15Store.ConversationRow row) {
        return new ConversationResource(Long.toString(row.reportId()), null, null, 0, null,
                row.updatedAt(), row.version());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("R15 JSON serialization failed", failure);
        }
    }

    private static long id(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (RuntimeException ignored) {
        }
        throw validation("资源标识无效");
    }

    private static long version(Long value) {
        if (value == null || value < 0) throw validation("资源版本无效");
        return value;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String hash(Object value) {
        String input = json(value);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可访问", 404, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", "数据版本已变化，请刷新后重试", 409, false);
    }

    private static BusinessException idempotencyConflict() {
        return new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }
}
