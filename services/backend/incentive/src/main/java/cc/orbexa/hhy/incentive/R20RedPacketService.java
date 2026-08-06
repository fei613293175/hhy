package cc.orbexa.hhy.incentive;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.OrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.IncreaseOrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.IncreaseQuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SubmitReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ViewSessionRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.HeartbeatRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CancelRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class R20RedPacketService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVIDENCE_IDS = 100;
    private static final long MAX_TOTAL_COUNT = 1_000_000L;
    private static final long MAX_AMOUNT_PER_CLAIM_CENT = 100_000_000L;
    private static final Set<String> SORTS = Set.of(
            "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc",
            "totalCount:asc", "totalCount:desc", "amountPerClaimCent:asc",
            "amountPerClaimCent:desc", "priority:desc,createdAt:asc");
    private static final Set<String> PAYMENT_CHANNELS = Set.of("ALIPAY", "WECHAT_PAY", "BALANCE");
    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT", "ESCALATE");

    private final R20RedPacketStore store;
    private final R20RedPacketContracts.Codec codec;

    public R20RedPacketService(
            R20RedPacketStore store, R20RedPacketContracts.Codec codec) {
        this.store = store;
        this.codec = codec;
    }

    public CampaignPage userCampaigns(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.userCampaigns(userId, query.store())), query);
    }

    public CampaignPage publicCampaigns(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.publicCampaigns(query.store())), query);
    }

    public CampaignResource publicCampaign(long userId, String id) {
        activeUser(userId);
        return execute(() -> store.publicCampaign(id(id, "红包活动")));
    }

    public CampaignResource userCampaign(long userId, String id) {
        activeUser(userId);
        return execute(() -> store.userCampaign(userId, id(id, "红包活动")));
    }

    public CampaignResource create(
            long userId, CreateRequest request, String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        CreateRequest normalized = normalizeCreate(request);
        return execute(() -> store.create(
                userCommand(userId, "redPacketPostRedPacketCampaigns", idempotencyKey, requestId),
                normalized, hash("redPacketPostRedPacketCampaigns", userId, normalized)));
    }

    public CampaignResource patch(
            long userId, String id, PatchRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        PatchRequest normalized = normalizePatch(request);
        long campaignId = id(id, "红包活动");
        return execute(() -> store.patch(
                userCommand(userId, "redPacketPatchRedPacketCampaignsById", idempotencyKey, requestId),
                campaignId, normalized,
                hash("redPacketPatchRedPacketCampaignsById", userId, normalized)));
    }

    public CampaignResource submitReview(
            long userId, String id, SubmitReviewRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String remark = optional(request.remark(), 2000, "备注");
        SubmitReviewRequest normalized = new SubmitReviewRequest(request.expectedVersion(), remark);
        long campaignId = id(id, "红包活动");
        return execute(() -> store.submitReview(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdSubmitReview",
                        idempotencyKey, requestId), campaignId, normalized,
                hash("redPacketPostRedPacketCampaignsByIdSubmitReview", userId, normalized)));
    }

    public CommandResultResource quote(
            long userId, String id, QuoteRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.totalCount() == null || request.amountPerClaimCent() == null) {
            throw validation("报价参数不符合要求");
        }
        amounts(request.totalCount(), request.amountPerClaimCent());
        long campaignId = id(id, "红包活动");
        return execute(() -> store.quote(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdQuote",
                        idempotencyKey, requestId), campaignId, request,
                hash("redPacketPostRedPacketCampaignsByIdQuote", userId, request)));
    }

    public CommandResultResource order(
            long userId, String id, OrderRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String quoteId = required(request.quoteId(), 64, "报价");
        String channel = required(request.paymentChannel(), 64, "支付渠道").toUpperCase(Locale.ROOT);
        if (!PAYMENT_CHANNELS.contains(channel)) throw validation("支付渠道不受支持");
        OrderRequest normalized = new OrderRequest(quoteId, channel, request.expectedVersion());
        long campaignId = id(id, "红包活动");
        return execute(() -> store.order(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdOrders",
                        idempotencyKey, requestId), campaignId, normalized,
                hash("redPacketPostRedPacketCampaignsByIdOrders", userId, normalized)));
    }

    public CampaignResource pause(
            long userId, String id, LifecycleRequest request,
            String idempotencyKey, String requestId) {
        return lifecycle(userId, id, request, idempotencyKey, requestId,
                "redPacketPostRedPacketCampaignsByIdPause");
    }

    public CampaignResource resume(
            long userId, String id, LifecycleRequest request,
            String idempotencyKey, String requestId) {
        return lifecycle(userId, id, request, idempotencyKey, requestId,
                "redPacketPostRedPacketCampaignsByIdResume");
    }

    public CampaignResource close(
            long userId, String id, LifecycleRequest request,
            String idempotencyKey, String requestId) {
        return lifecycle(userId, id, request, idempotencyKey, requestId,
                "redPacketPostRedPacketCampaignsByIdClose");
    }

    public CommandResultResource increaseQuote(
            long userId, String id, IncreaseQuoteRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.newAmountPerClaimCent() == null) {
            throw validation("提高金额参数不符合要求");
        }
        positive(request.newAmountPerClaimCent(), "新单个金额");
        if (request.newAmountPerClaimCent() > MAX_AMOUNT_PER_CLAIM_CENT) {
            throw validation("红包参数超出范围");
        }
        long campaignId = id(id, "红包活动");
        return execute(() -> store.increaseQuote(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdIncreaseQuotes",
                        idempotencyKey, requestId), campaignId, request,
                hash("redPacketPostRedPacketCampaignsByIdIncreaseQuotes", userId, request)));
    }

    public CommandResultResource increaseOrder(
            long userId, String id, IncreaseOrderRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String quoteId = required(request.quoteId(), 64, "报价");
        String channel = required(request.paymentChannel(), 64, "支付渠道").toUpperCase(Locale.ROOT);
        if (!PAYMENT_CHANNELS.contains(channel)) throw validation("支付渠道不受支持");
        long campaignId = id(id, "红包活动");
        IncreaseOrderRequest normalized = new IncreaseOrderRequest(quoteId, channel, request.expectedVersion());
        return execute(() -> store.increaseOrder(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdIncreaseOrders",
                        idempotencyKey, requestId), campaignId, normalized,
                hash("redPacketPostRedPacketCampaignsByIdIncreaseOrders", userId, normalized)));
    }

    public CampaignPage analytics(
            long userId, String id, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        long campaignId = id(id, "红包活动");
        return page(execute(() -> store.analytics(userId, campaignId, query.store())), query);
    }

    public CommandResultResource startViewSession(
            long userId, String id, ViewSessionRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null) throw validation("浏览会话参数不能为空");
        ViewSessionRequest normalized = new ViewSessionRequest(
                required(request.clientNonce(), 2000, "客户端随机数"),
                required(request.deviceContext(), 2000, "设备上下文"));
        long campaignId = id(id, "红包活动");
        return execute(() -> store.startViewSession(
                userCommand(userId, "redPacketPostRedPacketCampaignsByIdViewSessions",
                        idempotencyKey, requestId), campaignId, normalized,
                hash("redPacketPostRedPacketCampaignsByIdViewSessions", userId, normalized)));
    }

    public CommandResultResource heartbeat(
            long userId, String id, HeartbeatRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.clientSequence() < 0 || request.elapsedSeconds() < 0
                || request.elapsedSeconds() > 20) throw validation("浏览心跳参数不符合要求");
        long sessionId = id(id, "浏览会话");
        return execute(() -> store.heartbeat(
                userCommand(userId, "redPacketPostRedPacketViewSessionsByIdHeartbeat",
                        idempotencyKey, requestId), sessionId, request,
                hash("redPacketPostRedPacketViewSessionsByIdHeartbeat", userId, request)));
    }

    public CommandResultResource claim(
            long userId, String id, ClaimRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.finalHeartbeatSequence() < 0) {
            throw validation("领取参数不符合要求");
        }
        ClaimRequest normalized = new ClaimRequest(
                required(request.clientNonce(), 2000, "客户端随机数"), request.finalHeartbeatSequence());
        long sessionId = id(id, "浏览会话");
        return execute(() -> store.claim(
                userCommand(userId, "redPacketPostRedPacketViewSessionsByIdClaim",
                        idempotencyKey, requestId), sessionId, normalized,
                hash("redPacketPostRedPacketViewSessionsByIdClaim", userId, normalized)));
    }

    public CommandResultResource cancel(
            long userId, String id, CancelRequest request,
            String idempotencyKey, String requestId) {
        activeUser(userId);
        key(idempotencyKey);
        CancelRequest normalized = new CancelRequest(
                request == null ? null : optional(request.reason(), 2000, "取消原因"));
        long sessionId = id(id, "浏览会话");
        return execute(() -> store.cancel(
                userCommand(userId, "redPacketPostRedPacketViewSessionsByIdCancel",
                        idempotencyKey, requestId), sessionId, normalized,
                hash("redPacketPostRedPacketViewSessionsByIdCancel", userId, normalized)));
    }

    public CampaignPage claims(
            long userId, int page, int pageSize, String cursor,
            String status, String keyword, String sort) {
        activeUser(userId);
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.claims(userId, query.store())), query);
    }

    private CampaignResource lifecycle(long userId, String id, LifecycleRequest request,
                                       String idempotencyKey, String requestId, String operation) {
        activeUser(userId);
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String reason = optional(request.reason(), 2000, "原因");
        LifecycleRequest normalized = new LifecycleRequest(reason, request.expectedVersion());
        long campaignId = id(id, "红包活动");
        UserCommand command = userCommand(userId, operation, idempotencyKey, requestId);
        return execute(() -> switch (operation) {
            case "redPacketPostRedPacketCampaignsByIdPause" -> store.pause(
                    command, campaignId, normalized, hash(operation, userId, normalized));
            case "redPacketPostRedPacketCampaignsByIdResume" -> store.resume(
                    command, campaignId, normalized, hash(operation, userId, normalized));
            case "redPacketPostRedPacketCampaignsByIdClose" -> store.close(
                    command, campaignId, normalized, hash(operation, userId, normalized));
            default -> throw new IllegalArgumentException("unsupported lifecycle operation");
        });
    }

    public CampaignPage adminCampaigns(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        QueryPlan query = query(page, pageSize, cursor, status, keyword, sort, "createdAt:desc");
        return page(execute(() -> store.adminCampaigns(query.store())), query);
    }

    public CampaignResource adminCampaign(String id) {
        return execute(() -> store.adminCampaign(id(id, "红包活动")));
    }

    public CampaignResource review(
            AdminCommand actor, String id, AdminReviewRequest request,
            String idempotencyKey) {
        if (actor == null || actor.adminId() < 1 || actor.sessionId() < 1) {
            throw validation("管理员身份无效");
        }
        key(idempotencyKey);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        String decision = required(request.decision(), 32, "审核决定").toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision)) throw validation("审核决定不受支持");
        String reason = required(request.reason(), 2000, "审核理由");
        if (request.evidenceIds().size() > MAX_EVIDENCE_IDS) {
            throw validation("审核证据最多100项");
        }
        AdminReviewRequest normalized = new AdminReviewRequest(
                decision, reason, request.expectedVersion(), request.evidenceIds());
        long campaignId = id(id, "红包活动");
        AdminCommand command = new AdminCommand(
                actor.adminId(), actor.sessionId(), actor.username(),
                "adminRedPacketPostRedPacketCampaignsByIdReview", actor.requestId(), actor.ip(), idempotencyKey);
        return execute(() -> store.review(
                command, campaignId, normalized, hash(command.operationId(), actor.adminId(), normalized)));
    }

    private static CreateRequest normalizeCreate(CreateRequest request) {
        if (request == null) throw validation("红包活动不能为空");
        String contentId = required(request.contentId(), 64, "内容");
        long total = positive(request.totalCount(), "红包数量");
        long amount = positive(request.amountPerClaimCent(), "单个金额");
        amounts(total, amount);
        window(request.startAt(), request.endAt());
        return new CreateRequest(contentId, total, amount, request.startAt(), request.endAt(), request.targeting());
    }

    private static PatchRequest normalizePatch(PatchRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("expectedVersion不符合要求");
        }
        if (request.totalCount() == null && request.amountPerClaimCent() == null
                && request.startAt() == null && request.endAt() == null
                && request.targeting() == null) throw validation("至少需要一个变更字段");
        if (request.totalCount() != null) {
            long total = positive(request.totalCount(), "红包数量");
            if (total > MAX_TOTAL_COUNT) throw validation("红包参数超出范围");
        }
        if (request.amountPerClaimCent() != null) {
            long amount = positive(request.amountPerClaimCent(), "单个金额");
            if (amount > MAX_AMOUNT_PER_CLAIM_CENT) throw validation("红包参数超出范围");
        }
        if (request.startAt() != null || request.endAt() != null) {
            if (request.startAt() == null || request.endAt() == null) throw validation("开始和结束时间必须同时提供");
            window(request.startAt(), request.endAt());
        }
        return request;
    }

    private static void amounts(long total, long amount) {
        positive(total, "红包数量");
        positive(amount, "单个金额");
        if (total > MAX_TOTAL_COUNT || amount > MAX_AMOUNT_PER_CLAIM_CENT) {
            throw validation("红包参数超出范围");
        }
        try { Math.multiplyExact(total, amount); }
        catch (ArithmeticException failure) { throw validation("红包本金超出范围"); }
    }

    private static void window(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) throw validation("活动时间窗口不符合要求");
    }

    private static long positive(Long value, String name) {
        if (value == null || value < 1) throw validation(name + "必须大于零");
        return value;
    }

    private static QueryPlan query(
            int page, int pageSize, String cursor, String status,
            String keyword, String sort, String defaultSort) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw validation("分页参数不符合要求");
        if (cursor != null && !cursor.isBlank()) throw validation("R20当前仅支持页码分页");
        String normalizedStatus = optional(status, 64, "状态");
        String normalizedKeyword = optional(keyword, 100, "关键词");
        String requestedSort = optional(sort, 64, "排序");
        String normalizedSort = requestedSort == null ? defaultSort : requestedSort;
        if (!SORTS.contains(normalizedSort)) throw validation("排序字段不在白名单中");
        return new QueryPlan(new R20RedPacketStore.PageQuery(
                page, pageSize, (long) (page - 1) * pageSize,
                normalizedStatus, normalizedKeyword, normalizedSort));
    }

    private static CampaignPage page(
            R20RedPacketStore.PageSlice<CampaignResource> result, QueryPlan query) {
        long total = result.total();
        boolean more = query.store().offset() + result.items().size() < total;
        return new CampaignPage(result.items(), new R20RedPacketContracts.PageMeta(
                query.store().page(), query.store().pageSize(), Long.toString(total),
                null, Boolean.toString(more)));
    }

    private static UserCommand userCommand(long userId, String operation, String key, String requestId) {
        return new UserCommand(userId, operation, key, required(requestId, 64, "requestId"));
    }

    private String hash(String operation, long actorId, Object body) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("operationId", operation);
        canonical.put("actorId", actorId);
        canonical.put("body", body);
        return hex(sha256(codec.canonicalBytes(canonical)));
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

    private static String required(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw validation(name + "不符合要求");
        return value.strip();
    }

    private static String optional(String value, int max, String name) {
        return value == null || value.isBlank() ? null : required(value, max, name);
    }

    private static void key(String value) {
        if (value == null || value.isBlank() || value.strip().length() < 16 || value.strip().length() > 128) {
            throw validation("X-Idempotency-Key不符合要求");
        }
    }

    private static void activeUser(long userId) {
        if (userId < 1) throw new BusinessException("COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static <T> T execute(Operation<T> operation) {
        try {
            return operation.run();
        } catch (R20RedPacketStore.StoreException failure) {
            throw switch (failure.kind()) {
                case NOT_FOUND -> new BusinessException("COMMON-404-NOT_FOUND", failure.getMessage(), 404, false);
                case CONFLICT -> new BusinessException(
                        failure.getMessage().contains("幂等") ? "COMMON-409-IDEMPOTENCY_CONFLICT" : "COMMON-409-VERSION_CONFLICT",
                        failure.getMessage(), 409, true);
                case BUSINESS_RULE -> new BusinessException("COMMON-422-BUSINESS_RULE", failure.getMessage(), 422, false);
                case IDENTITY_NOT_VERIFIED -> new BusinessException(
                        "IDENTITY-422-NOT_VERIFIED", failure.getMessage(), 422, false);
                case STOCK_EXHAUSTED -> new BusinessException(
                        "REDPACKET-409-STOCK_EXHAUSTED", failure.getMessage(), 409, true);
                case ALREADY_CLAIMED -> new BusinessException(
                        "REDPACKET-409-ALREADY_CLAIMED", failure.getMessage(), 409, false);
                case VIEW_INVALID -> new BusinessException(
                        "REDPACKET-422-VIEW_INVALID", failure.getMessage(), 422, false);
                case INVALID_DATA, INTERNAL -> new IllegalStateException(failure.getMessage(), failure);
            };
        }
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
    }

    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }

    @FunctionalInterface
    private interface Operation<T> { T run(); }

    private record QueryPlan(R20RedPacketStore.PageQuery store) { }
}
