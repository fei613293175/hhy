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
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminSecurityService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminSecurityService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String TYPE_ADMIN_SESSION = "r01.admin-session.v1";
    private static final String TYPE_COMMAND_RESULT = "r01.command-result.v1";
    private static final String TYPE_SECURITY_RESOURCE = "r01.admin-self-security.v1";
    private static final String TYPE_MFA_ENROLLMENT = "r01.mfa-enrollment.v1";

    private final AdminSecurityStore repository;
    private final AdminTokenService tokens;
    private final AdminTotpService totp;
    private final AdminLoginFactWriter loginFacts;
    private final PasswordEncoder passwords;
    private final AdminSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final AdminIdempotencySnapshotCipher snapshots;
    private final Clock clock;
    private final String unknownAccountPasswordHash;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminSecurityService(
            AdminSecurityStore repository,
            AdminTokenService tokens,
            AdminTotpService totp,
            AdminLoginFactWriter loginFacts,
            PasswordEncoder passwords,
            AdminSecurityProperties properties,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            AdminIdempotencySnapshotCipher snapshots,
            Clock clock) {
        this.repository = repository;
        this.tokens = tokens;
        this.totp = totp;
        this.loginFacts = loginFacts;
        this.passwords = passwords;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.snapshots = snapshots;
        this.clock = clock;
        this.unknownAccountPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    AdminSecurityService(
            AdminSecurityStore repository,
            AdminTokenService tokens,
            AdminTotpService totp,
            AdminLoginFactWriter loginFacts,
            PasswordEncoder passwords,
            AdminSecurityProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this(repository, tokens, totp, loginFacts, passwords, properties, objectMapper,
                new AdminIdempotencySnapshotCipher("v1", properties.mfaRootSecret(), ""), clock);
    }

    @Transactional(noRollbackFor = CommittedAdminSecurityFailure.class)
    public AdminSessionResource login(
            LoginRequest request, String idempotencyKey, String requestId, String ip, String device) {
        Instant now = Instant.now(clock);
        String username = request.username().strip();
        String scope = "admin.login:" + hmac(username);
        String requestHash = hash(request);
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            return replay(prior, scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                    AdminSessionResource.class, () -> replaySession(prior.responseRef(), now), false);
        }
        AdminSecurityStore.AdminAccount admin = repository.findAdminByUsername(username).orElse(null);
        lockLoginRateLimit(admin, username, ip, device);
        enforceLoginRateLimit(admin, ip, device, now);
        if (admin == null) {
            passwords.matches(request.password(), unknownAccountPasswordHash);
            loginFacts.failure(null, mask(username), "PASSWORD_LOGIN",
                    "INVALID_CREDENTIALS", ip, device, requestId);
            throw committedRule("账号或密码不正确");
        }
        if (!"ACTIVE".equals(admin.status())) {
            loginFacts.failure(admin.id(), mask(username), "PASSWORD_LOGIN",
                    "ACCOUNT_RESTRICTED", ip, device, requestId);
            throw committedRule("管理员账号已受限");
        }
        if (!passwords.matches(request.password(), admin.passwordHash())) {
            loginFacts.failure(admin.id(), mask(username), "PASSWORD_LOGIN",
                    "INVALID_CREDENTIALS", ip, device, requestId);
            throw committedRule("账号或密码不正确");
        }

        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                    AdminSessionResource.class, () -> replaySession(claim.row().responseRef(), now), false);
        }

        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.accessTtl());
        long sessionId = repository.createSession(admin.id(), jti, expiresAt, ip, device);
        repository.loginLog(admin.id(), mask(username), "PASSWORD_LOGIN", "SUCCESS",
                null, ip, device, requestId);
        AdminSessionResource result = sessionResource(repository.findSession(sessionId).orElseThrow(), now);
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                "session:" + sessionId, result);
    }

    @Transactional(noRollbackFor = CommittedAdminSecurityFailure.class)
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
            return replay(prior, scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                    AdminSessionResource.class, () -> replaySession(prior.responseRef(), now), false);
        }
        if (!repository.verifyMfaTicket(token, now)) {
            throw unauthenticated();
        }
        long adminId = Long.parseLong(token.sub());
        lockAndEnforceMfaRateLimit(adminId, token.sid(), ip, device, now);
        AdminSecurityStore.IdempotencyRow lockedPrior =
                repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (lockedPrior != null) {
            return replay(lockedPrior, scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                    AdminSessionResource.class,
                    () -> replaySession(lockedPrior.responseRef(), now), false);
        }
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(adminId)
                .filter(row -> "ACTIVE".equals(row.status())).orElseThrow(() -> rule("MFA尚未启用"));
        if (!verifyAndConsumeMfa(adminId, method, request.code())) {
            loginFacts.failure(adminId, null, "MFA_VERIFY", "INVALID_MFA_CODE",
                    ip, device, requestId);
            throw committedRule("MFA验证码不正确");
        }
        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                    AdminSessionResource.class, () -> replaySession(claim.row().responseRef(), now), false);
        }
        if (!repository.upgradeSessionMfa(token.sid(), token.ver())) {
            throw conflict();
        }
        repository.loginLog(adminId, null, "MFA_VERIFY", "SUCCESS", null, ip, device, requestId);
        AdminSessionResource result = sessionResource(repository.findSession(token.sid()).orElseThrow(), now);
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_ADMIN_SESSION,
                "session:" + token.sid(), result);
    }

    @Transactional
    public CommandResultResource logout(
            AdminPrincipal principal, Long expectedVersion, String reason, String idempotencyKey,
            String requestId, String ip) {
        Instant now = Instant.now(clock);
        long expected = expectedVersion == null ? principal.sessionVersion() : expectedVersion;
        String scope = "admin.logout:" + principal.sessionId();
        String requestHash = hmac(expected + ":" + String.valueOf(reason));
        if (principal.replayOnly()) {
            return replayOnly(scope, idempotencyKey, requestHash, TYPE_COMMAND_RESULT,
                    CommandResultResource.class,
                    () -> command(Long.toString(principal.sessionId()), "REVOKED", expected + 1, now));
        }
        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_COMMAND_RESULT,
                    CommandResultResource.class,
                    () -> command(Long.toString(principal.sessionId()), "REVOKED", expected + 1, now), false);
        }
        if (!repository.revokeSession(principal.sessionId(), expected, now)) {
            throw conflict();
        }
        audit(principal.adminId(), "ADMIN_LOGOUT", "admin_session", principal.sessionId(),
                requestId, ip, "REVOKED", expected + 1, reason);
        CommandResultResource result =
                command(Long.toString(principal.sessionId()), "REVOKED", expected + 1, now);
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_COMMAND_RESULT,
                "session:" + principal.sessionId(), result);
    }

    @Transactional(readOnly = true)
    public AdminSelfSecurityResource overview(AdminPrincipal principal) {
        return overview(principal.adminId(), Instant.now(clock));
    }

    @Transactional(noRollbackFor = CommittedAdminSecurityFailure.class)
    public AdminSelfSecurityResource changePassword(
            AdminPrincipal principal, PasswordChangeRequest request, String idempotencyKey,
            String requestId, String ip, String device) {
        requireSecurityWritePrincipal(principal);
        Instant now = Instant.now(clock);
        String scope = "admin.password:" + principal.adminId();
        String requestHash = hash(request);
        if (principal.replayOnly()) {
            return replayOnly(scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now));
        }
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            return replay(prior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        AdminSecurityStore.AdminAccount admin = repository.findAdmin(principal.adminId())
                .orElseThrow(AdminSecurityService::unauthenticated);
        int length = request.newPassword().length();
        if (!passwords.matches(request.currentPassword(), admin.passwordHash())
                || length < properties.passwordMinLength()
                || length > properties.passwordMaxLength()) {
            throw rule("当前密码不正确或新密码不符合安全策略");
        }
        lockAndEnforceMfaRateLimit(admin.id(), principal.sessionId(), ip, device, now);
        AdminSecurityStore.IdempotencyRow lockedPrior =
                repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (lockedPrior != null) {
            return replay(lockedPrior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        requireActiveMfaAfterLock(admin.id(), request.mfaCode(),
                "MFA_PASSWORD_CHANGE", requestId, ip, device);
        AdminSecurityStore.IdempotencyClaim claim = claim(scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        if (!repository.updatePassword(admin.id(), admin.version(), passwords.encode(request.newPassword()))) {
            throw conflict();
        }
        repository.revokeAllSessions(admin.id(), now);
        audit(admin.id(), "ADMIN_PASSWORD_CHANGED", "admin_user", admin.id(),
                requestId, ip, "PASSWORD_CHANGED", admin.version() + 1, null);
        AdminSelfSecurityResource result = overview(admin.id(), now);
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                "admin:" + admin.id(), result);
    }

    @Transactional
    public MfaEnrollmentResource enrollMfa(
            AdminPrincipal principal, String idempotencyKey, String requestId, String ip) {
        requireSecurityWritePrincipal(principal);
        Instant now = Instant.now(clock);
        String scope = "admin.mfa.enroll:" + principal.adminId();
        String requestHash = hmac("enroll");
        if (principal.replayOnly()) {
            return replayOnly(scope, idempotencyKey, requestHash, TYPE_MFA_ENROLLMENT,
                    MfaEnrollmentResource.class,
                    () -> enrollmentResource(principal.adminId(), principal.username(), now));
        }
        AdminSecurityStore.IdempotencyClaim claim = claim(
                scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_MFA_ENROLLMENT,
                    MfaEnrollmentResource.class,
                    () -> enrollmentResource(principal.adminId(), principal.username(), now), false);
        }
        repository.lockRateLimitBuckets(List.of("admin-mfa-enrollment:" + principal.adminId()));
        AdminSecurityStore.MfaMethodRow existing = repository.mfaMethod(principal.adminId()).orElse(null);
        if (existing != null && "ACTIVE".equals(existing.status())) {
            throw rule("MFA已经启用");
        }
        if (existing != null && "PENDING".equals(existing.status())
                && !now.isAfter(existing.updatedAt().plus(properties.enrollmentTtl()))) {
            MfaEnrollmentResource resource = enrollmentResource(principal.adminId(), principal.username(), now);
            return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_MFA_ENROLLMENT,
                    "mfa:" + existing.id(), resource);
        }
        String oldSecretRef = existing == null ? null : existing.secretRef();
        String enrollmentId = UUID.randomUUID().toString();
        AdminTotpService.EnrollmentMaterial material =
                totp.createEnrollment(principal.adminId(), principal.username(), enrollmentId, now);
        revokeSecretAfterRollback(principal.adminId(), material.secretRef());
        long methodId = repository.beginMfaEnrollment(principal.adminId(), material.secretRef(), now);
        if (methodId < 1) {
            totp.revoke(principal.adminId(), material.secretRef());
            throw conflict();
        }
        if (oldSecretRef != null) {
            revokeSecretAfterCommit(principal.adminId(), oldSecretRef);
        }
        audit(principal.adminId(), "ADMIN_MFA_ENROLL_STARTED", "admin_mfa_method", methodId,
                requestId, ip, "PENDING", existing == null ? 0 : existing.version() + 1, null);
        MfaEnrollmentResource result = new MfaEnrollmentResource(material.enrollmentId(), "TOTP", material.qrCodeUrl(),
                material.manualKeyMasked(), material.expiresAt());
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_MFA_ENROLLMENT,
                "mfa:" + methodId, result);
    }

    @Transactional(noRollbackFor = CommittedAdminSecurityFailure.class)
    public AdminSelfSecurityResource confirmMfa(
            AdminPrincipal principal, MfaConfirmRequest request, String idempotencyKey,
            String requestId, String ip, String device) {
        requireSecurityWritePrincipal(principal);
        Instant now = Instant.now(clock);
        String scope = "admin.mfa.confirm:" + principal.adminId();
        String requestHash = hash(request);
        if (principal.replayOnly()) {
            return replayOnly(scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now));
        }
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            return replay(prior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(principal.adminId())
                .filter(row -> "PENDING".equals(row.status())).orElseThrow(() -> rule("没有待确认的MFA绑定"));
        lockAndEnforceMfaRateLimit(principal.adminId(), principal.sessionId(), ip, device, now);
        AdminSecurityStore.IdempotencyRow lockedPrior =
                repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (lockedPrior != null) {
            return replay(lockedPrior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        if (!request.enrollmentId().equals(enrollmentId(method.secretRef()))
                || now.isAfter(method.updatedAt().plus(properties.enrollmentTtl()))
                || !verifyAndConsumeMfa(principal.adminId(), method, request.code())) {
            loginFacts.failure(principal.adminId(), null, "MFA_ENROLL_CONFIRM",
                    "INVALID_MFA_CODE", ip, device, requestId);
            throw committedRule("MFA绑定信息已失效或验证码不正确");
        }
        AdminSecurityStore.IdempotencyClaim claim = claim(scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        if (!repository.confirmMfa(method.id(), method.version() + 1, now)) {
            throw conflict();
        }
        audit(principal.adminId(), "ADMIN_MFA_ENABLED", "admin_mfa_method", method.id(),
                requestId, ip, "ACTIVE", method.version() + 2, null);
        AdminSelfSecurityResource result = overview(principal.adminId(), now);
        return completeSnapshot(claim, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                "mfa:" + method.id(), result);
    }

    @Transactional(noRollbackFor = CommittedAdminSecurityFailure.class)
    public AdminSelfSecurityResource disableMfa(
            AdminPrincipal principal, MfaDisableRequest request, String idempotencyKey,
            String requestId, String ip, String device) {
        requireSecurityWritePrincipal(principal);
        Instant now = Instant.now(clock);
        String scope = "admin.mfa.disable:" + principal.adminId();
        String requestHash = hash(request);
        if (principal.replayOnly()) {
            return replayOnly(scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now));
        }
        AdminSecurityStore.IdempotencyRow prior = repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (prior != null) {
            return replay(prior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        lockAndEnforceMfaRateLimit(
                principal.adminId(), principal.sessionId(), ip, device, now);
        AdminSecurityStore.IdempotencyRow lockedPrior =
                repository.findIdempotency(scope, idempotencyKey).orElse(null);
        if (lockedPrior != null) {
            return replay(lockedPrior, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        AdminSecurityStore.MfaMethodRow method = requireActiveMfaAfterLock(
                principal.adminId(), request.code(), "MFA_DISABLE", requestId, ip, device);
        AdminSecurityStore.IdempotencyClaim claim = claim(scope, idempotencyKey, requestHash, now);
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                    AdminSelfSecurityResource.class, () -> overview(principal.adminId(), now), false);
        }
        if (!repository.disableMfa(method.id(), method.version() + 1, now)) {
            throw conflict();
        }
        repository.expireUnusedRecoveryCodes(principal.adminId(), now);
        audit(principal.adminId(), "ADMIN_MFA_DISABLED", "admin_mfa_method", method.id(),
                requestId, ip, "DISABLED", method.version() + 2, request.reason());
        AdminSelfSecurityResource result = overview(principal.adminId(), now);
        AdminSelfSecurityResource completed = completeSnapshot(
                claim, scope, idempotencyKey, requestHash, TYPE_SECURITY_RESOURCE,
                "mfa:" + method.id(), result);
        revokeSecretAfterCommit(principal.adminId(), method.secretRef());
        return completed;
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
                totp.enrollmentFromReference(adminId, username, method.secretRef(), method.updatedAt());
        if (now.isAfter(material.expiresAt())) {
            throw rule("MFA绑定已过期，请重新开始");
        }
        return new MfaEnrollmentResource(material.enrollmentId(), "TOTP", material.qrCodeUrl(),
                material.manualKeyMasked(), material.expiresAt());
    }

    private AdminSecurityStore.MfaMethodRow requireActiveMfaAfterLock(
            long adminId, String code, String eventType,
            String requestId, String ip, String device) {
        AdminSecurityStore.MfaMethodRow method = repository.mfaMethod(adminId)
                .filter(row -> "ACTIVE".equals(row.status())).orElseThrow(() -> rule("MFA尚未启用"));
        if (!verifyAndConsumeMfa(adminId, method, code)) {
            loginFacts.failure(adminId, null, eventType, "INVALID_MFA_CODE", ip, device, requestId);
            throw committedRule("MFA验证码不正确");
        }
        return method;
    }

    private boolean verifyAndConsumeMfa(
            long adminId, AdminSecurityStore.MfaMethodRow method, String code) {
        var matchedStep = totp.matchingStep(adminId, method.secretRef(), code);
        return matchedStep.isPresent()
                && repository.consumeMfaStep(
                        method.id(), method.status(), method.version(), matchedStep.getAsLong());
    }

    private void lockLoginRateLimit(
            AdminSecurityStore.AdminAccount admin, String username, String ip, String device) {
        repository.lockRateLimitBuckets(List.of(
                "admin-login:account:" + (admin == null ? hmac(username) : admin.id()),
                "admin-login:ip:" + hmac(String.valueOf(ip)),
                "admin-login:device:" + hmac(String.valueOf(device))));
    }

    private void enforceLoginRateLimit(
            AdminSecurityStore.AdminAccount admin, String ip, String device, Instant now) {
        Instant since = now.minusSeconds(properties.passwordLockSeconds());
        long limit = properties.passwordMaxFailures();
        boolean accountExceeded = admin != null && repository.recentPasswordFailures(admin.id(), since) >= limit;
        boolean ipExceeded = repository.recentLoginFailuresByIp(ip, since) >= limit;
        boolean deviceExceeded = repository.recentLoginFailuresByDevice(device, since) >= limit;
        if (accountExceeded || ipExceeded || deviceExceeded) {
            Instant oldest = null;
            if (accountExceeded) oldest = later(oldest, repository.oldestPasswordFailure(admin.id(), since));
            if (ipExceeded) oldest = later(oldest, repository.oldestLoginFailureByIp(ip, since));
            if (deviceExceeded) oldest = later(oldest, repository.oldestLoginFailureByDevice(device, since));
            throw rateLimited("登录尝试过于频繁，请稍后重试", retryAfter(oldest, now));
        }
    }

    private void lockAndEnforceMfaRateLimit(
            long adminId, long sessionId, String ip, String device, Instant now) {
        repository.lockRateLimitBuckets(List.of(
                "admin-mfa:admin:" + adminId,
                "admin-mfa:session:" + sessionId,
                "admin-mfa:ip:" + hmac(String.valueOf(ip)),
                "admin-mfa:device:" + hmac(String.valueOf(device))));
        Instant since = now.minusSeconds(properties.passwordLockSeconds());
        long limit = properties.passwordMaxFailures();
        boolean adminExceeded = repository.recentMfaFailures(adminId, since) >= limit;
        boolean ipExceeded = repository.recentMfaFailuresByIp(ip, since) >= limit;
        boolean deviceExceeded = repository.recentMfaFailuresByDevice(device, since) >= limit;
        if (adminExceeded || ipExceeded || deviceExceeded) {
            Instant oldest = null;
            if (adminExceeded) oldest = later(oldest, repository.oldestMfaFailure(adminId, since));
            if (ipExceeded) oldest = later(oldest, repository.oldestMfaFailureByIp(ip, since));
            if (deviceExceeded) oldest = later(oldest, repository.oldestMfaFailureByDevice(device, since));
            throw rateLimited("MFA验证失败次数过多，请稍后重试", retryAfter(oldest, now));
        }
    }

    private long retryAfter(Instant oldest, Instant now) {
        if (oldest == null) return properties.passwordLockSeconds();
        long remaining = Duration.between(
                now, oldest.plusSeconds(properties.passwordLockSeconds())).getSeconds();
        return Math.max(1, remaining);
    }

    private static Instant later(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private AdminSecurityStore.IdempotencyClaim claim(
            String scope, String key, String requestHash, Instant now) {
        AdminSecurityStore.IdempotencyClaim claim = repository.claimIdempotency(
                scope, key, requestHash, now.plus(IDEMPOTENCY_TTL));
        if (claim.replay()) {
            validateReplayDigest(claim.row(), requestHash);
            validateCompletedState(claim.row(), false);
        }
        return claim;
    }

    private <T> T replayOnly(
            String scope, String key, String requestHash, String responseType,
            Class<T> responseClass, Supplier<T> legacyReplay) {
        AdminSecurityStore.IdempotencyRow row = repository.findIdempotency(scope, key)
                .orElseThrow(AdminSecurityService::unauthenticated);
        validateReplayDigest(row, requestHash);
        validateCompletedState(row, true);
        return replay(row, scope, key, requestHash, responseType, responseClass, legacyReplay, true);
    }

    private <T> T replay(
            AdminSecurityStore.IdempotencyRow row,
            String scope,
            String key,
            String requestHash,
            String responseType,
            Class<T> responseClass,
            Supplier<T> legacyReplay,
            boolean replayOnly) {
        validateReplayDigest(row, requestHash);
        validateCompletedState(row, replayOnly);
        boolean noType = row.responseType() == null;
        boolean noCiphertext = row.responsePayloadCiphertext() == null;
        if (noType && noCiphertext) {
            return legacyReplay.get();
        }
        if (!responseType.equals(row.responseType())) {
            throw snapshotFailure();
        }
        try {
            byte[] plaintext = snapshots.decrypt(
                    scope, key, requestHash, responseType, row.responsePayloadCiphertext());
            return objectMapper.readValue(plaintext, responseClass);
        } catch (AdminIdempotencySnapshotCipher.SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw snapshotFailure();
        }
    }

    private <T> T completeSnapshot(
            AdminSecurityStore.IdempotencyClaim claim,
            String scope,
            String key,
            String requestHash,
            String responseType,
            String responseRef,
            T result) {
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(scope, key, requestHash, responseType, plaintext);
            repository.completeIdempotencySnapshot(
                    claim.row().id(), responseRef, responseType, ciphertext);
            return result;
        } catch (AdminIdempotencySnapshotCipher.SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw snapshotFailure();
        }
    }

    private static void validateReplayDigest(
            AdminSecurityStore.IdempotencyRow row, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT",
                    "同一幂等键对应不同请求", 409, false);
        }
    }

    private static void validateCompletedState(
            AdminSecurityStore.IdempotencyRow row, boolean replayOnly) {
        boolean noType = row.responseType() == null;
        boolean noCiphertext = row.responsePayloadCiphertext() == null;
        if (noType != noCiphertext) {
            throw snapshotFailure();
        }
        if (noType && row.responseRef() == null) {
            if (replayOnly) throw unauthenticated();
            throw conflict();
        }
        if (!noType && (row.responseType().isBlank() || row.responsePayloadCiphertext().isBlank())) {
            throw snapshotFailure();
        }
    }

    private static IllegalStateException snapshotFailure() {
        return new IllegalStateException("Administrator idempotency snapshot is unavailable");
    }

    private static void requireSecurityWritePrincipal(AdminPrincipal principal) {
        if (!principal.replayOnly()
                && !principal.permissionCodes().contains("admin.self.security")) {
            throw new BusinessException(
                    "COMMON-403-FORBIDDEN", "权限或能力不足", 403, false);
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
            return hmac(objectMapper.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash idempotent request", exception);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.idempotencyHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect idempotency request digest", exception);
        }
    }

    private String enrollmentId(String secretRef) {
        try {
            return totp.enrollmentId(secretRef);
        } catch (RuntimeException exception) {
            throw rule("MFA密钥引用无效");
        }
    }

    private void revokeSecretAfterCommit(long adminId, String secretRef) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            totp.revoke(adminId, secretRef);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    totp.revoke(adminId, secretRef);
                } catch (RuntimeException exception) {
                    LOG.error("admin_mfa_secret_revoke_failed adminId={}", adminId, exception);
                }
            }
        });
    }

    private void revokeSecretAfterRollback(long adminId, String secretRef) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        totp.revoke(adminId, secretRef);
                    } catch (RuntimeException exception) {
                        LOG.error("admin_mfa_secret_rollback_cleanup_failed adminId={}", adminId, exception);
                    }
                }
            }
        });
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

    private static BusinessException rateLimited(String message, long retryAfterSeconds) {
        return new BusinessException(
                "COMMON-429-RATE_LIMITED", message, 429, true, retryAfterSeconds);
    }

    private static BusinessException rule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private static CommittedAdminSecurityFailure committedRule(String message) {
        return new CommittedAdminSecurityFailure(
                "COMMON-422-BUSINESS_RULE", message, 422, false);
    }
}
