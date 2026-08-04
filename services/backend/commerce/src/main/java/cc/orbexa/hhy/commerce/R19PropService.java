package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R19PropContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.HeadlineSlotRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropOrderRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropCreateRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPage;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PageMeta;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class R19PropService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "name:asc", "name:desc", "quantity:asc", "quantity:desc", "expiresAt:asc");
    private static final Set<String> PAYMENT_CHANNELS = Set.of("ALIPAY", "WECHAT_PAY", "BALANCE");
    private static final Set<String> PROP_TYPES = Set.of("REFRESH", "TOP", "HEADLINE", "COLOR");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> PATCH_FIELDS = Set.of(
            "name", "durationSeconds", "executionType", "priceCent",
            "memberPriceCent", "scope", "status");

    private final R19PropStore store;
    private final R19PropStore.Codec codec;

    public R19PropService(R19PropStore store, R19PropStore.Codec codec) {
        this.store = store;
        this.codec = codec;
    }

    public PropPage propStore(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "name:asc");
        R19PropStore.PageSlice<PropResource> result = execute(() -> store.store(query.store()));
        return page(result, query);
    }

    public PropPage userProps(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "expiresAt:asc");
        R19PropStore.PageSlice<PropResource> result = execute(
                () -> store.userProps(userId, query.store()));
        return page(result, query);
    }

    public CommandResultResource order(
            long userId, PropOrderRequest request, String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null) throw validation("道具订单不能为空");
        long skuId = id(request.skuId(), "道具SKU");
        long quantity = request.quantity() == null ? 0 : request.quantity();
        if (quantity < 1 || quantity > 1000) throw validation("道具数量必须在1至1000之间");
        String channel = channel(request.paymentChannel());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", skuId);
        body.put("quantity", quantity);
        body.put("paymentChannel", channel);
        UserHash command = userHash(
                userId, "propPostPropsOrders", "/api/v1/props/orders", idempotencyKey,
                requestId, body);
        return execute(() -> store.order(
                command.context(), skuId, quantity, channel, command.hash()));
    }

    public CommandResultResource use(
            long userId, String id, PropUseRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        long userPropId = id(id, "用户道具");
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String contentId = Long.toString(id(request.targetContentId(), "目标内容"));
        PropUseRequest normalized = new PropUseRequest(contentId, request.scheduledAt(), request.expectedVersion());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetContentId", contentId);
        body.put("scheduledAt", request.scheduledAt());
        body.put("expectedVersion", request.expectedVersion());
        UserHash command = userHash(
                userId, "propPostMePropsByIdUse", "/api/v1/me/props/" + userPropId + "/use",
                idempotencyKey, requestId, body);
        return execute(() -> store.use(
                command.context(), userPropId, normalized, command.hash()));
    }

    public PropPage adminProps(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "name:asc");
        return page(execute(() -> store.adminProps(query.store())), query);
    }

    public PropResource create(
            AdminActorContext actor, PropCreateRequest request, String idempotencyKey) {
        if (request == null) throw validation("道具商品不能为空");
        String type = required(request.propType(), 64, "道具类型").toUpperCase(Locale.ROOT);
        if (!PROP_TYPES.contains(type)) throw validation("道具类型不受支持");
        String status = required(request.status(), 64, "状态").toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw validation("状态不受支持");
        if (request.durationSeconds() == null || request.durationSeconds() < 1
                || request.durationSeconds() > 2592000) {
            throw validation("使用时长必须在1秒到30天之间");
        }
        if (request.priceCent() == null || request.priceCent() < 0
                || (request.memberPriceCent() != null && (request.memberPriceCent() < 0 || request.memberPriceCent() > request.priceCent()))) {
            throw validation("价格或会员价不符合要求");
        }
        PropCreateRequest normalized = new PropCreateRequest(type,
                required(request.name(), 255, "名称"), request.durationSeconds(),
                required(request.executionType(), 255, "执行方式"),
                required(request.productCode(), 64, "商品编码"), required(request.skuCode(), 64, "SKU编码"),
                request.priceCent(), request.memberPriceCent(), request.scope(), status,
                required(request.reason(), 500, "创建原因"));
        AdminHash command = adminHash(actor, "adminPropsPostProps", "new-prop", idempotencyKey, normalized);
        return execute(() -> store.create(command.context(), normalized, command.hash()));
    }

    public PropResource patch(
            AdminActorContext actor, String id, PropPatchRequest request, String idempotencyKey) {
        long resourceId = id(id, "道具");
        PropPatchRequest normalized = normalizePatch(request);
        AdminHash command = adminHash(
                actor, "adminPropsPatchPropsById", id, idempotencyKey, normalized);
        return execute(() -> store.patch(command.context(), resourceId, normalized, command.hash()));
    }

    public PropPage headlineSlots(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.headlineSlots(query.store())), query);
    }

    public PropResource createHeadlineSlot(
            AdminActorContext actor, HeadlineSlotRequest request, String idempotencyKey) {
        HeadlineSlotRequest normalized = normalizeSlot(request);
        AdminHash command = adminHash(
                actor, "adminPropsPostHeadlineSlots", "new-slot", idempotencyKey, normalized);
        return execute(() -> store.createHeadlineSlot(
                command.context(), normalized, command.hash()));
    }

    public PropPage executions(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.executions(query.store())), query);
    }

    private PropPatchRequest normalizePatch(PropPatchRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.payload() == null || request.payload().isEmpty()) {
            throw validation("修改道具至少需要一个变更字段");
        }
        String reason = required(request.reason(), 500, "修改原因");
        if (!PATCH_FIELDS.containsAll(request.payload().keySet())) {
            throw validation("包含不支持的道具变更字段");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.payload().containsKey("name")) {
            payload.put("name", required(value(request.payload(), "name"), 255, "名称"));
        }
        if (request.payload().containsKey("durationSeconds")) {
            long duration = positiveLong(request.payload().get("durationSeconds"), "使用时长");
            if (duration > 2592000) throw validation("使用时长不能超过30天");
            payload.put("durationSeconds", duration);
        }
        if (request.payload().containsKey("executionType")) {
            payload.put("executionType", required(
                    value(request.payload(), "executionType"), 255, "执行方式"));
        }
        if (request.payload().containsKey("priceCent")) {
            payload.put("priceCent", nonNegativeLong(
                    request.payload().get("priceCent"), "普通价"));
        }
        if (request.payload().containsKey("memberPriceCent")) {
            Object memberPrice = request.payload().get("memberPriceCent");
            payload.put("memberPriceCent", memberPrice == null
                    ? null : nonNegativeLong(memberPrice, "会员价"));
        }
        if (request.payload().containsKey("scope")) {
            Object scope = request.payload().get("scope");
            if (scope == null) payload.put("scope", Map.of());
            else {
                if (!(scope instanceof Map<?, ?>)) throw validation("适用范围必须是对象");
                payload.put("scope", scope);
            }
        }
        if (request.payload().containsKey("status")) {
            String status = required(value(request.payload(), "status"), 64, "状态")
                    .toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) throw validation("状态不受支持");
            payload.put("status", status);
        }
        Long price = (Long) payload.get("priceCent");
        Long memberPrice = (Long) payload.get("memberPriceCent");
        if (price != null && memberPrice != null && memberPrice > price) {
            throw validation("会员价不能高于普通价");
        }
        return new PropPatchRequest(reason, request.expectedVersion(), payload);
    }

    private static long positiveLong(Object value, String name) {
        long parsed = nonNegativeLong(value, name);
        if (parsed < 1) throw validation(name + "必须大于零");
        return parsed;
    }

    private static long nonNegativeLong(Object value, String name) {
        if (!(value instanceof Number number)) throw validation(name + "不符合要求");
        long parsed = number.longValue();
        if (parsed < 0 || (number instanceof Double || number instanceof Float)
                && number.doubleValue() != parsed) {
            throw validation(name + "不符合要求");
        }
        return parsed;
    }

    private HeadlineSlotRequest normalizeSlot(HeadlineSlotRequest request) {
        if (request == null || request.payload() == null || request.payload().isEmpty()) {
            throw validation("资源位内容不能为空");
        }
        String reason = optional(request.reason(), 2000, "原因");
        Map<String, Object> payload = request.payload();
        required(value(payload, "pageCode"), 255, "页面编码");
        required(value(payload, "slotCode"), 255, "资源位编码");
        Object capacity = payload.get("capacity");
        if (capacity != null && (!(capacity instanceof Number) || ((Number) capacity).intValue() < 1)) {
            throw validation("资源位容量必须大于零");
        }
        return new HeadlineSlotRequest(reason, request.expectedVersion(), payload);
    }

    private AdminHash adminHash(
            AdminActorContext actor, String operationId, String resourceKey,
            String idempotencyKey, Object body) {
        if (actor == null || actor.adminId() < 1 || actor.sessionId() < 1) {
            throw validation("管理员身份无效");
        }
        key(idempotencyKey);
        String resource = required(resourceKey, 64, "资源标识");
        String requestId = required(actor.requestId(), 64, "requestId");
        String ip = required(actor.ip(), 128, "IP");
        R19PropStore.AdminCommand context = new R19PropStore.AdminCommand(
                actor.adminId(), actor.sessionId(), actor.username(), operationId,
                resource, scope(actor.adminId(), operationId, resource), idempotencyKey,
                requestId, ip);
        return new AdminHash(context, hash(operationId, actor.adminId(), body));
    }

    private UserHash userHash(
            long userId, String operationId, String path, String idempotencyKey,
            String requestId, Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("method", "POST");
        canonical.put("path", path);
        canonical.put("userId", userId);
        canonical.put("operationId", operationId);
        canonical.put("body", body);
        return new UserHash(
                new R19PropStore.UserCommand(
                        userId, operationId, idempotencyKey, required(requestId, 64, "requestId")),
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
                for (String value : new String[] {
                        Long.toString(adminId), operationId, resourceKey}) {
                    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                    data.writeInt(encoded.length);
                    data.write(encoded);
                }
            }
            return "r19adm:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sha256(bytes.toByteArray()));
        } catch (Exception failure) {
            throw new IllegalStateException("R19幂等作用域计算失败", failure);
        }
    }

    private static QueryPlan query(
            int page, int pageSize, String cursor, String status,
            String keyword, String sort, String defaultSort) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw validation("分页参数不符合要求");
        }
        if (cursor != null && !cursor.isBlank()) throw validation("R19当前仅支持页码分页");
        String normalizedStatus = optional(status, 64, "状态");
        String normalizedKeyword = optional(keyword, 100, "关键词");
        String normalizedSort = optional(sort, 64, "排序");
        normalizedSort = normalizedSort == null ? defaultSort : normalizedSort;
        if (!SORTS.contains(normalizedSort)) throw validation("排序字段不在白名单中");
        return new QueryPlan(new R19PropStore.PageQuery(
                page, pageSize, (long) (page - 1) * pageSize,
                normalizedStatus, normalizedKeyword, normalizedSort));
    }

    private static PropPage page(R19PropStore.PageSlice<PropResource> result, QueryPlan query) {
        long total = result.total();
        boolean more = query.store().offset() + result.items().size() < total;
        return new PropPage(result.items(), new PageMeta(
                query.store().page(), query.store().pageSize(), Long.toString(total),
                null, Boolean.toString(more)));
    }

    private static String channel(String value) {
        String normalized = required(value, 64, "支付渠道").toUpperCase(Locale.ROOT);
        if (!PAYMENT_CHANNELS.contains(normalized)) throw validation("支付渠道不受支持");
        return normalized;
    }

    private static void activeUser(long userId) {
        if (userId < 1) throw new BusinessException(
                "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
    }

    private static long id(String value, String name) {
        String normalized = required(value, 64, name);
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException failure) {
            throw validation(name + "标识不符合要求");
        }
    }

    private static String value(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static String required(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw validation(name + "不符合要求");
        }
        return value.strip();
    }

    private static String optional(String value, int max, String name) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, name);
    }

    private static void key(String value) {
        required(value, 128, "X-Idempotency-Key");
        if (value.strip().length() < 16) throw validation("X-Idempotency-Key不符合要求");
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "道具资源不存在", 404, false);
    }

    private static <T> T execute(Operation<T> operation) {
        try {
            return operation.run();
        } catch (R19PropStore.StoreException failure) {
            throw switch (failure.kind()) {
                case NOT_FOUND -> notFound();
                case CONFLICT -> new BusinessException(
                        failure.getMessage().contains("幂等键")
                                ? "COMMON-409-IDEMPOTENCY_CONFLICT"
                                : "COMMON-409-VERSION_CONFLICT",
                        failure.getMessage(), 409, true);
                case BUSINESS_RULE -> new BusinessException(
                        "COMMON-422-BUSINESS_RULE", failure.getMessage(), 422, false);
                case INVALID_DATA, INTERNAL -> new IllegalStateException(failure.getMessage(), failure);
            };
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run(); }

    private record QueryPlan(R19PropStore.PageQuery store) { }
    private record UserHash(R19PropStore.UserCommand context, String hash) { }
    private record AdminHash(R19PropStore.AdminCommand context, String hash) { }
}
