package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContactSummary;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.ContentContracts.DictionaryRequest;
import cc.orbexa.hhy.content.ContentContracts.FeatureFlag;
import cc.orbexa.hhy.content.ContentContracts.HomeItem;
import cc.orbexa.hhy.content.ContentContracts.HomeModule;
import cc.orbexa.hhy.content.ContentContracts.HomeResource;
import cc.orbexa.hhy.content.ContentContracts.NavigationTarget;
import cc.orbexa.hhy.content.ContentContracts.OfficialMarkRequest;
import cc.orbexa.hhy.content.ContentContracts.PageMeta;
import cc.orbexa.hhy.content.ContentContracts.PublisherSummary;
import cc.orbexa.hhy.content.ContentContracts.RecommendRequest;
import cc.orbexa.hhy.content.ContentContracts.Statistics;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import cc.orbexa.hhy.content.ContentContracts.TrackingContext;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Map<String, String> CONTENT_SORTS = Map.of(
            "createdAt:desc", "p.created_at DESC,p.id DESC",
            "createdAt:asc", "p.created_at ASC,p.id ASC",
            "updatedAt:desc", "p.updated_at DESC,p.id DESC",
            "updatedAt:asc", "p.updated_at ASC,p.id ASC",
            "id:desc", "p.id DESC", "id:asc", "p.id ASC");
    private static final Map<String, String> DICTIONARY_SORTS = Map.of(
            "createdAt:desc", "created_at DESC,id DESC",
            "createdAt:asc", "created_at ASC,id ASC",
            "updatedAt:desc", "updated_at DESC,id DESC",
            "updatedAt:asc", "updated_at ASC,id ASC",
            "id:desc", "id DESC", "id:asc", "id ASC");
    private static final Set<String> HOME_TYPES = Set.of(
            "BANNER", "GRID", "HORIZONTAL_LIST", "VERTICAL_LIST", "NOTICE", "QUICK_ACTIONS");
    private final ContentStore store;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ContentService(ContentStore store, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ContentPage list(
            int page, int pageSize, String cursor, String status, String keyword, String sort,
            String contentType, String categoryCode, String regionCode) {
        return list(page, pageSize, cursor, status, keyword, sort,
                contentType, categoryCode, regionCode, null);
    }

    @Transactional(readOnly = true)
    public ContentPage list(
            int page, int pageSize, String cursor, String status, String keyword, String sort,
            String contentType, String categoryCode, String regionCode, String publisherId) {
        String order = CONTENT_SORTS.get(sort);
        if (order == null) throw validation("排序字段不在允许范围内");
        Long beforeId = cursor(cursor);
        String storedType = switch (clean(contentType)) {
            case null -> null;
            case "GROUP_CHAT" -> "GROUP";
            case "PROJECT", "APP", "TEAM_LEADER" -> contentType;
            default -> throw validation("内容类型不符合要求");
        };
        ContentStore.PageRows rows = store.page(new ContentStore.ContentQuery(
                page, pageSize, beforeId, clean(status), clean(keyword), order,
                storedType, clean(categoryCode), clean(regionCode), optionalId(publisherId)));
        List<ContentResource> items = rows.items().stream().map(this::resource).toList();
        String next = rows.hasMore() && !rows.items().isEmpty()
                ? Long.toString(rows.items().getLast().id()) : null;
        return new ContentPage(items, new PageMeta(
                page, pageSize, Long.toString(rows.total()), next, Boolean.toString(rows.hasMore())));
    }

    @Transactional(readOnly = true)
    public ContentResource detail(String id) {
        return store.detail(id(id, "内容标识无效")).map(this::resource).orElseThrow(ContentService::notFound);
    }

    @Transactional
    public CommandResult online(
            long adminId, String id, StatusRequest request, String key, String requestId, String ip) {
        return transition(adminId, id, request.expectedVersion(), request.reason(), key, requestId, ip,
                "ONLINE", Set.of("APPROVED", "OFFLINE_BY_OWNER", "OFFLINE_BY_PLATFORM"), "CONTENT_ONLINE");
    }

    @Transactional
    public CommandResult offline(
            long adminId, String id, StatusRequest request, String key, String requestId, String ip) {
        if (clean(request.reason()) == null) throw validation("平台下架必须填写原因");
        return transition(adminId, id, request.expectedVersion(), request.reason(), key, requestId, ip,
                "OFFLINE_BY_PLATFORM", Set.of("ONLINE"), "CONTENT_OFFLINE");
    }

    @Transactional
    public CommandResult ban(
            long adminId, String id, ContentContracts.BanRequest request,
            String key, String requestId, String ip) {
        return transition(adminId, id, request.expectedVersion(), request.reason(), key, requestId, ip,
                "BANNED", Set.of("DRAFT", "PENDING_REVIEW", "REVIEWING", "APPROVED", "REJECTED",
                        "RECTIFICATION", "ONLINE", "OFFLINE_BY_OWNER", "OFFLINE_BY_PLATFORM"), "CONTENT_BANNED");
    }

    @Transactional
    public CommandResult recommend(
            long adminId, String idValue, RecommendRequest request,
            String key, String requestId, String ip) {
        if (request.startAt() != null && request.endAt() != null
                && !request.endAt().isAfter(request.startAt())) {
            throw validation("推荐结束时间必须晚于开始时间");
        }
        Map<String, Object> recommendation = new java.util.LinkedHashMap<>();
        recommendation.put("enabled", request.enabled());
        if (request.weight() != null) recommendation.put("weight", request.weight());
        if (request.startAt() != null) recommendation.put("startAt", request.startAt().toString());
        if (request.endAt() != null) recommendation.put("endAt", request.endAt().toString());
        return attributes(adminId, idValue, request.expectedVersion(), key, requestId, ip,
                "CONTENT_RECOMMEND", Map.of("recommendation", recommendation));
    }

    @Transactional
    public CommandResult official(
            long adminId, String idValue, OfficialMarkRequest request,
            String key, String requestId, String ip) {
        if (request.enabled() && clean(request.label()) == null) throw validation("启用官方标识时必须填写标签");
        Map<String, Object> mark = new java.util.LinkedHashMap<>();
        mark.put("enabled", request.enabled());
        if (clean(request.label()) != null) mark.put("label", request.label().strip());
        return attributes(adminId, idValue, request.expectedVersion(), key, requestId, ip,
                "CONTENT_OFFICIAL_MARK", Map.of("officialMark", mark));
    }

    @Transactional(readOnly = true)
    public ContentPage dictionaries(int page, int pageSize, String cursor, String status, String keyword, String sort) {
        if (clean(cursor) != null) throw validation("内容字典暂不支持游标与页码混用");
        String normalizedStatus = clean(status) == null ? null : status.toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !Set.of("ENABLED", "DISABLED").contains(normalizedStatus)) {
            throw validation("字典状态不符合要求");
        }
        String order = DICTIONARY_SORTS.get(sort);
        if (order == null) throw validation("排序字段不在允许范围内");
        Boolean enabled = normalizedStatus == null ? null : "ENABLED".equals(normalizedStatus);
        List<ContentResource> items = store.dictionaries(page, pageSize, clean(keyword), enabled, order).stream()
                .map(this::dictionaryResource).toList();
        long total = store.dictionaryCount(clean(keyword), enabled);
        boolean more = (long) page * pageSize < total;
        return new ContentPage(items, new PageMeta(page, pageSize, Long.toString(total), null, Boolean.toString(more)));
    }

    @Transactional
    public CommandResult putDictionary(
            long adminId, String codeValue, DictionaryRequest request,
            String key, String requestId, String ip) {
        String code = codeValue == null ? "" : codeValue.strip();
        if (!code.matches("^[A-Za-z0-9_.:-]{1,128}$")) throw validation("字典代码不符合要求");
        String requestHash = hash(request);
        String scope = "r06.dict:" + adminId + ":" + code;
        ContentStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, requestHash);
        Instant now = Instant.now(clock);
        String items = json(request.items());
        ContentStore.DictionaryRow updated = store.putDictionary(code, request.expectedVersion(), items, now);
        if (updated == null) throw versionConflict();
        CommandResult result = new CommandResult(code, null, updated.enabled() ? "ENABLED" : "DISABLED",
                updated.version(), now);
        store.audit(adminId, "CONTENT_DICTIONARY_UPDATED", updated.id(), null,
                json(Map.of("code", code, "version", updated.version(), "requestId", requestId)), ip);
        store.outbox(adminId, "content.dictionary.updated.v1", code, result.status(), now);
        store.complete(claim.id(), encode(result));
        return result;
    }

    @Transactional(readOnly = true)
    public HomeResource home(String requestId) {
        Instant now = Instant.now(clock);
        List<HomeModule> modules = store.homeModules().stream().map(row -> homeModule(row, requestId))
                .filter(module -> module.startAt() == null || !module.startAt().isAfter(now))
                .filter(module -> module.endAt() == null || module.endAt().isAfter(now))
                .toList();
        return new HomeResource(modules, now, List.<FeatureFlag>of(),
                tracking("SCR-HOME-001", "HOME", null, requestId));
    }

    private CommandResult transition(
            long adminId, String idValue, long expectedVersion, String reason,
            String key, String requestId, String ip, String target,
            Set<String> allowed, String action) {
        long id = id(idValue, "内容标识无效");
        String requestHash = hash(Map.of(
                "id", id, "expectedVersion", expectedVersion, "reason", clean(reason) == null ? "" : reason,
                "target", target));
        String scope = "r06.content:" + adminId + ":" + action + ":" + id;
        ContentStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, requestHash);
        ContentStore.LockedContent locked = store.lock(id).orElseThrow(ContentService::notFound);
        if (locked.version() != expectedVersion) throw versionConflict();
        if (!allowed.contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.transition(id, expectedVersion, target, now)) throw versionConflict();
        String actor = "admin:" + adminId;
        store.statusLog(id, locked.status(), target, clean(reason), actor);
        store.audit(adminId, action, id, json(Map.of("status", locked.status(), "version", locked.version())),
                json(Map.of("status", target, "version", expectedVersion + 1, "requestId", requestId)), ip);
        store.outbox(adminId, event(action), Long.toString(id), target, now);
        CommandResult result = new CommandResult(Long.toString(id), null, target, expectedVersion + 1, now);
        store.complete(claim.id(), encode(result));
        return result;
    }

    private CommandResult attributes(
            long adminId, String idValue, long expectedVersion, String key,
            String requestId, String ip, String action, Map<String, Object> patch) {
        long id = id(idValue, "内容标识无效");
        String requestHash = hash(Map.of("id", id, "expectedVersion", expectedVersion, "patch", patch));
        String scope = "r06.content:" + adminId + ":" + action + ":" + id;
        ContentStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, requestHash);
        ContentStore.LockedContent locked = store.lock(id).orElseThrow(ContentService::notFound);
        if (locked.version() != expectedVersion) throw versionConflict();
        if (Set.of("DELETED", "BANNED").contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.updateAttributes(id, expectedVersion, json(patch), "admin:" + adminId, now)) {
            throw versionConflict();
        }
        store.audit(adminId, action, id, null,
                json(Map.of("version", expectedVersion + 1, "requestId", requestId, "patch", patch)), ip);
        store.outbox(adminId, event(action), Long.toString(id), locked.status(), now);
        CommandResult result = new CommandResult(
                Long.toString(id), null, locked.status(), expectedVersion + 1, now);
        store.complete(claim.id(), encode(result));
        return result;
    }

    private ContentStore.IdempotencyClaim claim(String scope, String key, String hash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        return store.claim(scope, key, hash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
    }

    private CommandResult replay(ContentStore.IdempotencyClaim claim, String requestHash) {
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        if (claim.responseRef() == null) throw versionConflict();
        try {
            String[] parts = claim.responseRef().split(":", -1);
            if (parts.length != 5 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
            return new CommandResult(parts[1], null, parts[2], Long.parseLong(parts[3]),
                    Instant.ofEpochMilli(Long.parseLong(parts[4])));
        } catch (RuntimeException invalid) {
            throw versionConflict();
        }
    }

    private ContentResource resource(ContentStore.ContentRow row) {
        Map<String, Object> attributes = object(row.attributesJson());
        return new ContentResource(
                Long.toString(row.id()), outwardType(row.type()), row.title(), row.summary(),
                string(attributes.get("description")), string(attributes.get("categoryCode")),
                string(attributes.get("regionCode")), List.of(),
                new PublisherSummary(Long.toString(row.ownerId()),
                        clean(row.nickname()) == null ? "用户" + row.ownerId() : row.nickname(),
                        row.avatar(), row.bio(), false, null, null),
                store.contacts(row.id()).stream().map(contact -> new ContactSummary(
                        contact.channel(), contact.displayMask(), true, "LOGIN", false)).toList(),
                row.status(), row.reviewStatus(), new Statistics(
                        count(row.views()), count(row.favorites()), 0, count(row.contacts()), count(row.chats())),
                row.createdAt(), row.updatedAt(), row.version(), attributes);
    }

    private ContentResource dictionaryResource(ContentStore.DictionaryRow row) {
        return new ContentResource(Long.toString(row.id()), "DICTIONARY", row.title(), null, null,
                row.code(), null, List.of(), null, List.of(), row.enabled() ? "ENABLED" : "DISABLED",
                null, null, null, null, row.version(), object(row.itemsJson()));
    }

    private HomeModule homeModule(ContentStore.HomeRow row, String requestId) {
        JsonNode config = tree(row.configJson());
        String source = clean(row.sourceType());
        String type = source == null ? "VERTICAL_LIST" : source.toUpperCase(Locale.ROOT);
        if (!HOME_TYPES.contains(type)) type = "VERTICAL_LIST";
        List<HomeItem> items = new java.util.ArrayList<>();
        JsonNode configured = config.path("items");
        if (configured.isArray()) for (JsonNode item : configured) {
            if (!item.hasNonNull("id") || !item.hasNonNull("title")) continue;
            JsonNode target = item.path("target");
            String targetType = target.path("targetType").asText("NONE");
            items.add(new HomeItem(item.path("id").asText(), item.path("itemType").asText("ACTION"),
                    item.path("title").asText(), nullable(item, "subtitle"), nullable(item, "coverUrl"),
                    strings(item.path("badges")), new NavigationTarget(targetType,
                            nullable(target, "route"), nullable(target, "url"), target.path("requiresLogin").asBoolean(true)),
                    tracking("SCR-HOME-001", row.code(), nullable(item, "contentId"), requestId)));
        }
        return new HomeModule(Long.toString(row.id()), type, row.title(), nullable(config, "subtitle"),
                config.path("layoutType").asText(type.toLowerCase(Locale.ROOT)), List.copyOf(items), null,
                tracking("SCR-HOME-001", row.code(), null, requestId), instant(config, "startAt"), instant(config, "endAt"));
    }

    private TrackingContext tracking(String page, String source, String contentId, String requestId) {
        return new TrackingContext(page, source, null, contentId, requestId, List.of());
    }
    private Map<String, Object> object(String json) {
        try { return mapper.readValue(json == null ? "{}" : json, new TypeReference<>() { }); }
        catch (Exception invalid) { throw new IllegalStateException("Content JSON is invalid", invalid); }
    }
    private JsonNode tree(String json) {
        try { return mapper.readTree(json == null ? "{}" : json); }
        catch (Exception invalid) { throw new IllegalStateException("Home configuration is invalid", invalid); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalStateException("Content JSON serialization failed", invalid); }
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception invalid) { throw new IllegalStateException("Content request hash failed", invalid); }
    }
    private static String encode(CommandResult result) {
        return "v1:" + result.resourceId() + ":" + result.status() + ":" + result.version()
                + ":" + result.acceptedAt().toEpochMilli();
    }
    private static String event(String action) {
        return action.toLowerCase(Locale.ROOT).replace('_', '.') + ".v1";
    }
    private static Long cursor(String cursor) {
        if (clean(cursor) == null) return null;
        return id(cursor, "游标不符合要求");
    }
    private static long id(String value, String message) {
        try { long id = Long.parseLong(value); if (id < 1) throw new NumberFormatException(); return id; }
        catch (RuntimeException invalid) { throw validation(message); }
    }
    private static Long optionalId(String value) {
        return clean(value) == null ? null : id(value, "发布者标识无效");
    }
    private static long count(String value) {
        try { return Math.max(0, Long.parseLong(value == null ? "0" : value)); }
        catch (NumberFormatException invalid) { return 0; }
    }
    private static String outwardType(String type) { return "GROUP".equals(type) ? "GROUP_CHAT" : type; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String nullable(JsonNode node, String name) {
        JsonNode value = node.get(name); return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
    private static List<String> strings(JsonNode value) {
        if (!value.isArray()) return List.of();
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        value.forEach(item -> { if (item.isTextual()) values.add(item.asText()); });
        return List.copyOf(values);
    }
    private static Instant instant(JsonNode node, String name) {
        try { String value = nullable(node, name); return value == null ? null : Instant.parse(value); }
        catch (RuntimeException invalid) { throw validation("首页模块排期不符合要求"); }
    }
    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }
    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "内容不存在或不可见", 404, false);
    }
    private static BusinessException versionConflict() {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", "数据版本已变化，请刷新后重试", 409, false);
    }
    private static BusinessException idempotencyConflict() {
        return new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }
    private static BusinessException statusConflict() {
        return new BusinessException("CONTENT-409-STATUS_TRANSITION", "当前内容状态不允许执行此操作", 409, false);
    }
}
