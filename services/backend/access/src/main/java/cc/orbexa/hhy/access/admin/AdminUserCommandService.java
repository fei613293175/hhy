package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.AdminUserContracts.CommandResultResource;
import cc.orbexa.hhy.access.admin.AdminUserContracts.RestrictionRequest;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserControlRequest;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserCommandService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SNAPSHOT_TYPE = "r02.admin-user-command.v1";

    private final AdminUserStore users;
    private final AdminSecurityStore security;
    private final ObjectMapper objectMapper;
    private final AdminIdempotencySnapshotCipher snapshots;
    private final Clock clock;
    private final byte[] hmacSecret;

    public AdminUserCommandService(
            AdminUserStore users,
            AdminSecurityStore security,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminIdempotencySnapshotCipher snapshots,
            AdminSecurityProperties properties,
            Clock clock) {
        this.users = users;
        this.security = security;
        this.objectMapper = objectMapper;
        this.snapshots = snapshots;
        this.clock = clock;
        this.hmacSecret = properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CommandOutcome restrict(
            AdminPrincipal principal, String userId, RestrictionRequest request,
            String key, String requestId, String ip) {
        long id = parseUserId(userId);
        return idempotent(actorScope(principal, "restrict", id), key, request, () -> {
            Instant now = Instant.now(clock);
            AdminUserStore.UserRow before = locked(id);
            requireVersion(before, request.expectedVersion());
            requireControllable(before);
            users.expireRestrictions(id, now);
            users.upsertRestriction(id, request.restrictionType(), request.reason(),
                    request.expiresAt(), principal.adminId());
            if (!users.applyRestrictionStatus(id, before.version())) throw versionConflict();
            AdminUserStore.UserRow after = locked(id);
            users.statusLog(id, before.status(), after.status(), request.reason(), principal.adminId());
            audit(principal.adminId(), "USER_RESTRICTION_UPSERTED", id, requestId, ip,
                    after.status(), after.version(), request.restrictionType());
            return CommandOutcome.user(AdminUserService.resource(after));
        });
    }

    @Transactional
    public CommandOutcome removeRestriction(
            AdminPrincipal principal, String userId, String type,
            String key, String requestId, String ip) {
        long id = parseUserId(userId);
        String intent = type.strip();
        return idempotent(actorScope(principal, "restriction.remove", id),
                key, intent, () -> {
                    Instant now = Instant.now(clock);
                    AdminUserStore.UserRow before = locked(id);
                    requireControllable(before);
                    users.expireRestrictions(id, now);
                    if (!users.removeRestriction(id, intent, principal.adminId(), now)) {
                        throw notFound("当前限制不存在或已经失效");
                    }
                    boolean restricted = users.hasActiveRestrictions(id, now);
                    if (!users.reconcileRestrictionStatus(id, before.version(), restricted)) {
                        throw versionConflict();
                    }
                    AdminUserStore.UserRow after = locked(id);
                    users.statusLog(id, before.status(), after.status(), "解除限制:" + intent,
                            principal.adminId());
                    audit(principal.adminId(), "USER_RESTRICTION_REMOVED", id, requestId, ip,
                            after.status(), after.version(), intent);
                    return CommandOutcome.command(new CommandResultResource(
                            Long.toString(id), null, "REMOVED", after.version(), now));
                });
    }

    @Transactional
    public CommandOutcome freeze(
            AdminPrincipal principal, String userId, UserControlRequest request,
            String key, String requestId, String ip) {
        long id = parseUserId(userId);
        return idempotent(actorScope(principal, "freeze", id), key, request, () -> {
            Instant now = Instant.now(clock);
            AdminUserStore.UserRow before = locked(id);
            requireVersion(before, request.expectedVersion());
            if (!("ACTIVE".equals(before.status()) || "RESTRICTED".equals(before.status()))) {
                throw businessRule("当前账号状态不允许冻结");
            }
            var pending = users.pendingFreezeApprovalForUpdate(id);
            if (pending.isEmpty()) {
                long approvalId = users.createFreezeApproval(id, principal.adminId());
                audit(principal.adminId(), "USER_FREEZE_APPROVAL_REQUESTED", id, requestId, ip,
                        before.status(), before.version(), request.reason());
                return CommandOutcome.command(new CommandResultResource(
                        Long.toString(id), "APR-" + approvalId, "PENDING_APPROVAL", 0L, now));
            }
            AdminUserStore.FreezeApproval approval = pending.get();
            if (approval.requesterId() == principal.adminId()) {
                return CommandOutcome.command(new CommandResultResource(
                        Long.toString(id), "APR-" + approval.id(), "PENDING_APPROVAL",
                        approval.version(), now));
            }
            if (!users.approveFreeze(approval.id(), approval.version(), principal.adminId())) {
                throw versionConflict();
            }
            if (!users.freezeUser(id, before.version())) throw versionConflict();
            users.revokeUserSessions(id, now);
            AdminUserStore.UserRow after = locked(id);
            users.statusLog(id, before.status(), after.status(), request.reason(), principal.adminId());
            audit(principal.adminId(), "USER_FROZEN", id, requestId, ip,
                    after.status(), after.version(), request.reason());
            return CommandOutcome.user(AdminUserService.resource(after));
        });
    }

    @Transactional
    public CommandOutcome unfreeze(
            AdminPrincipal principal, String userId, UserControlRequest request,
            String key, String requestId, String ip) {
        long id = parseUserId(userId);
        return idempotent(actorScope(principal, "unfreeze", id), key, request, () -> {
            Instant now = Instant.now(clock);
            AdminUserStore.UserRow before = locked(id);
            requireVersion(before, request.expectedVersion());
            users.expireRestrictions(id, now);
            boolean restricted = users.hasActiveRestrictions(id, now);
            if (!users.unfreezeUser(id, before.version(), restricted)) {
                throw businessRule("当前账号状态不允许解冻");
            }
            AdminUserStore.UserRow after = locked(id);
            users.statusLog(id, before.status(), after.status(), request.reason(), principal.adminId());
            audit(principal.adminId(), "USER_UNFROZEN", id, requestId, ip,
                    after.status(), after.version(), request.reason());
            return CommandOutcome.user(AdminUserService.resource(after));
        });
    }

    @Transactional
    public CommandOutcome forceLogout(
            AdminPrincipal principal, String userId, UserControlRequest request,
            String key, String requestId, String ip) {
        long id = parseUserId(userId);
        return idempotent(actorScope(principal, "force-logout", id), key, request, () -> {
            Instant now = Instant.now(clock);
            AdminUserStore.UserRow before = locked(id);
            requireVersion(before, request.expectedVersion());
            requireControllable(before);
            int revoked = users.revokeUserSessions(id, now);
            if (!users.touchUserVersion(id, before.version())) throw versionConflict();
            AdminUserStore.UserRow after = locked(id);
            audit(principal.adminId(), "USER_FORCE_LOGOUT", id, requestId, ip,
                    after.status(), after.version(), "sessions=" + revoked);
            return CommandOutcome.user(AdminUserService.resource(after));
        });
    }

    private CommandOutcome idempotent(
            String scope, String key, Object request, Supplier<CommandOutcome> command) {
        Instant now = Instant.now(clock);
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyClaim claim = security.claimIdempotency(
                scope, key, requestHash, now.plus(IDEMPOTENCY_TTL));
        if (claim.replay()) return replay(claim.row(), scope, key, requestHash);
        CommandOutcome result = command.get();
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(scope, key, requestHash, SNAPSHOT_TYPE, plaintext);
            security.completeIdempotencySnapshot(
                    claim.row().id(), result.reference(), SNAPSHOT_TYPE, ciphertext);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Administrator user command snapshot is unavailable", exception);
        }
    }

    private static String actorScope(AdminPrincipal principal, String operation, long userId) {
        return "admin.user:" + principal.adminId() + ":" + operation + ":" + userId;
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
            byte[] plaintext = snapshots.decrypt(
                    scope, key, requestHash, SNAPSHOT_TYPE, row.responsePayloadCiphertext());
            return objectMapper.readValue(plaintext, CommandOutcome.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Administrator user command replay is unavailable", exception);
        }
    }

    private AdminUserStore.UserRow locked(long userId) {
        return users.findUserForUpdate(userId).orElseThrow(
                () -> notFound("用户不存在或不可见"));
    }

    private void audit(
            long adminId, String action, long userId, String requestId, String ip,
            String status, long version, String reason) {
        try {
            var safe = objectMapper.createObjectNode();
            safe.put("requestId", requestId);
            safe.put("status", status);
            safe.put("version", version);
            safe.put("reason", limited(reason));
            security.operationLog(adminId, action, "USER", userId, null, safe.toString(), ip);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize user control audit", exception);
        }
    }

    private String hash(Object request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(objectMapper.writeValueAsBytes(request)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash administrator user command", exception);
        }
    }

    private static String limited(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), 200));
    }

    private static void requireVersion(AdminUserStore.UserRow row, long expectedVersion) {
        if (row.version() != expectedVersion) throw versionConflict();
    }

    private static void requireControllable(AdminUserStore.UserRow row) {
        if (!("ACTIVE".equals(row.status()) || "RESTRICTED".equals(row.status())
                || "FROZEN".equals(row.status()))) {
            throw businessRule("当前账号状态不允许执行该操作");
        }
    }

    private static long parseUserId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException("non-positive");
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException("COMMON-400-VALIDATION", "用户标识无效", 400, false);
        }
    }

    private static BusinessException versionConflict() {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", "数据版本已变化，请刷新后重试", 409, false);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException("COMMON-404-NOT_FOUND", message, 404, false);
    }

    private static BusinessException businessRule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public record CommandOutcome(UserResource user, CommandResultResource command) {
        public CommandOutcome {
            if ((user == null) == (command == null)) {
                throw new IllegalArgumentException("Exactly one command outcome payload is required");
            }
        }

        public static CommandOutcome user(UserResource value) {
            return new CommandOutcome(value, null);
        }

        public static CommandOutcome command(CommandResultResource value) {
            return new CommandOutcome(null, value);
        }

        public Object payload() {
            return user == null ? command : user;
        }

        String reference() {
            return user == null ? command.resourceId() + ":" + command.status() : user.id();
        }
    }
}
