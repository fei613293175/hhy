package cc.orbexa.hhy.content;

import cc.orbexa.hhy.content.R12ReviewContracts.AppealPage;
import cc.orbexa.hhy.content.R12ReviewContracts.AppealResource;
import cc.orbexa.hhy.content.R12ReviewContracts.PageMeta;
import cc.orbexa.hhy.content.R12ReviewContracts.ReportPage;
import cc.orbexa.hhy.content.R12ReviewContracts.ReportResource;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewActorContext;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewAssignRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewDecisionRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewPage;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R12ReviewService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String RESPONSE_TYPE = "r12.review-resource.v1";
    private static final String REVIEW_DEFAULT_SORT = "priority:desc,createdAt:asc";
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "PENDING_REVIEW", "REVIEWING", "APPROVED", "REJECTED");
    private static final Map<String, String> REVIEW_SORTS = Map.of(
            REVIEW_DEFAULT_SORT, "risk priority descending, submitted time ascending",
            "createdAt:desc", "COALESCE(submitted.created_at,content.created_at) DESC,content.id DESC",
            "createdAt:asc", "COALESCE(submitted.created_at,content.created_at) ASC,content.id ASC",
            "updatedAt:desc", "content.updated_at DESC,content.id DESC",
            "updatedAt:asc", "content.updated_at ASC,content.id ASC",
            "id:desc", "content.id DESC",
            "id:asc", "content.id ASC");
    private static final Map<String, String> REPORT_SORTS = Map.of(
            "createdAt:desc", "report.created_at DESC,report.id DESC",
            "createdAt:asc", "report.created_at ASC,report.id ASC",
            "updatedAt:desc", "report.updated_at DESC,report.id DESC",
            "updatedAt:asc", "report.updated_at ASC,report.id ASC",
            "id:desc", "report.id DESC",
            "id:asc", "report.id ASC");
    private static final Map<String, String> APPEAL_SORTS = Map.of(
            "createdAt:desc", "appeal.created_at DESC,appeal.id DESC",
            "createdAt:asc", "appeal.created_at ASC,appeal.id ASC",
            "updatedAt:desc", "appeal.updated_at DESC,appeal.id DESC",
            "updatedAt:asc", "appeal.updated_at ASC,appeal.id ASC",
            "id:desc", "appeal.id DESC",
            "id:asc", "appeal.id ASC");

    private final R12ReviewStore store;
    private final ContentContactCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public R12ReviewService(
            R12ReviewStore store, ContentContactCipher cipher, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewPage queue(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        R12ReviewStore.PageQuery query = query(
                page, pageSize, cursor, status, keyword, sort, REVIEW_SORTS);
        R12ReviewStore.ReviewPageRows rows = store.reviews(query);
        List<ReviewResource> items = rows.items().stream().map(R12ReviewService::review).toList();
        R12ReviewStore.ReviewRow last = rows.items().isEmpty() ? null : rows.items().getLast();
        return new ReviewPage(items, page(query, rows.total(), rows.hasMore(), last == null ? null : last.id(),
                last == null ? null : sortValue(query.sort(), last.createdAt(), last.updatedAt())));
    }

    @Transactional(readOnly = true)
    public ReviewResource detail(String idValue) {
        long id = id(idValue, "审核标识无效");
        return store.review(id).map(R12ReviewService::review).orElseThrow(R12ReviewService::notFound);
    }

    @Transactional(readOnly = true)
    public ReportPage reports(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        R12ReviewStore.PageQuery query = query(
                page, pageSize, cursor, status, keyword, sort, REPORT_SORTS);
        R12ReviewStore.ReportPageRows rows = store.reports(query);
        List<ReportResource> items = rows.items().stream().map(R12ReviewService::report).toList();
        R12ReviewStore.ReportRow last = rows.items().isEmpty() ? null : rows.items().getLast();
        return new ReportPage(items, page(query, rows.total(), rows.hasMore(), last == null ? null : last.id(),
                last == null ? null : sortValue(query.sort(), last.createdAt(), last.updatedAt())));
    }

    @Transactional(readOnly = true)
    public AppealPage appeals(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        R12ReviewStore.PageQuery query = query(
                page, pageSize, cursor, status, keyword, sort, APPEAL_SORTS);
        R12ReviewStore.AppealPageRows rows = store.appeals(query);
        List<AppealResource> items = rows.items().stream().map(R12ReviewService::appeal).toList();
        R12ReviewStore.AppealRow last = rows.items().isEmpty() ? null : rows.items().getLast();
        return new AppealPage(items, page(query, rows.total(), rows.hasMore(), last == null ? null : last.id(),
                last == null ? null : sortValue(query.sort(), last.createdAt(), last.updatedAt())));
    }

    @Transactional
    public ReviewResource assign(
            ReviewActorContext actor, String idValue, ReviewAssignRequest request,
            String idempotencyKey) {
        long id = id(idValue, "审核标识无效");
        long assigneeId = id(request.assigneeId(), "审核员标识无效");
        String key = key(idempotencyKey);
        String reason = clean(request.reason());
        long expectedVersion = requiredVersion(request.expectedVersion());
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("reviewId", id);
        intent.put("assigneeId", assigneeId);
        intent.put("reason", reason);
        intent.put("expectedVersion", expectedVersion);
        String requestHash = hash(intent);
        String scope = "r12.adminReviewPostReviewsByIdAssign:admin:" + actor.adminId();
        R12ReviewStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);

        R12ReviewStore.ReviewRow locked = store.lockReview(id).orElseThrow(R12ReviewService::notFound);
        if (locked.version() != expectedVersion) throw versionConflict();
        if (!Set.of("PENDING_REVIEW", "REVIEWING").contains(locked.status())) throw statusConflict();
        if (!store.activeAdmin(assigneeId)) throw business("审核员不存在或不可用");
        R12ReviewStore.SnapshotRow snapshot = store.submittedSnapshot(id, expectedVersion)
                .orElseThrow(() -> business("审核对象缺少不可变提交快照"));
        String device = store.sessionDevice(actor.adminId(), actor.sessionId()).orElse(null);
        Instant now = Instant.now(clock);
        String targetStatus = "PENDING_REVIEW".equals(locked.status()) ? "REVIEWING" : locked.status();
        if (!store.advance(id, expectedVersion, targetStatus, now)) throw versionConflict();
        long newVersion = expectedVersion + 1;
        String commandHash = digest(scope + "\u0000" + key);
        String commandId = "assign-" + commandHash;
        store.reviewRecord(id, snapshot, "ASSIGN", reason, assigneeId, commandId, now);
        if ("PENDING_REVIEW".equals(locked.status())) {
            store.reviewRecord(id, snapshot, "CLAIM", reason, assigneeId,
                    "claim-" + commandHash, now);
            store.statusLog(id, locked.status(), targetStatus, reason, newVersion,
                    "admin:" + actor.adminId(), now);
        }
        ReviewResource before = review(locked);
        Map<String, Object> after = event(
                actor, device, id, snapshot, expectedVersion, newVersion,
                locked.status(), targetStatus,
                reason, List.of(), commandId, now);
        after.put("assigneeId", assigneeId);
        store.audit(actor.adminId(), "REVIEW_ASSIGNED", id, json(before), json(after), actor.ip(), now);
        store.outbox("content.review.assigned.v1", id, actor.requestId(), json(after));
        ReviewResource result = store.review(id).map(R12ReviewService::review)
                .orElseThrow(R12ReviewService::notFound);
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional
    public ReviewResource decide(
            ReviewActorContext actor, String idValue, ReviewDecisionRequest request,
            String idempotencyKey) {
        long id = id(idValue, "审核标识无效");
        String key = key(idempotencyKey);
        String decision = required(request.decision(), "审核决定不符合要求");
        if (!Set.of("APPROVE", "REJECT", "ESCALATE").contains(decision)) {
            throw validation("审核决定不符合要求");
        }
        String reason = required(request.reason(), "审核原因不能为空");
        long expectedVersion = requiredVersion(request.expectedVersion());
        List<String> evidenceIds = request.evidenceIds().stream()
                .map(R12ReviewService::clean)
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .toList();
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("reviewId", id);
        intent.put("decision", decision);
        intent.put("reason", reason);
        intent.put("expectedVersion", expectedVersion);
        intent.put("evidenceIds", evidenceIds);
        String requestHash = hash(intent);
        String scope = "r12.adminReviewPostReviewsByIdDecide:admin:" + actor.adminId();
        R12ReviewStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) return replay(claim, scope, key, requestHash);

        R12ReviewStore.ReviewRow locked = store.lockReview(id).orElseThrow(R12ReviewService::notFound);
        if (locked.version() != expectedVersion) throw versionConflict();
        if (!"REVIEWING".equals(locked.status())) throw statusConflict();
        R12ReviewStore.SnapshotRow snapshot = store.submittedSnapshot(id, expectedVersion)
                .orElseThrow(() -> business("审核对象缺少不可变提交快照"));
        R12ReviewStore.SecondReviewState secondReview = store.secondReviewState(snapshot.id());
        if ("ESCALATE".equals(decision) && secondReview.escalated()) {
            throw business("当前提交版本已经进入二审");
        }
        if (!"ESCALATE".equals(decision) && secondReview.escalated()
                && (secondReview.assigneeId() == null
                || secondReview.assigneeId() != actor.adminId())) {
            throw business("二审必须先分配并由被分配的审核员作出决定");
        }
        String targetStatus = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "ESCALATE" -> "REVIEWING";
            default -> throw new IllegalStateException("Unsupported review decision");
        };
        String device = store.sessionDevice(actor.adminId(), actor.sessionId()).orElse(null);
        Instant now = Instant.now(clock);
        if (!store.advance(id, expectedVersion, targetStatus, now)) throw versionConflict();
        long newVersion = expectedVersion + 1;
        String commandId = "decide-" + digest(scope + "\u0000" + key);
        store.reviewRecord(id, snapshot, decision, reason, actor.adminId(), commandId, now);
        if (!locked.status().equals(targetStatus)) {
            store.statusLog(id, locked.status(), targetStatus, reason, newVersion,
                    "admin:" + actor.adminId(), now);
        }
        ReviewResource before = review(locked);
        Map<String, Object> after = event(
                actor, device, id, snapshot, expectedVersion, newVersion,
                locked.status(), targetStatus,
                reason, evidenceIds, commandId, now);
        after.put("decision", decision);
        store.audit(actor.adminId(), "REVIEW_DECIDED", id, json(before), json(after), actor.ip(), now);
        String eventType = "ESCALATE".equals(decision)
                ? "content.review.escalated.v1" : "content.review.decided.v1";
        store.outbox(eventType, id, actor.requestId(), json(after));
        ReviewResource result = store.review(id).map(R12ReviewService::review)
                .orElseThrow(R12ReviewService::notFound);
        complete(claim, scope, key, requestHash, result);
        return result;
    }

    @Transactional
    public ReportResource decideReport(
            ReviewActorContext actor, String idValue, ReviewDecisionRequest request,
            String idempotencyKey) {
        long id = id(idValue, "举报标识无效");
        DecisionIntent intent = decisionIntent(id, request);
        String key = key(idempotencyKey);
        String scope = "r15.adminReportsPostContentReportsByIdDecide:admin:" + actor.adminId();
        String requestHash = hash(intent.values());
        R12ReviewStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return store.report(id).map(R12ReviewService::report).orElseThrow(R12ReviewService::notFound);
        }
        R12ReviewStore.ReportRow before = store.lockReport(id).orElseThrow(R12ReviewService::notFound);
        if (before.version() != intent.expectedVersion()) throw versionConflict();
        Instant now = Instant.now(clock);
        if (!store.decideReport(id, intent.expectedVersion(), targetStatus(intent.decision()), now)) {
            throw versionConflict();
        }
        ReportResource result = store.report(id).map(R12ReviewService::report)
                .orElseThrow(R12ReviewService::notFound);
        store.audit(actor.adminId(), "CONTENT_REPORT_DECIDED", id, json(report(before)),
                json(result), actor.ip(), now);
        store.outbox("content.report.decided.v1", id, actor.requestId(), json(result));
        completeResource(claim, scope, key, requestHash, "r15.content-report-decision.v1", result);
        return result;
    }

    @Transactional
    public AppealResource decideAppeal(
            ReviewActorContext actor, String idValue, ReviewDecisionRequest request,
            String idempotencyKey) {
        long id = id(idValue, "申诉标识无效");
        DecisionIntent intent = decisionIntent(id, request);
        String key = key(idempotencyKey);
        String scope = "r15.adminAppealsPostAppealsByIdDecide:admin:" + actor.adminId();
        String requestHash = hash(intent.values());
        R12ReviewStore.IdempotencyClaim claim = claim(scope, key, requestHash);
        if (claim.replay()) {
            if (claim.responseRef() == null) throw versionConflict();
            return store.appeal(id).map(R12ReviewService::appeal).orElseThrow(R12ReviewService::notFound);
        }
        R12ReviewStore.AppealRow before = store.lockAppeal(id).orElseThrow(R12ReviewService::notFound);
        if (before.version() != intent.expectedVersion()) throw versionConflict();
        Instant now = Instant.now(clock);
        if (!store.decideAppeal(id, intent.expectedVersion(), targetStatus(intent.decision()), now)) {
            throw versionConflict();
        }
        AppealResource result = store.appeal(id).map(R12ReviewService::appeal)
                .orElseThrow(R12ReviewService::notFound);
        store.audit(actor.adminId(), "CONTENT_APPEAL_DECIDED", id, json(appeal(before)),
                json(result), actor.ip(), now);
        store.outbox("content.appeal.decided.v1", id, actor.requestId(), json(result));
        completeResource(claim, scope, key, requestHash, "r15.content-appeal-decision.v1", result);
        return result;
    }

    private DecisionIntent decisionIntent(long id, ReviewDecisionRequest request) {
        String decision = required(request.decision(), "决定不符合要求");
        if (!Set.of("APPROVE", "REJECT", "ESCALATE").contains(decision)) {
            throw validation("决定不符合要求");
        }
        String reason = required(request.reason(), "决定原因不能为空");
        long expectedVersion = requiredVersion(request.expectedVersion());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("decision", decision);
        values.put("reason", reason);
        values.put("expectedVersion", expectedVersion);
        values.put("evidenceIds", request.evidenceIds());
        return new DecisionIntent(decision, expectedVersion, values);
    }

    private static String targetStatus(String decision) {
        return switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "ESCALATE" -> "ESCALATED";
            default -> throw new IllegalStateException("Unsupported decision");
        };
    }

    private static Map<String, Object> event(
            ReviewActorContext actor, String device, long contentId,
            R12ReviewStore.SnapshotRow snapshot, long beforeVersion, long afterVersion,
            String beforeStatus, String status, String reason, List<String> evidenceIds,
            String commandId, Instant occurredAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actorId", actor.adminId());
        event.put("actorUsername", actor.username());
        event.put("permission", actor.permission());
        event.put("contentId", Long.toString(contentId));
        event.put("reviewId", Long.toString(contentId));
        event.put("submittedSnapshotId", snapshot.id());
        event.put("submittedSnapshotVersion", snapshot.versionNo());
        event.put("beforeStatus", beforeStatus);
        event.put("beforeVersion", beforeVersion);
        event.put("afterVersion", afterVersion);
        event.put("contentVersion", afterVersion);
        event.put("status", status);
        event.put("reason", reason);
        event.put("evidenceIds", evidenceIds);
        event.put("requestId", actor.requestId());
        event.put("ip", actor.ip());
        event.put("device", device);
        event.put("result", "SUCCESS");
        event.put("commandId", commandId);
        event.put("occurredAt", occurredAt);
        return event;
    }

    private R12ReviewStore.PageQuery query(
            int page, int pageSize, String cursor, String status, String keyword, String sort,
            Map<String, String> sorts) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw validation("分页参数不符合要求");
        if (!sorts.containsKey(sort)) throw validation("排序字段不在允许范围内");
        R12ReviewStore.CursorKey cursorKey = cursor(cursor, sort);
        String normalizedStatus = clean(status);
        if (sorts == REVIEW_SORTS && normalizedStatus != null && !REVIEW_STATUSES.contains(normalizedStatus)) {
            throw validation("审核状态不符合要求");
        }
        return new R12ReviewStore.PageQuery(
                page, pageSize, cursorKey, normalizedStatus, clean(keyword), sort);
    }

    private R12ReviewStore.IdempotencyClaim claim(String scope, String key, String requestHash) {
        R12ReviewStore.IdempotencyClaim claim = store.claim(
                scope, key, requestHash, Instant.now(clock).plus(IDEMPOTENCY_TTL));
        if (claim.replay() && !requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        return claim;
    }

    private ReviewResource replay(
            R12ReviewStore.IdempotencyClaim claim, String scope, String key, String requestHash) {
        if (!requestHash.equals(claim.requestHash())) throw idempotencyConflict();
        if (!(RESPONSE_TYPE + ":ok").equals(claim.responseRef())
                || !RESPONSE_TYPE.equals(claim.responseType())
                || claim.responsePayloadCiphertext() == null) {
            throw versionConflict();
        }
        try {
            byte[] plain = cipher.decryptSnapshot(
                    scope, key, requestHash, RESPONSE_TYPE, claim.responsePayloadCiphertext());
            return mapper.readValue(plain, ReviewResource.class);
        } catch (ContentContactCipher.ContactIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R12 review idempotency snapshot unavailable", failure);
        }
    }

    private void complete(
            R12ReviewStore.IdempotencyClaim claim, String scope, String key,
            String requestHash, ReviewResource result) {
        try {
            store.complete(claim.id(), RESPONSE_TYPE + ":ok", RESPONSE_TYPE,
                    cipher.encryptSnapshot(scope, key, requestHash, RESPONSE_TYPE,
                            mapper.writeValueAsBytes(result)));
        } catch (Exception failure) {
            throw new IllegalStateException("R12 review idempotency snapshot completion failed", failure);
        }
    }

    private void completeResource(
            R12ReviewStore.IdempotencyClaim claim, String scope, String key,
            String requestHash, String responseType, Object result) {
        try {
            store.complete(claim.id(), responseType + ":ok", responseType,
                    cipher.encryptSnapshot(scope, key, requestHash, responseType,
                            mapper.writeValueAsBytes(result)));
        } catch (Exception failure) {
            throw new IllegalStateException("R15 decision idempotency snapshot completion failed", failure);
        }
    }

    private static ReviewResource review(R12ReviewStore.ReviewRow row) {
        return new ReviewResource(
                Long.toString(row.id()), "CONTENT", Long.toString(row.id()), row.status(),
                row.riskLevel(), row.assigneeId() == null ? null : Long.toString(row.assigneeId()),
                row.decision(), row.reason(), row.createdAt(), row.version());
    }

    private static ReportResource report(R12ReviewStore.ReportRow row) {
        return new ReportResource(
                Long.toString(row.id()), string(row.reporterId()), "CONTENT", string(row.contentId()),
                row.reasonCode(), row.status(), decision(row.status()), row.createdAt(), row.version());
    }

    private static AppealResource appeal(R12ReviewStore.AppealRow row) {
        return new AppealResource(
                Long.toString(row.id()), string(row.appellantId()), "CONTENT", Long.toString(row.contentId()),
                null, row.status(), decision(row.status()), row.createdAt(), row.version());
    }

    private static String decision(String status) {
        return switch (status) {
            case "APPROVED" -> "APPROVE";
            case "REJECTED" -> "REJECT";
            case "ESCALATED" -> "ESCALATE";
            default -> null;
        };
    }

    private static PageMeta page(
            R12ReviewStore.PageQuery query, long total, boolean hasMore,
            Long lastId, Instant lastSortValue) {
        String nextCursor = hasMore && lastId != null && !REVIEW_DEFAULT_SORT.equals(query.sort())
                ? cursor(query.sort(), lastId, lastSortValue) : null;
        return new PageMeta(query.page(), query.pageSize(), Long.toString(total),
                nextCursor, Boolean.toString(hasMore));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("R12 review JSON serialization failed", failure);
        }
    }

    private String hash(Object value) {
        return digest(json(value));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("R12 review request hash unavailable", failure);
        }
    }

    private static String key(String value) {
        String result = value == null ? null : value.strip();
        if (result == null || result.length() < 16 || result.length() > 128) {
            throw validation("幂等键不符合要求");
        }
        return result;
    }

    private static long requiredVersion(Long value) {
        if (value == null || value < 0) throw validation("数据版本不符合要求");
        return value;
    }

    private static R12ReviewStore.CursorKey cursor(String value, String sort) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        if (REVIEW_DEFAULT_SORT.equals(sort)) throw validation("默认优先级排序仅支持页码分页");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cleaned), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", -1);
            if (parts.length != 4 || !"v1".equals(parts[0]) || !sort.equals(parts[1])) {
                throw new IllegalArgumentException();
            }
            long id = Long.parseLong(parts[3]);
            if (id <= 0) throw new IllegalArgumentException();
            Instant time = parts[2].isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(parts[2]));
            if (!sort.startsWith("id:") && time == null) throw new IllegalArgumentException();
            return new R12ReviewStore.CursorKey(id, time);
        } catch (RuntimeException failure) {
            throw validation("游标不符合要求");
        }
    }

    private static String cursor(String sort, long id, Instant value) {
        String timestamp = sort.startsWith("id:") ? "" : Long.toString(value.toEpochMilli());
        String payload = "v1\n" + sort + "\n" + timestamp + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static Instant sortValue(String sort, Instant createdAt, Instant updatedAt) {
        return switch (sort) {
            case REVIEW_DEFAULT_SORT, "createdAt:asc", "createdAt:desc" -> createdAt;
            case "updatedAt:asc", "updatedAt:desc" -> updatedAt;
            case "id:asc", "id:desc" -> null;
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        };
    }

    private static long id(String value, String message) {
        return optionalId(value, message);
    }

    private static long optionalId(String value, String message) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException failure) {
            throw validation(message);
        }
    }

    private static String required(String value, String message) {
        String result = clean(value);
        if (result == null) throw validation(message);
        return result;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.strip();
        return result.isEmpty() ? null : result;
    }

    private record DecisionIntent(String decision, long expectedVersion, Map<String, Object> values) { }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可访问", 404, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "数据版本已变化，请重新读取", 409, false);
    }

    private static BusinessException statusConflict() {
        return new BusinessException(
                "CONTENT-409-STATUS_TRANSITION", "当前状态不允许执行该审核操作", 409, false);
    }

    private static BusinessException idempotencyConflict() {
        return new BusinessException(
                "COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }
}
