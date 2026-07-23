package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.ContactInput;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R11Contracts.TeamLeaderAttributes;
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
public class R11Service {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String CONTENT_RESPONSE = "r11.content-resource.v1";
    private static final Set<String> EDITABLE =
            Set.of("DRAFT", "REJECTED", "RECTIFICATION", "OFFLINE_BY_OWNER");
    private static final Set<String> CONTACT_CHANNELS = Set.of("WECHAT", "PHONE", "QQ", "EMAIL");
    private static final Set<String> TEAM_ATTRIBUTES = Set.of(
            "teamName", "nickname", "logoMediaId", "personalIntro", "teamIntro", "sizeRange",
            "skills", "cooperationTypes", "cooperationRequirement", "pastCases", "acceptPrivateChat");

    private final R11Store store;
    private final R08Store shared;
    private final ContentService content;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R11Service(
            R11Store store, R08Store shared, ContentService content,
            ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.shared = shared;
        this.content = content;
        this.cipher = cipher;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isTeamLeader(String idValue) {
        Long id = optionalId(idValue);
        return id != null && store.teamLeader(id).isPresent();
    }

    @Transactional
    public ContentResource create(long userId, CreateProjectRequest request, String key) {
        requireVerified(userId);
        if (!"TEAM_LEADER".equals(normalized(request.contentType()))) {
            throw validation("R11只允许创建TEAM_LEADER内容");
        }
        String title = required(request.title(), "标题不符合要求");
        String summary = optional(request.summary(), "摘要不符合要求");
        String description = required(request.description(), "详细说明不符合要求");
        String category = required(request.categoryCode(), "分类不符合要求");
        String region = optional(request.regionCode(), "地区不符合要求");
        Map<String, Object> raw = attributes(request.attributes());
        TeamLeaderAttributes team = team(raw, null);
        List<Long> media = media(request.mediaIds());
        List<ContactInput> contacts = contacts(request.contacts());
        ensureOwnedMedia(userId, media, team.logoMediaId());
        Map<String, Object> snapshot = snapshot(raw, team, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentType", "TEAM_LEADER", "title", title,
                "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", media, "contacts", contacts));
        String scope = "r11.team-leader-create:" + userId;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        if (shared.integerConfig("content.team_leader_per_account") < 1) {
            throw business("COMMON-422-BUSINESS_RULE", "当前暂不可创建团队长资料", 422);
        }
        if (store.lockOwnerTeamLeader(userId).isPresent()) {
            throw business("COMMON-422-BUSINESS_RULE", "每个账号只能创建一份团队长资料", 422);
        }
        if (shared.countOwnedInStatus(userId, "DRAFT") >= shared.integerConfig("content.limit.normal.drafts")) {
            throw business("COMMON-422-BUSINESS_RULE", "草稿数量已达到当前配置上限", 422);
        }
        Instant now = Instant.now(clock);
        long id = store.createTeamLeader(userId, title, summary, category, region,
                team, json(snapshot), media, now);
        shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.team-leader.created.v1", Long.toString(id), "DRAFT", now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ContentResource detail(long userId, String idValue) {
        long id = id(idValue, "内容标识无效");
        R11Store.TeamLeaderRow row = store.teamLeader(id).orElseThrow(R11Service::notFound);
        if (!"TEAM_LEADER".equals(row.type())
                || (!"ONLINE".equals(row.status()) && row.ownerId() != userId)
                || Set.of("DELETED", "BANNED").contains(row.status())) throw notFound();
        return content.detail(Long.toString(id));
    }

