package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.access.user.UserAuthContracts.ChallengeResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.DeviceSummaryResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.InviteCodeValidateRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordLoginRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.PasswordResetRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.RefreshRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegistrationAgreementVersionResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegistrationConfigResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RegisterRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SecurityChallengeRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SmsLoginRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SmsSendRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "password123", "12345678", "123456789", "qwerty123");

    private final UserAuthStore repository;
    private final UserTokenService tokens;
    private final UserIdempotencySnapshotCipher snapshots;
    private final UserAuthProperties properties;
    private final UserAuthPolicy policy;
    private final UserAuthVerificationService verification;
    private final PasswordEncoder passwords;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UserAuthService(UserAuthStore repository, UserTokenService tokens,
                           UserIdempotencySnapshotCipher snapshots, UserAuthProperties properties,
                           UserAuthPolicy policy, UserAuthVerificationService verification,
                           PasswordEncoder passwords, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.tokens = tokens;
        this.snapshots = snapshots;
        this.properties = properties;
        this.policy = policy;
        this.verification = verification;
        this.passwords = passwords;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ChallengeResource createChallenge(SecurityChallengeRequest request, String key) {
        String hash = tokens.intentHash("authPostAuthSecurityChallenges", request.scene().name(),
                request.clientNonce(), nullable(request.deviceFingerprint()));
        return idempotent(scope("ch", request.clientNonce()), key, hash,
                "auth-challenge-v1", ChallengeResource.class,
                () -> verification.createChallenge(request));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UserSessionResource passwordLogin(PasswordLoginRequest request, String key, String ip) {
        String hash = tokens.intentHash("authPostAuthPasswordLogin", request.phone(), request.password(),
                request.challengeId(), request.challengeProof(), deviceIntent(request.device()));
        return idempotent(scope("pl", request.phone()), key, hash, "user-auth-session-v1",
                UserSessionResource.class, () -> {
                    verification.verifyChallenge(request.challengeId(), request.challengeProof(), AuthScene.LOGIN);
                    UserAuthStore.CredentialRow credential = repository.findCredentialForUpdate(request.phone())
                            .orElseThrow(UserAuthService::badCredentials);
                    Instant now = Instant.now(clock);
                    if (!"ACTIVE".equals(credential.userStatus())) throw accountRestricted();
                    if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
                        throw accountRestricted();
                    }
                    boolean matches;
                    try { matches = passwords.matches(request.password(), credential.passwordHash()); }
                    catch (RuntimeException exception) { matches = false; }
                    if (!matches) {
                        int failures = credential.failedCount() + 1;
                        Instant lockedUntil = failures >= policy.passwordMaxFailures()
                                ? now.plus(policy.passwordLockDuration()) : null;
                        repository.recordPasswordFailure(credential.credentialId(), failures, lockedUntil);
                        throw badCredentials();
                    }
                    repository.clearPasswordFailures(credential.credentialId());
                    return createSession(credential.userId(), request.phone(), request.device(), ip, "PASSWORD");
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CommandResultResource sendSms(SmsSendRequest request, String key, String ip) {
        String hash = tokens.intentHash("authPostAuthSmsSend", request.phone(), request.scene().name(),
                request.challengeId(), request.challengeProof());
        return idempotent(scope("ss", request.phone()), key, hash, "sms-command-v1",
                CommandResultResource.class, () -> verification.sendSms(request, ip));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UserSessionResource smsLogin(SmsLoginRequest request, String key, String ip) {
        String hash = tokens.intentHash("authPostAuthSmsLogin", request.phone(), request.smsCode(),
                deviceIntent(request.device()));
        return idempotent(scope("sl", request.phone()), key, hash, "user-auth-session-v1",
                UserSessionResource.class, () -> {
                    UserAuthStore.UserRow user = repository.findUser(request.phone())
                            .orElseThrow(UserAuthService::badCredentials);
                    if (!"ACTIVE".equals(user.status())) throw accountRestricted();
                    verification.verifySms(request.phone(), AuthScene.LOGIN, request.smsCode());
                    return createSession(user.id(), request.phone(), request.device(), ip, "SMS");
                });
    }

    @Transactional(readOnly = true)
    public CommandResultResource validateInvite(InviteCodeValidateRequest request) {
        long inviterId = repository.findActiveInviter(request.inviteCode())
                .orElseThrow(() -> business("邀请码无效"));
        return new CommandResultResource(Long.toString(inviterId), null, "VALID", 0L, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public RegistrationConfigResource registrationConfig() {
        List<RegistrationAgreementVersionResource> versions = repository.findCurrentAgreementVersions().stream()
                .map(value -> new RegistrationAgreementVersionResource(
                        Long.toString(value.id()), value.code(), value.effectiveAt()))
                .toList();
        return new RegistrationConfigResource(versions);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UserSessionResource register(RegisterRequest request, String key, String ip) {
        String hash = tokens.intentHash("authPostAuthRegister", request.phone(), request.smsCode(),
                request.password(), request.inviteCode(), String.join("\u001f", request.agreementVersions()),
                deviceIntent(request.device()));
        return idempotent(scope("rg", request.phone()), key, hash, "user-auth-session-v1",
                UserSessionResource.class, () -> {
                    validatePassword(request.phone(), request.password());
                    List<Long> agreements;
                    try { agreements = repository.validateAgreementVersions(request.agreementVersions()); }
                    catch (IllegalArgumentException exception) { throw business("协议版本不符合要求"); }
                    repository.lockRegistration(request.phone());
                    if (repository.findUser(request.phone()).isPresent()) throw business("手机号已注册");
                    long inviterId = repository.findActiveInviter(request.inviteCode())
                            .orElseThrow(() -> business("邀请码无效"));
                    verification.verifySms(request.phone(), AuthScene.REGISTER, request.smsCode());
                    Instant now = Instant.now(clock);
                    long userId = repository.createUser(request.phone());
                    repository.createCredential(userId, passwords.encode(request.password()), now);
                    repository.createProfile(userId);
                    Device device = upsertDevice(userId, request.device(), now);
                    repository.recordRegistration(userId, request.phone(), request.inviteCode(),
                            inviterId, device.id(), ip);
                    repository.acceptAgreementVersions(userId, device.id(), agreements);
                    return createSession(userId, request.phone(), request.device(), ip, "REGISTER", device);
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CommandResultResource resetPassword(PasswordResetRequest request, String key) {
        String hash = tokens.intentHash("authPostAuthPasswordReset", request.phone(), request.smsCode(),
                request.newPassword());
        return idempotent(scope("pr", request.phone()), key, hash, "password-reset-v1",
                CommandResultResource.class, () -> {
                    validatePassword(request.phone(), request.newPassword());
                    UserAuthStore.CredentialRow credential = repository.findCredentialForUpdate(request.phone())
                            .orElseThrow(UserAuthService::badCredentials);
                    verification.verifySms(request.phone(), AuthScene.RESET_PASSWORD, request.smsCode());
                    Instant now = Instant.now(clock);
                    repository.updatePasswordAndRevokeSessions(credential.credentialId(), credential.userId(),
                            passwords.encode(request.newPassword()), now);
                    return new CommandResultResource(Long.toString(credential.userId()), null,
                            "PASSWORD_RESET", 0L, now);
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UserSessionResource refresh(RefreshRequest request, String headerRefreshToken, String key) {
        if (!tokens.sameSecret(headerRefreshToken, request.refreshToken())) throw unauthenticated();
        String oldHash = tokens.refreshHash(request.refreshToken());
        String requestHash = tokens.intentHash("authPostAuthRefresh", request.refreshToken(), request.deviceId());
        return idempotent(scope("rf", oldHash), key, requestHash, "user-auth-session-v1",
                UserSessionResource.class, () -> refreshSession(request, oldHash));
    }

    private UserSessionResource refreshSession(RefreshRequest request, String oldRefreshHash) {
        Instant now = Instant.now(clock);
        UserAuthStore.UserSessionRow session = repository.findSessionForUpdate(oldRefreshHash)
                .orElseThrow(UserAuthService::sessionRevoked);
        if (!"ACTIVE".equals(session.userStatus())) throw accountRestricted();
        if (session.expiresAt() == null || !session.expiresAt().isAfter(now)
                || session.deviceId() == null
                || !tokens.sameSecret(Long.toString(session.deviceId()), request.deviceId())) {
            throw sessionRevoked();
        }
        long nextVersion = session.version() + 1;
        String accessJti = UUID.randomUUID().toString();
        String refreshToken = tokens.newRefreshToken();
        String refreshHash = tokens.refreshHash(refreshToken);
        Instant accessExpires = now.plus(properties.accessTtl());
        Instant refreshExpires = now.plus(properties.refreshTtl());
        String accessToken = tokens.issueAccess(session.userId(), session.id(), nextVersion, accessJti, accessExpires);
        if (!repository.rotateSession(session.id(), session.version(), oldRefreshHash,
                accessJti, refreshHash, refreshExpires)) {
            throw new BusinessException("COMMON-409-VERSION_CONFLICT", "会话已被其他请求更新", 409, false);
        }
        repository.touchDevice(session.deviceId(), session.userId(), now);
        DeviceSummaryResource device = new DeviceSummaryResource(Long.toString(session.deviceId()),
                session.deviceName(), "ANDROID", null, null, now, null);
        return new UserSessionResource(accessToken, refreshToken, accessExpires,
                Long.toString(session.userId()), Long.toString(session.id()), device, List.of());
    }

    private UserSessionResource createSession(long userId, String phone, Map<String, Object> device,
                                              String ip, String method) {
        return createSession(userId, phone, device, ip, method, upsertDevice(userId, device, Instant.now(clock)));
    }

    private UserSessionResource createSession(long userId, String phone, Map<String, Object> values,
                                              String ip, String method, Device device) {
        Instant now = Instant.now(clock);
        String accessJti = UUID.randomUUID().toString();
        String refreshToken = tokens.newRefreshToken();
        String refreshHash = tokens.refreshHash(refreshToken);
        Instant refreshExpires = now.plus(properties.refreshTtl());
        long sessionId = repository.createSession(userId, device.id(), accessJti, refreshHash, refreshExpires);
        Instant accessExpires = now.plus(properties.accessTtl());
        String accessToken = tokens.issueAccess(userId, sessionId, 0L, accessJti, accessExpires);
        repository.recordLogin(userId, phone, device.id(), ip, method);
        DeviceSummaryResource summary = device.id() == null ? null : new DeviceSummaryResource(
                Long.toString(device.id()), device.model(), device.platform(), device.osVersion(),
                device.appVersion(), now, null);
        return new UserSessionResource(accessToken, refreshToken, accessExpires,
                Long.toString(userId), Long.toString(sessionId), summary, List.of());
    }

    private Device upsertDevice(long userId, Map<String, Object> values, Instant now) {
        if (values == null || values.isEmpty()) return new Device(null, null, "ANDROID", null, null);
        String rawFingerprint = string(values, "deviceFingerprint");
        if (rawFingerprint == null) rawFingerprint = string(values, "deviceId");
        if (rawFingerprint == null || rawFingerprint.isBlank()) return new Device(null, null, "ANDROID", null, null);
        String model = limited(string(values, "model"), 255);
        String platform = string(values, "platform");
        platform = Set.of("ANDROID", "WEB", "ADMIN_WEB").contains(platform) ? platform : "ANDROID";
        long id = repository.upsertDevice(userId,
                tokens.intentHash("device-fingerprint", rawFingerprint), model, now);
        return new Device(id, model, platform,
                limited(string(values, "osVersion"), 64), limited(string(values, "appVersion"), 32));
    }

    private void validatePassword(String phone, String password) {
        if (password.length() < policy.passwordMinLength() || password.length() > policy.passwordMaxLength()
                || (policy.passwordRequireLetters() && password.chars().noneMatch(Character::isLetter))
                || (policy.passwordRequireDigits() && password.chars().noneMatch(Character::isDigit))
                || password.equals(phone) || WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw business("密码不符合安全策略");
        }
    }

    private <T> T idempotent(String scope, String key, String requestHash,
                             String responseType, Class<T> type, Supplier<T> action) {
        UserAuthStore.IdempotencyClaim claim = repository.claimIdempotency(
                scope, key, requestHash, Instant.now(clock).plus(properties.idempotencyTtl()));
        if (claim.replay()) return replay(claim.row(), scope, key, requestHash, responseType, type);
        try {
            T result = action.get();
            byte[] plain = objectMapper.writeValueAsBytes(result);
            String cipher = snapshots.encrypt(scope, key, requestHash, responseType, plain);
            repository.completeIdempotencySnapshot(claim.row().id(), responseType + ":ok", responseType, cipher);
            return result;
        } catch (BusinessException exception) {
            repository.abandonIdempotency(claim.row().id());
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist user idempotency snapshot", exception);
        }
    }

    private <T> T replay(UserAuthStore.IdempotencyRow row, String scope, String key,
                         String requestHash, String responseType, Class<T> type) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null || !responseType.equals(row.responseType())
                || row.responsePayloadCiphertext() == null || row.responsePayloadCiphertext().isBlank()) {
            throw new BusinessException("COMMON-409-VERSION_CONFLICT", "同一请求仍在处理中", 409, true);
        }
        try {
            byte[] plain = snapshots.decrypt(scope, key, requestHash, responseType, row.responsePayloadCiphertext());
            return objectMapper.readValue(plain, type);
        } catch (UserIdempotencySnapshotCipher.SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("User idempotency snapshot is unavailable", exception);
        }
    }

    private String scope(String operation, String actor) {
        return "ua:" + operation + ":" + tokens.intentHash("idempotency-scope", actor).substring(0, 48);
    }
    private String deviceIntent(Map<String, Object> device) {
        try { return device == null ? "" : objectMapper.writeValueAsString(device); }
        catch (Exception exception) { throw business("设备信息无效"); }
    }
    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key); return value instanceof String text ? text : null;
    }
    private static String limited(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
    private static String nullable(String value) { return value == null ? "" : value; }
    private static BusinessException badCredentials() {
        return new BusinessException("COMMON-401-UNAUTHENTICATED", "账号或凭证不正确", 401, false);
    }
    private static BusinessException unauthenticated() {
        return new BusinessException("COMMON-401-UNAUTHENTICATED", "刷新令牌无效", 401, false);
    }
    private static BusinessException sessionRevoked() {
        return new BusinessException("AUTH-401-SESSION_REVOKED", "登录会话已失效", 401, false);
    }
    private static BusinessException accountRestricted() {
        return new BusinessException("AUTH-423-ACCOUNT_RESTRICTED", "账号已冻结或受限", 423, false);
    }
    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private record Device(Long id, String model, String platform, String osVersion, String appVersion) { }
}
