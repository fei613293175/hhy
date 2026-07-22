package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.ContactInput;
import cc.orbexa.hhy.content.R08Contracts.Conversation;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.DirectConversationRequest;
import cc.orbexa.hhy.content.R08Contracts.FavoriteRequest;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PublicPage;
import cc.orbexa.hhy.content.R08Contracts.PublicPageBlock;
import cc.orbexa.hhy.content.R08Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R08Contracts.SeoMetadata;
import cc.orbexa.hhy.content.R08Contracts.ShareRequest;
import cc.orbexa.hhy.content.R08Contracts.ShareResult;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
public class R08Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> CONTACT_CHANNELS =
            Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE");
    private static final Set<String> SHARE_CHANNELS =
            Set.of("WECHAT", "WECHAT_MOMENTS", "COPY_LINK", "OTHER");
    private static final Set<String> EDITABLE =
            Set.of("DRAFT", "REJECTED", "RECTIFICATION", "OFFLINE_BY_OWNER");
    private static final String CONTENT_RESPONSE = "r08.content-resource.v1";
    private static final String SHARE_RESPONSE = "r08.share-result.v1";
    private static final String CHAT_RESPONSE = "r08.direct-conversation.v1";

    private final R08Store store;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R08Service(
            R08Store store, ContentService content, ContentContactCipher cipher,
            ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional
    public ContentResource create(long userId, CreateProjectRequest request, String key) {
        requireVerified(userId);
        if (!"PROJECT".equals(normalized(request.contentType()))) {
            throw validation("R08只允许创建PROJECT内容");
        }
        String title = required(request.title(), "标题不符合要求");
        String description = required(request.description(), "详细说明不符合要求");
        String category = required(request.categoryCode(), "分类不符合要求");
        String summary = optional(request.summary());
        String region = optional(request.regionCode());
        Map<String, Object> attributes = attributes(request.attributes());
        String conditions = optionalAttribute(attributes, "conditions");
        String website = optionalAttribute(attributes, "website");
        List<Long> media = media(request.mediaIds());
        List<ContactInput> contacts = contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(attributes, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentType", "PROJECT", "title", title,
                "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", media, "contacts", contacts));
        String scope = "r08.project-create:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, CONTENT_RESPONSE, ContentResource.class);
        ensureOwnedMedia(userId, media);
        int draftLimit = store.integerConfig("content.limit.normal.drafts");
        if (store.countOwnedInStatus(userId, "DRAFT") >= draftLimit) {
            throw business("COMMON-422-BUSINESS_RULE", "草稿数量已达到当前配置上限", 422, false);
        }
        Instant now = Instant.now(clock);
        long id = store.createProject(userId, title, summary, description, category, region,
                conditions, website, json(snapshot), media, now);
        store.replaceContacts(id, encryptedContacts(id, contacts), now);
        store.outbox(userId, "CONTENT", "content.project.created.v1", Long.toString(id), "DRAFT", now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, CONTENT_RESPONSE, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ContentResource detail(long userId, String idValue) {
        long id = id(idValue, "内容标识无效");
        R08Store.ProjectRow project = store.project(id).orElseThrow(R08Service::notFound);
        if (!"PROJECT".equals(project.type()) || (!"ONLINE".equals(project.status()) && project.ownerId() != userId)) {
            throw notFound();
        }
        if (Set.of("DELETED", "BANNED").contains(project.status())) throw notFound();
        return content.detail(Long.toString(id));
    }

    @Transactional
    public ContentResource patch(long userId, String idValue, PatchProjectRequest request, String key) {
        requireVerified(userId);
        long id = id(idValue, "内容标识无效");
        R08Store.ProjectRow current = store.project(id).orElseThrow(R08Service::notFound);
        if (!"PROJECT".equals(current.type()) || current.ownerId() != userId) throw forbidden();
        if (current.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(current.status())) throw statusConflict();
        String title = request.title() == null ? current.title() : required(request.title(), "标题不符合要求");
        String summary = request.summary() == null ? current.summary() : optional(request.summary());
        String description = request.description() == null
                ? current.description() : required(request.description(), "详细说明不符合要求");
        String category = request.categoryCode() == null
                ? current.categoryCode() : required(request.categoryCode(), "分类不符合要求");
        String region = request.regionCode() == null ? current.regionCode() : optional(request.regionCode());
        Map<String, Object> attributes = request.attributes() == null
                ? object(current.attributesJson()) : attributes(request.attributes());
        String conditions = request.attributes() == null
                ? current.conditions() : optionalAttribute(attributes, "conditions");
        String website = request.attributes() == null
                ? current.website() : optionalAttribute(attributes, "website");
        List<Long> media = request.mediaIds() == null ? List.of() : media(request.mediaIds());
        if (request.mediaIds() != null) ensureOwnedMedia(userId, media);
        List<ContactInput> contacts = request.contacts() == null ? null : contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(attributes, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", id, "expectedVersion", request.expectedVersion(),
                "title", title, "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", request.mediaIds() == null ? "UNCHANGED" : media,
                "contacts", contacts == null ? "UNCHANGED" : contacts));
        String scope = "r08.project-patch:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, CONTENT_RESPONSE, ContentResource.class);
        R08Store.ProjectRow locked = store.lockProject(id).orElseThrow(R08Service::notFound);
        if (!"PROJECT".equals(locked.type()) || locked.ownerId() != userId) throw forbidden();
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.updateProject(id, request.expectedVersion(), title, summary, description, category,
                region, conditions, website, json(snapshot), media, request.mediaIds() != null, now, userId)) {
            throw versionConflict();
        }
        if (contacts != null) store.replaceContacts(id, encryptedContacts(id, contacts), now);
        store.outbox(userId, "CONTENT", "content.project.updated.v1", Long.toString(id), locked.status(), now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, CONTENT_RESPONSE, result);
        return result;
    }

    @Transactional
    public ContentResource favorite(long userId, String idValue, FavoriteRequest request, String key) {
        long id = id(idValue, "内容标识无效");
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", id, "expectedVersion", request.expectedVersion(),
                "reason", request.reason() == null ? "" : request.reason(),
                "payload", request.payload() == null ? Map.of() : request.payload()));
        String scope = "r08.favorite:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, CONTENT_RESPONSE, ContentResource.class);
        R08Store.ProjectRow locked = store.lockProject(id).orElseThrow(R08Service::notFound);
        requireOnlineProject(locked);
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        Instant now = Instant.now(clock);
        boolean inserted = store.favorite(userId, id, now);
        store.outbox(userId, "CONTENT", inserted ? "content.favorited.v1" : "content.favorite.replayed.v1",
                Long.toString(id), inserted ? "FAVORITED" : "ALREADY_FAVORITED", now);
        ContentResource result = content.publicDetail(Long.toString(id));
        complete(claim, scope, key, requestHash, CONTENT_RESPONSE, result);
        return result;
    }

    @Transactional
    public ShareResult share(long userId, String idValue, ShareRequest request, String key) {
        long id = id(idValue, "内容标识无效");
        String channel = normalized(request.channel());
        if (!SHARE_CHANNELS.contains(channel)) throw validation("分享渠道不符合要求");
        String requestHash = hash(Map.of("userId", userId, "contentId", id, "channel", channel));
        String scope = "r08.share:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, SHARE_RESPONSE, ShareResult.class);
        R08Store.ProjectRow project = store.project(id).orElseThrow(R08Service::notFound);
        requireOnlineProject(project);
        Instant now = Instant.now(clock);
        String url = shareUrl(id);
        store.share(userId, id, channel, now);
        store.outbox(userId, "CONTENT", "content.shared.v1", Long.toString(id), channel, now);
        ShareResult result = new ShareResult(Long.toString(id), channel, url, now);
        complete(claim, scope, key, requestHash, SHARE_RESPONSE, result);
        return result;
    }

    @Transactional
    public Conversation direct(long userId, DirectConversationRequest request, String key) {
        long peerId = id(request.peerUserId(), "对方用户标识无效");
        if (peerId == userId) throw business("COMMON-422-BUSINESS_RULE", "不能与自己创建会话", 422, false);
        if (!store.activeUser(userId) || !store.activeUser(peerId)) throw notFound();
        if (store.blockedEitherWay(userId, peerId)) throw forbidden();
        Long sourceId = request.sourceContentId() == null ? null
                : id(request.sourceContentId(), "来源内容标识无效");
        if (sourceId != null) {
            R08Store.ProjectRow source = store.project(sourceId).orElseThrow(R08Service::notFound);
            requireOnlineProject(source);
            if (source.ownerId() != peerId) throw validation("来源内容与会话对象不一致");
        }
        String requestHash = hash(Map.of(
                "userId", userId, "peerId", peerId,
                "sourceContentId", sourceId == null ? "" : sourceId));
        String scope = "r08.direct:" + userId + ":" + peerId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash, CHAT_RESPONSE, Conversation.class);
        store.lockDirectPair(userId, peerId);
        R08Store.ConversationRow row = store.directConversation(userId, peerId).orElse(null);
        boolean created = row == null;
        Instant now = Instant.now(clock);
        if (created) {
            int limit = store.integerConfig("chat.stranger.daily_conversation_limit");
            if (store.directConversationCountToday(userId, now) >= limit) {
                throw business("COMMON-429-RATE_LIMITED", "今日新建陌生会话数量已达上限", 429, true);
            }
            row = store.createDirectConversation(userId, peerId, now);
            store.outbox(userId, "CONVERSATION", "chat.direct.created.v1",
                    Long.toString(row.id()), "ACTIVE", now);
        }
        Conversation result = conversation(row);
        complete(claim, scope, key, requestHash, CHAT_RESPONSE, result);
        return result;
    }

    @Transactional(readOnly = true)
    public PublicPage publicShare(String idValue) {
        long id = id(idValue, "内容标识无效");
        R08Store.ProjectRow project = store.project(id).orElseThrow(R08Service::notFound);
        requireOnlineProject(project);
        ContentResource resource = content.publicDetail(Long.toString(id));
        String url = shareUrl(id);
        List<PublicPageBlock> blocks = List.of(
                new PublicPageBlock("hero", "HERO", resource.title(), resource.summary(),
                        resource.media(), null, 0),
                new PublicPageBlock("description", "RICH_TEXT", "项目说明", resource.description(),
                        List.of(), null, 1));
        SeoMetadata seo = new SeoMetadata(clip(resource.title(), 120),
                clip(resource.summary() == null ? resource.description() : resource.summary(), 300),
                List.of("合伙云", "项目合作"), url, null, "index,follow");
        return new PublicPage("PROJ", resource.title(), resource.summary(), blocks, seo,
                null, null, resource.version());
    }

    private Conversation conversation(R08Store.ConversationRow row) {
        R08Store.PublisherRow peer = store.publisher(row.peerId()).orElseThrow(R08Service::notFound);
        PublisherSummary publisher = new PublisherSummary(Long.toString(peer.userId()),
                peer.nickname() == null || peer.nickname().isBlank() ? "用户" + peer.userId() : peer.nickname(),
                peer.avatar(), peer.bio(), peer.verified(), peer.memberBadge(), null);
        return new Conversation(Long.toString(row.id()), publisher, null, 0, null, row.updatedAt(), row.version());
    }

    private List<R08Store.ContactWrite> encryptedContacts(long contentId, List<ContactInput> contacts) {
        List<R08Store.ContactWrite> values = new ArrayList<>();
        for (int index = 0; index < contacts.size(); index++) {
            ContactInput input = contacts.get(index);
            String channel = normalized(input.channel());
            String value = input.value().strip();
            values.add(new R08Store.ContactWrite(channel, cipher.encrypt(contentId, channel, value),
                    mask(channel, value), index));
        }
        return List.copyOf(values);
    }

    private List<ContactInput> contacts(List<ContactInput> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        List<ContactInput> result = new ArrayList<>();
        for (ContactInput input : raw) {
            String channel = normalized(input.channel());
            if (!CONTACT_CHANNELS.contains(channel) || !channels.add(channel)) {
                throw validation("联系方式渠道重复或不符合要求");
            }
            String value = required(input.value(), "联系方式不符合要求");
            result.add(new ContactInput(channel, value));
        }
        return List.copyOf(result);
    }

    private void requireVerified(long userId) {
        if (!store.identityVerified(userId)) throw forbidden();
    }

    private void ensureOwnedMedia(long userId, List<Long> media) {
        if (!store.ownsReadyMedia(userId, media)) throw forbidden();
    }

    private List<Long> media(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : raw) {
            long parsed = id(value, "媒体标识无效");
            if (!result.add(parsed)) throw validation("媒体标识不能重复");
        }
        return List.copyOf(result);
    }

    private Map<String, Object> snapshot(
            Map<String, Object> attributes, String description, String category, String region) {
        Map<String, Object> result = new LinkedHashMap<>(attributes);
        result.put("description", description);
        result.put("categoryCode", category);
        if (region == null) result.remove("regionCode"); else result.put("regionCode", region);
        return result;
    }

    private Map<String, Object> attributes(Map<String, Object> value) {
        if (value == null) return new LinkedHashMap<>();
        try {
            byte[] encoded = mapper.writeValueAsBytes(value);
            if (encoded.length > 32_768) throw validation("扩展属性过大");
            return new LinkedHashMap<>(value);
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw validation("扩展属性不符合要求");
        }
    }

    private Map<String, Object> object(String json) {
        try { return mapper.readValue(json == null ? "{}" : json, new TypeReference<>() { }); }
        catch (Exception failure) { throw new IllegalStateException("R08 stored project JSON is invalid", failure); }
    }

    private String optionalAttribute(Map<String, Object> attributes, String key) {
        Object raw = attributes.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String text)) throw validation("扩展属性" + key + "不符合要求");
        return optional(text);
    }

    private R08Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.length() < 16 || key.length() > 128) throw validation("幂等键不符合要求");
        return store.claim(scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
    }

    private <T> void complete(
            R08Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, T result) {
        try {
            String payload = cipher.encryptSnapshot(scope, key, requestHash, responseType,
                    mapper.writeValueAsBytes(result));
            store.complete(claim.id(), responseType + ":ok", responseType, payload);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R08 idempotency snapshot serialization failed", failure);
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
            throw new IllegalStateException("R08 idempotency snapshot is unavailable", failure);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R08 request hash unavailable", failure);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R08 JSON serialization failed", failure); }
    }

    private String shareUrl(long contentId) {
        String host = store.textConfig("domain.h5.host").strip().toLowerCase(Locale.ROOT);
        if (!host.matches("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")) {
            throw new IllegalStateException("Invalid active R08 H5 host");
        }
        return "https://" + host + "/share/project/" + contentId;
    }

    private static void requireOnlineProject(R08Store.ProjectRow project) {
        if (!"PROJECT".equals(project.type()) || !"ONLINE".equals(project.status())) throw notFound();
    }

    private static String mask(String channel, String value) {
        return switch (channel) {
            case "PHONE" -> value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
            case "EMAIL" -> {
                int at = value.indexOf('@');
                yield at > 0 ? value.substring(0, 1) + "***" + value.substring(at) : "***";
            }
            case "LINK" -> "https://***";
            case "QR_CODE" -> "二维码";
            default -> value.length() <= 2 ? "***" : value.substring(0, 2) + "***";
        };
    }

    private static String required(String value, String message) {
        String result = value == null ? null : value.strip();
        if (result == null || result.isEmpty() || result.length() > 2000) throw validation(message);
        return result;
    }
    private static String optional(String value) {
        if (value == null) return null;
        String result = value.strip();
        if (result.isEmpty()) return null;
        if (result.length() > 2000) throw validation("字段长度不符合要求");
        return result;
    }
    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
    private static long id(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException failure) {
            throw validation(message);
        }
    }
    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
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
    private static BusinessException statusConflict() {
        return business("COMMON-409-VERSION_CONFLICT", "当前状态不允许执行该操作", 409, false);
    }
    private static BusinessException idempotencyConflict() {
        return business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }
    private static BusinessException business(String code, String message, int status, boolean retryable) {
        return new BusinessException(code, message, status, retryable);
    }
}