    @Transactional
    public ContentResource patch(long userId, String idValue, PatchProjectRequest request, String key) {
        requireVerified(userId);
        long id = id(idValue, "内容标识无效");
        R11Store.TeamLeaderRow current = store.teamLeader(id).orElseThrow(R11Service::notFound);
        if (!"TEAM_LEADER".equals(current.type()) || current.ownerId() != userId) throw forbidden();
        if (current.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(current.status())) throw statusConflict();
        String title = request.title() == null ? current.title() : required(request.title(), "标题不符合要求");
        String summary = request.summary() == null ? current.summary() : optional(request.summary(), "摘要不符合要求");
        Map<String, Object> raw = request.attributes() == null
                ? object(current.attributesJson()) : attributes(request.attributes());
        TeamLeaderAttributes team = team(raw, current.attributes());
        String description = request.description() == null
                ? required(string(raw.get("description")), "详细说明不符合要求")
                : required(request.description(), "详细说明不符合要求");
        String category = request.categoryCode() == null
                ? required(current.categoryCode(), "分类不符合要求")
                : required(request.categoryCode(), "分类不符合要求");
        String region = request.regionCode() == null
                ? current.region() : optional(request.regionCode(), "地区不符合要求");
        List<Long> media = request.mediaIds() == null ? List.of() : media(request.mediaIds());
        if (request.mediaIds() != null || !java.util.Objects.equals(team.logoMediaId(), current.attributes().logoMediaId())) {
            ensureOwnedMedia(userId, media, team.logoMediaId());
        }
        List<ContactInput> contacts = request.contacts() == null ? null : contacts(request.contacts());
        Map<String, Object> snapshot = snapshot(raw, team, description, category, region);
        String requestHash = hash(Map.of(
                "userId", userId, "contentId", id, "expectedVersion", request.expectedVersion(),
                "title", title, "summary", summary == null ? "" : summary, "snapshot", snapshot,
                "media", request.mediaIds() == null ? "UNCHANGED" : media,
                "contacts", contacts == null ? "UNCHANGED" : contacts));
        String scope = "r11.team-leader-patch:" + userId + ":" + id;
        R08Store.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);
        R11Store.TeamLeaderRow locked = store.lockTeamLeader(id).orElseThrow(R11Service::notFound);
        if (locked.ownerId() != userId) throw forbidden();
        if (locked.version() != request.expectedVersion()) throw versionConflict();
        if (!EDITABLE.contains(locked.status())) throw statusConflict();
        Instant now = Instant.now(clock);
        if (!store.updateTeamLeader(id, request.expectedVersion(), title, summary, category, region,
                team, json(snapshot), media, request.mediaIds() != null, now, userId)) throw versionConflict();
        if (contacts != null) shared.replaceContacts(id, encryptedContacts(id, contacts), now);
        shared.outbox(userId, "CONTENT", "content.team-leader.updated.v1",
                Long.toString(id), locked.status(), now);
        ContentResource result = content.detail(Long.toString(id));
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    private TeamLeaderAttributes team(Map<String, Object> raw, TeamLeaderAttributes fallback) {
        rejectUnknownTeamAttributes(raw);
        String teamName = value(raw, "teamName", fallback == null ? null : fallback.teamName(), true, "团队名称不符合要求");
        String nickname = value(raw, "nickname", fallback == null ? null : fallback.nickname(), false, "昵称不符合要求");
        Long logo = raw.containsKey("logoMediaId") ? optionalId(raw.get("logoMediaId"), "Logo媒体标识不符合要求")
                : fallback == null ? null : fallback.logoMediaId();
        String personal = value(raw, "personalIntro", fallback == null ? null : fallback.personalIntro(), false, "个人介绍不符合要求");
        String teamIntro = value(raw, "teamIntro", fallback == null ? null : fallback.teamIntro(), false, "团队介绍不符合要求");
        String size = value(raw, "sizeRange", fallback == null ? null : fallback.sizeRange(), false, "团队规模不符合要求");
        String skills = value(raw, "skills", fallback == null ? null : fallback.skills(), false, "擅长方式不符合要求");
        String cooperation = value(raw, "cooperationTypes", fallback == null ? null : fallback.cooperationTypes(), false, "合作类型不符合要求");
        String requirement = value(raw, "cooperationRequirement", fallback == null ? null : fallback.cooperationRequirement(), false, "合作要求不符合要求");
        List<String> cases = raw.containsKey("pastCases") ? cases(raw.get("pastCases")) : fallback == null ? null : fallback.pastCases();
        Boolean privateChat = raw.containsKey("acceptPrivateChat") ? bool(raw.get("acceptPrivateChat"))
                : fallback == null ? null : fallback.acceptPrivateChat();
        return new TeamLeaderAttributes(teamName, nickname, logo, personal, teamIntro, size,
                skills, cooperation, requirement, cases, privateChat);
    }

