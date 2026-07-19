package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.access.user.UserAuthContracts.ChallengeResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.SecurityChallengeRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.SmsSendRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class UserAuthVerificationService {
    private static final int CHALLENGE_WIDTH = 160;
    private static final int CHALLENGE_HEIGHT = 56;
    private static final int GLYPH_SCALE = 4;
    private static final String[][] DIGIT_GLYPHS = {
            {"11111", "10001", "10011", "10101", "11001", "10001", "11111"},
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
            {"11110", "00001", "00001", "11110", "10000", "10000", "11111"},
            {"11110", "00001", "00001", "01110", "00001", "00001", "11110"},
            {"10010", "10010", "10010", "11111", "00010", "00010", "00010"},
            {"11111", "10000", "10000", "11110", "00001", "00001", "11110"},
            {"01111", "10000", "10000", "11110", "10001", "10001", "01110"},
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
            {"01110", "10001", "10001", "01111", "00001", "00001", "11110"},
    };
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
            throw businessRuleInvalid("短信场景无效");
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
        long id = parseChallengeId(challengeId, "安全挑战无效");
        UserAuthStore.ChallengeRow row = repository.findChallengeForUpdate(id)
                .orElseThrow(() -> challengeInvalid("安全挑战无效或已过期"));
        Instant now = Instant.now(clock);
        if (!scene.name().equals(row.type()) || row.usedAt() != null || row.expiresAt() == null
                || !row.expiresAt().isAfter(now) || row.attempts() >= row.maxAttempts()) {
            throw challengeInvalid("安全挑战无效或已过期");
        }
        String actual = tokens.intentHash("challenge:" + scene, proof);
        if (!tokens.sameSecret(row.answerHash(), actual)) {
            repository.failChallenge(id);
            throw challengeInvalid("安全验证失败");
        }
        if (!repository.consumeChallenge(id)) throw challengeInvalid("安全挑战已被使用");
    }

    public void verifySms(String phone, AuthScene scene, String code) {
        UserAuthStore.SmsCodeRow row = repository.findSmsCodeForUpdate(phone, scene.name())
                .orElseThrow(() -> businessRuleInvalid("短信验证码无效或已过期"));
        Instant now = Instant.now(clock);
        if (row.usedAt() != null || row.expiresAt() == null || !row.expiresAt().isAfter(now)
                || row.attempts() >= row.maxAttempts()) {
            throw businessRuleInvalid("短信验证码无效或已过期");
        }
        String actual = tokens.intentHash("sms:" + scene, phone, code);
        if (!tokens.sameSecret(row.codeHash(), actual)) {
            repository.failSmsCode(row.id());
            throw businessRuleInvalid("短信验证码无效或已过期");
        }
        if (!repository.consumeSmsCode(row.id())) throw businessRuleInvalid("短信验证码已被使用");
    }

    private static long parseChallengeId(String value, String message) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw challengeInvalid(message); }
    }

    static String renderChallenge(String answer) {
        if (answer == null || !answer.matches("\\d{4}")) {
            throw new IllegalArgumentException("challenge answer must contain four digits");
        }
        byte[] rgb = new byte[CHALLENGE_WIDTH * CHALLENGE_HEIGHT * 3];
        for (int offset = 0; offset < rgb.length; offset += 3) {
            rgb[offset] = (byte) 0xF2;
            rgb[offset + 1] = (byte) 0xF4;
            rgb[offset + 2] = (byte) 0xF8;
        }
        for (int index = 0; index < answer.length(); index++) {
            drawDigit(rgb, DIGIT_GLYPHS[answer.charAt(index) - '0'], 24 + index * 29, 14);
        }
        return Base64.getEncoder().encodeToString(encodePng(rgb));
    }

    private static void drawDigit(byte[] rgb, String[] glyph, int startX, int startY) {
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < glyph[row].length(); column++) {
                if (glyph[row].charAt(column) != '1') continue;
                for (int dy = 0; dy < GLYPH_SCALE; dy++) {
                    for (int dx = 0; dx < GLYPH_SCALE; dx++) {
                        int x = startX + column * GLYPH_SCALE + dx;
                        int y = startY + row * GLYPH_SCALE + dy;
                        int offset = (y * CHALLENGE_WIDTH + x) * 3;
                        rgb[offset] = 0x17;
                        rgb[offset + 1] = 0x20;
                        rgb[offset + 2] = 0x33;
                    }
                }
            }
        }
    }

    private static byte[] encodePng(byte[] rgb) {
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                int rowBytes = CHALLENGE_WIDTH * 3;
                for (int y = 0; y < CHALLENGE_HEIGHT; y++) {
                    deflater.write(0);
                    deflater.write(rgb, y * rowBytes, rowBytes);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(png)) {
                output.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
                ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                try (DataOutputStream header = new DataOutputStream(headerBytes)) {
                    header.writeInt(CHALLENGE_WIDTH);
                    header.writeInt(CHALLENGE_HEIGHT);
                    header.writeByte(8);
                    header.writeByte(2);
                    header.writeByte(0);
                    header.writeByte(0);
                    header.writeByte(0);
                }
                writeChunk(output, "IHDR", headerBytes.toByteArray());
                writeChunk(output, "IDAT", compressed.toByteArray());
                writeChunk(output, "IEND", new byte[0]);
            }
            return png.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("unable to encode challenge image", exception);
        }
    }

    private static void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        output.writeInt((int) crc.getValue());
    }

    private static BusinessException challengeInvalid(String message) {
        return new BusinessException("AUTH-422-SECURITY_CHALLENGE_INVALID", message, 422, false);
    }

    private static BusinessException businessRuleInvalid(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }
}
