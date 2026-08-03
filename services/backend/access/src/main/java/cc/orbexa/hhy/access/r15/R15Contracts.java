package cc.orbexa.hhy.access.r15;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class R15Contracts {
    private R15Contracts() { }

    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }

    public record NavigationTargetResource(
            String targetType, String route, String url, boolean requiresLogin) { }

    public record PublicPageBlockResource(
            String blockId, String blockType, String heading, String body,
            List<Object> media, Object action, long sortOrder) {
        public PublicPageBlockResource {
            media = media == null ? List.of() : List.copyOf(media);
        }
    }

    public record PublicPageResource(
            String code, String title, String description, List<PublicPageBlockResource> content,
            Object seoMetadata, Object download, Object trackingContext, long version) {
        public PublicPageResource {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    public record NotificationResource(
            String id, String type, String title, String body, NavigationTargetResource target,
            Instant readAt, Instant createdAt) { }

    public record NotificationPage(List<NotificationResource> items, PageMeta page) {
        public NotificationPage { items = List.copyOf(items); }
    }

    public record NotificationReadRequest(
            @NotBlank @Size(max = 64) String lastReadMessageId) { }

    public record CommandResultResource(
            String resourceId, String businessNo, String status, Long version, Instant acceptedAt) { }

    public record SupportTicketResource(
            String id, String ticketNo, String category, String subject, String status,
            String assignee, Instant lastMessageAt, Instant createdAt, long version) { }

    public record SupportTicketPage(List<SupportTicketResource> items, PageMeta page) {
        public SupportTicketPage { items = List.copyOf(items); }
    }

    public record SupportMessageRequest(
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 100) List<@NotBlank String> attachments) {
        public SupportMessageRequest {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record SupportAssignRequest(
            @NotBlank @Size(max = 64) String assigneeId,
            @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion) { }

    public record SupportCloseRequest(
            @Size(max = 2000) String reason,
            @PositiveOrZero Long expectedVersion,
            Map<String, Object> payload) {
        public SupportCloseRequest {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    public record ConversationResource(
            String id, Object peer, Object lastMessage, long unreadCount,
            String lastReadMessageId, Instant updatedAt, long version) { }

    public record ConversationPage(List<ConversationResource> items, PageMeta page) {
        public ConversationPage { items = List.copyOf(items); }
    }

    public record DecideRequest(
            @NotBlank @Size(max = 2000) String decision,
            @NotBlank @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 100) List<String> evidenceIds) {
        public DecideRequest {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }
}