    private Map<String, Object> snapshot(Map<String, Object> raw, TeamLeaderAttributes team,
            String description, String category, String region) {
        Map<String, Object> result = new LinkedHashMap<>(raw);
        result.put("teamName", team.teamName());
        put(result, "nickname", team.nickname()); put(result, "logoMediaId", team.logoMediaId());
        put(result, "personalIntro", team.personalIntro()); put(result, "teamIntro", team.teamIntro());
        put(result, "sizeRange", team.sizeRange()); put(result, "skills", team.skills());
        put(result, "cooperationTypes", team.cooperationTypes());
        put(result, "cooperationRequirement", team.cooperationRequirement());
        put(result, "pastCases", team.pastCases()); put(result, "acceptPrivateChat", team.acceptPrivateChat());
        result.put("description", description); result.put("categoryCode", category); put(result, "regionCode", region);
        return result;
    }

    private void ensureOwnedMedia(long userId, List<Long> media, Long logo) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(media);
        if (logo != null) ids.add(logo);
        if (!shared.ownsReadyMedia(userId, List.copyOf(ids))) throw forbidden();
    }

    private List<ContactInput> contacts(List<ContactInput> raw) {
        if (raw == null) return List.of();
        Set<String> channels = new LinkedHashSet<>();
        List<ContactInput> result = new ArrayList<>();
        for (ContactInput input : raw) {
            String channel = normalized(input.channel());
            if (!CONTACT_CHANNELS.contains(channel) || !channels.add(channel)) throw validation("联系方式渠道重复或不符合要求");
            result.add(new ContactInput(channel, required(input.value(), "联系方式不符合要求")));
        }
        return List.copyOf(result);
    }

    private List<R08Store.ContactWrite> encryptedContacts(long contentId, List<ContactInput> contacts) {
        List<R08Store.ContactWrite> result = new ArrayList<>();
        for (int index = 0; index < contacts.size(); index++) {
            ContactInput input = contacts.get(index);
            result.add(new R08Store.ContactWrite(input.channel(),
                    cipher.encrypt(contentId, input.channel(), input.value()), mask(input.channel(), input.value()), index));
        }
        return List.copyOf(result);
    }

    private R08Store.IdempotencyClaim claim(String scope, String key, String hash) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) throw validation("X-Idempotency-Key不符合要求");
        R08Store.IdempotencyClaim claim = shared.claim(scope, key.strip(), hash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (!hash.equals(claim.requestHash())) throw idempotencyConflict();
        return claim;
    }

    private ContentResource replay(R08Store.IdempotencyClaim claim, String scope, String key, String hash) {
        if (claim.responseRef() == null || !CONTENT_RESPONSE.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null) throw versionConflict();
        try {
            byte[] plain = cipher.decryptSnapshot(scope, key.strip(), hash, CONTENT_RESPONSE, claim.responsePayloadCiphertext());
            return mapper.readValue(plain, ContentResource.class);
        } catch (ContentContactCipher.ContactIntegrityException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("R11 idempotency snapshot unavailable", failure); }
    }

    private void complete(R08Store.IdempotencyClaim claim, String scope, String key, String hash, ContentResource result) {
        try {
            shared.complete(claim.id(), CONTENT_RESPONSE + ":ok", CONTENT_RESPONSE,
                    cipher.encryptSnapshot(scope, key.strip(), hash, CONTENT_RESPONSE, mapper.writeValueAsBytes(result)));
        } catch (Exception failure) { throw new IllegalStateException("R11 idempotency snapshot completion failed", failure); }
    }

    private List<Long> media(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : raw) if (!result.add(id(value, "媒体标识无效"))) throw validation("媒体标识不能重复");
        return List.copyOf(result);
    }

    private List<String> cases(Object value) {
        if (!(value instanceof List<?> values) || values.size() > 20) throw validation("过往案例不符合要求");
        if (values.isEmpty()) return null;
        return values.stream().map(item -> required(string(item), "过往案例不符合要求")).toList();
    }

    private static void rejectUnknownTeamAttributes(Map<String, Object> raw) {
        Set<String> allowed = new LinkedHashSet<>(TEAM_ATTRIBUTES);
        allowed.addAll(Set.of("description", "categoryCode", "regionCode"));
        if (raw.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            throw validation("扩展属性包含未登记字段");
        }
    }

    private Map<String, Object> attributes(Map<String, Object> raw) {
        if (raw == null) return new LinkedHashMap<>();
        try { return new LinkedHashMap<>(mapper.convertValue(raw, new TypeReference<Map<String, Object>>() { })); }
        catch (RuntimeException failure) { throw validation("扩展属性不符合要求"); }
    }

    private Map<String, Object> object(String value) {
        try { return mapper.readValue(value, new TypeReference<Map<String, Object>>() { }); }
        catch (Exception failure) { throw new IllegalStateException("R11 stored attributes are invalid", failure); }
    }

    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException("R11 request hash unavailable", failure); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("R11 JSON serialization failed", failure); }
    }

    private void requireVerified(long userId) { if (!shared.identityVerified(userId)) throw forbidden(); }
    private static String value(Map<String, Object> raw, String key, String fallback, boolean required, String message) {
        if (!raw.containsKey(key)) return required ? required(fallback, message) : fallback;
        return required ? required(string(raw.get(key)), message) : optional(string(raw.get(key)), message);
    }
    private static Boolean bool(Object value) { if (value instanceof Boolean result) return result; throw validation("私聊偏好不符合要求"); }
    private static void put(Map<String, Object> map, String key, Object value) { if (value == null) map.remove(key); else map.put(key, value); }
    private static String mask(String channel, String value) {
        if ("PHONE".equals(channel)) return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
        if ("EMAIL".equals(channel)) { int at = value.indexOf('@'); return at > 0 ? value.substring(0, 1) + "***" + value.substring(at) : "***"; }
        return value.length() <= 2 ? "***" : value.substring(0, 2) + "***";
    }
    private static String required(String value, String message) { String result = value == null ? null : value.strip(); if (result == null || result.isEmpty() || result.length() > 2000) throw validation(message); return result; }
    private static String optional(String value, String message) { if (value == null) return null; String result = value.strip(); if (result.isEmpty()) return null; if (result.length() > 2000) throw validation(message); return result; }
    private static String normalized(String value) { return value == null ? "" : value.strip().toUpperCase(Locale.ROOT); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static long id(String value, String message) { Long result = optionalId(value); if (result == null) throw validation(message); return result; }
    private static Long optionalId(Object value, String message) { Long result = value == null ? null : optionalId(value.toString()); if (value != null && result == null) throw validation(message); return result; }
    private static Long optionalId(String value) { try { long id = Long.parseLong(value); return id > 0 ? id : null; } catch (RuntimeException failure) { return null; } }
    private static BusinessException validation(String message) { return business("COMMON-400-VALIDATION", message, 400); }
    private static BusinessException forbidden() { return business("COMMON-403-FORBIDDEN", "没有执行该操作的权限", 403); }
    private static BusinessException notFound() { return business("COMMON-404-NOT_FOUND", "资源不存在或不可访问", 404); }
    private static BusinessException versionConflict() { return business("COMMON-409-VERSION_CONFLICT", "数据版本已变化，请重新读取", 409); }
    private static BusinessException statusConflict() { return business("COMMON-409-VERSION_CONFLICT", "当前状态不允许执行该操作", 409); }
    private static BusinessException idempotencyConflict() { return business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409); }
    private static BusinessException business(String code, String message, int status) { return new BusinessException(code, message, status, false); }
}
