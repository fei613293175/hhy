package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.AdminIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.admin.AdminSecurityProperties;
import cc.orbexa.hhy.access.admin.AdminSecurityStore;
import cc.orbexa.hhy.access.admin.AdminUserContracts.CommandResultResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.PageMetaResource;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.FreezeRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.IdentityPageResource;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.MediaAccessRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.ReviewRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.ReadTicket;
import cc.orbexa.hhy.access.storage.R04MediaStorageGateway;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator identity query service. Command operations are added in the same R05 slice. */
@Service
public class AdminIdentityService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SNAPSHOT_TYPE = "r05.admin-identity-command.v1";
    private static final Map<String, String> SORTS = Map.of(
            "createdAt:desc", "s.created_at DESC,s.id DESC",
            "createdAt:asc", "s.created_at ASC,s.id ASC",
            "status:asc", "s.status ASC,s.id ASC",
            "status:desc", "s.status DESC,s.id DESC",
            "id:asc", "s.id ASC",
            "id:desc", "s.id DESC");
    private final AdminIdentityStore store;
    private final AdminIdentityPolicy policy;
    private final R04MediaStorageGateway storage;
    private final AdminSecurityStore security;
    private final AdminIdempotencySnapshotCipher snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] hmacSecret;

    public AdminIdentityService(
            AdminIdentityStore store, AdminIdentityPolicy policy,
            R04MediaStorageGateway storage, AdminSecurityStore security,
            AdminIdempotencySnapshotCipher snapshots,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminSecurityProperties properties, Clock clock) {
        this.store = store;
        this.policy = policy;
        this.storage = storage;
        this.security = security;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hmacSecret = properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public IdentityPageResource list(
            int page, int pageSize, String cursor, String status, String keyword, String sort) {
        if (cursor != null && !cursor.isBlank()) {
            throw validation("当前接口不支持同时使用页码和游标");
        }
        String orderBy = SORTS.get(sort);
        if (orderBy == null) throw validation("排序字段不在允许范围内");
        AdminIdentityStore.IdentityPage result = store.page(
                page, pageSize, status, keyword, orderBy);
        boolean hasMore = (long) page * pageSize < result.total();
        return new IdentityPageResource(
                result.items().stream().map(AdminIdentityService::resource).toList(),
                new PageMetaResource(page, pageSize, Long.toString(result.total()),
                        null, Boolean.toString(hasMore)));
    }

    @Transactional
    public IdentitySessionResource detail(
            AdminPrincipal principal, String userId, String requestId, String ip) {
        long id = resourceId(userId, "用户标识无效");
        Session session = store.latestByUser(id).orElseThrow(AdminIdentityService::notFound);
        store.profileAccess(principal.adminId(), id, "VIEW_PROFILE",
                "查看实名认证详情", requestId, ip, null);
        return resource(session);
    }

    @Transactional
    public CommandOutcome mediaAccess(
            AdminPrincipal principal, String userId, MediaAccessRequest request,
            String key, String requestId, String ip) {
        long id = resourceId(userId, "用户标识无效");
        return idempotent(scope(principal, "media", id), key, request, () -> {
            MediaObject media = store.latestMedia(id).orElseThrow(
                    () -> new BusinessException("COMMON-404-NOT_FOUND", "实名原图不存在或不可见", 404, false));
            Duration ceiling = policy.previewTtl();
            Duration requested = request.ttlSeconds() == null || request.ttlSeconds() == 0
                    ? ceiling : Duration.ofSeconds(request.ttlSeconds());
            if (requested.compareTo(ceiling) > 0) throw validation("预览有效期超过允许范围");
            ReadTicket ticket = storage.createReadUrl(media, requested);
            Instant now = Instant.now(clock);
            long tokenId = store.createMediaAccessToken(
                    media.id(), "admin:" + principal.adminId(), ticket.expiresAt());
            store.profileAccess(principal.adminId(), id, "VIEW_MEDIA", request.purpose().strip(),
                    requestId, ip, media.id());
            operation(principal.adminId(), "IDENTITY_MEDIA_VIEW", id, requestId, ip,
                    Map.of("mediaId", media.id(), "expiresAt", ticket.expiresAt().toString()));
            return CommandOutcome.command(new CommandResultResource(
                    Long.toString(tokenId), ticket.url().toString(), "PREVIEW_READY",
                    media.version(), now));
        });
    }

    @Transactional
    public CommandOutcome review(
            AdminPrincipal principal, String sessionId, ReviewRequest request,
            String key, String requestId, String ip) {
        long id = resourceId(sessionId, "认证会话标识无效");
        String decision = request.decision().strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("APPROVE", "REJECT", "ESCALATE").contains(decision)) {
            throw validation("复核结论不符合要求");
        }
        List<Long> evidence = request.evidenceIds().stream()
                .map(value -> resourceId(value, "复核证据标识无效")).distinct().toList();
        return idempotent(scope(principal, "review", id), key, request, () -> {
            Session before = store.sessionForUpdate(id).orElseThrow(AdminIdentityService::notFound);
            requireVersion(before, request.expectedVersion());
            if (!store.evidenceBelongsToSession(id, evidence)) {
                throw validation("复核证据不属于当前认证会话");
            }
            Session after;
            try {
                after = store.review(before, decision, request.reason().strip(), principal.adminId(),
                        key, evidence, Instant.now(clock));
            } catch (IllegalStateException concurrent) {
                throw versionConflict();
            }
            store.profileAccess(principal.adminId(), before.userId(), "REVIEW",
                    request.reason().strip(), requestId, ip, null);
            operation(principal.adminId(), "IDENTITY_MANUAL_" + decision, before.userId(),
                    requestId, ip, Map.of("sessionId", id, "status", after.status(),
                            "version", after.version(), "evidenceIds", evidence));
            return CommandOutcome.session(resource(after));
        });
    }

    @Transactional
    public CommandOutcome freeze(
            AdminPrincipal principal, String userId, FreezeRequest request,
            String key, String requestId, String ip) {
        long id = resourceId(userId, "用户标识无效");
        return idempotent(scope(principal, "freeze", id), key, request, () -> {
            Session session = store.latestByUserForUpdate(id).orElseThrow(AdminIdentityService::notFound);
            requireVersion(session, request.expectedVersion());
            if (store.profileFrozen(id)) throw business("当前实名已经冻结");
            AdminIdentityStore.FreezeApproval approval =
                    store.pendingFreezeApprovalForUpdate(id).orElse(null);
            Instant now = Instant.now(clock);
            if (approval == null) {
                long approvalId = store.createFreezeApproval(id, principal.adminId());
                operation(principal.adminId(), "IDENTITY_FREEZE_APPROVAL_REQUESTED", id,
                        requestId, ip, Map.of("approvalId", approvalId));
                return CommandOutcome.command(new CommandResultResource(
                        Long.toString(id), "APR-" + approvalId, "PENDING_APPROVAL", 0L, now));
            }
            if (approval.requesterId() == principal.adminId()) {
                return CommandOutcome.command(new CommandResultResource(
                        Long.toString(id), "APR-" + approval.id(), "PENDING_APPROVAL",
                        approval.version(), now));
            }
            if (!store.approveFreeze(approval.id(), approval.version(), principal.adminId())) {
                throw versionConflict();
            }
            try {
                store.freeze(session, request.reason().strip(), principal.adminId(), key, now);
            } catch (IllegalStateException concurrent) {
                throw versionConflict();
            }
            store.profileAccess(principal.adminId(), id, "FREEZE", request.reason().strip(),
                    requestId, ip, null);
            operation(principal.adminId(), "IDENTITY_FROZEN", id, requestId, ip,
                    Map.of("approvalId", approval.id(), "sessionId", session.id()));
            return CommandOutcome.command(new CommandResultResource(
                    Long.toString(id), "APR-" + approval.id(), "IDENTITY_FROZEN",
                    session.version(), now));
        });
    }

    static IdentitySessionResource resource(Session session) {
        return new IdentitySessionResource(
                Long.toString(session.id()), Long.toString(session.userId()), session.status(),
                session.provider(), null, session.failureCode(), session.expiresAt(), session.version());
    }

    static long resourceId(String value, String message) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (RuntimeException invalid) {
            throw validation(message);
        }
    }

    private CommandOutcome idempotent(
            String scope, String key, Object request, Supplier<CommandOutcome> command) {
        if (key == null || key.length() < 16 || key.length() > 128) {
            throw validation("幂等键不符合要求");
        }
        Instant now = Instant.now(clock);
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyClaim claim = security.claimIdempotency(
                scope, key, requestHash, now.plus(IDEMPOTENCY_TTL));
        if (claim.replay()) return replay(claim.row(), scope, key, requestHash);
        CommandOutcome result = command.get();
        try {
            byte[] plain = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(scope, key, requestHash, SNAPSHOT_TYPE, plain);
            security.completeIdempotencySnapshot(
                    claim.row().id(), result.reference(), SNAPSHOT_TYPE, ciphertext);
            return result;
        } catch (Exception failure) {
            throw new IllegalStateException("Identity administrator replay snapshot is unavailable", failure);
        }
    }

    private CommandOutcome replay(
            AdminSecurityStore.IdempotencyRow row, String scope, String key, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null || !SNAPSHOT_TYPE.equals(row.responseType())
                || row.responsePayloadCiphertext() == null) {
            throw versionConflict();
        }
        try {
            byte[] plain = snapshots.decrypt(
                    scope, key, requestHash, SNAPSHOT_TYPE, row.responsePayloadCiphertext());
            return objectMapper.readValue(plain, CommandOutcome.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Identity administrator replay is unavailable", failure);
        }
    }

    private String hash(Object request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(objectMapper.writeValueAsBytes(request)));
        } catch (Exception failure) {
            throw new IllegalStateException("Identity administrator request hash is unavailable", failure);
        }
    }

    private void operation(
            long adminId, String action, long userId, String requestId,
            String ip, Map<String, ?> values) {
        try {
            var safe = objectMapper.createObjectNode();
            safe.put("requestId", requestId);
            safe.set("result", objectMapper.valueToTree(values));
            security.operationLog(adminId, action, "IDENTITY", userId, null, safe.toString(), ip);
        } catch (Exception failure) {
            throw new IllegalStateException("Identity administrator operation audit is unavailable", failure);
        }
    }

    private static void requireVersion(Session session, long expectedVersion) {
        if (session.version() != expectedVersion) throw versionConflict();
    }

    private static String scope(AdminPrincipal principal, String operation, long resourceId) {
        return "admin.identity:" + principal.adminId() + ":" + operation + ":" + resourceId;
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "实名记录不存在或不可见", 404, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "数据版本已变化，请刷新后重试", 409, false);
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public record CommandOutcome(
            IdentitySessionResource session, CommandResultResource command) {
        public CommandOutcome {
            if ((session == null) == (command == null)) {
                throw new IllegalArgumentException("Exactly one identity command payload is required");
            }
        }

        public static CommandOutcome session(IdentitySessionResource value) {
            return new CommandOutcome(value, null);
        }

        public static CommandOutcome command(CommandResultResource value) {
            return new CommandOutcome(null, value);
        }

        public Object payload() {
            return session == null ? command : session;
        }

        String reference() {
            return session == null ? command.resourceId() + ":" + command.status() : session.id();
        }
    }
}
