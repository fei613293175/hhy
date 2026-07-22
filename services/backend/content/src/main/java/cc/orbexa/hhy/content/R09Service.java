package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.ContactInput;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PublicPage;
import cc.orbexa.hhy.content.R08Contracts.PublicPageBlock;
import cc.orbexa.hhy.content.R08Contracts.SeoMetadata;
import cc.orbexa.hhy.content.R09Contracts.AppAttributes;
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

/** R09 App implementation behind the frozen generic content operations. */
@Service
public class R09Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Set<String> CONTACT_CHANNELS =
            Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE");
    private static final Set<String> EDITABLE =
            Set.of("DRAFT", "REJECTED", "RECTIFICATION", "OFFLINE_BY_OWNER");
    private static final String CONTENT_RESPONSE = "r09.content-resource.v1";

    private final R09Store store;
    private final R08Store shared;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R09Service(
            R09Store store, R08Store shared, ContentService content,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isApp(String idValue) {
        Long id = optionalId(idValue);
        return id != null && store.app(id).isPresent();
    }

    @Transactional
    public ContentResource create(long userId, CreateProjectRequest request, String key) {
        requireVerified(userId);
        if (!"APP".equals(normalized(request.contentType()))) throw validation("R09只允许创建APP内容");
        String title = required(request.title(), "标题不符合要求");
        String summary = optional(request.summary());
        String description = required(request.description(), "详细说明不符合要求");
        String category = required(request.categoryCode(), "分类不符合要求");
        String region = optional(request.regionCode());
        Map<String, Object> attributes = attributes(request.attributes());
        AppAttributes app = appAttributes(attributes, null);
        List<Long> media = media(request.mediaIds());
        List<ContactInput> contacts = contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(attributes, app, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentType", "APP", "title", title,
                "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", media, "contacts", contacts));
        String scope = "r09.app-create:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        ensureOwnedNonApkMedia(userId, media);
        int draftLimit = shared.integerConfig("content.limit.normal.drafts");
        if (shared.countOwnedInStatus(userId, "DRAFT") >= draftLimit) {
            throw business("COMMON-422-BUSINESS_RULE", "草稿数量已达到当前配置上限", 422, false);
        }
        Instant now = Instant.now(clock);
        long id = store.createApp(userId, title, summary, app.appName(), app.platform(),
                app.versionText(), app.downloadUrl(), app.website(), json(snapshot), media, now);
        shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.app.created.v1", Long.toString(id), "DRAFT", now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ContentResource detail(long userId, String idValue) {
        long id = id(idValue, "内容标识无效");
        R09Store.AppRow app = store.app(id).orElseThrow(R09Service::notFound);
        if (!"APP".equals(app.type()) || (!"ONLINE".equals(app.status()) && app.ownerId() != userId)
                || Set.of("DELETED", "BANNED").contains(app.status())) throw notFound();
        return content.detail(Long.toString(id));
    }

    @Transactional
    public ContentResource patch(long userId, String idValue, PatchProjectRequest request, String key) {
        requireVerified(userId);
        long id = id(idValue, "内容标识无效");
        R09Store.AppRow current = store.app(id).orElseThrow(R09Service::notFound);
        if (!"APP".equals(current.type()) || current.ownerId() != userId) throw forbidden();
        if (current.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(current.status())) throw statusConflict();
        String title = request.title() == null ? current.title() : required(request.title(), "标题不符合要求");
        String summary = request.summary() == null ? current.summary() : optional(request.summary());
        Map<String, Object> attributes = request.attributes() == null
                ? object(current.attributesJson()) : attributes(request.attributes());
        AppAttributes fallback = new AppAttributes(current.appName(), current.platform(), current.versionText(),
                current.downloadUrl(), current.website());
        AppAttributes app = appAttributes(attributes, fallback);
        String description = request.description() == null
                ? required(string(attributes.get("description")), "详细说明不符合要求")
                : required(request.description(), "详细说明不符合要求");
        String category = request.categoryCode() == null
                ? required(string(attributes.get("categoryCode")), "分类不符合要求")
                : required(request.categoryCode(), "分类不符合要求");
        String region = request.regionCode() == null
                ? optional(string(attributes.get("regionCode"))) : optional(request.regionCode());
        List<Long> media = request.mediaIds() == null ? List.of() : media(request.mediaIds());
        if (request.mediaIds() != null) ensureOwnedNonApkMedia(userId, media);
        List<ContactInput> contacts = request.contacts() == null ? null : contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(attributes, app, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", id, "expectedVersion", request.expectedVersion(),
                "title", title, "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", request.mediaIds() == null ? "UNCHANGED" : media,
                "contacts", contacts == null ? "UNCHANGED" : contacts));
        String scope = "r09.app-patch:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        R09Store.AppRow locked = store.lockApp(id).orElseThrow(R09Service::notFound);
        if (!"APP".equals(locked.type()) || locked.ownerId() != userId) throw forbidden();
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.updateApp(id, request.expectedVersion(), title, summary, app.appName(), app.platform(),
                app.versionText(), app.downloadUrl(), app.website(), json(snapshot), media,
                request.mediaIds() != null, now, userId)) throw versionConflict();
        if (contacts != null) shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.app.updated.v1", Long.toString(id), locked.status(), now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional(readOnly = true)
    public PublicPage publicShare(String idValue) {
        long id = id(idValue, "内容标识无效");
        R09Store.AppRow app = store.app(id).orElseThrow(R09Service::notFound);
        if (!"APP".equals(app.type()) || !"ONLINE".equals(app.status())) throw notFound();
        ContentResource resource = content.publicDetail(Long.toString(id));
        String url = shareUrl(id);
        List<PublicPageBlock> blocks = List.of(
                new PublicPageBlock("hero", "HERO", resource.title(), resource.summary(),
                        resource.media(), null, 0),
                new PublicPageBlock("description", "RICH_TEXT", "应用介绍", resource.description(),
                        List.of(), null, 1));
        SeoMetadata seo = new SeoMetadata(clip(resource.title(), 120),
                clip(resource.summary() == null ? resource.description() : resource.summary(), 300),
                List.of("合伙云", "应用推广"), url, null, "index,follow");
        Map<String, Object> download = new LinkedHashMap<>();
        if (app.downloadUrl() != null) download.put("externalUrl", app.downloadUrl());
        if (app.website() != null) download.put("website", app.website());
        if (app.platform() != null) download.put("platform", app.platform());
        if (app.versionText() != null) download.put("versionText", app.versionText());
        return new PublicPage("APP", resource.title(), resource.summary(), blocks, seo,
                Map.copyOf(download), null, resource.version());
    }

    private AppAttributes appAttributes(Map<String, Object> attributes, AppAttributes fallback) {
        String appName = attributes.containsKey("appName")
                ? required(string(attributes.get("appName")), "App名称不符合要求")
                : fallback == null ? required(null, "App名称不符合要求") : fallback.appName();
        String platform = value(attributes, "platform", fallback == null ? null : fallback.platform());
        String version = value(attributes, "versionText", fallback == null ? null : fallback.versionText());
        String download = secureUrl(value(attributes, "downloadUrl",
                fallback == null ? null : fallback.downloadUrl()), "外部下载链接不符合要求");
        String website = secureUrl(value(attributes, "website",
                fallback == null ? null : fallback.website()), "官网链接不符合要求");
        return new AppAttributes(appName, platform, version, download, website);
    }

    private static String value(Map<String, Object> attributes, String key, String fallback) {
        return attributes.containsKey(key) ? optional(string(attributes.get(key))) : fallback;
    }

    private static String secureUrl(String value, String message) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (RuntimeException failure) {
            throw validation(message);
        }
    }

    private Map<String, Object> snapshot(
            Map<String, Object> attributes, AppAttributes app,
            String description, String category, String region) {
        Map<String, Object> result = new LinkedHashMap<>(attributes);
        result.put("appName", app.appName());
        putOrRemove(result, "platform", app.platform());
        putOrRemove(result, "versionText", app.versionText());
        putOrRemove(result, "downloadUrl", app.downloadUrl());
        putOrRemove(result, "website", app.website());
        result.put("description", description);
        result.put("categoryCode", category);
        putOrRemove(result, "regionCode", region);
        return result;
    }

    private static void putOrRemove(Map<String, Object> target, String key, String value) {
        if (value == null) target.remove(key); else target.put(key, value);
    }

    private void requireVerified(long userId) {
        if (!shared.identityVerified(userId)) throw forbidden();
    }

    private void ensureOwnedNonApkMedia(long userId, List<Long> media) {
        if (!store.ownsReadyNonApkMedia(userId, media)) throw forbidden();
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
                throw validation("联系方式渠道重复或不符合要求");
            }
            result.add(new ContactInput(channel, required(input.value(), "联系方式不符合要求")));
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
                    scope, key.strip(), requestHash, CONTENT_RESPONSE, claim.responsePayloadCiphertext());
            return mapper.readValue(plain, ContentResource.class);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R09 idempotency snapshot is unavailable", failure);
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
            throw new IllegalStateException("R09 idempotency snapshot completion failed", failure);
        }
    }

    private Map<String, Object> attributes(Map<String, Object> raw) {
        if (raw == null) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(mapper.convertValue(raw, new TypeReference<Map<String, Object>>() { }));
        } catch (RuntimeException failure) {
            throw validation("扩展属性不符合要求");
        }
    }

    private Map<String, Object> object(String value) {
        try { return mapper.readValue(value, new TypeReference<Map<String, Object>>() { }); }
        catch (Exception failure) { throw new IllegalStateException("R09 stored App attributes are invalid", failure); }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R09 request hash unavailable", failure);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R09 JSON serialization failed", failure); }
    }

    private String shareUrl(long contentId) {
        String host = shared.textConfig("domain.h5.host").strip().toLowerCase(Locale.ROOT);
        if (!host.matches("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")) {
            throw new IllegalStateException("Invalid active R09 H5 host");
        }
        return "https://" + host + "/share/app/" + contentId;
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

    private static String string(Object value) { return value == null ? null : value.toString(); }
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
        Long parsed = optionalId(value);
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
