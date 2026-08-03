package cc.orbexa.hhy.boot.payment;

import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies exact callback bytes before JSON parsing or persistence. */
public final class R17PaymentSignatureVerifier {
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);
    private final Map<String, byte[]> secrets;
    private final Clock clock;

    public R17PaymentSignatureVerifier(String configuredSecrets, Clock clock) {
        this.secrets = parse(configuredSecrets);
        this.clock = clock;
    }

    public ProviderCallbackContext verify(
            String gateway,
            String timestamp,
            String nonce,
            String signature,
            String idempotencyKey,
            String rawBody) {
        String normalizedGateway = required(gateway, 32).toUpperCase(Locale.ROOT);
        String normalizedTimestamp = required(timestamp, 64);
        String normalizedNonce = required(nonce, 128);
        String normalizedSignature = required(signature, 256);
        if (!normalizedNonce.matches("^[A-Za-z0-9_-]{16,128}$")) reject();
        Instant callbackTime;
        try {
            callbackTime = Instant.parse(normalizedTimestamp);
        } catch (Exception failure) {
            throw rejected();
        }
        if (Duration.between(callbackTime, Instant.now(clock)).abs().compareTo(MAX_SKEW) > 0) reject();
        byte[] secret = secrets.get(normalizedGateway);
        if (secret == null) reject();
        String canonical = normalizedTimestamp + "\n" + normalizedNonce + "\n"
                + normalizedGateway + "\n" + rawBody;
        byte[] expected = hmac(secret, canonical.getBytes(StandardCharsets.UTF_8));
        byte[] supplied = decodeSignature(normalizedSignature);
        if (supplied == null || !MessageDigest.isEqual(expected, supplied)) reject();
        return new ProviderCallbackContext(
                normalizedGateway,
                normalizedTimestamp,
                normalizedNonce,
                normalizedSignature,
                required(idempotencyKey, 128),
                rawBody);
    }

    static String signForTest(byte[] secret, String timestamp, String nonce, String gateway, String body) {
        return HexFormat.of().formatHex(hmac(
                secret,
                (timestamp + "\n" + nonce + "\n" + gateway + "\n" + body)
                        .getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, byte[]> parse(String configured) {
        Map<String, byte[]> result = new HashMap<>();
        if (configured == null || configured.isBlank()) return Map.of();
        for (String item : configured.split(",")) {
            String[] pair = item.strip().split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new IllegalStateException("Invalid HHY_PAYMENT_NOTIFY_SECRETS entry");
            }
            byte[] secret;
            try {
                secret = Base64.getUrlDecoder().decode(pair[1]);
            } catch (IllegalArgumentException failure) {
                throw new IllegalStateException("Payment callback secret must be base64url", failure);
            }
            if (secret.length < 32) throw new IllegalStateException("Payment callback secret is too short");
            result.put(pair[0].strip().toUpperCase(Locale.ROOT), secret.clone());
        }
        return Map.copyOf(result);
    }

    private static byte[] decodeSignature(String value) {
        try {
            if (value.matches("^[0-9a-fA-F]{64}$")) return HexFormat.of().parseHex(value);
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private static byte[] hmac(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        }
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) reject();
        return value.strip();
    }

    private static void reject() {
        throw rejected();
    }

    private static BusinessException rejected() {
        return new BusinessException(
                "COMMON-401-UNAUTHENTICATED", "支付回调签名无效或已过期", 401, false);
    }
}
