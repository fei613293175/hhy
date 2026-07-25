package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.ContentContracts.PageMeta;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R12PublishingService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String COMMAND_RESPONSE = "r12.content-command-result.v1";
    private static final Set<String> MANAGEABLE_STATUSES = Set.of(
            "DRAFT", "PENDING_REVIEW", "REVIEWING", "REJECTED", "APPROVED", "ONLINE",
            "OFFLINE_BY_OWNER", "OFFLINE_BY_PLATFORM", "RECTIFICATION", "BANNED");
    private static final Set<String> SUBMITTABLE = Set.of("DRAFT", "REJECTED", "RECTIFICATION");
    private static final Set<String> COPYABLE = Set.of(
            "DRAFT", "PENDING_REVIEW", "REVIEWING", "REJECTED", "APPROVED", "ONLINE",
            "OFFLINE_BY_OWNER", "OFFLINE_BY_PLATFORM", "RECTIFICATION");
    private static final Map<String, String> OWNER_SORTS = Map.of(
            "createdAt:desc", "p.created_at DESC,p.id DESC",
            "createdAt:asc", "p.created_at ASC,p.id ASC",
            "updatedAt:desc", "p.updated_at DESC,p.id DESC",
            "updatedAt:asc", "p.updated_at ASC,p.id ASC",
            "id:desc", "p.id DESC", "id:asc", "p.id ASC");
    private static final Map<String, String> REVIEW_SORTS = Map.of(
            "createdAt:desc", "review.created_at DESC,review.id DESC",
            "createdAt:asc", "review.created_at ASC,review.id ASC",
            "updatedAt:desc", "review.created_at DESC,review.id DESC",
            "updatedAt:asc", "review.created_at ASC,review.id ASC",
            "id:desc", "review.id DESC", "id:asc", "review.id ASC");

    private final R12PublishingStore store;
    private final R08Store shared;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R12PublishingService(
            R12PublishingStore store, R08Store shared, ContentService content,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ContentPage mine(
            long userId, int page, int pageSize, String cursor, String status, String keyword,
            String sort, String contentType, String categoryCode, String regionCode) {
        return ownerPage(userId, page, pageSize, cursor, status, keyword, sort,
                contentType, categoryCode, regionCode, false);
    }

    @Transactional(readOnly = true)
    public ContentPage drafts(
            long userId, int page, int pageSize, String cursor, String status,
            String keyword, String sort) {
        String normalized = normalized(status);
        if (normalized != null && !"DRAFT".equals(normalized)) {
            throw validation("草稿箱只允许查询DRAFT状态");
        }
        return ownerPage(userId, page, pageSize, cursor, "DRAFT", keyword, sort,
                null, null, null, true);
    }

    @Transactional(readOnly = true)
    public ContentPage reviews(
            long userId, String idValue, int page, int pageSize, String cursor,
            String status, String keyword, String sort, String contentType,
            String categoryCode, String regionCode) {
        long id = id(idValue, "内容标识无效");
        ContentResource resource = ownedResource(userId, id);
        if (!matches(resource, null, null, contentType, categoryCode, regionCode)) {
            return empty(page, pageSize);
        }
        if (!REVIEW_SORTS.containsKey(sort)) throw validation("排序字段不在允许范围内");
        R12PublishingStore.ReviewPage result = store.reviews(id, new R12PublishingStore.ReviewQuery(
                page, pageSize, cursor(cursor, sort), normalized(status), clean(keyword), sort));
        List<ContentResource> items = result.items().stream()
                .map(review -> withReview(resource, review)).toList();
        String next = result.hasMore() && !result.items().isEmpty()
                ? cursor(sort, result.items().getLast().id(), result.items().getLast().createdAt()) : null;
        return new ContentPage(items, new PageMeta(
                page, pageSize, Long.toString(result.total()), next, Boolean.toString(result.hasMore())));
    }

    @Transactional(readOnly = true)
    public ContentPage analytics(
            long userId, String idValue, int page, int pageSize, String cursor,
            String status, String keyword, String sort, String contentType,
            String categoryCode, String regionCode) {
        long id = id(idValue, "内容标识无效");
        ContentResource resource = ownedResource(userId, id);
        if (!OWNER_SORTS.containsKey(sort)) throw validation("排序字段不在允许范围内");
        if (page != 1 || clean(cursor) != null
                || !matches(resource, status, keyword, contentType, categoryCode, regionCode)) {
            return empty(page, pageSize);
        }
        return new ContentPage(List.of(resource), new PageMeta(1, pageSize, "1", null, "false"));
    }

    @Transactional
    public CommandResult submit(long userId, String id, StatusRequest request, String key) {
        return submit(userId, id, request, key, CommandContext.unknown());
    }

    @Transactional
    public CommandResult submit(
            long userId, String id, StatusRequest request, String key, CommandContext context) {
        return transition(userId, id, request, key, context, Action.SUBMIT);
    }

    @Transactional
    public CommandResult online(long userId, String id, StatusRequest request, String key) {
        return online(userId, id, request, key, CommandContext.unknown());
    }

    @Transactional
    public CommandResult online(
            long userId, String id, StatusRequest request, String key, CommandContext context) {
        return transition(userId, id, request, key, context, Action.ONLINE);
    }

    @Transactional
    public CommandResult offline(long userId, String id, StatusRequest request, String key) {
        return offline(userId, id, request, key, CommandContext.unknown());
    }

    @Transactional
    public CommandResult offline(
            long userId, String id, StatusRequest request, String key, CommandContext context) {
        return transition(userId, id, request, key, context, Action.OFFLINE);
    }

    @Transactional
    public CommandResult delete(long userId, String idValue, long expectedVersion, String key) {
        return delete(userId, idValue, expectedVersion, key, CommandContext.unknown());
    }

    @Transactional
    public CommandResult delete(
            long userId, String idValue, long expectedVersion, String key, CommandContext context) {
        return transition(userId, idValue, new StatusRequest(expectedVersion, null), key, context, Action.DELETE);
    }

    @Transactional
    public CommandResult copy(long userId, String idValue, StatusRequest request, String key) {
        return copy(userId, idValue, request, key, CommandContext.unknown());
    }

    @Transactional
    public CommandResult copy(
            long userId, String idValue, StatusRequest request, String key, CommandContext context) {
        long sourceId = id(idValue, "内容标识无效");
        String reason = clean(request.reason());
        String requestHash = hash(Map.of(
                "userId", userId, "sourceId", sourceId,
                "expectedVersion", request.expectedVersion(), "reason", reason == null ? "" : reason));
        String scope = "r12.content-copy:user:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        requireVerified(userId);
        R12PublishingStore.OwnedContent source = lockOwner(userId, sourceId);
        if (source.version() != request.expectedVersion()) throw versionConflict();
        if (!COPYABLE.contains(source.status())) throw statusConflict();
        if ("TEAM_LEADER".equals(source.type())) {
            throw business("团队长资料不可复制，请编辑现有资料");
        }
        store.lockOwnerQuota(userId);
        if (shared.countOwnedInStatus(userId, "DRAFT") >= shared.integerConfig("content.limit.normal.drafts")) {
            throw business("草稿数量已达到当前配置上限");
        }
        List<R12PublishingStore.ContactEnvelope> sourceContacts = store.activeContacts(sourceId);
        Instant now = Instant.now(clock);
        long targetId = store.copySkeleton(sourceId, userId, now);
        List<R12PublishingStore.ContactEnvelope> copiedContacts = new ArrayList<>();
        for (R12PublishingStore.ContactEnvelope contact : sourceContacts) {
            String plain = cipher.decrypt(sourceId, contact.channel(), contact.valueCipher());
            copiedContacts.add(new R12PublishingStore.ContactEnvelope(
                    contact.channel(), cipher.encrypt(targetId, contact.channel(), plain),
                    contact.displayMask(), contact.sortOrder()));
        }
        store.copyContacts(targetId, copiedContacts, now);
        store.outbox(userId, "content.copied.v1", targetId, null, "DRAFT", 0, reason,
                audit(context), now);
        CommandResult result = new CommandResult(Long.toString(targetId), null, "DRAFT", 0, now);
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    private ContentPage ownerPage(
            long userId, int page, int pageSize, String cursorValue, String statusValue,
            String keyword, String sort, String contentType, String categoryCode,
            String regionCode, boolean draftsOnly) {
        if (!OWNER_SORTS.containsKey(sort)) throw validation("排序字段不在允许范围内");
        String status = normalized(statusValue);
        if (!draftsOnly && status != null && !MANAGEABLE_STATUSES.contains(status)) {
            throw validation("内容状态不符合要求");
        }
        String storedType = storedType(contentType);
        R12PublishingStore.PageIds result = store.ownerPage(new R12PublishingStore.OwnerQuery(
                userId, page, pageSize, cursor(cursorValue, sort), status, clean(keyword), sort,
                storedType, clean(categoryCode), clean(regionCode), draftsOnly));
        List<ContentResource> items = result.items().stream()
                .map(value -> content.detail(Long.toString(value.id()))).toList();
        String next = result.hasMore() && !result.items().isEmpty()
                ? cursor(sort, result.items().getLast().id(), result.items().getLast().sortValue()) : null;
        return new ContentPage(items, new PageMeta(
                page, pageSize, Long.toString(result.total()), next, Boolean.toString(result.hasMore())));
    }

    private CommandResult transition(
            long userId, String idValue, StatusRequest request, String key,
            CommandContext context, Action action) {
        long contentId = id(idValue, "内容标识无效");
        String reason = clean(request.reason());
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", contentId, "action", action.name(),
                "expectedVersion", request.expectedVersion(), "reason", reason == null ? "" : reason));
        String scope = "r12.content-" + action.name().toLowerCase(Locale.ROOT) + ":user:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        R12PublishingStore.OwnedContent locked = lockOwner(userId, contentId);
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        Instant now = Instant.now(clock);
        String target;
        String reviewStatus = null;
        String event;
        switch (action) {
            case SUBMIT -> {
                requireVerified(userId);
                if (!SUBMITTABLE.contains(locked.status())) throw statusConflict();
                if (Set.of("REJECTED", "RECTIFICATION").contains(locked.status())
                        && locked.version() <= store.latestCorrectionVersion(contentId)) {
                    throw statusConflict();
                }
                store.lockOwnerQuota(userId);
                if (shared.countOwnedInStatus(userId, "PENDING_REVIEW")
                        >= shared.integerConfig("content.limit.normal.pending")) {
                    throw business("待审核内容数量已达到当前配置上限");
                }
                if (store.submissionsSince(userId, now.truncatedTo(ChronoUnit.DAYS))
                        >= shared.integerConfig("content.limit.normal.daily_submissions")) {
                    throw business("今日提交审核次数已达到当前配置上限");
                }
                target = "PENDING_REVIEW";
                reviewStatus = "PENDING";
                event = "content.submitted.v1";
            }
            case ONLINE -> {
                requireVerified(userId);
                if (!Set.of("APPROVED", "OFFLINE_BY_OWNER").contains(locked.status())) throw statusConflict();
                if ("OFFLINE_BY_OWNER".equals(locked.status()) && !store.approvedSnapshotIsCurrent(contentId)) {
                    throw statusConflict();
                }
                store.lockOwnerQuota(userId);
                String tier = publishingTier(store.publishingTier(userId));
                if (shared.countOwnedInStatus(userId, "ONLINE")
                        >= shared.integerConfig("content.limit." + tier + ".online")) {
                    throw business("在线内容数量已达到当前配置上限");
                }
                target = "ONLINE";
                event = "content.online.v1";
            }
            case OFFLINE -> {
                if (!"ONLINE".equals(locked.status())) throw statusConflict();
                target = "OFFLINE_BY_OWNER";
                event = "content.offline.owner.v1";
            }
            case DELETE -> {
                if (!COPYABLE.contains(locked.status())) throw statusConflict();
                target = "DELETED";
                event = "content.deleted.v1";
            }
            default -> throw new IllegalStateException("Unknown R12 publishing action");
        }
        long nextVersion = locked.version() + 1;
        if (!store.transitionOwned(contentId, userId, locked.version(), locked.status(), target, reviewStatus, now)) {
            throw versionConflict();
        }
        store.statusLog(contentId, locked.status(), target, reason, userId, nextVersion);
        if (action == Action.SUBMIT) store.submissionSnapshot(contentId, userId, now);
        store.outbox(userId, event, contentId, locked.status(), target, nextVersion, reason,
                audit(context), now);
        CommandResult result = new CommandResult(Long.toString(contentId), null, target, nextVersion, now);
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    private ContentResource ownedResource(long userId, long contentId) {
        ContentResource resource = content.detail(Long.toString(contentId));
        if (resource.publisher() == null
                || !Long.toString(userId).equals(resource.publisher().userId())
                || "DELETED".equals(resource.status())) {
            throw forbidden();
        }
        return resource;
    }

    private R12PublishingStore.OwnedContent lockOwner(long userId, long contentId) {
        R12PublishingStore.OwnedContent value = store.lockOwned(contentId)
                .orElseThrow(R12PublishingService::notFound);
        if (value.ownerId() != userId) throw forbidden();
        return value;
    }

    private void requireVerified(long userId) {
        if (!shared.identityVerified(userId)) {
            throw new BusinessException("IDENTITY-422-NOT_VERIFIED", "未完成实名认证", 422, false);
        }
    }

    private R08Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        return shared.claim(scope, key.strip(), requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
    }

    private CommandResult replay(
            R08Store.IdempotencyClaim claim, String scope, String key, String requestHash) {
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        if (claim.responseRef() == null || !COMMAND_RESPONSE.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null
                || claim.responsePayloadCiphertext().isBlank()) throw versionConflict();
        try {
            byte[] payload = cipher.decryptSnapshot(
                    scope, key.strip(), requestHash, COMMAND_RESPONSE,
                    claim.responsePayloadCiphertext());
            return mapper.readValue(payload, CommandResult.class);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R12 idempotency snapshot is unavailable", failure);
        }
    }

    private void complete(
            R08Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, CommandResult result) {
        try {
            String payload = cipher.encryptSnapshot(
                    scope, key.strip(), requestHash, COMMAND_RESPONSE,
                    mapper.writeValueAsBytes(result));
            shared.complete(claim.id(), COMMAND_RESPONSE + ":ok", COMMAND_RESPONSE, payload);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R12 idempotency snapshot serialization failed", failure);
        }
    }

    private static R12PublishingStore.CommandAudit audit(CommandContext context) {
        CommandContext safe = context == null ? CommandContext.unknown() : context;
        return new R12PublishingStore.CommandAudit(
                clean(safe.requestId()), clean(safe.clientIp()), clean(safe.device()));
    }

    private static String publishingTier(String value) {
        String tier = normalized(value);
        return switch (tier == null ? "NORMAL" : tier) {
            case "MONTH" -> "month";
            case "QUARTER" -> "quarter";
            case "YEAR" -> "year";
            default -> "normal";
        };
    }

    private ContentResource withReview(ContentResource source, R12PublishingStore.ReviewFact review) {
        Map<String, Object> attributes = new LinkedHashMap<>(source.attributes());
        Map<String, Object> reviewData = new LinkedHashMap<>();
        reviewData.put("id", Long.toString(review.id()));
        if (review.decision() != null) reviewData.put("decision", review.decision());
        if (review.reason() != null) reviewData.put("reason", review.reason());
        if (review.adminId() != null) reviewData.put("adminId", Long.toString(review.adminId()));
        if (review.versionNo() != null) reviewData.put("versionNo", review.versionNo());
        if (review.createdAt() != null) reviewData.put("createdAt", review.createdAt().toString());
        attributes.put("review", Map.copyOf(reviewData));
        long version = review.versionNo() == null ? source.version() : parseVersion(review.versionNo(), source.version());
        return new ContentResource(
                source.id(), source.contentType(), source.title(), source.summary(), source.description(),
                source.categoryCode(), source.regionCode(), source.media(), source.publisher(),
                source.contactsMasked(), source.status(), review.decision(), source.statistics(),
                source.createdAt(), review.createdAt(), version, attributes);
    }

    private static long parseVersion(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    private static boolean matches(
            ContentResource resource, String status, String keyword, String contentType,
            String categoryCode, String regionCode) {
        String normalizedStatus = normalized(status);
        if (normalizedStatus != null && !normalizedStatus.equals(resource.status())) return false;
        String normalizedType = clean(contentType);
        if (normalizedType != null && !normalizedType.equals(resource.contentType())) return false;
        if (clean(categoryCode) != null && !categoryCode.strip().equals(resource.categoryCode())) return false;
        if (clean(regionCode) != null && !regionCode.strip().equals(resource.regionCode())) return false;
        String text = clean(keyword);
        if (text != null) {
            String needle = text.toLowerCase(Locale.ROOT);
            String title = resource.title() == null ? "" : resource.title().toLowerCase(Locale.ROOT);
            String summary = resource.summary() == null ? "" : resource.summary().toLowerCase(Locale.ROOT);
            if (!title.contains(needle) && !summary.contains(needle)) return false;
        }
        return true;
    }

    private static ContentPage empty(int page, int pageSize) {
        return new ContentPage(List.of(), new PageMeta(page, pageSize, "0", null, "false"));
    }

    private String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R12 publishing request hashing failed", failure);
        }
    }

    private static R12PublishingStore.CursorKey cursor(String value, String sort) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cleaned), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", -1);
            if (parts.length != 4 || !"v1".equals(parts[0]) || !sort.equals(parts[1])) {
                throw new IllegalArgumentException();
            }
            long id = Long.parseLong(parts[3]);
            if (id <= 0) throw new IllegalArgumentException();
            Instant sortValue = parts[2].isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(parts[2]));
            if (!sort.startsWith("id:") && sortValue == null) throw new IllegalArgumentException();
            return new R12PublishingStore.CursorKey(id, sortValue);
        } catch (RuntimeException invalid) {
            throw validation("分页游标无效");
        }
    }

    private static String cursor(String sort, long id, Instant sortValue) {
        String timestamp = sort.startsWith("id:") ? "" : Long.toString(sortValue.toEpochMilli());
        String payload = "v1\n" + sort + "\n" + timestamp + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String storedType(String value) {
        return switch (clean(value)) {
            case null -> null;
            case "PROJECT", "APP", "TEAM_LEADER" -> value.strip();
            case "GROUP_CHAT" -> "GROUP";
            default -> throw validation("内容类型不符合要求");
        };
    }

    private static long id(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException invalid) {
            throw validation(message);
        }
    }

    private static String normalized(String value) {
        String clean = clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException forbidden() {
        return new BusinessException("COMMON-403-FORBIDDEN", "无权访问该内容", 403, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "内容不存在", 404, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", "内容版本已变化", 409, false);
    }

    private static BusinessException statusConflict() {
        return new BusinessException("CONTENT-409-STATUS_TRANSITION", "当前内容状态不允许该操作", 409, false);
    }

    private static BusinessException idempotencyConflict() {
        return new BusinessException(
                "COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public record CommandContext(String requestId, String clientIp, String device) {
        public static CommandContext unknown() {
            return new CommandContext("missing", "unknown", "unknown");
        }
    }

    private enum Action { SUBMIT, ONLINE, OFFLINE, DELETE }
}
