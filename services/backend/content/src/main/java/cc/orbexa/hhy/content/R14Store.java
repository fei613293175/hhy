package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface R14Store {
    ConversationPageRow conversations(ConversationQuery query);
    MessagePageRow messages(MessageQuery query);
    Optional<MembershipRow> membership(long conversationId, long userId, boolean lock);
    Optional<MessageRow> message(long conversationId, long messageId);
    Optional<MessageRow> messageByClient(long senderId, String clientMessageId);
    long messagesSentSince(long conversationId, long senderId, Instant since);
    Optional<MediaRow> privateChatMedia(long userId, long mediaId);
    boolean reportEvidenceValid(
            long conversationId, long reporterId, List<Long> messageIds, List<Long> mediaIds);
    MessageRow insertMessage(
            long conversationId, long senderId, String clientMessageId,
            String messageType, String payloadJson, Instant now);
    void insertAttachment(long messageId, long mediaId, Instant now);
    void advanceConversation(long conversationId, long senderId, long messageId, Instant now);
    void markRead(long conversationId, long userId, long lastReadMessageId, Instant now);
    boolean hideConversation(long conversationId, long userId, Instant now);
    boolean block(long userId, long blockedUserId, String reason, Instant now);
    boolean unblock(long userId, long blockedUserId);
    long report(
            long reporterId, long targetUserId, long conversationId,
            String reasonCode, String description, List<Long> messageIds,
            List<Long> evidenceMediaIds, Instant now);
    void outbox(long actorId, String aggregateType, String eventType,
                String aggregateId, String status, Instant now);

    record Cursor(Instant updatedAt, long id) { }
    record ConversationQuery(
            long userId, int page, int pageSize, Cursor cursor, String keyword) { }
    record MessageQuery(
            long conversationId, int page, int pageSize, Long beforeMessageId) { }
    record ConversationPageRow(List<ConversationRow> items, long total, boolean hasMore) { }
    record MessagePageRow(List<MessageRow> items, long total, boolean hasMore) { }
    record ConversationRow(
            long id, long peerId, Long lastMessageId, String lastMessageType,
            String lastMessagePreview, Long lastMessageSenderId, Instant lastMessageAt,
            long unreadCount, Long lastReadMessageId, Instant updatedAt, long version) { }
    record MembershipRow(long conversationId, long peerId, long version) { }
    record MediaRow(long id, long sizeBytes) { }
    record MessageRow(
            long id, long conversationId, long senderId, String clientMessageId,
            String messageType, String payloadJson, String status,
            Instant createdAt, Instant readAt) { }
}
