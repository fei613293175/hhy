package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.access.user.UserAuthContracts.ChallengeResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SecurityChallengeRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SmsSendRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class UserAuthVerificationService {
    private final UserAuthStore repository;
    private final UserAuthPolicy policy;
    private final UserTokenService tokens;
    private final ObjectProvider<SmsProvider> smsProviders;
    private final Clock clock;

    public UserAuthVerificationService(UserAuthStore repository, UserAuthPolicy policy,
                                       UserTokenService tokens, ObjectProvider<SmsProvider> smsProviders,
                                       Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.tokens = tokens;
        this.smsProviders = smsProviders;
        this.clock = clock;
    }

    public ChallengeResource createChallenge(SecurityChallengeRequest request) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(policy.challengeTtl());
        String answer = tokens.newNumericCode(4);
        String answerHash = tokens.intentHash("challenge:" + request.scene(), answer);
        String nonceHash = tokens.intentHash("challenge-nonce", request.clientNonce());
        String fingerprintHash = request.deviceFingerprint() == null ? null
                : tokens.intentHash("device-fingerprint", request.deviceFingerprint());
        long id = repository.createChallenge(request.scene().name(), answerHash, nonceHash,
                fingerprintHash, expiresAt, policy.challengeMaxAttempts());
        return new ChallengeResource(Long.toString(id), "INTERNAL_IMAGE", expiresAt,
                renderChallenge(answer), null);
    }

    public CommandResultResource sendSms(SmsSendRequest request, String ip) {
        SmsProvider provider = smsProviders.getIfAvailable();
        if (provider == null) {
            throw new BusinessException("COMMON-500-INTERNAL", "短信供应商尚未激活", 500, false);
        }
        if (request.scene() == AuthScene.SENSITIVE_OPERATION) {
            throw invalid("短信场景无效");
        }
        verifyChallenge(request.challengeId(), request.challengeProof(), request.scene());
        Instant now = Instant.now(clock);
        if (repository.smsCooldownActive(request.phone(), request.scene().name(), now.minus(policy.smsCooldown()))) {
            throw new BusinessException("COMMON-429-RATE_LIMITED", "验证码发送过于频繁", 429, true);
        }
        if (repository.smsPhoneCountToday(request.phone()) >= policy.smsDailyPhoneLimit()
                || repository.smsIpCountToday(ip) >= policy.smsDailyIpLimit()) {
            throw new BusinessException("COMMON-429-RATE_LIMITED", "验证码发送次数已达上限", 429, false);
        }
        String code = tokens.newNumericCode(policy.smsCodeLength());
        String codeHash = tokens.intentHash("sms:" + request.scene(), request.phone(), code);
        long smsId = repository.createSmsCode(request.phone(), request.scene().name(), codeHash,
                now.plus(policy.smsTtl()), policy.smsMaxAttempts());
        SmsProvider.DeliveryReceipt receipt = provider.sendVerificationCode(request.phone(), request.scene(), code);
        if (receipt == null || receipt.providerMessageId() == null || receipt.providerMessageId().isBlank()) {
            throw new IllegalStateException("SMS provider returned no delivery receipt");
        }
        repository.recordSmsDelivery(smsId, request.phone(), request.scene().name(),
                receipt.templateCode(), receipt.providerMessageId(), ip);
        return new CommandResultResource(Long.toString(smsId), receipt.providerMessageId(),
                "SENT", 0L, now);
    }

    public void verifyChallenge(String challengeId, String proof, AuthScene scene) {
        long id = parseId(challengeId, "安全挑战无效");
        UserAuthStore.ChallengeRow row = repository.findChallengeForUpdate(id)
                .orElseThrow(() -> invalid("安全挑战无效或已过期"));
        Instant now = Instant.now(clock);
        if (!scene.name().equals(row.type()) || row.usedAt() != null || row.expiresAt() == null
                || !row.expiresAt().isAfter(now) || row.attempts() >= row.maxAttempts()) {
            throw invalid("安全挑战无效或已过期");
        }
        String actual = tokens.intentHash("challenge:" + scene, proof);
        if (!tokens.sameSecret(row.answerHash(), actual)) {
            repository.failChallenge(id);
            throw invalid("安全验证失败");
        }
        if (!repository.consumeChallenge(id)) throw invalid("安全挑战已被使用");
    }

    public void verifySms(String phone, AuthScene scene, String code) {
        UserAuthStore.SmsCodeRow row = repository.findSmsCodeForUpdate(phone, scene.name())
                .orElseThrow(() -> invalid("短信验证码无效或已过期"));
        Instant now = Instant.now(clock);
        if (row.usedAt() != null || row.expiresAt() == null || !row.expiresAt().isAfter(now)
                || row.attempts() >= row.maxAttempts()) {
            throw invalid("短信验证码无效或已过期");
        }
        String actual = tokens.intentHash("sms:" + scene, phone, code);
        if (!tokens.sameSecret(row.codeHash(), actual)) {
            repository.failSmsCode(row.id());
            throw invalid("短信验证码无效或已过期");
        }
        if (!repository.consumeSmsCode(row.id())) throw invalid("短信验证码已被使用");
    }

    private static long parseId(String value, String message) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw invalid(message); }
    }

    private static String renderChallenge(String answer) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='160' height='56'>"
                + "<rect width='160' height='56' fill='#f2f4f8'/><text x='24' y='39' "
                + "font-family='monospace' font-size='32' letter-spacing='9' fill='#172033'>"
                + answer + "</text></svg>";
        return Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }
}
