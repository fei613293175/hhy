package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.ContactInput;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.NavigationTarget;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PublicPage;
import cc.orbexa.hhy.content.R08Contracts.PublicPageBlock;
import cc.orbexa.hhy.content.R08Contracts.SeoMetadata;
import cc.orbexa.hhy.content.R10Contracts.GroupAttributes;
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

/** R10 group implementation behind the frozen generic content operations. */
@Service
public class R10Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> CONTACT_CHANNELS =
            Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "JOIN_PASSWORD");
    private static final Set<String> EDITABLE =
            Set.of("DRAFT", "REJECTED", "RECTIFICATION", "OFFLINE_BY_OWNER");
    private static final String CONTENT_RESPONSE = "r10.content-resource.v1";

    private final R10Store store;
    private final R08Store shared;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R10Service(
            R10Store store, R08Store shared, ContentService content,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isGroup(String idValue) {
        Long id = optionalId(idValue);
        return id != null && store.group(id).isPresent();
    }

    @Transactional
    public ContentResource create(long userId, CreateProjectRequest request, String key) {
        requireVerified(userId);
        if (!"GROUP_CHAT".equals(normalized(request.contentType()))) {
            throw validation("R10只允许创建GROUP_CHAT内容");
        }
        String title = required(request.title(), "标题不符合要求");
        String summary = optional(request.summary(), 2000, "摘要不符合要求");
        String description = required(request.description(), "详细说明不符合要求");
        String category = required(request.categoryCode(), "分类不符合要求");
        String region = optional(request.regionCode(), 2000, "地区不符合要求");
        Map<String, Object> attributes = attributes(request.attributes());
        GroupAttributes group = groupAttributes(attributes, null);
        List<Long> media = media(request.mediaIds());
        List<ContactInput> contacts = contacts(request.contacts());
        ensureOwnedMedia(userId, media, group.qrMediaId());
        Map<String, Object> snapshot = snapshot(attributes, group, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentType", "GROUP_CHAT", "title", title,
                "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", media, "contacts", contacts));
        String scope = "r10.group-create:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        int draftLimit = shared.integerConfig("content.limit.normal.drafts");
        if (shared.countOwnedInStatus(userId, "DRAFT") >= draftLimit) {
            throw business("COMMON-422-BUSINESS_RULE", "草稿数量已达到当前配置上限", 422, false);
        }
        Instant now = Instant.now(clock);
        long id = store.createGroup(userId, title, summary, group.platform(), group.sizeRange(),
                group.joinRequirement(), group.qrMediaId(), group.groupLink(), group.groupNo(),
                json(snapshot), media, now);
        shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.group.created.v1", Long.toString(id), "DRAFT", now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ContentResource detail(long userId, String idValue) {
        long id = id(idValue, "内容标识无效");
        R10Store.GroupRow group = store.group(id).orElseThrow(R10Service::notFound);
        if (!"GROUP".equals(group.type())
                || (!"ONLINE".equals(group.status()) && group.ownerId() != userId)
                || Set.of("DELETED", "BANNED").contains(group.status())) {
            throw notFound();
        }
        return content.detail(Long.toString(id));
    }

    @Transactional
    public ContentResource patch(long userId, String idValue, PatchProjectRequest request, String key) {
        requireVerified(userId);
        long id = id(idValue, "内容标识无效");
        R10Store.GroupRow current = store.group(id).orElseThrow(R10Service::notFound);
        if (!"GROUP".equals(current.type()) || current.ownerId() != userId) throw forbidden();
        if (current.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(current.status())) throw statusConflict();
        String title = request.title() == null
                ? current.title() : required(request.title(), "标题不符合要求");
        String summary = request.summary() == null
                ? current.summary() : optional(request.summary(), 2000, "摘要不符合要求");
        Map<String, Object> attributes = request.attributes() == null
                ? object(current.attributesJson()) : attributes(request.attributes());
        GroupAttributes fallback = new GroupAttributes(
                current.platform(), current.sizeRange(), current.joinRequirement(),
                current.qrMediaId(), current.groupLink(), current.groupNo());
        GroupAttributes group = groupAttributes(attributes, fallback);
        String description = request.description() == null
                ? required(string(attributes.get("description")), "详细说明不符合要求")
                : required(request.description(), "详细说明不符合要求");
        String category = request.categoryCode() == null
                ? required(string(attributes.get("categoryCode")), "分类不符合要求")
                : required(request.categoryCode(), "分类不符合要求");
        String region = request.regionCode() == null
                ? optional(string(attributes.get("regionCode")), 2000, "地区不符合要求")
                : optional(request.regionCode(), 2000, "地区不符合要求");
        List<Long> media = request.mediaIds() == null ? List.of() : media(request.mediaIds());
        if (request.mediaIds() != null || !java.util.Objects.equals(group.qrMediaId(), current.qrMediaId())) {
            ensureOwnedMedia(userId, media, group.qrMediaId());
        }
        List<ContactInput> contacts = request.contacts() == null ? null : contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(attributes, group, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", id, "expectedVersion", request.expectedVersion(),
                "title", title, "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", request.mediaIds() == null ? "UNCHANGED" : media,
                "contacts", contacts == null ? "UNCHANGED" : contacts));
        String scope = "r10.group-patch:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        R10Store.GroupRow locked = store.lockGroup(id).orElseThrow(R10Service::notFound);
        if (!"GROUP".equals(locked.type()) || locked.ownerId() != userId) throw forbidden();
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.updateGroup(id, request.expectedVersion(), title, summary,
                group.platform(), group.sizeRange(), group.joinRequirement(), group.qrMediaId(),
                group.groupLink(), group.groupNo(), json(snapshot), media,
                request.mediaIds() != null, now, userId)) {
            throw versionConflict();
        }
        if (contacts != null) shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.group.updated.v1",
                Long.toString(id), locked.status(), now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional(readOnly = true)
    public PublicPage publicShare(String idValue) {
        long id = id(idValue, "内容标识无效");
        R10Store.GroupRow group = store.group(id).orElseThrow(R10Service::notFound);
        if (!"GROUP".equals(group.type()) || !"ONLINE".equals(group.status())) throw notFound();
        ContentResource resource = content.publicDetail(Long.toString(id));
        String url = shareUrl(id);
        List<PublicPageBlock> blocks = new ArrayList<>();
        blocks.add(new PublicPageBlock("hero", "HERO", resource.title(), resource.summary(),
                resource.media(), null, 0));
        blocks.add(new PublicPageBlock("description", "RICH_TEXT", "群聊说明",
                resource.description(), List.of(), null, 1));
        String facts = group.sizeRange() == null
                ? "群平台：" + group.platform()
                : "群平台：" + group.platform() + "；规模：" + group.sizeRange();
        blocks.add(new PublicPageBlock("group-facts", "FACTS", "群聊信息", facts,
                List.of(), new NavigationTarget("IN_APP_ROUTE", "/content/group/" + id, null, true), 2));
        SeoMetadata seo = new SeoMetadata(clip(resource.title(), 120),
                clip(resource.summary() == null ? resource.description() : resource.summary(), 300),
                List.of("合伙云", "群聊推广"), url, null, "index,follow");
        return new PublicPage("GROUP", resource.title(), resource.summary(), blocks, seo,
                null, null, resource.version());
    }

    private GroupAttributes groupAttributes(Map<String, Object> attributes, GroupAttributes fallback) {
        String platform = attributes.containsKey("platform")
                ? requiredField(string(attributes.get("platform")), 255, "群平台不符合要求")
                : fallback == null ? requiredField(null, 255, "群平台不符合要求") : fallback.platform();
        String sizeRange = value(attributes, "sizeRange", fallback == null ? null : fallback.sizeRange(),
                "群规模不符合要求");
        String joinRequirement = value(attributes, "joinRequirement",
                fallback == null ? null : fallback.joinRequirement(), "入群要求不符合要求");
        Long qrMediaId = attributes.containsKey("qrMediaId")
                ? optionalId(attributes.get("qrMediaId"), "二维码媒体标识不符合要求")
                : fallback == null ? null : fallback.qrMediaId();
        String groupLink = attributes.containsKey("groupLink")
                ? secureUrl(optional(string(attributes.get("groupLink")), 255, "群链接不符合要求"),
                        "群链接不符合要求")
                : fallback == null ? null : fallback.groupLink();
        String groupNo = value(attributes, "groupNo", fallback == null ? null : fallback.groupNo(),
                "群号不符合要求");
        return new GroupAttributes(platform, sizeRange, joinRequirement, qrMediaId, groupLink, groupNo);
    }

    private static String value(
            Map<String, Object> attributes, String key, String fallback, String message) {
        return attributes.containsKey(key)
                ? optional(string(attributes.get(key)), 255, message) : fallback;
    }

    private Map<String, Object> snapshot(
            Map<String, Object> attributes, GroupAttributes group,
            String description, String category, String region) {
        Map<String, Object> result = new LinkedHashMap<>(attributes);
        result.put("platform", group.platform());
        putOrRemove(result, "sizeRange", group.sizeRange());
        putOrRemove(result, "joinRequirement", group.joinRequirement());
        putOrRemove(result, "qrMediaId",
                group.qrMediaId() == null ? null : Long.toString(group.qrMediaId()));
        putOrRemove(result, "groupLink", group.groupLink());
        putOrRemove(result, "groupNo", group.groupNo());
        result.put("description", description);
        result.put("categoryCode", category);
        putOrRemove(result, "regionCode", region);
        return result;
    }

    private void requireVerified(long userId) {
        if (!shared.identityVerified(userId)) throw forbidden();
    }

    private void ensureOwnedMedia(long userId, List<Long> media, Long qrMediaId) {
        LinkedHashSet<Long> required = new LinkedHashSet<>(media);
        if (qrMediaId != null) required.add(qrMediaId);
        if (!shared.ownsReadyMedia(userId, List.copyOf(required))) throw forbidden();
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

    private List<ContactInput> contacts(List<ContactInput> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        List<ContactInput> result = new ArrayList<>();
        for (ContactInput input : raw) {
            String channel = normalized(input.channel());
            if (!CONTACT_CHANNELS.contains(channel) || !channels.add(channel)) {
                throw validation("群聊联系方式或入群口令渠道重复或不符合要求");
            }
            String message = "JOIN_PASSWORD".equals(channel)
                    ? "入群口令不符合要求" : "联系方式不符合要求";
            result.add(new ContactInput(channel, required(input.value(), message)));
        }
        return List.copyOf(result);
    }

    private List<R08Store.ContactWrite> encryptedContacts(long contentId, List<ContactInput> contacts) {
        List<R08Store.ContactWrite> values = new ArrayList<>();
        for (int index = 0; index < contacts.size(); index++) {
            ContactInput input = contacts.get(index);
            values.add(new R08Store.ContactWrite(input.channel(),
                    cipher.encrypt(contentId, input.channel(), input.value()),
                    mask(input.channel(), input.value()), index));
        }
        return List.copyOf(values);
    }

    private R08Store.IdempotencyClaim claim(String scope, String key, String requestHash) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) {
            throw validation("X-Idempotency-Key不符合要求");
        }
        R08Store.IdempotencyClaim claim = shared.claim(
                scope, key.strip(), requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        return claim;
    }

    private ContentResource replay(
            R08Store.IdempotencyClaim claim, String scope, String key, String requestHash) {
        if (claim.responseRef() == null || !CONTENT_RESPONSE.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null || claim.responsePayloadCiphertext().isBlank()) {
            throw versionConflict();
        }
        try {
            byte[] plain = cipher.decryptSnapshot(
                    scope, key.strip(), requestHash, CONTENT_RESPONSE,
                    claim.responsePayloadCiphertext());
            return mapper.readValue(plain, ContentResource.class);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R10 idempotency snapshot is unavailable", failure);
        }
    }

    private void complete(
            R08Store.IdempotencyClaim claim, String scope, String key,
            String requestHash, ContentResource result) {
        try {
            byte[] plain = mapper.writeValueAsBytes(result);
            shared.complete(claim.id(), CONTENT_RESPONSE + ":ok", CONTENT_RESPONSE,
                    cipher.encryptSnapshot(scope, key.strip(), requestHash, CONTENT_RESPONSE, plain));
        } catch (Exception failure) {
            throw new IllegalStateException("R10 idempotency snapshot completion failed", failure);
        }
    }

    private Map<String, Object> attributes(Map<String, Object> raw) {
        if (raw == null) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(
                    mapper.convertValue(raw, new TypeReference<Map<String, Object>>() { }));
        } catch (RuntimeException failure) {
            throw validation("扩展属性不符合要求");
        }
    }

    private Map<String, Object> object(String value) {
        try {
            return mapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception failure) {
            throw new IllegalStateException("R10 stored group attributes are invalid", failure);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R10 request hash unavailable", failure);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R10 JSON serialization failed", failure); }
    }

    private String shareUrl(long contentId) {
        String host = shared.textConfig("domain.h5.host").strip().toLowerCase(Locale.ROOT);
        if (!host.matches("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")) {
            throw new IllegalStateException("Invalid active R10 H5 host");
        }
        return "https://" + host + "/share/group/" + contentId;
    }

    private static String secureUrl(String value, String message) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.toASCIIString().length() > 255) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (RuntimeException failure) {
            throw validation(message);
        }
    }

    private static String mask(String channel, String value) {
        return switch (channel) {
            case "PHONE" -> value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
            case "EMAIL" -> {
                int at = value.indexOf('@');
                yield at > 0 ? value.substring(0, 1) + "***" + value.substring(at) : "***";
            }
            case "JOIN_PASSWORD" -> "口令***";
            default -> value.length() <= 2 ? "***" : value.substring(0, 2) + "***";
        };
    }

    private static void putOrRemove(Map<String, Object> target, String key, Object value) {
        if (value == null) target.remove(key); else target.put(key, value);
    }

    private static String string(Object value) { return value == null ? null : value.toString(); }

    private static String required(String value, String message) {
        return requiredField(value, 2000, message);
    }

    private static String requiredField(String value, int max, String message) {
        String result = value == null ? null : value.strip();
        if (result == null || result.isEmpty() || result.length() > max) throw validation(message);
        return result;
    }

    private static String optional(String value, int max, String message) {
        if (value == null) return null;
        String result = value.strip();
        if (result.isEmpty()) return null;
        if (result.length() > max) throw validation(message);
        return result;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static long id(String value, String message) {
        Long parsed = optionalId(value);
        if (parsed == null) throw validation(message);
        return parsed;
    }

    private static Long optionalId(Object value, String message) {
        if (value == null || (value instanceof String text && text.isBlank())) return null;
        Long parsed = optionalId(value.toString());
        if (parsed == null) throw validation(message);
        return parsed;
    }

    private static Long optionalId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed < 1 ? null : parsed;
        } catch (RuntimeException failure) {
            return null;
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
