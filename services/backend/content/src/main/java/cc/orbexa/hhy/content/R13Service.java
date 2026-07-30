package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.ContentContracts.PageMeta;
import cc.orbexa.hhy.content.R13Contracts.InvalidFeedbackRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R13Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String UNFAVORITE_RESPONSE = "r13.unfavorite-command.v1";
    private static final String INVALID_FEEDBACK_RESPONSE = "r13.invalid-feedback-command.v1";
    private final R13Store store;
    private final R08Store shared;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R13Service(
            R13Store store, R08Store shared, ContentService content,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ContentPage favorites(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        requireActive(userId);
        return page(store.favorites(query(userId, page, pageSize, cursor, status, keyword, sort)),
                page, pageSize);
    }

    @Transactional(readOnly = true)
    public ContentPage history(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        requireActive(userId);
        return page(store.history(query(userId, page, pageSize, cursor, status, keyword, sort)),
                page, pageSize);
    }

    @Transactional
    public CommandResult unfavorite(long userId, String idValue, String key) {
        requireActive(userId);
        long contentId = id(idValue, "内容标识无效");
        String scope = "r13.unfavorite:" + userId + ":" + contentId;
        String requestHash = hash(Map.of("userId", userId, "contentId", contentId));
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            return replay(claim, scope, key, requestHash, UNFAVORITE_RESPONSE, CommandResult.class);
        }
        R08Store.ContentRow locked = shared.lockContent(contentId).orElseThrow(R13Service::notFound);
        Instant now = Instant.now(clock);
        boolean removed = store.unfavorite(userId, contentId, now);
        if (removed) {
            shared.outbox(userId, "CONTENT", "content.unfavorited.v1",
                    Long.toString(contentId), "UNFAVORITED", now);
        }
        CommandResult result = new CommandResult(Long.toString(contentId), null,
                removed ? "UNFAVORITED" : "NOT_FAVORITED", locked.version(), now);
        complete(claim, scope, key, requestHash, UNFAVORITE_RESPONSE, result);
        return result;
    }

    @Transactional
    public CommandResult invalidFeedback(
            long userId, String idValue, InvalidFeedbackRequest request, String key) {
        requireActive(userId);
        long contentId = id(idValue, "内容标识无效");
        String reasonCode = normalizedReason(request.reasonCode());
        String description = optional(request.description());
        Map<String, Object> requestFact = new LinkedHashMap<>();
        requestFact.put("userId", userId);
        requestFact.put("contentId", contentId);
        requestFact.put("reasonCode", reasonCode);
        requestFact.put("description", description == null ? "" : description);
        String scope = "r13.invalid-feedback:" + userId + ":" + contentId;
        String requestHash = hash(requestFact);
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            return replay(claim, scope, key, requestHash, INVALID_FEEDBACK_RESPONSE, CommandResult.class);
        }
        R08Store.ContentRow locked = shared.lockContent(contentId).orElseThrow(R13Service::notFound);
        if (!"ONLINE".equals(locked.status())) throw notFound();
        Instant now = Instant.now(clock);
        long reportId = store.invalidFeedback(userId, contentId, reasonCode, description, now);
        shared.outbox(userId, "CONTENT_REPORT", "content.invalid-feedback.created.v1",
                Long.toString(reportId), "PENDING", now);
        CommandResult result = new CommandResult(Long.toString(reportId), null, "PENDING", 0, now);
        complete(claim, scope, key, requestHash, INVALID_FEEDBACK_RESPONSE, result);
        return result;
    }

    private ContentPage page(R13Store.ActivityPage result, int page, int pageSize) {
        List<ContentResource> items = content.resourcesById(
                result.items().stream().map(R13Store.ActivityRow::contentId).toList());
        String next = result.hasMore() && !result.items().isEmpty()
                ? cursor(result.items().getLast()) : null;
        return new ContentPage(items, new PageMeta(page, pageSize,
                Long.toString(result.total()), next, Boolean.toString(result.hasMore())));
    }

    private R13Store.ActivityQuery query(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数不符合要求");
        String cleanCursor = clean(cursor);
        if (cleanCursor != null && page != 1) throw validation("游标与页码不能同时使用");
        String cleanStatus = upper(status);
        if (cleanStatus != null && cleanStatus.length() > 64) throw validation("状态筛选不符合要求");
        String cleanKeyword = clean(keyword);
        if (cleanKeyword != null && cleanKeyword.length() > 100) throw validation("关键词筛选不符合要求");
        String selectedSort = clean(sort);
        R13Store.SortDirection direction = switch (selectedSort == null ? "createdAt:desc" : selectedSort) {
            case "createdAt:desc" -> R13Store.SortDirection.DESC;
            case "createdAt:asc" -> R13Store.SortDirection.ASC;
            default -> throw validation("排序字段不在允许范围内");
        };
        return new R13Store.ActivityQuery(userId, page, pageSize,
                parseCursor(cleanCursor), cleanStatus, cleanKeyword, direction);
    }

    private static R13Store.ActivityCursor parseCursor(String value) {
        if (value == null) return null;
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            long millis = Long.parseLong(parts[0]);
            long activityId = Long.parseLong(parts[1]);
            if (millis < 0 || activityId < 1) throw new IllegalArgumentException();
            return new R13Store.ActivityCursor(Instant.ofEpochMilli(millis), activityId);
        } catch (RuntimeException invalid) {
            throw validation("游标不符合要求");
        }
    }

    private static String cursor(R13Store.ActivityRow row) {
        return row.occurredAt().toEpochMilli() + ":" + row.activityId();
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
            throw new IllegalStateException("R13 idempotency snapshot serialization failed", failure);
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
            byte[] plain = cipher.decryptSnapshot(
                    scope, key, requestHash, responseType, claim.responsePayloadCiphertext());
            return mapper.readValue(plain, type);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R13 idempotency snapshot is unavailable", failure);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R13 request hash unavailable", failure);
        }
    }

    private static String normalizedReason(String value) {
        String result = upper(value);
        if (result == null || result.length() > 64 || !result.matches("[A-Z0-9_-]+")) {
            throw validation("原因代码不符合要求");
        }
        return result;
    }

    private static String optional(String value) {
        String result = clean(value);
        if (result != null && result.length() > 2000) throw validation("详细说明不符合要求");
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
        return business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }

    private static BusinessException business(String code, String message, int status, boolean retryable) {
        return new BusinessException(code, message, status, retryable);
    }
}
