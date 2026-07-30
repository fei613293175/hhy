package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R08Contracts.Conversation;
import cc.orbexa.hhy.content.R08Contracts.PublisherSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the frozen R14 direct-chat contract. */
public final class R14Contracts {
    private R14Contracts() { }

    public record ConversationPage(List<Conversation> items, PageMeta page) { }
    public record MessagePage(List<ChatMessage> items, PageMeta page) { }
    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }
    public record LastMessage(
            String messageId, String messageType, String preview,
            String senderId, Instant createdAt) { }

    public record SendMessageRequest(
            @NotBlank @Size(max = 64) String clientMessageId,
            @NotBlank @Size(max = 32) String messageType,
            Map<String, Object> payload) { }

    public record ReadRequest(@NotBlank @Size(max = 64) String lastReadMessageId) { }
    public record BlockRequest(@Size(max = 2000) String reason) { }
    public record ReportRequest(
            @NotBlank @Size(max = 2000) String reasonCode,
            @NotBlank @Size(max = 2000) String description,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> evidenceMediaIds,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> messageIds,
            @PositiveOrZero Long expectedVersion) { }

    public record ChatMessage(
            String id,
            String conversationId,
            PublisherSummary sender,
            String clientMessageId,
            String messageType,
            Map<String, Object> payload,
            String status,
            Long serverSequence,
            Instant createdAt,
            Instant readAt) { }

    public record CommandResult(
            String resourceId,
            String businessNo,
            String status,
            Long version,
            Instant acceptedAt) { }
}
