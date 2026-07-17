package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSelfSecurityResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.AdminSessionResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.CommandResultResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaConfirmRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaDisableRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaEnrollmentResource;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaVerifyRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.PasswordChangeRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSecurityService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final AdminSecurityStore repository;
    private final AdminTokenService tokens;
    private final AdminTotpService totp;
    private final AdminLoginFactWriter loginFacts;
    private final PasswordEncoder passwords;
    private final AdminSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminSecurityService(
            AdminSecurityStore repository,
            AdminTokenService tokens,
            AdminTotpService totp,
            AdminLoginFactWriter loginFacts,
            PasswordEncoder passwords,
            AdminSecurityProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.tokens = tokens;
        this.totp = totp;
        this.loginFacts = loginFacts;
        this.passwords = passwords;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AdminSessionResource login(
            LoginRequest request, String idempotencyKey, String requestId, String ip, String device) {
        Instant now = Instant.now(clock);
        String username = request.username().strip();
        String scope = "admin.login:" + sha256(username.toLowerCase(Locale.ROOT));
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            validateReplay(prior, requestHash);
            return replaySession(prior.responseRef(), now);
        }
        AdminSecurityStore.AdminAccount admin = repository.findAdminByUsername(username).orElse(null);
        if (admin == null) {
            loginFacts.failure(null, mask(username), "PASSWORD_LOGIN",
                    "INVALID_CREDENTIALS", ip, device, requestId);
            throw rule("账号或密码不正确");
        }
        if (!"ACTIVE".equals(admin.status())) {
            loginFacts.failure(admin.id(), mask(username), "PASSWORD_LOGIN",
                    "ACCOUNT_RESTRICTED", ip, device, requestId);
            throw new BusinessException("AUTH-423-ACCOUNT_RESTRICTED", "管理员账号已受限", 423, false);
        }
        long failures = repository.recentPasswordFailures(
                admin.id(), now.minusSeconds(properties.passwordLockSeconds()));
        if (failures >= properties.passwordMaxFailures()) {
            loginFacts.failure(admin.id(), mask(username), "PASSWORD_LOGIN",
                    "RATE_LIMITED", ip, device, requestId);
            throw new BusinessException("COMMON-429-RATE_LIMITED", "登录失败次数过多，请稍后重试", 429, true);
        }
        if (!passwords.matches(request.password(), admin.passwordHash())) {
            loginFacts.failure(admin.id(), mask(username), "PASSWORD_LOGIN",
                    "INVALID_CREDENTIALS", ip, device, requestId);
            throw rule("账号或密码不正确");
        }

        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replaySession(claim.row().responseRef(), now);
        }

        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.accessTtl());
        long sessionId = repository.createSession(admin.id(), jti, expiresAt, ip, device);
        repository.loginLog(admin.id(), mask(username), "PASSWORD_LOGIN", "SUCCESS",
                null, ip, device, requestId);
        repository.completeIdempotency(claim.row().id(), "session:" + sessionId);
        return sessionResource(repository.findSession(sessionId).orElseThrow(), now);
    }

    @Transactional
    public AdminSessionResource verifyMfa(
            MfaVerifyRequest request, String headerTicket, String idempotencyKey,
            String requestId, String ip, String device) {
        if (!MessageDigest.isEqual(
                request.mfaTicket().getBytes(StandardCharsets.UTF_8),
                headerTicket.getBytes(StandardCharsets.UTF_8))) {
            throw rule("MFA票据不一致");
        }
        Instant now = Instant.now(clock);
        AdminTokenService.TokenClaims token = parseMfaTicket(headerTicket);
        String scope = "admin.mfa.verify:" + token.sid();
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            validateReplay(prior, requestHash);
            return replaySession(prior.responseRef(), now);
        }
        if (!repository.verifyMfaTicket(token, now)) {
            throw unauthenticated();
        }
        long adminId = Long.parseLong(token.sub());
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(adminId)
                .filter(row -> "ACTIVE".equals(row.status())).orElseThrow(() -> rule("MFA尚未启用"));
        if (!totp.verify(adminId, method.secretRef(), request.code())) {
            loginFacts.failure(adminId, null, "MFA_VERIFY", "INVALID_MFA_CODE",
                    ip, device, requestId);
            throw rule("MFA验证码不正确");
        }
        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replaySession(claim.row().responseRef(), now);
        }
        if (!repository.upgradeSessionMfa(token.sid(), token.ver())) {
            throw conflict();
        }
        repository.loginLog(adminId, null, "MFA_VERIFY", "SUCCESS", null, ip, device, requestId);
        repository.completeIdempotency(claim.row().id(), "session:" + token.sid());
        return sessionResource(repository.findSession(token.sid()).orElseThrow(), now);
    }

    @Transactional
    public CommandResultResource logout(
            AdminPrincipal principal, Long expectedVersion, String reason, String idempotencyKey,
            String requestId, String ip) {
        Instant now = Instant.now(clock);
        long expected = expectedVersion == null ? principal.sessionVersion() : expectedVersion;
        AdminSecurityStore.IdempotencyClaim claim = claim(
                "admin.logout:" + principal.sessionId(), idempotencyKey,
                sha256(expected + ":" + String.valueOf(reason)), now);
        if (!claim.replay()) {
            if (!repository.revokeSession(principal.sessionId(), expected, now)) {
                throw conflict();
            }
            audit(principal.adminId(), "ADMIN_LOGOUT", "admin_session", principal.sessionId(),
                    requestId, ip, "REVOKED", expected + 1, reason);
            repository.completeIdempotency(claim.row().id(), "session:" + principal.sessionId());
        }
        return command(Long.toString(principal.sessionId()), "REVOKED", expected + 1, now);
    }

    @Transactional(readOnly = true)
    public AdminSelfSecurityResource overview(AdminPrincipal principal) {
        return overview(principal.adminId(), Instant.now(clock));
    }

    @Transactional
    public CommandResultResource changePassword(
            AdminPrincipal principal, PasswordChangeRequest request, String idempotencyKey,
            String requestId, String ip) {
        Instant now = Instant.now(clock);
        AdminSecurityStore.IdempotencyClaim claim = claim(
                "admin.password:" + principal.adminId(), idempotencyKey, hash(request), now);
        if (claim.replay()) {
            return command(Long.toString(principal.adminId()), "PASSWORD_CHANGED", null, now);
        }
        AdminSecurityStore.AdminAccount admin = repository.findAdmin(principal.adminId())
                .orElseThrow(AdminSecurityService::unauthenticated);
        int length = request.newPassword().length();
        if (!passwords.matches(request.currentPassword(), admin.passwordHash())
                || length < properties.passwordMinLength()
                || length > properties.passwordMaxLength()) {
            throw rule("当前密码不正确或新密码不符合安全策略");
        }
        requireActiveMfa(admin.id(), request.mfaCode());
        if (!repository.updatePassword(admin.id(), admin.version(), passwords.encode(request.newPassword()))) {
            throw conflict();
        }
        repository.revokeAllSessions(admin.id(), now);
        audit(admin.id(), "ADMIN_PASSWORD_CHANGED", "admin_user", admin.id(),
                requestId, ip, "PASSWORD_CHANGED", admin.version() + 1, null);
        repository.completeIdempotency(claim.row().id(), "admin:" + admin.id());
        return command(Long.toString(admin.id()), "PASSWORD_CHANGED", admin.version() + 1, now);
    }

    @Transactional
    public MfaEnrollmentResource enrollMfa(
            AdminPrincipal principal, String idempotencyKey, String requestId, String ip) {
        Instant now = Instant.now(clock);
        AdminSecurityStore.IdempotencyClaim claim = claim(
                "admin.mfa.enroll:" + principal.adminId(), idempotencyKey, sha256("enroll"), now);
        if (claim.replay()) {
            return enrollmentResource(principal.adminId(), principal.username(), now);
        }
        AdminSecurityStore.MfaMethodRow existing = repository.mfaMethod(principal.adminId()).orElse(null);
        if (existing != null && "ACTIVE".equals(existing.status())) {
            throw rule("MFA已经启用");
        }
        String enrollmentId = existing != null && "PENDING".equals(existing.status())
                ? enrollmentId(existing.secretRef()) : UUID.randomUUID().toString();
        AdminTotpService.EnrollmentMaterial material =
                totp.enrollment(principal.adminId(), principal.username(), enrollmentId, now);
        long methodId = repository.beginMfaEnrollment(principal.adminId(), material.secretRef(), now);
        if (methodId < 1) {
            throw conflict();
        }
        audit(principal.adminId(), "ADMIN_MFA_ENROLL_STARTED", "admin_mfa_method", methodId,
                requestId, ip, "PENDING", existing == null ? 0 : existing.version() + 1, null);
        repository.completeIdempotency(claim.row().id(), "mfa:" + methodId);
        return new MfaEnrollmentResource(material.enrollmentId(), "TOTP", material.qrCodeUrl(),
                material.manualKeyMasked(), material.expiresAt());
    }

    @Transactional
    public CommandResultResource confirmMfa(
            AdminPrincipal principal, MfaConfirmRequest request, String idempotencyKey,
            String requestId, String ip) {
        Instant now = Instant.now(clock);
        AdminSecurityStore.IdempotencyClaim claim = claim(
                "admin.mfa.confirm:" + principal.adminId(), idempotencyKey, hash(request), now);
        if (claim.replay()) {
            return command(Long.toString(principal.adminId()), "ACTIVE", null, now);
        }
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(principal.adminId())
                .filter(row -> "PENDING".equals(row.status())).orElseThrow(() -> rule("没有待确认的MFA绑定"));
        if (!request.enrollmentId().equals(enrollmentId(method.secretRef()))
                || now.isAfter(method.createdAt().plus(properties.enrollmentTtl()))
                || !totp.verify(principal.adminId(), method.secretRef(), request.code())) {
            throw rule("MFA绑定信息已失效或验证码不正确");
        }
        if (!repository.confirmMfa(method.id(), method.version(), now)) {
            throw conflict();
        }
        audit(principal.adminId(), "ADMIN_MFA_ENABLED", "admin_mfa_method", method.id(),
                requestId, ip, "ACTIVE", method.version() + 1, null);
        repository.completeIdempotency(claim.row().id(), "mfa:" + method.id());
        return command(Long.toString(method.id()), "ACTIVE", method.version() + 1, now);
    }

    @Transactional
    public CommandResultResource disableMfa(
            AdminPrincipal principal, MfaDisableRequest request, String idempotencyKey,
            String requestId, String ip) {
        Instant now = Instant.now(clock);
        AdminSecurityStore.IdempotencyClaim claim = claim(
                "admin.mfa.disable:" + principal.adminId(), idempotencyKey, hash(request), now);
        if (claim.replay()) {
            return command(Long.toString(principal.adminId()), "DISABLED", null, now);
        }
        AdminSecurityStore.MfaMethodRow method = requireActiveMfa(principal.adminId(), request.code());
        if (!repository.disableMfa(method.id(), method.version(), now)) {
            throw conflict();
        }
        repository.expireUnusedRecoveryCodes(principal.adminId(), now);
        audit(principal.adminId(), "ADMIN_MFA_DISABLED", "admin_mfa_method", method.id(),
                requestId, ip, "DISABLED", method.version() + 1, request.reason());
        repository.completeIdempotency(claim.row().id(), "mfa:" + method.id());
        return command(Long.toString(method.id()), "DISABLED", method.version() + 1, now);
    }

    private AdminSessionResource replaySession(String responseRef, Instant now) {
        if (responseRef == null || !responseRef.startsWith("session:")) {
            throw conflict();
        }
        long sessionId = Long.parseLong(responseRef.substring("session:".length()));
        return sessionResource(repository.findSession(sessionId).orElseThrow(AdminSecurityService::unauthenticated), now);
    }

    private AdminSessionResource sessionResource(AdminSecurityStore.SessionRow session, Instant now) {
        List<String> permissions = repository.permissionCodes(session.adminId());
        if ("NONE".equals(session.mfaLevel())
                && repository.mfaMethod(session.adminId()).filter(row -> "ACTIVE".equals(row.status())).isPresent()) {
            Instant ticketExpiry = min(session.expiresAt(), now.plus(properties.mfaTicketTtl()));
            AdminTokenService.IssuedToken ticket = tokens.issueMfaTicket(
                    session.adminId(), session.id(), session.version(), session.accessJti(), ticketExpiry);
            return new AdminSessionResource(null, ticket.expiresAt(), Long.toString(session.adminId()),
                    session.username(), permissions, "TOTP", ticket.value());
        }
        AdminPrincipal principal = new AdminPrincipal(session.adminId(), session.id(), session.version(),
                session.accessJti(), session.username(), Set.copyOf(permissions));
        AdminTokenService.IssuedToken access = tokens.issueAccess(principal, session.expiresAt());
        return new AdminSessionResource(access.value(), access.expiresAt(), Long.toString(session.adminId()),
                session.username(), permissions, "NONE", null);
    }

    private AdminSelfSecurityResource overview(long adminId, Instant now) {
        AdminSecurityStore.SecuritySnapshot value = repository.securitySnapshot(adminId, now);
        return new AdminSelfSecurityResource(Long.toString(value.adminId()), value.username(),
                !value.methods().isEmpty(), value.methods(), value.activeSessions(),
                value.lastPasswordChangedAt(), value.lastLoginAt(), maskIp(value.lastLoginIp()),
                value.recoveryCodesRemaining());
    }

    private MfaEnrollmentResource enrollmentResource(long adminId, String username, Instant now) {
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(adminId)
                .filter(row -> "PENDING".equals(row.status())).orElseThrow(AdminSecurityService::conflict);
        AdminTotpService.EnrollmentMaterial material =
                totp.enrollment(adminId, username, enrollmentId(method.secretRef()), method.createdAt());
        if (now.isAfter(material.expiresAt())) {
            throw rule("MFA绑定已过期，请重新开始");
        }
        return new MfaEnrollmentResource(material.enrollmentId(), "TOTP", material.qrCodeUrl(),
                material.manualKeyMasked(), material.expiresAt());
    }

    private AdminSecurityStore.MfaMethodRow requireActiveMfa(long adminId, String code) {
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(adminId)
                .filter(row -> "ACTIVE".equals(row.status())).orElseThrow(() -> rule("MFA尚未启用"));
        if (!totp.verify(adminId, method.secretRef(), code)) {
            throw rule("MFA验证码不正确");
        }
        return method;
    }

    private AdminSecurityStore.IdempotencyClaim claim(
            String scope, String key, String requestHash, Instant now) {
        AdminSecurityStore.IdempotencyClaim claim = repository.claimIdempotency(
                scope, key, requestHash, now.plus(IDEMPOTENCY_TTL));
        if (claim.replay() && !requestHash.equals(claim.row().requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键对应不同请求", 409, false);
        }
        if (claim.replay() && claim.row().responseRef() == null) {
            throw conflict();
        }
        return claim;
    }

    private static void validateReplay(AdminSecurityStore.IdempotencyRow row, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null) {
            throw conflict();
        }
    }

    private void audit(
            long adminId, String action, String resource, long resourceId,
            String requestId, String ip, String status, long version, String reason) {
        try {
            var safe = objectMapper.createObjectNode();
            safe.put("requestId", requestId);
            safe.put("status", status);
            safe.put("version", version);
            if (reason != null && !reason.isBlank()) {
                safe.put("reason", reason.length() > 200 ? reason.substring(0, 200) : reason);
            }
            repository.operationLog(adminId, action, resource, resourceId, null, safe.toString(), ip);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize administrator audit fact", exception);
        }
    }

    private String hash(Object value) {
        try {
            return sha256(objectMapper.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash idempotent request", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String enrollmentId(String secretRef) {
        if (secretRef == null || !secretRef.startsWith("derived:v1:")) {
            throw rule("MFA密钥引用无效");
        }
        return secretRef.substring("derived:v1:".length());
    }

    private static CommandResultResource command(String resourceId, String status, Long version, Instant now) {
        return new CommandResultResource(resourceId, "ADMIN-" + resourceId, status, version, now);
    }

    private AdminTokenService.TokenClaims parseMfaTicket(String ticket) {
        try {
            return tokens.parseMfaTicket(ticket);
        } catch (AdminTokenService.InvalidAdminTokenException exception) {
            throw unauthenticated();
        }
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 2) return "**";
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
    }

    private static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        int last = ip.lastIndexOf('.');
        if (last > 0) return ip.substring(0, last + 1) + "*";
        int colon = ip.indexOf(':');
        return colon > 0 ? ip.substring(0, colon) + ":****" : "***";
    }

    private static BusinessException unauthenticated() {
        return new BusinessException("COMMON-401-UNAUTHENTICATED", "未登录或Token无效", 401, false);
    }

    private static BusinessException conflict() {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", "资源版本冲突", 409, true);
    }

    private static BusinessException rule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }
}
