package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminCommandContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminPaymentQueryRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CreatePaymentRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PageMeta;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentPage;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderNotification;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ReconciliationRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ResolvePaymentExceptionRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.UserCommandContext;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** R17 payment policy, validation, owner isolation, and error mapping. */
public final class R17PaymentService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CALLBACK_BYTES = 64 * 1024;
    private static final Set<String> SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "amountCent:asc", "amountCent:desc");
    private final R17PaymentStore store;
    private final R17PaymentStore.Codec codec;
    private final Set<String> allowedReturnHosts;

    public R17PaymentService(
            R17PaymentStore store,
            R17PaymentStore.Codec codec,
            Set<String> allowedReturnHosts) {
        this.store = store;
        this.codec = codec;
        this.allowedReturnHosts = Set.copyOf(allowedReturnHosts);
    }

    public PaymentResource cashier(long userId, String orderNo) {
        activeUser(userId);
        return execute(() -> store.cashier(userId, orderNo(orderNo)))
                .orElseThrow(R17PaymentService::notFound);
    }

    public PaymentPage paymentStatus(
            long userId,
            String orderNo,
            int page,
            int pageSize,
            String cursor,
            String status,
            String keyword,
            String sort) {
        activeUser(userId);
        orderNo(orderNo);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort);
        R17PaymentStore.PageSlice result = execute(() -> store.payments(userId, query.store()));
        return new PaymentPage(result.items(), page(query, result));
    }

    public PaymentResource createPayment(
            UserCommandContext context, String orderNo, CreatePaymentRequest request) {
        userContext(context, "paymentPostOrdersByOrdernoPayments");
        requireKey(context.idempotencyKey());
        if (request == null) throw validation("支付请求不能为空");
        String gateway = required(request.gateway(), 32, "支付方式").toUpperCase(Locale.ROOT);
        if (!gateway.equals("ALIPAY") && !gateway.equals("WECHAT_PAY")) {
            throw validation("支付方式不在允许范围内");
        }
        String returnUrl = allowedReturnUrl(request.returnUrl());
        CreatePaymentRequest normalized = new CreatePaymentRequest(gateway, returnUrl);
        String cleanOrderNo = orderNo(orderNo);
        String hash = requestHash(
                "POST", "/api/v1/orders/" + cleanOrderNo + "/payments",
                Long.toString(context.userId()), normalized);
        return execute(() -> store.createPayment(context, cleanOrderNo, normalized, hash));
    }

    public PaymentResource providerNotification(
            ProviderCallbackContext context, ProviderNotification notification) {
        callbackContext(context);
        requireKey(context.idempotencyKey());
        if (notification == null) throw validation("支付回调不能为空");
        ProviderNotification normalized = new ProviderNotification(
                required(notification.notificationId(), 64, "回调编号"),
                required(notification.eventType(), 128, "回调事件"),
                orderNo(notification.orderNo()),
                nonNegative(notification.amountCent(), "回调金额"),
                optionalCurrency(notification.currency()),
                required(notification.status(), 64, "回调状态"),
                notification.rawPayload());
        byte[] raw = context.rawBody().getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_CALLBACK_BYTES) throw validation("支付回调内容超过限制");
        String hash = hex(sha256(raw));
        String scope = "r17cb:" + hash.substring(0, 48);
        String encrypted = codec.encrypt(
                scope, normalized.notificationId(), hash, "r17.ProviderCallback.v1", raw);
        R17PaymentStore.CallbackResult result = execute(() -> store.applyNotification(
                context, normalized, hash, encrypted));
        if (result.amountMismatch()) {
            throw new BusinessException(
                    "PAYMENT-409-AMOUNT_MISMATCH",
                    "支付回调金额或币种与订单不一致，已转入异常账务",
                    409,
                    false);
        }
        return result.payment();
    }

    public PaymentPage adminPayments(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        return list(null, page, pageSize, cursor, status, keyword, sort, ListKind.PAYMENTS);
    }

    public PaymentPage adminCallbacks(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        return list(null, page, pageSize, cursor, status, keyword, sort, ListKind.CALLBACKS);
    }

    public PaymentPage adminExceptions(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        return list(null, page, pageSize, cursor, status, keyword, sort, ListKind.EXCEPTIONS);
    }

    public PaymentResource queryPayment(
            AdminCommandContext context, String id, AdminPaymentQueryRequest request) {
        adminContext(context, "adminPaymentsPostPaymentsByIdQuery");
        requireKey(context.idempotencyKey());
        long paymentId = id(id);
        AdminPaymentQueryRequest normalized = request == null
                ? new AdminPaymentQueryRequest(null, null, Map.of())
                : new AdminPaymentQueryRequest(
                        optional(request.reason(), 2000, "查单原因"),
                        request.expectedVersion(), request.payload());
        if (normalized.expectedVersion() != null && normalized.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String hash = requestHash(
                "POST", "/admin-api/v1/payments/" + paymentId + "/query",
                Long.toString(context.adminId()), normalized);
        return execute(() -> store.queryPayment(
                context, paymentId, normalized.expectedVersion(), hash));
    }

    public CommandResultResource resolveException(
            AdminCommandContext context,
            String id,
            ResolvePaymentExceptionRequest request) {
        adminContext(context, "adminPaymentsPostPaymentExceptionsByIdResolve");
        requireKey(context.idempotencyKey());
        if (request == null || request.expectedVersion() < 0) {
            throw validation("异常账务处理请求不符合要求");
        }
        ResolvePaymentExceptionRequest normalized = new ResolvePaymentExceptionRequest(
                required(request.resolution(), 2000, "处理结论"),
                required(request.reason(), 2000, "处理原因"), request.expectedVersion());
        long exceptionId = id(id);
        String hash = requestHash(
                "POST", "/admin-api/v1/payment-exceptions/" + exceptionId + "/resolve",
                Long.toString(context.adminId()), normalized);
        return execute(() -> store.resolveException(context, exceptionId, normalized, hash));
    }

    public CommandResultResource reconcile(
            AdminCommandContext context, ReconciliationRequest request) {
        adminContext(context, "adminPaymentsPostPaymentReconciliationRun");
        requireKey(context.idempotencyKey());
        ReconciliationRequest normalized = request == null
                ? new ReconciliationRequest(null, false)
                : new ReconciliationRequest(
                        optional(request.parameters(), 2000, "对账参数"),
                        Boolean.TRUE.equals(request.dryRun()));
        String hash = requestHash(
                "POST", "/admin-api/v1/payment-reconciliation/run",
                Long.toString(context.adminId()), normalized);
        return execute(() -> store.reconcile(context, normalized, hash));
    }

    private PaymentPage list(
            Long userId,
            int page,
            int pageSize,
            String cursor,
            String status,
            String keyword,
            String sort,
            ListKind kind) {
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort);
        R17PaymentStore.PageSlice result = execute(() -> switch (kind) {
            case PAYMENTS -> store.payments(userId, query.store());
            case CALLBACKS -> store.callbacks(query.store());
            case EXCEPTIONS -> store.exceptions(query.store());
        });
        return new PaymentPage(result.items(), page(query, result));
    }

    private QueryPlan query(
            int page,
            int pageSize,
            String cursor,
            String status,
            String keyword,
            String sort) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw validation("分页参数不符合要求");
        }
        String normalizedSort = sort == null || sort.isBlank() ? "createdAt:desc" : sort.strip();
        if (!SORTS.contains(normalizedSort)) throw validation("排序字段不在允许范围内");
        long offset;
        if (cursor == null || cursor.isBlank()) {
            try {
                offset = Math.multiplyExact((long) page - 1, pageSize);
            } catch (ArithmeticException failure) {
                throw validation("分页参数超出范围");
            }
        } else {
            if (page != 1) throw validation("cursor与page不能同时指定");
            offset = decodeCursor(cursor, normalizedSort);
        }
        String cleanStatus = clean(status, 64, "状态");
        String cleanKeyword = clean(keyword, 100, "关键词");
        return new QueryPlan(
                page, pageSize, offset, normalizedSort,
                new R17PaymentStore.PageQuery(
                        page, pageSize, offset, cleanStatus, cleanKeyword, normalizedSort));
    }

    private static PageMeta page(QueryPlan query, R17PaymentStore.PageSlice result) {
        long consumed = query.offset() + result.items().size();
        boolean more = consumed < result.total();
        return new PageMeta(
                query.page(), query.pageSize(), Long.toString(result.total()),
                more ? cursor(query.sort(), consumed) : null, Boolean.toString(more));
    }

    private String requestHash(String method, String path, String actor, Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("method", method);
        canonical.put("path", path);
        canonical.put("actor", actor);
        canonical.put("body", body);
        return hex(sha256(codec.canonicalBytes(canonical)));
    }

    private String allowedReturnUrl(String value) {
        String normalized = required(value, 2048, "回跳地址");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException failure) {
            throw validation("回跳地址不符合要求");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getPort() != -1
                || !allowedReturnHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw validation("回跳地址不在安全白名单内");
        }
        return uri.toASCIIString();
    }

    private static String orderNo(String value) {
        String result = required(value, 128, "订单号");
        if (!result.matches("^[A-Za-z0-9_-]{1,128}$")) throw notFound();
        return result;
    }

    private static long id(String value) {
        String result = required(value, 64, "资源标识");
        if (!result.matches("^[0-9]{1,19}$")) throw notFound();
        try {
            long id = Long.parseLong(result);
            if (id < 1) throw notFound();
            return id;
        } catch (NumberFormatException failure) {
            throw notFound();
        }
    }

    private static String optionalCurrency(String value) {
        if (value == null) return null;
        String result = required(value, 3, "币种").toUpperCase(Locale.ROOT);
        if (!result.matches("^[A-Z]{3}$")) throw validation("币种不符合要求");
        return result;
    }

    private static Long nonNegative(Long value, String field) {
        if (value != null && value < 0) throw validation(field + "不能为负数");
        return value;
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw validation(field + "不符合要求");
        }
        return value.strip();
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, field);
    }

    private static String clean(String value, int max, String field) {
        return optional(value, max, field);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) {
            throw validation("X-Idempotency-Key不符合要求");
        }
    }

    private static void activeUser(long userId) {
        if (userId < 1) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
    }

    private static void userContext(UserCommandContext context, String operation) {
        if (context == null || context.userId() < 1 || !operation.equals(context.operationId())
                || context.requestId() == null || context.requestId().isBlank()) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
    }

    private static void adminContext(AdminCommandContext context, String operation) {
        if (context == null || context.adminId() < 1 || context.sessionId() < 1
                || !operation.equals(context.operationId())
                || context.requestId() == null || context.requestId().isBlank()
                || context.ip() == null || context.ip().isBlank()) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "管理员登录状态已失效", 401, false);
        }
    }

    private static void callbackContext(ProviderCallbackContext context) {
        if (context == null || context.rawBody() == null
                || context.gateway() == null || context.gateway().isBlank()
                || context.timestamp() == null || context.timestamp().isBlank()
                || context.nonce() == null || context.nonce().isBlank()
                || context.signature() == null || context.signature().isBlank()) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "支付回调签名信息不完整", 401, false);
        }
    }

    private static String cursor(String sort, long offset) {
        String raw = "r17\n" + sort + "\n" + offset;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static long decodeCursor(String cursor, String sort) {
        if (cursor.length() > 256 || !cursor.matches("^[A-Za-z0-9_-]+$")) {
            throw validation("游标不符合要求");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String[] fields = new String(decoded, StandardCharsets.UTF_8).split("\\n", -1);
            if (fields.length != 3 || !"r17".equals(fields[0]) || !sort.equals(fields[1])) {
                throw validation("游标与排序不匹配");
            }
            long offset = Long.parseLong(fields[2]);
            if (offset < 0) throw validation("游标不符合要求");
            return offset;
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw validation("游标不符合要求");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
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
        } catch (R17PaymentStore.StoreException failure) {
            throw switch (failure.kind()) {
                case NOT_FOUND -> notFound();
                case CONFLICT -> new BusinessException(
                        "COMMON-409-VERSION_CONFLICT", failure.getMessage(), 409, false);
                case BUSINESS_RULE -> new BusinessException(
                        "COMMON-422-BUSINESS_RULE", failure.getMessage(), 422, false);
                case AMOUNT_MISMATCH -> new BusinessException(
                        "PAYMENT-409-AMOUNT_MISMATCH", failure.getMessage(), 409, false);
                case INTERNAL -> failure;
            };
        }
    }

    @FunctionalInterface
    private interface Action<T> {
        T run();
    }

    private enum ListKind { PAYMENTS, CALLBACKS, EXCEPTIONS }

    private record QueryPlan(
            int page, int pageSize, long offset, String sort, R17PaymentStore.PageQuery store) { }
}
