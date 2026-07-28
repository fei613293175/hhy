package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R08Contracts.Conversation;
import cc.orbexa.hhy.content.R08Contracts.DirectConversationRequest;
import cc.orbexa.hhy.content.R08Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R14Contracts.BlockRequest;
import cc.orbexa.hhy.content.R14Contracts.ChatMessage;
import cc.orbexa.hhy.content.R14Contracts.CommandResult;
import cc.orbexa.hhy.content.R14Contracts.ConversationPage;
import cc.orbexa.hhy.content.R14Contracts.LastMessage;
import cc.orbexa.hhy.content.R14Contracts.MessagePage;
import cc.orbexa.hhy.content.R14Contracts.PageMeta;
import cc.orbexa.hhy.content.R14Contracts.ReadRequest;
import cc.orbexa.hhy.content.R14Contracts.ReportRequest;
import cc.orbexa.hhy.content.R14Contracts.SendMessageRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R14Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> MESSAGE_TYPES =
            Set.of("TEXT", "IMAGE", "CONTENT_CARD", "CONTACT_CARD");
    private static final Set<String> CONTENT_TYPES =
            Set.of("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER");
    private static final Set<String> CONTACT_TYPES =
            Set.of("PHONE", "WECHAT", "QQ", "EMAIL", "OTHER");
    private static final String MESSAGE_RESPONSE = "r14.chat-message.v1";
    private static final String COMMAND_RESPONSE = "r14.chat-command.v1";

    private final R14Store store;
    private final R08Store shared;
    private final R08Service r08;
    private final R14RealtimeService realtime;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R14Service(
            R14Store store, R08Store shared, R08Service r08, R14RealtimeService realtime,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.r08 = r08;
        this.realtime = realtime;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConversationPage conversations(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        requireActive(userId);
        page(page, pageSize, cursor);
        String cleanStatus = upper(status);
        if (cleanStatus != null && !"ACTIVE".equals(cleanStatus)) throw validation("会话状态筛选不符合要求");
        String cleanSort = clean(sort);
        if (cleanSort != null && !"updatedAt:desc".equals(cleanSort)) throw validation("排序字段不在允许范围内");
        String cleanKeyword = clean(keyword);
        if (cleanKeyword != null && cleanKeyword.length() > 100) throw validation("关键词不符合要求");
        R14Store.ConversationPageRow result = store.conversations(new R14Store.ConversationQuery(
                userId, page, pageSize, conversationCursor(cursor), cleanKeyword));
        List<Conversation> items = result.items().stream().map(this::conversation).toList();
        String next = result.hasMore() && !result.items().isEmpty()
                ? conversationCursor(result.items().getLast()) : null;
        return new ConversationPage(items, new PageMeta(
                page, pageSize, Long.toString(result.total()), next, Boolean.toString(result.hasMore())));
    }

    @Transactional
    public Conversation direct(long userId, DirectConversationRequest request, String key) {
        return r08.direct(userId, request, key);
    }

    @Transactional(readOnly = true)
    public MessagePage messages(
            long userId, String idValue, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        requireActive(userId);
        long conversationId = id(idValue, "会话标识无效");
        requireMembership(conversationId, userId, false);
        page(page, pageSize, cursor);
        if (clean(status) != null || clean(keyword) != null) throw validation("消息分页不支持当前筛选条件");
        String cleanSort = clean(sort);
        if (cleanSort != null && !"createdAt:desc".equals(cleanSort)) throw validation("排序字段不在允许范围内");
        R14Store.MessagePageRow result = store.messages(new R14Store.MessageQuery(
                conversationId, page, pageSize, messageCursor(cursor)));
        List<ChatMessage> items = result.items().stream().map(this::message).toList();
        String next = result.hasMore() && !result.items().isEmpty()
                ? Long.toString(result.items().getLast().id()) : null;
        return new MessagePage(items, new PageMeta(
                page, pageSize, Long.toString(result.total()), next, Boolean.toString(result.hasMore())));
    }

    @Transactional
    public ChatMessage send(
            long userId, String idValue, SendMessageRequest request, String key) {
        requireActive(userId);
        long conversationId = id(idValue, "会话标识无效");
        R14Store.MembershipRow membership = requireMembership(conversationId, userId, true);
        if (shared.blockedEitherWay(userId, membership.peerId())) throw forbidden();
        String clientMessageId = required(request.clientMessageId(), 64, "客户端消息标识不符合要求");
        String messageType = upper(request.messageType());
        if (!MESSAGE_TYPES.contains(messageType)) throw validation("消息类型不符合要求");
        Map<String, Object> plainPayload = payload(conversationId, clientMessageId,
                messageType, request.payload(), null);
        String requestHash = hash(Map.of(
                "userId", userId, "conversationId", conversationId,
                "clientMessageId", clientMessageId, "messageType", messageType,
                "payload", plainPayload));
        String scope = "r14.message-send:" + userId + ":" + conversationId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, MESSAGE_RESPONSE, ChatMessage.class);
        if (store.messageByClient(userId, clientMessageId).isPresent()) throw idempotencyConflict();
        int limit = shared.integerConfig("chat.message.per_minute_limit");
        Instant now = Instant.now(clock);
        if (store.messagesSentSince(conversationId, userId, now.minusSeconds(60)) >= limit) {
            throw business("COMMON-429-RATE_LIMITED", "消息发送过于频繁", 429, true);
        }
        Long mediaId = "IMAGE".equals(messageType) ? idValue(plainPayload.get("mediaId"), "媒体标识无效") : null;
        if (mediaId != null) {
            R14Store.MediaRow media = store.privateChatMedia(userId, mediaId).orElseThrow(R14Service::forbidden);
            long maxBytes = Math.multiplyExact((long) shared.integerConfig("chat.image.max_mb"), 1024L * 1024L);
            if (media.sizeBytes() > maxBytes) {
                throw business("COMMON-422-BUSINESS_RULE", "图片大小超过当前限制", 422, false);
            }
        }
        Map<String, Object> storedPayload = "CONTACT_CARD".equals(messageType)
                ? payload(conversationId, clientMessageId, messageType, plainPayload, Boolean.TRUE) : plainPayload;
        R14Store.MessageRow row = store.insertMessage(
                conversationId, userId, clientMessageId, messageType, json(storedPayload), now);
        if (mediaId != null) store.insertAttachment(row.id(), mediaId, now);
        store.advanceConversation(conversationId, userId, row.id(), now);
        store.outbox(userId, "CHAT_MESSAGE", "chat.message.sent.v1",
                Long.toString(row.id()), "SENT", now);
        ChatMessage result = message(row);
        realtime.messagePersisted(userId, membership.peerId(), result);
        complete(claim, scope, key, requestHash, MESSAGE_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult read(long userId, String idValue, ReadRequest request, String key) {
        requireActive(userId);
        long conversationId = id(idValue, "会话标识无效");
        R14Store.MembershipRow membership = requireMembership(conversationId, userId, true);
        long messageId = id(request.lastReadMessageId(), "消息标识无效");
        R14Store.MessageRow message = store.message(conversationId, messageId).orElseThrow(R14Service::notFound);
        String scope = "r14.message-read:" + userId + ":" + conversationId;
        String requestHash = hash(Map.of("userId", userId, "conversationId", conversationId, "messageId", messageId));
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, COMMAND_RESPONSE, CommandResult.class);
        Instant now = Instant.now(clock);
        store.markRead(conversationId, userId, messageId, now);
        store.outbox(userId, "CONVERSATION", "chat.conversation.read.v1",
                Long.toString(conversationId), "READ", now);
        realtime.readPersisted(userId, membership.peerId(), conversationId, messageId);
        CommandResult result = new CommandResult(Long.toString(conversationId),
                Long.toString(message.id()), "READ", membership.version(), now);
        complete(claim, scope, key, requestHash, COMMAND_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult hide(long userId, String idValue, String key) {
        requireActive(userId);
        long conversationId = id(idValue, "会话标识无效");
        R14Store.MembershipRow membership = requireMembership(conversationId, userId, true);
        String scope = "r14.conversation-hide:" + userId + ":" + conversationId;
        String requestHash = hash(Map.of("userId", userId, "conversationId", conversationId));
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, COMMAND_RESPONSE, CommandResult.class);
        Instant now = Instant.now(clock);
        boolean hidden = store.hideConversation(conversationId, userId, now);
        if (hidden) store.outbox(userId, "CONVERSATION_VIEW", "chat.conversation.hidden.v1",
                Long.toString(conversationId), "HIDDEN", now);
        CommandResult result = new CommandResult(Long.toString(conversationId), null,
                "HIDDEN", membership.version(), now);
        complete(claim, scope, key, requestHash, COMMAND_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult block(long userId, String peerValue, BlockRequest request, String key) {
        requireActive(userId);
        long peerId = id(peerValue, "用户标识无效");
        if (peerId == userId) throw business("COMMON-422-BUSINESS_RULE", "不能拉黑自己", 422, false);
        if (!shared.activeUser(peerId)) throw notFound();
        String reason = optional(request == null ? null : request.reason(), 2000, "拉黑原因不符合要求");
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("userId", userId); facts.put("peerId", peerId); facts.put("reason", reason == null ? "" : reason);
        String scope = "r14.user-block:" + userId + ":" + peerId;
        String requestHash = hash(facts);
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, COMMAND_RESPONSE, CommandResult.class);
        Instant now = Instant.now(clock);
        boolean inserted = store.block(userId, peerId, reason, now);
        if (inserted) store.outbox(userId, "USER_BLOCK", "chat.user.blocked.v1",
                Long.toString(peerId), "BLOCKED", now);
        CommandResult result = new CommandResult(Long.toString(peerId), null, "BLOCKED", null, now);
        complete(claim, scope, key, requestHash, COMMAND_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult unblock(long userId, String peerValue, String key) {
        requireActive(userId);
        long peerId = id(peerValue, "用户标识无效");
        if (peerId == userId) throw business("COMMON-422-BUSINESS_RULE", "不能对自己执行该操作", 422, false);
        String scope = "r14.user-unblock:" + userId + ":" + peerId;
        String requestHash = hash(Map.of("userId", userId, "peerId", peerId));
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, COMMAND_RESPONSE, CommandResult.class);
        Instant now = Instant.now(clock);
        boolean removed = store.unblock(userId, peerId);
        if (removed) store.outbox(userId, "USER_BLOCK", "chat.user.unblocked.v1",
                Long.toString(peerId), "UNBLOCKED", now);
        CommandResult result = new CommandResult(Long.toString(peerId), null, "UNBLOCKED", null, now);
        complete(claim, scope, key, requestHash, COMMAND_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult report(
            long userId, String idValue, ReportRequest request, String key) {
        requireActive(userId);
        long conversationId = id(idValue, "会话标识无效");
        R14Store.MembershipRow membership = requireMembership(conversationId, userId, true);
        if (request.expectedVersion() != null && request.expectedVersion() != membership.version()) {
            throw versionConflict();
        }
        String reason = required(request.reasonCode(), 64, "举报原因不符合要求");
        if (!R14ChatReportReasonCatalog.isEnabled(reason)) {
            throw validation("举报原因不符合要求");
        }
        String description = required(request.description(), 2000, "举报说明不符合要求");
        List<Long> messageIds = ids(request.messageIds(), "举报消息标识无效");
        List<Long> mediaIds = ids(request.evidenceMediaIds(), "举报媒体标识无效");
        if (!store.reportEvidenceValid(conversationId, userId, messageIds, mediaIds)) throw forbidden();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("userId", userId); facts.put("conversationId", conversationId);
        facts.put("reasonCode", reason); facts.put("description", description);
        facts.put("messageIds", messageIds); facts.put("mediaIds", mediaIds);
        facts.put("expectedVersion", request.expectedVersion() == null ? "" : request.expectedVersion());
        String scope = "r14.chat-report:" + userId + ":" + conversationId;
        String requestHash = hash(facts);
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, COMMAND_RESPONSE, CommandResult.class);
        Instant now = Instant.now(clock);
        long reportId = store.report(userId, membership.peerId(), conversationId,
                reason, description, messageIds, mediaIds, now);
        store.outbox(userId, "CHAT_REPORT", "chat.report.created.v1",
                Long.toString(reportId), "PENDING", now);
        CommandResult result = new CommandResult(Long.toString(reportId), null, "PENDING", 0L, now);
        complete(claim, scope, key, requestHash, COMMAND_RESPONSE, result);
        return result;
    }

    private Conversation conversation(R14Store.ConversationRow row) {
        LastMessage last = row.lastMessageId() == null ? null : new LastMessage(
                Long.toString(row.lastMessageId()), row.lastMessageType(),
                row.lastMessagePreview() == null ? "" : row.lastMessagePreview(),
                row.lastMessageSenderId() == null ? null : Long.toString(row.lastMessageSenderId()),
                row.lastMessageAt());
        return new Conversation(Long.toString(row.id()), publisher(row.peerId()), last,
                row.unreadCount(), row.lastReadMessageId() == null ? null : Long.toString(row.lastReadMessageId()),
                row.updatedAt(), row.version());
    }

    private ChatMessage message(R14Store.MessageRow row) {
        Map<String, Object> payload = object(row.payloadJson());
        if ("CONTACT_CARD".equals(row.messageType())) {
            payload = payload(row.conversationId(), row.clientMessageId(), row.messageType(), payload, Boolean.FALSE);
        }
        return new ChatMessage(Long.toString(row.id()), Long.toString(row.conversationId()),
                publisher(row.senderId()), row.clientMessageId(), row.messageType(), payload,
                row.status(), null, row.createdAt(), row.readAt());
    }

    private PublisherSummary publisher(long userId) {
        R08Store.PublisherRow row = shared.publisher(userId).orElse(
                new R08Store.PublisherRow(userId, null, null, null, false, null));
        String nickname = clean(row.nickname());
        return new PublisherSummary(Long.toString(userId), nickname == null ? "用户" + userId : nickname,
                row.avatar(), row.bio(), row.verified(), row.memberBadge(), null);
    }

    private Map<String, Object> payload(
            long conversationId, String clientMessageId, String type,
            Map<String, Object> raw, Boolean encryptContacts) {
        if (raw == null) throw validation("消息内容不符合要求");
        return switch (type) {
            case "TEXT" -> textPayload(raw);
            case "IMAGE" -> imagePayload(raw);
            case "CONTENT_CARD" -> contentPayload(raw);
            case "CONTACT_CARD" -> contactPayload(conversationId, clientMessageId, raw, encryptContacts);
            default -> throw validation("消息类型不符合要求");
        };
    }

    private Map<String, Object> textPayload(Map<String, Object> raw) {
        exactKeys(raw, Set.of("text"));
        return Map.of("text", required(string(raw.get("text")), 5000, "文本消息不符合要求"));
    }

    private Map<String, Object> imagePayload(Map<String, Object> raw) {
        allowedKeys(raw, Set.of("mediaId", "thumbnailUrl", "width", "height"));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("mediaId", Long.toString(idValue(raw.get("mediaId"), "媒体标识无效")));
        optionalUri(raw, result, "thumbnailUrl");
        optionalDimension(raw, result, "width");
        optionalDimension(raw, result, "height");
        return Map.copyOf(result);
    }

    private Map<String, Object> contentPayload(Map<String, Object> raw) {
        allowedKeys(raw, Set.of("contentId", "contentType", "title", "coverUrl"));
        long contentId = idValue(raw.get("contentId"), "内容标识无效");
        String contentType = upper(string(raw.get("contentType")));
        if (!CONTENT_TYPES.contains(contentType)) throw validation("内容卡片类型不符合要求");
        R08Store.ContentRow content = shared.content(contentId).orElseThrow(R14Service::notFound);
        if (!"ONLINE".equals(content.status()) || !contentType.equals(content.type())) throw notFound();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("contentId", Long.toString(contentId));
        result.put("contentType", contentType);
        result.put("title", required(string(raw.get("title")), 160, "内容卡片标题不符合要求"));
        optionalUri(raw, result, "coverUrl");
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contactPayload(
            long conversationId, String clientMessageId,
            Map<String, Object> raw, Boolean encrypt) {
        allowedKeys(raw, Set.of("fields", "note"));
        Object fieldsValue = raw.get("fields");
        if (!(fieldsValue instanceof List<?> fields) || fields.isEmpty() || fields.size() > 5) {
            throw validation("联系方式字段不符合要求");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            if (!(fields.get(index) instanceof Map<?, ?> rawField)) throw validation("联系方式字段不符合要求");
            Map<String, Object> field = (Map<String, Object>) rawField;
            allowedKeys(field, Set.of("type", "label", "value"));
            String fieldType = upper(string(field.get("type")));
            if (!CONTACT_TYPES.contains(fieldType)) throw validation("联系方式类型不符合要求");
            String value = required(string(field.get("value")),
                    Boolean.FALSE.equals(encrypt) ? 2048 : 256, "联系方式内容不符合要求");
            String normalizedValue;
            if (encrypt == null) {
                normalizedValue = value;
            } else if (encrypt) {
                normalizedValue = cipher.encrypt(
                        conversationId, contactChannel(clientMessageId, index), value);
            } else {
                normalizedValue = required(
                        decryptContact(conversationId, clientMessageId, index, value),
                        256, "联系方式内容不符合要求");
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("type", fieldType);
            String label = optional(string(field.get("label")), 32, "联系方式标签不符合要求");
            if (label != null) item.put("label", label);
            item.put("value", normalizedValue);
            normalized.add(Map.copyOf(item));
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("fields", List.copyOf(normalized));
        String note = optional(string(raw.get("note")), 200, "联系卡备注不符合要求");
        if (note != null) result.put("note", note);
        return Map.copyOf(result);
    }

    private String decryptContact(long conversationId, String clientMessageId, int index, String value) {
        try { return cipher.decrypt(conversationId, contactChannel(clientMessageId, index), value); }
        catch (ContentContactCipher.ContactIntegrityException failure) { throw failure; }
    }

    private static String contactChannel(String clientMessageId, int index) {
        return "CHAT_CONTACT:" + clientMessageId + ":" + index;
    }

    private static void exactKeys(Map<String, Object> raw, Set<String> keys) {
        allowedKeys(raw, keys);
        if (!raw.keySet().containsAll(keys)) throw validation("消息内容缺少必填字段");
    }

    private static void allowedKeys(Map<String, Object> raw, Set<String> keys) {
        if (!keys.containsAll(raw.keySet())) throw validation("消息内容包含未允许字段");
    }

    private static void optionalUri(Map<String, Object> raw, Map<String, Object> target, String key) {
        String value = optional(string(raw.get(key)), 2048, key + "不符合要求");
        if (value == null) return;
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute()) throw new IllegalArgumentException();
        } catch (RuntimeException failure) {
            throw validation(key + "不符合要求");
        }
        target.put(key, value);
    }

    private static void optionalDimension(Map<String, Object> raw, Map<String, Object> target, String key) {
        Object value = raw.get(key);
        if (value == null) return;
        if (!(value instanceof Number number)) throw validation(key + "不符合要求");
        long parsed = number.longValue();
        if (parsed < 1 || parsed > 10_000 || number.doubleValue() != parsed) throw validation(key + "不符合要求");
        target.put(key, parsed);
    }

    private R14Store.MembershipRow requireMembership(long conversationId, long userId, boolean lock) {
        return store.membership(conversationId, userId, lock).orElseThrow(R14Service::forbidden);
    }

    private void requireActive(long userId) {
        if (!shared.activeUser(userId)) throw forbidden();
    }

    private R08Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        return shared.claim(scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
    }

    private <T> void complete(
            R08Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, T result) {
        try {
            String payload = cipher.encryptSnapshot(scope, key, requestHash, responseType,
                    mapper.writeValueAsBytes(result));
            shared.complete(claim.id(), responseType + ":ok", responseType, payload);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R14 idempotency snapshot serialization failed", failure);
        }
    }

    private <T> T replay(
            R08Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, Class<T> type) {
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        if (claim.responseRef() == null || !responseType.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null || claim.responsePayloadCiphertext().isBlank()) {
            throw versionConflict();
        }
        try {
            return mapper.readValue(cipher.decryptSnapshot(
                    scope, key, requestHash, responseType, claim.responsePayloadCiphertext()), type);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R14 idempotency snapshot is unavailable", failure);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R14 request hash unavailable", failure);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R14 JSON serialization failed", failure); }
    }

    private Map<String, Object> object(String value) {
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception failure) { throw new IllegalStateException("R14 stored message JSON is invalid", failure); }
    }

    private static void page(int page, int pageSize, String cursor) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数不符合要求");
        if (clean(cursor) != null && page != 1) throw validation("游标与页码不能同时使用");
    }

    private static R14Store.Cursor conversationCursor(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            String[] parts = clean.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            long millis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (millis < 0 || id < 1) throw new IllegalArgumentException();
            return new R14Store.Cursor(Instant.ofEpochMilli(millis), id);
        } catch (RuntimeException failure) {
            throw validation("游标不符合要求");
        }
    }

    private static String conversationCursor(R14Store.ConversationRow row) {
        return row.updatedAt().toEpochMilli() + ":" + row.id();
    }

    private static Long messageCursor(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        return id(clean, "游标不符合要求");
    }

    private static List<Long> ids(List<String> raw, String message) {
        if (raw == null) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : raw) if (!result.add(id(value, message))) throw validation(message);
        return List.copyOf(result);
    }

    private static String string(Object value) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw validation("字段类型不符合要求");
        return text;
    }

    private static String required(String value, int max, String message) {
        String result = clean(value);
        if (result == null || result.length() > max) throw validation(message);
        return result;
    }

    private static String optional(String value, int max, String message) {
        String result = clean(value);
        if (result != null && result.length() > max) throw validation(message);
        return result;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.strip();
        return result.isEmpty() ? null : result;
    }

    private static String upper(String value) {
        String result = clean(value);
        return result == null ? null : result.toUpperCase(Locale.ROOT);
    }

    private static long idValue(Object value, String message) {
        return id(string(value), message);
    }

    private static long id(String value, String message) {
        try {
            long result = Long.parseLong(value);
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (RuntimeException failure) {
            throw validation(message);
        }
    }

    private static BusinessException validation(String message) {
        return business("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException forbidden() {
        return business("COMMON-403-FORBIDDEN", "没有执行该操作的权限", 403, false);
    }

    private static BusinessException notFound() {
        return business("COMMON-404-NOT_FOUND", "资源不存在或不可访问", 404, false);
    }

    private static BusinessException versionConflict() {
        return business("COMMON-409-VERSION_CONFLICT", "数据版本已变化，请重新读取", 409, false);
    }

    private static BusinessException idempotencyConflict() {
        return business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键或客户端消息标识对应不同请求", 409, false);
    }

    private static BusinessException business(String code, String message, int status, boolean retryable) {
        return new BusinessException(code, message, status, retryable);
    }
}
