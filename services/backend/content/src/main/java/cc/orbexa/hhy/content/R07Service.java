package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R07Contracts.CommandResult;
import cc.orbexa.hhy.content.R07Contracts.ContactAccess;
import cc.orbexa.hhy.content.R07Contracts.ContactAccessRequest;
import cc.orbexa.hhy.content.R07Contracts.PageMeta;
import cc.orbexa.hhy.content.R07Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R07Contracts.SearchResult;
import cc.orbexa.hhy.content.R07Contracts.SearchResults;
import cc.orbexa.hhy.content.R07Contracts.SearchTerm;
import cc.orbexa.hhy.content.R07Contracts.SearchTerms;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R07Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> CONTACT_CHANNELS =
            Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE", "JOIN_PASSWORD");
    private static final Map<String, String> SEARCH_SORTS = Map.of(
            "relevance:desc", "score DESC,p.updated_at DESC,p.id DESC",
            "createdAt:desc", "p.created_at DESC,p.id DESC",
            "id:desc", "p.id DESC");
    private static final Map<String, String> HOT_SORTS = Map.of(
            "weight:desc", "weight DESC,id DESC",
            "createdAt:desc", "created_at DESC,id DESC",
            "id:desc", "id DESC");
    private static final Map<String, String> HISTORY_SORTS = Map.of(
            "createdAt:desc", "created_at DESC,id DESC",
            "id:desc", "id DESC");
    private static final String CONTACT_RESPONSE = "r07.contact-access.v1";
    private static final String CLEAR_RESPONSE = "r07.search-history-clear.v1";

    private final R07Store store;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R07Service(R07Store store, ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional
    public SearchResults search(
            long userId, String query, String contentType, String categoryCode, String regionCode,
            int page, int pageSize, String cursor, String sort) {
        String keyword = requiredText(query, 100, "搜索关键词不符合要求");
        String storedType = storedType(contentType);
        PageRequest request = page(page, pageSize, cursor);
        String order = SEARCH_SORTS.get(sort);
        if (order == null) throw validation("搜索排序不符合要求");
        requireCursorOrder(request, sort);
        R07Store.SearchRows rows = store.search(new R07Store.SearchQuery(
                userId, keyword, storedType, optionalText(categoryCode, 64, "分类不符合要求"),
                optionalText(regionCode, 32, "地区不符合要求"), request.page(), request.pageSize(),
                request.beforeId(), order));
        store.recordSearch(userId, keyword, Instant.now(clock));
        List<SearchResult> items = rows.items().stream().map(row -> new SearchResult(
                Long.toString(row.id()), outwardType(row.type()), row.title(), row.summary(), null,
                new PublisherSummary(Long.toString(row.ownerId()), nickname(row.nickname(), row.ownerId()),
                        row.avatar(), row.bio(), row.verified(), row.memberBadge(), row.followed()),
                row.score(), badges(row.verified(), row.memberBadge()))).toList();
        return new SearchResults(items, pageMeta(request, rows.total(), rows.hasMore(),
                rows.items().isEmpty() ? null : rows.items().getLast().id()));
    }

    @Transactional(readOnly = true)
    public SearchTerms hot(int page, int pageSize, String cursor, String keyword, String sort) {
        PageRequest request = page(page, pageSize, cursor);
        String order = HOT_SORTS.get(sort);
        if (order == null) throw validation("热搜排序不符合要求");
        requireCursorOrder(request, sort);
        R07Store.TermRows rows = store.hotTerms(new R07Store.TermQuery(
                request.page(), request.pageSize(), request.beforeId(),
                optionalText(keyword, 100, "关键词筛选不符合要求"), order), Instant.now(clock));
        return terms(request, rows);
    }

    @Transactional(readOnly = true)
    public SearchTerms history(long userId, int page, int pageSize, String cursor, String keyword, String sort) {
        PageRequest request = page(page, pageSize, cursor);
        String order = HISTORY_SORTS.get(sort);
        if (order == null) throw validation("历史排序不符合要求");
        requireCursorOrder(request, sort);
        return terms(request, store.history(userId, new R07Store.TermQuery(
                request.page(), request.pageSize(), request.beforeId(),
                optionalText(keyword, 100, "关键词筛选不符合要求"), order)));
    }

    @Transactional(readOnly = true)
    public PublisherSummary publisher(long viewerId, String id) {
        long publisherId = id(id, "发布者标识无效");
        R07Store.PublisherRow row = store.publisher(publisherId, viewerId, Instant.now(clock))
                .orElseThrow(R07Service::notFound);
        return new PublisherSummary(Long.toString(row.userId()), nickname(row.nickname(), row.userId()),
                row.avatar(), row.bio(), row.verified(), row.memberBadge(), row.followed());
    }

    @Transactional
    public ContactAccess contact(
            long userId, String contentIdValue, String channelValue,
            ContactAccessRequest request, String key) {
        long contentId = id(contentIdValue, "内容标识无效");
        String channel = requiredText(channelValue, 32, "联系方式渠道不符合要求").toUpperCase(Locale.ROOT);
        if (!CONTACT_CHANNELS.contains(channel)) throw validation("联系方式渠道不符合要求");
        String scope = "r07.contact:" + userId + ":" + contentId + ":" + channel;
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("userId", userId);
        intent.put("contentId", contentId);
        intent.put("channel", channel);
        intent.put("clientContext", request.clientContext());
        String requestHash = hash(intent);
        R07Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            ContactAccess replayed = replay(
                    claim, scope, key, requestHash, CONTACT_RESPONSE, ContactAccess.class);
            store.contactAudit(userId, contentId, channel, "REPLAY", Instant.now(clock));
            return replayed;
        }
        try {
            R07Store.ContactRow row = store.contact(contentId, channel).orElseThrow(R07Service::notFound);
            String value;
            try {
                value = cipher.decrypt(contentId, channel, row.valueCipher());
            } catch (ContentContactCipher.ContactIntegrityException legacyOrInvalid) {
                store.contactRejected(userId, contentId, channel, "REJECTED_UNAVAILABLE", Instant.now(clock));
                throw business("COMMON-422-BUSINESS_RULE", "联系方式暂不可用", 422, false);
            }
            if (value.isBlank() || value.length() > 2048) {
                store.contactRejected(userId, contentId, channel, "REJECTED_INVALID", Instant.now(clock));
                throw business("COMMON-422-BUSINESS_RULE", "联系方式暂不可用", 422, false);
            }
            Instant now = Instant.now(clock);
            String action = action(request.clientContext());
            ContactAccess result = new ContactAccess(channel, value, now);
            store.contactAudit(userId, contentId, channel, action, now);
            store.outbox(userId, "CONTENT", "content.contact.accessed.v1",
                    Long.toString(contentId), action, now);
            complete(claim, scope, key, requestHash, CONTACT_RESPONSE, result);
            return result;
        } catch (RuntimeException failure) {
            store.abandon(claim.id());
            throw failure;
        }
    }

    @Transactional
    public CommandResult clearHistory(long userId, String key) {
        String scope = "r07.search-history-clear:" + userId;
        String requestHash = hash(Map.of("userId", userId, "operation", "clear"));
        R07Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, CLEAR_RESPONSE, CommandResult.class);
        try {
            int removed = store.clearHistory(userId);
            Instant now = Instant.now(clock);
            CommandResult result = new CommandResult(Long.toString(userId), null, "CLEARED", removed, now);
            store.outbox(userId, "SEARCH_HISTORY", "search.history.cleared.v1",
                    Long.toString(userId), "CLEARED", now);
            complete(claim, scope, key, requestHash, CLEAR_RESPONSE, result);
            return result;
        } catch (RuntimeException failure) {
            store.abandon(claim.id());
            throw failure;
        }
    }

    private SearchTerms terms(PageRequest request, R07Store.TermRows rows) {
        List<SearchTerm> items = rows.items().stream()
                .map(row -> new SearchTerm(Long.toString(row.id()), row.keyword().strip(), row.createdAt()))
                .toList();
        return new SearchTerms(items, pageMeta(request, rows.total(), rows.hasMore(),
                rows.items().isEmpty() ? null : rows.items().getLast().id()));
    }

    private R07Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        return store.claim(scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
    }

    private <T> void complete(
            R07Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, T result) {
        try {
            String payload = cipher.encryptSnapshot(scope, key, requestHash, responseType,
                    mapper.writeValueAsBytes(result));
            store.complete(claim.id(), responseType + ":ok", responseType, payload);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R07 idempotency snapshot serialization failed", failure);
        }
    }

    private <T> T replay(
            R07Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, Class<T> type) {
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        if (claim.responseRef() == null || !responseType.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null || claim.responsePayloadCiphertext().isBlank()) {
            throw business("COMMON-409-VERSION_CONFLICT", "同一请求仍在处理中", 409, true);
        }
        try {
            byte[] plain = cipher.decryptSnapshot(
                    scope, key, requestHash, responseType, claim.responsePayloadCiphertext());
            return mapper.readValue(plain, type);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R07 idempotency snapshot is unavailable", failure);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R07 request hash unavailable", failure);
        }
    }

    private static PageRequest page(int page, int pageSize, String cursor) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数不符合要求");
        Long beforeId = cursor == null || cursor.isBlank() ? null : id(cursor, "游标不符合要求");
        return new PageRequest(page, pageSize, beforeId);
    }

    private static PageMeta pageMeta(PageRequest request, long total, boolean more, Long lastId) {
        return new PageMeta(request.page(), request.pageSize(), Long.toString(total),
                more && lastId != null ? Long.toString(lastId) : null, Boolean.toString(more));
    }

    private static void requireCursorOrder(PageRequest request, String sort) {
        if (request.beforeId() != null && !"id:desc".equals(sort)) {
            throw validation("该排序暂不支持游标，请使用页码");
        }
    }

    private static String storedType(String value) {
        return switch (value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT)) {
            case null -> null;
            case "GROUP_CHAT" -> "GROUP";
            case "PROJECT", "APP", "TEAM_LEADER" -> value.strip().toUpperCase(Locale.ROOT);
            default -> throw validation("内容类型不符合要求");
        };
    }

    private static String requiredText(String value, int max, String message) {
        String clean = value == null ? null : value.strip();
        if (clean == null || clean.isEmpty() || clean.length() > max) throw validation(message);
        return clean;
    }

    private static String optionalText(String value, int max, String message) {
        if (value == null || value.isBlank()) return null;
        return requiredText(value, max, message);
    }

    private static long id(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException invalid) {
            throw validation(message);
        }
    }

    private static String action(Map<String, Object> context) {
        Object raw = context.get("action");
        return raw != null && "COPY".equalsIgnoreCase(String.valueOf(raw)) ? "COPY" : "VIEW";
    }

    private static List<String> badges(boolean verified, String memberBadge) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (verified) values.add("VERIFIED");
        if (memberBadge != null && !memberBadge.isBlank()) values.add(memberBadge);
        return List.copyOf(values);
    }

    private static String nickname(String value, long userId) {
        return value == null || value.isBlank() ? "用户" + userId : value;
    }

    private static String outwardType(String value) { return "GROUP".equals(value) ? "GROUP_CHAT" : value; }
    private static BusinessException validation(String message) {
        return business("COMMON-400-VALIDATION", message, 400, false);
    }
    private static BusinessException notFound() {
        return business("COMMON-404-NOT_FOUND", "资源不存在或不可访问", 404, false);
    }
    private static BusinessException idempotencyConflict() {
        return business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }
    private static BusinessException business(String code, String message, int status, boolean retryable) {
        return new BusinessException(code, message, status, retryable);
    }

    private record PageRequest(int page, int pageSize, Long beforeId) { }
}
