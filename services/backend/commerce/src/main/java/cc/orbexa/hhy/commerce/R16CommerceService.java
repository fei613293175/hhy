package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R16CommerceContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.PageMeta;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class R16CommerceService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BENEFITS = 100;
    private static final Set<String> PRODUCT_SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "name:asc", "name:desc", "displayOrder:asc", "displayOrder:desc");
    private static final Set<String> SKU_SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "name:asc", "name:desc", "priceCent:asc", "priceCent:desc");
    private static final Set<String> ORDER_SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "status:asc", "status:desc",
            "orderNo:asc", "orderNo:desc");

    private final R16CommerceStore store;
    private final R16CommerceStore.Codec codec;

    public R16CommerceService(R16CommerceStore store, R16CommerceStore.Codec codec) {
        this.store = store;
        this.codec = codec;
    }

    public ProductPage products(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "updatedAt:desc", PRODUCT_SORTS);
        R16CommerceStore.PageSlice<ProductResource> result = execute(() -> store.products(query.store()));
        return new ProductPage(result.items(), page(query, result));
    }

    public ProductSkuPage skus(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "updatedAt:desc", SKU_SORTS);
        R16CommerceStore.PageSlice<ProductSkuResource> result = execute(() -> store.skus(query.store()));
        return new ProductSkuPage(result.items(), page(query, result));
    }

    public OrderPage userOrders(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "createdAt:desc", ORDER_SORTS);
        R16CommerceStore.PageSlice<OrderResource> result =
                execute(() -> store.orders(userId, query.store()));
        return new OrderPage(result.items(), page(query, result));
    }

    public OrderResource userOrder(long userId, String orderNo) {
        activeUser(userId);
        return execute(() -> store.order(userId, required(orderNo, 128, "订单号")))
                .orElseThrow(R16CommerceService::notFound);
    }

    public OrderPage adminOrders(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(
                page, pageSize, cursor, status, keyword, sort, "createdAt:desc", ORDER_SORTS);
        R16CommerceStore.PageSlice<OrderResource> result =
                execute(() -> store.orders(null, query.store()));
        return new OrderPage(result.items(), page(query, result));
    }

    public OrderResource adminOrder(String orderNo) {
        return execute(() -> store.order(null, required(orderNo, 128, "订单号")))
                .orElseThrow(R16CommerceService::notFound);
    }

    public ProductResource createProduct(
            AdminActorContext actor, ProductCreateRequest request, String idempotencyKey) {
        actor(actor, "adminProductsPostProducts");
        requireKey(idempotencyKey);
        ProductCreateRequest normalized = new ProductCreateRequest(
                required(request.productCode(), 64, "商品代码"),
                required(request.name(), 255, "商品名称"),
                required(request.productType(), 64, "商品类型"),
                optional(request.description(), 2000, "商品说明"),
                request.displayOrder(),
                required(request.status(), 64, "商品状态"));
        return execute(() -> store.createProduct(
                command(actor, normalized.productCode(), idempotencyKey),
                normalized,
                requestHash(actor, "POST", "/admin-api/v1/products",
                        normalized.productCode(), normalized)));
    }

    public ProductResource patchProduct(
            AdminActorContext actor, String id, ProductPatchRequest request, String idempotencyKey) {
        actor(actor, "adminProductsPatchProductsById");
        requireKey(idempotencyKey);
        long productId = resourceId(id);
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.name() == null && request.productType() == null && request.description() == null
                && request.displayOrder() == null && request.status() == null) {
            throw validation("修改商品至少需要一个变更字段");
        }
        ProductPatchRequest normalized = new ProductPatchRequest(
                optionalRequired(request.name(), 255, "商品名称"),
                optionalRequired(request.productType(), 64, "商品类型"),
                optional(request.description(), 2000, "商品说明"),
                request.displayOrder(),
                optionalRequired(request.status(), 64, "商品状态"),
                request.expectedVersion());
        String path = "/admin-api/v1/products/" + productId;
        return execute(() -> store.patchProduct(
                command(actor, Long.toString(productId), idempotencyKey),
                productId,
                normalized,
                requestHash(actor, "PATCH", path, Long.toString(productId), normalized)));
    }

    public ProductSkuResource createSku(
            AdminActorContext actor, ProductSkuCreateRequest request, String idempotencyKey) {
        actor(actor, "adminProductsPostSkus");
        requireKey(idempotencyKey);
        ProductSkuCreateRequest normalized = normalizeCreateSku(request);
        return execute(() -> store.createSku(
                command(actor, normalized.skuCode(), idempotencyKey),
                normalized,
                requestHash(actor, "POST", "/admin-api/v1/skus",
                        normalized.skuCode(), normalized)));
    }

    public ProductSkuResource patchSku(
            AdminActorContext actor, String id, ProductSkuPatchRequest request, String idempotencyKey) {
        actor(actor, "adminProductsPatchSkusById");
        requireKey(idempotencyKey);
        long skuId = resourceId(id);
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.name() == null && request.priceCent() == null && request.memberPriceCent() == null
                && request.durationDays() == null && request.benefits() == null
                && request.commissionEnabled() == null && request.level1Bps() == null
                && request.level2Bps() == null && request.saleStartsAt() == null
                && request.saleEndsAt() == null && request.status() == null) {
            throw validation("修改SKU至少需要一个变更字段");
        }
        validateSkuFields(
                request.name(), request.priceCent(), request.memberPriceCent(), request.durationDays(),
                request.benefits(), request.commissionEnabled(), request.level1Bps(), request.level2Bps(),
                request.saleStartsAt(), request.saleEndsAt(), request.status(), false);
        ProductSkuPatchRequest normalized = new ProductSkuPatchRequest(
                optionalRequired(request.name(), 255, "SKU名称"),
                request.priceCent(),
                request.memberPriceCent(),
                request.durationDays(),
                request.benefits(),
                request.commissionEnabled(),
                request.level1Bps(),
                request.level2Bps(),
                request.saleStartsAt(),
                request.saleEndsAt(),
                optionalRequired(request.status(), 64, "SKU状态"),
                request.expectedVersion());
        String path = "/admin-api/v1/skus/" + skuId;
        return execute(() -> store.patchSku(
                command(actor, Long.toString(skuId), idempotencyKey),
                skuId,
                normalized,
                requestHash(actor, "PATCH", path, Long.toString(skuId), normalized)));
    }

    private ProductSkuCreateRequest normalizeCreateSku(ProductSkuCreateRequest request) {
        validateSkuFields(
                request.name(), request.priceCent(), request.memberPriceCent(), request.durationDays(),
                request.benefits(), request.commissionEnabled(), request.level1Bps(), request.level2Bps(),
                request.saleStartsAt(), request.saleEndsAt(), request.status(), true);
        return new ProductSkuCreateRequest(
                Long.toString(resourceId(request.productId())),
                required(request.skuCode(), 64, "SKU代码"),
                required(request.name(), 255, "SKU名称"),
                request.priceCent(),
                request.memberPriceCent(),
                request.durationDays(),
                request.benefits(),
                request.commissionEnabled(),
                zero(request.level1Bps()),
                zero(request.level2Bps()),
                request.saleStartsAt(),
                request.saleEndsAt(),
                required(request.status(), 64, "SKU状态"));
    }

    private static void validateSkuFields(
            String name,
            Long priceCent,
            Long memberPriceCent,
            Long durationDays,
            List<BenefitResource> benefits,
            Boolean commissionEnabled,
            Integer level1Bps,
            Integer level2Bps,
            Instant saleStartsAt,
            Instant saleEndsAt,
            String status,
            boolean create) {
        if (create) {
            required(name, 255, "SKU名称");
            required(status, 64, "SKU状态");
            if (priceCent == null || benefits == null || commissionEnabled == null) {
                throw validation("SKU必填字段不完整");
            }
        }
        if (priceCent != null && priceCent < 0
                || memberPriceCent != null && memberPriceCent < 0) {
            throw validation("SKU金额不能为负数");
        }
        if (durationDays != null && durationDays < 0) {
            throw validation("SKU有效天数不能为负数");
        }
        if (benefits != null) {
            if (benefits.size() > MAX_BENEFITS) throw validation("SKU权益数量超过限制");
            for (BenefitResource benefit : benefits) {
                if (benefit == null || benefit.value() == null) {
                    throw validation("SKU权益字段不完整");
                }
                required(benefit.benefitCode(), 64, "权益代码");
                required(benefit.name(), 255, "权益名称");
                optional(benefit.unit(), 64, "权益单位");
            }
        }
        int first = zero(level1Bps);
        int second = zero(level2Bps);
        if (first < 0 || second < 0 || first > 10000 || second > 10000 || first + second > 10000) {
            throw validation("SKU分佣比例不符合要求");
        }
        if (Boolean.FALSE.equals(commissionEnabled) && (first != 0 || second != 0)) {
            throw validation("关闭分佣时分佣比例必须为零");
        }
        if (saleStartsAt != null && saleEndsAt != null && !saleStartsAt.isBefore(saleEndsAt)) {
            throw validation("SKU销售时间窗口不符合要求");
        }
    }

    private R16CommerceStore.CommandContext command(
            AdminActorContext actor, String resourceKey, String idempotencyKey) {
        String scope = scope(actor.adminId(), actor.operationId(), resourceKey);
        return new R16CommerceStore.CommandContext(
                actor.adminId(), actor.sessionId(), actor.username(), actor.operationId(),
                resourceKey, scope, idempotencyKey, actor.requestId(), actor.ip());
    }

    private String requestHash(
            AdminActorContext actor,
            String method,
            String path,
            String resourceKey,
            Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("method", method);
        canonical.put("path", path);
        canonical.put("adminId", actor.adminId());
        canonical.put("operationId", actor.operationId());
        canonical.put("resourceKey", resourceKey);
        canonical.put("body", body);
        return hex(sha256(codec.canonicalBytes(canonical)));
    }

    public static String scope(long adminId, String operationId, String resourceKey) {
        if (adminId < 1) throw validation("管理员身份无效");
        required(operationId, 64, "operationId");
        required(resourceKey, 64, "资源标识");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                write(data, Long.toString(adminId));
                write(data, operationId);
                write(data, resourceKey);
            }
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sha256(bytes.toByteArray()));
            String scope = "r16adm:" + encoded;
            if (scope.length() >= 64) throw new IllegalStateException("R16 scope exceeds the frozen bound");
            return scope;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive R16 idempotency scope", exception);
        }
    }

    private static void write(DataOutputStream data, String value) throws Exception {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(encoded.length);
        data.write(encoded);
    }

    private static PageMeta page(QueryPlan query, R16CommerceStore.PageSlice<?> result) {
        long consumed = query.offset() + result.items().size();
        boolean more = consumed < result.total();
        String next = more ? cursor(query.sort(), consumed) : null;
        return new PageMeta(
                query.page(), query.pageSize(), Long.toString(result.total()),
                next, Boolean.toString(more));
    }

    private static QueryPlan query(
            int page,
            int pageSize,
            String cursor,
            String status,
            String keyword,
            String sort,
            String defaultSort,
            Set<String> allowedSorts) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw validation("分页参数不符合要求");
        }
        String normalizedSort = sort == null || sort.isBlank() ? defaultSort : sort.strip();
        if (!allowedSorts.contains(normalizedSort)) throw validation("排序字段不在允许范围内");
        long offset;
        if (cursor == null || cursor.isBlank()) {
            try {
                offset = Math.multiplyExact((long) page - 1, pageSize);
            } catch (ArithmeticException exception) {
                throw validation("分页参数超出范围");
            }
        } else {
            if (page != 1) throw validation("cursor与page不能同时指定");
            offset = decodeCursor(cursor, normalizedSort);
        }
        String cleanStatus = clean(status, 64, "状态");
        String cleanKeyword = clean(keyword, 100, "关键词");
        return new QueryPlan(
                page, pageSize, offset, cleanStatus, cleanKeyword, normalizedSort,
                new R16CommerceStore.PageQuery(
                        page, pageSize, offset, cleanStatus, cleanKeyword, normalizedSort));
    }

    private static String cursor(String sort, long offset) {
        String raw = "r16\n" + sort + "\n" + offset;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static long decodeCursor(String cursor, String sort) {
        if (cursor.length() > 256 || !cursor.matches("^[A-Za-z0-9_-]+$")) {
            throw validation("游标不符合要求");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(cursor)) {
                throw validation("游标不符合要求");
            }
            String[] fields = new String(decoded, StandardCharsets.UTF_8).split("\\n", -1);
            if (fields.length != 3 || !"r16".equals(fields[0]) || !sort.equals(fields[1])) {
                throw validation("游标与排序不匹配");
            }
            long offset = Long.parseLong(fields[2]);
            if (offset < 0) throw validation("游标不符合要求");
            return offset;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw validation("游标不符合要求");
        }
    }

    private static void actor(AdminActorContext actor, String operationId) {
        if (actor == null || actor.adminId() < 1 || actor.sessionId() < 1
                || !operationId.equals(actor.operationId())
                || actor.requestId() == null || actor.requestId().isBlank()
                || actor.ip() == null || actor.ip().isBlank()) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "管理员登录状态已失效", 401, false);
        }
    }

    private static void activeUser(long userId) {
        if (userId < 1) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
    }

    private static long resourceId(String value) {
        String normalized = required(value, 64, "资源标识");
        if (!normalized.matches("^[0-9]{1,19}$")) throw notFound();
        try {
            long id = Long.parseLong(normalized);
            if (id < 1) throw notFound();
            return id;
        } catch (NumberFormatException exception) {
            throw notFound();
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) {
            throw validation("X-Idempotency-Key不符合要求");
        }
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw validation(field + "不符合要求");
        }
        return value.strip();
    }

    private static String optionalRequired(String value, int max, String field) {
        return value == null ? null : required(value, max, field);
    }

    private static String optional(String value, int max, String field) {
        if (value == null) return null;
        if (value.length() > max) throw validation(field + "不符合要求");
        return value;
    }

    private static String clean(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, field);
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可见", 404, false);
    }

    private static <T> T execute(Action<T> action) {
        try {
            return action.run();
        } catch (R16CommerceStore.StoreException failure) {
            throw switch (failure.kind()) {
                case NOT_FOUND -> notFound();
                case CONFLICT -> new BusinessException(
                        "COMMON-409-VERSION_CONFLICT", "资源版本或幂等请求发生冲突", 409, false);
                case BUSINESS_RULE -> new BusinessException(
                        "COMMON-422-BUSINESS_RULE", failure.getMessage(), 422, false);
                case INVALID_DATA, INTERNAL -> failure;
            };
        }
    }

    @FunctionalInterface
    private interface Action<T> {
        T run();
    }

    private record QueryPlan(
            int page,
            int pageSize,
            long offset,
            String status,
            String keyword,
            String sort,
            R16CommerceStore.PageQuery store) { }
}
