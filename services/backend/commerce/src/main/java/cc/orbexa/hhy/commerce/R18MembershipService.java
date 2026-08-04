package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R18MembershipContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipBenefitsPutRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipGrantRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipOrderRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipPage;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipSkuPatchRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipUpgradeOrderRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipUpgradeQuoteRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.PageMeta;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class R18MembershipService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SKU_SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "name:asc", "name:desc", "priceCent:asc", "priceCent:desc");
    private static final Set<String> MEMBERSHIP_SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "expiresAt:asc", "expiresAt:desc");
    private static final Set<String> PAYMENT_CHANNELS = Set.of("ALIPAY", "WECHAT_PAY");

    private final R18MembershipStore store;
    private final R18MembershipStore.Codec codec;

    public R18MembershipService(R18MembershipStore store, R18MembershipStore.Codec codec) {
        this.store = store;
        this.codec = codec;
    }

    public MembershipPage skus(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "priceCent:asc", SKU_SORTS);
        R18MembershipStore.PageSlice<MembershipResource> result =
                execute(() -> store.skus(query.store()));
        return new MembershipPage(result.items(), page(query, result.total()));
    }

    public MembershipResource current(long userId) {
        activeUser(userId);
        return execute(() -> store.current(userId)).orElseThrow(R18MembershipService::notFound);
    }

    public CommandResultResource createPurchase(
            long userId,
            MembershipOrderRequest request,
            String idempotencyKey,
            String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        long skuId = id(request.skuId(), "会员SKU");
        String channel = channel(request.paymentChannel());
        UserHash command = userHash(
                userId, "membershipPostMembershipOrders", "/api/v1/membership/orders",
                idempotencyKey, requestId, Map.of("skuId", Long.toString(skuId), "paymentChannel", channel));
        return execute(() -> store.createPurchase(command.context(), skuId, channel, command.hash()));
    }

    public MembershipResource createUpgradeQuote(
            long userId,
            MembershipUpgradeQuoteRequest request,
            String idempotencyKey,
            String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        long skuId = id(request.targetSkuId(), "目标会员SKU");
        UserHash command = userHash(
                userId, "membershipPostMembershipUpgradeQuotes",
                "/api/v1/membership/upgrade-quotes", idempotencyKey, requestId,
                Map.of("targetSkuId", Long.toString(skuId)));
        return execute(() -> store.createUpgradeQuote(command.context(), skuId, command.hash()));
    }

    public CommandResultResource createUpgradeOrder(
            long userId,
            MembershipUpgradeOrderRequest request,
            String idempotencyKey,
            String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        long quoteId = id(request.quoteId(), "升级报价");
        String channel = channel(request.paymentChannel());
        UserHash command = userHash(
                userId, "membershipPostMembershipUpgradeOrders",
                "/api/v1/membership/upgrade-orders", idempotencyKey, requestId,
                Map.of("quoteId", Long.toString(quoteId), "paymentChannel", channel));
        return execute(() -> store.createUpgradeOrder(
                command.context(), quoteId, channel, command.hash()));
    }

    public MembershipPage adminSkus(
            int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "priceCent:asc", SKU_SORTS);
        R18MembershipStore.PageSlice<MembershipResource> result =
                execute(() -> store.skus(query.store()));
        return new MembershipPage(result.items(), page(query, result.total()));
    }

    public MembershipPage userMemberships(
            int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort,
                "updatedAt:desc", MEMBERSHIP_SORTS);
        R18MembershipStore.PageSlice<MembershipResource> result =
                execute(() -> store.userMemberships(query.store()));
        return new MembershipPage(result.items(), page(query, result.total()));
    }

    public MembershipResource patchSku(
            AdminActorContext actor,
            String id,
            MembershipSkuPatchRequest request,
            String idempotencyKey) {
        AdminHash command = adminHash(
                actor, "adminMembershipPatchMembershipSkusById", id,
                idempotencyKey, normalize(request));
        return execute(() -> store.patchSku(
                command.context(), id(id, "会员SKU"), command.patch(), command.hash()));
    }

    public MembershipResource replaceBenefits(
            AdminActorContext actor,
            String id,
            MembershipBenefitsPutRequest request,
            String idempotencyKey) {
        MembershipBenefitsPutRequest normalized = normalize(request);
        AdminHash command = adminHash(
                actor, "adminMembershipPutMembershipSkusByIdBenefits", id,
                idempotencyKey, normalized);
        return execute(() -> store.replaceBenefits(
                command.context(), id(id, "会员SKU"), normalized, command.hash()));
    }

    public MembershipResource grant(
            AdminActorContext actor,
            MembershipGrantRequest request,
            String idempotencyKey) {
        long userId = id(request.userId(), "用户");
        String skuId = optionalId(request.skuId(), "会员SKU");
        if (request.durationDays() != null
                && (request.durationDays() < 1 || request.durationDays() > 36_500)) {
            throw validation("赠送天数必须大于零");
        }
        String reason = required(request.reason(), 2000, "赠送原因");
        MembershipGrantRequest normalized = new MembershipGrantRequest(
                Long.toString(userId), skuId, request.durationDays(), reason);
        AdminHash command = adminHash(
                actor, "adminMembershipPostUserMembershipsGrants",
                Long.toString(userId), idempotencyKey, normalized);
        return execute(() -> store.grant(command.context(), normalized, command.hash()));
    }

    private MembershipSkuPatchRequest normalize(MembershipSkuPatchRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.name() == null && request.priceCent() == null && request.durationDays() == null
                && request.benefits() == null && request.status() == null) {
            throw validation("修改会员SKU至少需要一个变更字段");
        }
        validateSku(request.name(), request.priceCent(), request.durationDays(),
                request.benefits(), request.status());
        return new MembershipSkuPatchRequest(
                optionalRequired(request.name(), 255, "会员SKU名称"),
                request.priceCent(), request.durationDays(), request.benefits(),
                normalizeStatus(request.status()), request.expectedVersion());
    }

    private MembershipBenefitsPutRequest normalize(MembershipBenefitsPutRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.benefits() == null) throw validation("权益配置不能为空");
        validateSku(request.name(), request.priceCent(), request.durationDays(),
                request.benefits(), request.status());
        return new MembershipBenefitsPutRequest(
                optionalRequired(request.name(), 255, "会员SKU名称"),
                request.priceCent(), request.durationDays(), request.benefits(),
                normalizeStatus(request.status()), request.expectedVersion());
    }

    private static void validateSku(
            String name, Long priceCent, Long durationDays,
            Map<String, Object> benefits, String status) {
        optionalRequired(name, 255, "会员SKU名称");
        if (priceCent != null && priceCent < 0) throw validation("会员价格不能为负数");
        if (durationDays != null && (durationDays < 1 || durationDays > 36_500)) {
            throw validation("会员期限必须在1至36500天之间");
        }
        normalizeStatus(status);
        if (benefits != null) {
            if (benefits.size() > 100) throw validation("会员权益数量超过限制");
            for (Map.Entry<String, Object> benefit : benefits.entrySet()) {
                required(benefit.getKey(), 64, "权益代码");
                if (benefit.getValue() == null) throw validation("权益值不能为空");
            }
        }
    }

    private AdminHash adminHash(
            AdminActorContext actor,
            String operationId,
            String resourceKey,
            String idempotencyKey,
            Object body) {
        if (actor == null || actor.adminId() < 1 || actor.sessionId() < 1) {
            throw validation("管理员身份无效");
        }
        key(idempotencyKey);
        String normalizedResource = required(resourceKey, 64, "资源标识");
        String scope = scope(actor.adminId(), operationId, normalizedResource);
        R18MembershipStore.AdminCommand context = new R18MembershipStore.AdminCommand(
                actor.adminId(), actor.sessionId(), actor.username(), operationId,
                normalizedResource, scope, idempotencyKey,
                required(actor.requestId(), 64, "requestId"), required(actor.ip(), 128, "IP"));
        return new AdminHash(context, hash(operationId, actor.adminId(), body),
                body instanceof MembershipSkuPatchRequest patch ? patch : null);
    }

    private UserHash userHash(
            long userId,
            String operationId,
            String path,
            String idempotencyKey,
            String requestId,
            Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("method", "POST");
        canonical.put("path", path);
        canonical.put("userId", userId);
        canonical.put("operationId", operationId);
        canonical.put("body", body);
        return new UserHash(
                new R18MembershipStore.UserCommand(
                        userId, operationId, idempotencyKey,
                        required(requestId, 64, "requestId")),
                hex(sha256(codec.canonicalBytes(canonical))));
    }

    private String hash(String operationId, long adminId, Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("operationId", operationId);
        canonical.put("adminId", adminId);
        canonical.put("body", body);
        return hex(sha256(codec.canonicalBytes(canonical)));
    }

    public static String scope(long adminId, String operationId, String resourceKey) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                write(data, Long.toString(adminId));
                write(data, operationId);
                write(data, resourceKey);
            }
            return "r18adm:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sha256(bytes.toByteArray()));
        } catch (Exception failure) {
            throw new IllegalStateException("R18 idempotency scope failed", failure);
        }
    }

    private static QueryPlan query(
            int page, int pageSize, String cursor, String status,
            String keyword, String sort, String defaultSort, Set<String> allowedSorts) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw validation("分页参数不符合要求");
        }
        if (cursor != null && !cursor.isBlank()) throw validation("R18当前仅支持页码分页");
        String normalizedStatus = optional(status, 64, "状态");
        String normalizedKeyword = optional(keyword, 100, "关键词");
        String normalizedSort = sort == null || sort.isBlank() ? defaultSort : sort.strip();
        if (!allowedSorts.contains(normalizedSort)) throw validation("排序字段不符合要求");
        return new QueryPlan(new R18MembershipStore.PageQuery(
                page, pageSize, (long) (page - 1) * pageSize,
                normalizedStatus, normalizedKeyword, normalizedSort));
    }

    private static PageMeta page(QueryPlan query, long total) {
        long page = query.store().page();
        long size = query.store().pageSize();
        return new PageMeta(page, size, Long.toString(total), null,
                Boolean.toString(page * size < total));
    }

    private static String channel(String value) {
        String normalized = required(value, 2000, "支付渠道").toUpperCase(Locale.ROOT);
        if (!PAYMENT_CHANNELS.contains(normalized)) throw validation("支付渠道不支持");
        return normalized;
    }

    private static String normalizeStatus(String value) {
        if (value == null) return null;
        String normalized = required(value, 64, "会员SKU状态").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw validation("会员SKU状态不符合要求");
        }
        return normalized;
    }

    private static String optionalId(String value, String name) {
        return value == null || value.isBlank() ? null : Long.toString(id(value, name));
    }

    private static long id(String value, String name) {
        try {
            long result = Long.parseLong(required(value, 64, name));
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException failure) {
            throw validation(name + "标识不符合要求");
        }
    }

    private static void key(String value) {
        if (value == null || value.length() < 16 || value.length() > 128 || value.isBlank()) {
            throw validation("X-Idempotency-Key不符合要求");
        }
    }

    private static void activeUser(long userId) {
        if (userId < 1) throw new BusinessException(
                "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
    }

    private static String required(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw validation(name + "不符合要求");
        }
        return value.strip();
    }

    private static String optionalRequired(String value, int max, String name) {
        return value == null ? null : required(value, max, name);
    }

    private static String optional(String value, int max, String name) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, name);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static void write(DataOutputStream data, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "会员资源不存在", 404, false);
    }

    private static <T> T execute(Operation<T> operation) {
        try {
            return operation.run();
        } catch (R18MembershipStore.StoreException failure) {
            throw switch (failure.kind()) {
                case NOT_FOUND -> notFound();
                case CONFLICT -> new BusinessException(
                        "COMMON-409-VERSION_CONFLICT", failure.getMessage(), 409, true);
                case BUSINESS_RULE -> new BusinessException(
                        "COMMON-422-BUSINESS_RULE", failure.getMessage(), 422, false);
                case INVALID_DATA, INTERNAL -> new IllegalStateException(failure.getMessage(), failure);
            };
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }

    private record QueryPlan(R18MembershipStore.PageQuery store) { }
    private record UserHash(R18MembershipStore.UserCommand context, String hash) { }
    private record AdminHash(
            R18MembershipStore.AdminCommand context,
            String hash,
            MembershipSkuPatchRequest patch) { }
}
